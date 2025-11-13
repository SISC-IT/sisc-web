# transformer/training/train_transformer.py
from __future__ import annotations

# ─────────────────────────────────────────────────────────────────────────────
#  코드 설명
#  1) DB(PostgreSQL, psycopg2)에서 public.price_data로부터 "모든" 티커와 일봉 데이터 추출
#  2) 수집된 일봉 데이터를 기반으로 사용자 정의 피처를 생성하고
#  3) 미래 수익률 라벨링(BUY/HOLD/SELL) 후 Transformer 분류 모델을 학습
#  4) 최적 가중치(.h5)와 스케일러(.pkl)를 저장
#
# 주의:
#  - 시간대 처리는 일관성을 위해 기본적으로 UTC 'date' 컬럼을 우선 사용한다.
#  - run_date 컷은 Asia/Seoul 기준 날짜를 UTC로 변환 후 비교한다.
#  - 대량 티커 수집 시 API 호출 간 sleep을 넣어 서버 과부하/차단을 완화한다.
# ─────────────────────────────────────────────────────────────────────────────

from typing import Dict, List, Optional, Any
import os
import pickle
import sys
import numpy as np
import pandas as pd
from sklearn.preprocessing import MinMaxScaler
from sklearn.model_selection import train_test_split
import tensorflow as tf
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau

# --- 프로젝트/레포 경로 설정 ---------------------------------------------------
_this_file = os.path.abspath(__file__)
project_root = os.path.dirname(os.path.dirname(_this_file))      # .../transformer
repo_root    = os.path.dirname(project_root)                     # .../
libs_root    = os.path.join(repo_root, "libs")                   # .../libs

# sys.path에 중복 없이 추가
for p in (project_root, repo_root, libs_root):
    if p not in sys.path:
        sys.path.append(p)
# ------------------------------------------------------------------------------

from modules.features import FEATURES, build_features
from modules.models import build_transformer_classifier
from libs.utils.get_db_conn import get_db_conn

# from AI.libs.utils.io import _log
_log = print  # TODO: io._log 로 교체

# DB 이름(프로젝트 환경에 맞춰 설정)
db_name = "db"

# 클래스 이름 매핑(라벨→사람이 읽을 수 있는 문자열)
CLASS_NAMES = ["BUY", "HOLD", "SELL"]


# =============================================================================
# DB에서 "모든" 티커 목록 가져오기 (제한 없음)
#  - PostgreSQL(psycopg2) 연결을 get_db_conn(db_name)으로 얻는다고 가정
#  - 스키마: public.price_data (PK: (ticker, date))
# =============================================================================
def load_all_tickers_from_db(verbose: bool = True) -> List[str]:
    """
    public.price_data에서 사용 가능한 모든 티커를 DISTINCT로 추출하여 반환한다.

    반환
    ----
    List[str]
        대문자 티커 문자열 리스트(중복 제거/공백 제거/알파벳 정렬)

    구현 상세
    --------
    - psycopg2 커넥션을 사용한다고 가정하고, 사용 후 conn.close()로 커넥션을 닫는다.
    - DataFrame 정리 단계에서 NULL/빈문자열, 공백 등을 제거한다.
    """
    conn = get_db_conn(db_name)  # psycopg2 커넥션 또는 SQLAlchemy 엔진
    try:
        sql = """
            SELECT DISTINCT ticker
            FROM public.price_data
            WHERE ticker IS NOT NULL AND ticker <> ''
        """
        df = pd.read_sql(sql, conn)
    finally:
        # psycopg2 커넥션일 경우 명시적으로 닫아 리소스 누수 방지
        try:
            conn.close()
        except Exception:
            pass

    if df.empty or "ticker" not in df.columns:
        raise RuntimeError("[load_all_tickers_from_db] price_data에서 티커를 찾지 못했습니다.")

    # 문자열 정리: 공백 제거 → 대문자화 → 결측/중복 제거 → 정렬
    tickers = (
        df["ticker"]
        .astype(str)
        .str.strip()
        .str.upper()
        .dropna()
        .drop_duplicates()
        .tolist()
    )
    tickers = sorted([t for t in tickers if t])  # 안전한 최종 정리

    if not tickers:
        raise RuntimeError("[load_all_tickers_from_db] 정리 후 유효한 티커가 없습니다. DB 데이터를 확인하세요.")

    if verbose:
        _log(f"[DB] 모든 티커 로드 완료: {len(tickers)}개. 예시: {tickers[:10]}")

    return tickers


# =============================================================================
# 1) 라벨링 정책 (분류)
# =============================================================================
def _label_by_future_return(close: pd.Series, pred_h: int, hold_thr: float = 0.003) -> pd.Series:
    """
    미래 수익률 기준 다중분류 라벨 생성.
    - r = (C[t+pred_h] / C[t]) - 1
    - |r| <= hold_thr  → HOLD
    - r  > hold_thr    → BUY
    - r  < -hold_thr   → SELL
    ※ pred_h 이후 데이터가 없으면 NaN

    Parameters
    ----------
    close : pd.Series
        종가 시계열 (인덱스: datetime)
    pred_h : int
        예측 지평(캔들 수)
    hold_thr : float
        HOLD 구간 임계치 (절댓값 기준)
    """
    future = close.shift(-pred_h)
    r = (future / close) - 1.0

    buy = (r > hold_thr).astype(int)
    sell = (r < -hold_thr).astype(int)
    hold = ((r.abs() <= hold_thr) & r.notna()).astype(int)

    # np.select는 조건을 위에서부터 평가하므로, 우선순위: BUY > HOLD > SELL
    label = np.select([buy.eq(1), hold.eq(1), sell.eq(1)], [0, 1, 2], default=np.nan)
    return pd.Series(label, index=close.index, dtype="float")


# =============================================================================
# 2) 시퀀스/스케일링 유틸
# =============================================================================
def _build_sequences(feats: pd.DataFrame, use_cols: List[str], seq_len: int) -> np.ndarray:
    """
    주어진 피처 프레임에서 rolling window 방식으로 (N, seq_len, n_features) 시퀀스 배열 생성.
    - NaN 포함 구간은 제외됨 (사전에 dropna 권장)
    - feats.index는 datetime(정렬 완료)이어야 함
    """
    X_list = []
    for i in range(seq_len, len(feats) + 1):
        window = feats[use_cols].iloc[i - seq_len : i].values.astype("float32")
        # NaN이 있으면 스킵 (안전장치)
        if np.isnan(window).any():
            continue
        X_list.append(window)
    if not X_list:
        return np.empty((0, seq_len, len(use_cols)), dtype="float32")
    return np.stack(X_list, axis=0)


def _align_labels(feats: pd.DataFrame, labels: pd.Series, seq_len: int) -> np.ndarray:
    """
    시퀀스 끝 시점에 대한 라벨을 맞추기 위해, 시퀀스 시작 오프셋(seq_len-1)만큼 라벨을 잘라서 정렬.
    - feats, labels는 동일 인덱스(날짜)여야 함
    """
    return labels.iloc[seq_len - 1 :].values


def _fit_scaler_on_train(X: np.ndarray) -> MinMaxScaler:
    """
    학습 데이터 전체 분포에 맞춰 스케일러 적합.
    - 입력 X: (N, seq_len, n_features)
    - 스케일은 feature-wise로 수행하기 위해 2D로 변형 후 적합
    """
    _, _, f = X.shape
    scaler = MinMaxScaler(feature_range=(0, 1), clip=True)
    X2 = X.reshape(-1, f)  # (N*seq_len, n_features)
    scaler.fit(X2)
    return scaler


def _apply_scaler(X: np.ndarray, scaler: MinMaxScaler) -> np.ndarray:
    """학습/검증/테스트에 동일 스케일 적용."""
    n, s, f = X.shape
    X2 = X.reshape(-1, f)
    X2 = scaler.transform(X2)
    return X2.reshape(n, s, f).astype("float32")


# =============================================================================
# 3) 학습 메인 파이프라인
# =============================================================================
def train_transformer_classifier(
    *,
    raw_data: pd.DataFrame,
    seq_len: int,
    pred_h: int,
    model_out_path: str,
    scaler_out_path: Optional[str] = None,
    tickers: Optional[List[str]] = None,
    run_date: Optional[str] = None,
    test_size: float = 0.2,
    random_state: int = 42,
    hold_thr: float = 0.003,
    batch_size: int = 64,
    epochs: int = 50,
) -> Dict[str, Any]:
    """
    Transformer 분류기 학습 파이프라인.
    - 입력: 원천 OHLCV(raw_data; 여러 티커 혼합 가능)
    - 처리: 피처 생성 → 시퀀스 빌드 → 라벨링 → 스케일 → 학습
    - 출력: history, 저장된 가중치 경로 등

    Parameters
    ----------
    raw_data : DataFrame
        ['ticker','open','high','low','close','volume', ('ts_local' or 'date')] 포함
    seq_len : int
        입력 시퀀스 길이
    pred_h : int
        미래 라벨링 지평(일수/캔들수)
    model_out_path : str
        최종 가중치 저장 경로(.h5 권장):
    scaler_out_path : str, optional
        스케일러 저장 경로(.pkl). 추론 시 동일 스케일 사용을 원할 때 권장.
    tickers : list, optional
        학습 대상 티커 필터. None이면 모두 사용.
    run_date : str, optional
        'YYYY-MM-DD'. 이 날짜(포함)까지의 데이터 사용.
    test_size : float
        train/val 분할 비율.
    hold_thr : float
        HOLD 라벨 임계값(|r| <= hold_thr).
    """
    # ---------- 데이터 준비 ----------
    if raw_data is None or raw_data.empty:
        raise ValueError("raw_data가 비어있습니다.")

    df = raw_data.copy()

    # 컬럼 소문자화(혼용 방지). 단, tz 정보 유지를 위해 시점 컬럼 해석은 아래에서 별도로 처리
    df = df.rename(columns={c: c.lower() for c in df.columns})

    # ⚠️ 시간대 컬럼 선택 정책:
    #  - 항상 'date'(UTC)를 우선 사용 → run_date 컷 등에서 일관성 확보
    #  - 'date'가 없고 'ts_local'(Asia/Seoul)만 있으면 tz-aware로 변환/보존
    ts_col = "date" if "date" in df.columns else ("ts_local" if "ts_local" in df.columns else None)
    if ts_col is None:
        raise ValueError("raw_data에 'date' 또는 'ts_local' 컬럼이 필요합니다.")

    if ts_col == "date":
        # 'date'는 UTC 기준 타임스탬프로 파싱
        df[ts_col] = pd.to_datetime(df[ts_col], utc=True, errors="coerce")
    else:
        # 'ts_local'만 있는 경우: Asia/Seoul로 인식(naive면 현지 부여)
        df[ts_col] = pd.to_datetime(df[ts_col], errors="coerce")
        if df[ts_col].dt.tz is None:
            df[ts_col] = df[ts_col].dt.tz_localize("Asia/Seoul")

    if df[ts_col].isna().any():
        raise ValueError("타임스탬프 파싱 중 NaT가 발생했습니다. 원본 데이터를 확인하세요.")

    if tickers is not None:
        df["ticker"] = df["ticker"].astype(str)
        df = df[df["ticker"].isin([str(t) for t in tickers])]

    # run_date 컷 (Asia/Seoul 기준 날짜 → UTC로 변환하여 비교)
    if run_date is not None:
        # Asia/Seoul 자정까지 포함되도록 끝점 계산
        end_dt = pd.to_datetime(run_date).tz_localize("Asia/Seoul", nonexistent="shift_forward").normalize()
        end_dt = end_dt + pd.Timedelta(days=1) - pd.Timedelta(microseconds=1)
        # df는 UTC(date) 또는 Asia/Seoul(ts_local)일 수 있으므로 UTC로 변환해 비교
        end_cut_utc = end_dt.tz_convert("UTC")
        # 비교 시 df[ts_col]도 UTC 기준으로 맞춰 사용
        compare_ts = df[ts_col].dt.tz_convert("UTC")
        df = df[compare_ts <= end_cut_utc]

    # 정렬
    df = df.sort_values(["ticker", ts_col]).reset_index(drop=True)

    # ---------- 피처 + 라벨 ----------
    # 모델 입력 피처 후보 (CLOSE_RAW는 라벨링용으로만 사용하고 입력 피처에서는 제외)
    model_feats = [f for f in FEATURES if f != "CLOSE_RAW"]
    X_all, y_all = [], []

    for t, g in df.groupby("ticker", sort=False):
        # 모델 피처 함수가 'date' 인덱스를 기대한다고 가정
        g = g.rename(columns={ts_col: "date"}).set_index("date")
        ohlcv = g[["open", "high", "low", "close", "volume"]].copy()

        # 사용자 정의 피처 빌드 (FEATURES와 build_features는 프로젝트 모듈 제공)
        feats = build_features(ohlcv)  # 반드시 'CLOSE_RAW' 포함한다고 가정
        if len(feats) < (seq_len + pred_h + 1):
            # 시퀀스/라벨링 최소 길이 부족 시 스킵
            _log(f"[WARN] {t}: 데이터가 부족하여 스킵 (len={len(feats)})")
            continue

        # 라벨 생성 (미래 수익률 기준)
        labels = _label_by_future_return(feats["CLOSE_RAW"], pred_h=pred_h, hold_thr=hold_thr)

        # 동시 NaN 제거 및 정렬
        feats = feats.dropna()
        labels = labels.reindex(feats.index)

        # 라벨 NaN(미래 없음) 제거
        valid_mask = labels.notna()
        feats = feats[valid_mask]
        labels = labels[valid_mask]

        # 시퀀스/라벨 정렬
        X_seq = _build_sequences(feats, model_feats, seq_len)
        y_seq = _align_labels(feats, labels, seq_len)

        # 마지막 pred_h 구간은 미래가 없어 NaN일 수 있음 → 제거
        valid_idx = ~np.isnan(y_seq)
        X_seq = X_seq[valid_idx]
        y_seq = y_seq[valid_idx].astype(int)

        if len(X_seq) == 0:
            _log(f"[WARN] {t}: 유효 시퀀스 0개 (정책/길이 확인)")
            continue

        X_all.append(X_seq)
        y_all.append(y_seq)

    if not X_all:
        raise ValueError("학습에 사용할 수 있는 시퀀스가 생성되지 않았습니다. (데이터 길이/라벨 정책 확인)")

    X = np.concatenate(X_all, axis=0)  # (N, seq_len, n_features)
    y = np.concatenate(y_all, axis=0)  # (N,)

    # ---------- 스케일링(학습 데이터 기준) ----------
    scaler = _fit_scaler_on_train(X)
    X = _apply_scaler(X, scaler)

    # 클래스 불균형이 심할 수 있으니 stratify 분할 권장
    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=test_size, random_state=random_state, stratify=y
    )

    # ---------- 모델 ----------
    n_features = X.shape[-1]
    model = build_transformer_classifier(seq_len=seq_len, n_features=n_features)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )

    # ---------- 콜백 ----------
    os.makedirs(os.path.dirname(model_out_path), exist_ok=True)
    ckpt = ModelCheckpoint(
        filepath=model_out_path,
        monitor="val_accuracy",
        save_best_only=True,
        save_weights_only=True,
        verbose=1
    )
    es = EarlyStopping(monitor="val_accuracy", patience=8, restore_best_weights=True, verbose=1)
    rlrop = ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=4, min_lr=1e-5, verbose=1)

    # ---------- 학습 ----------
    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=epochs,
        batch_size=batch_size,
        callbacks=[ckpt, es, rlrop],
        verbose=1
    )

    # ---------- 스케일러 저장(옵션) ----------
    if scaler_out_path:
        os.makedirs(os.path.dirname(scaler_out_path), exist_ok=True)
        with open(scaler_out_path, "wb") as f:
            pickle.dump(scaler, f)
        _log(f"[TRAIN] Scaler saved: {scaler_out_path}")

    _log(f"[TRAIN] Weights saved(최고): {model_out_path}")
    return {
        "history": history.history,
        "n_samples": int(len(X)),
        "class_dist": {int(k): int(v) for k, v in zip(*np.unique(y, return_counts=True))},
        "model_path": model_out_path,
        "scaler_path": scaler_out_path
    }


# =============================================================================
# DB에서 OHLCV 수집 (public.price_data)
#  - 스키마: (ticker TEXT, date DATE, open/high/low/close NUMERIC, volume BIGINT, adjusted_close NUMERIC)
#  - 대량 티커를 대비해 IN 절을 청크로 나눠 반복 조회
#  - 반환 컬럼: ['ticker','date(UTC tz-aware)','open','high','low','close','volume','ts_local(Asia/Seoul)']
# =============================================================================
def _fetch_db_ohlcv_for_tickers(
    tickers: List[str],
    start_date: str,
    end_date: str,
    use_adjusted_close: bool = True,
    chunk_size: int = 200,
) -> pd.DataFrame:
    """
    DB에서 지정한 티커 리스트와 날짜 구간에 해당하는 OHLCV를 읽어 하나의 DataFrame으로 반환.

    Parameters
    ----------
    tickers : List[str]
        조회할 티커 목록(대문자/소문자 무관, 내부에서 그대로 비교)
    start_date : str
        'YYYY-MM-DD' (price_data.date는 DATE 타입 기준)
    end_date : str
        'YYYY-MM-DD' (포함 조건)
    use_adjusted_close : bool
        True면 adjusted_close가 있는 행은 그 값을 close로 대체(배당/분할 반영)
    chunk_size : int
        너무 많은 티커로 IN 절이 길어지는 것을 방지하기 위한 청크 크기

    Returns
    -------
    DataFrame
        ['ticker','date','open','high','low','close','volume','ts_local'] 정렬 완료
    """
    conn = get_db_conn(db_name)
    try:
        frames = []
        # 티커 청크 분할
        for i in range(0, len(tickers), chunk_size):
            chunk = tickers[i:i+chunk_size]
            # IN 절 플레이스홀더 생성: (%s, %s, ..., %s)
            placeholders = ",".join(["%s"] * len(chunk))
            sql = f"""
                SELECT
                    ticker,
                    date,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    adjusted_close
                FROM public.price_data
                WHERE date >= %s
                  AND date <= %s
                  AND ticker IN ({placeholders})
                ORDER BY ticker, date
            """
            params = [start_date, end_date] + chunk
            df = pd.read_sql(sql, conn, params=params)
            if not df.empty:
                frames.append(df)
        if not frames:
            return pd.DataFrame(columns=["ticker","date","open","high","low","close","volume","ts_local"])

        out = pd.concat(frames, ignore_index=True)

        # ---- 데이터 정리: 숫자형 변환 (NUMERIC -> float) ----
        num_cols = ["open","high","low","close","volume","adjusted_close"]
        for c in num_cols:
            if c in out.columns:
                out[c] = pd.to_numeric(out[c], errors="coerce")

        # ---- 조정 종가 적용 옵션 ----
        if use_adjusted_close and "adjusted_close" in out.columns:
            # adjusted_close가 존재할 때만 대체(결측은 원래 close 유지)
            out["close"] = np.where(out["adjusted_close"].notna(), out["adjusted_close"], out["close"])

        # ---- 타임존 컬럼 구성 ----
        # DB의 date는 "캘린더 날짜"이므로 UTC 자정으로 타임스탬프화
        out["date"] = pd.to_datetime(out["date"], format="%Y-%m-%d", errors="coerce").dt.tz_localize("UTC")
        out["ts_local"] = out["date"].dt.tz_convert("Asia/Seoul")

        # ---- 최종 컬럼/정렬 ----
        out = out[["ticker","date","open","high","low","close","volume","ts_local"]].sort_values(["ticker","date"])
        out = out.dropna(subset=["open","high","low","close"])  # 필수값 결측 제거
        return out.reset_index(drop=True)
    finally:
        try:
            conn.close()
        except Exception:
            pass

# =============================================================================
# 5) 단독 실행(초기 가중치 생성)용 CLI 엔트리포인트
# =============================================================================
def run_training(config: dict):
    """config 딕셔너리 기반 Transformer 학습 실행"""

    # ---- (A) 사용할 티커 소스 결정 ----
    use_db = (config.get("tickers_source", "db") == "db") or (not config.get("tickers"))
    if use_db:
        tickers = load_all_tickers_from_db(verbose=True)  # ← DB에서 "모든" 티커
    else:
        tickers = [str(t).upper() for t in config["tickers"]]
        _log(f"[CFG] 수동 입력 티커 {len(tickers)}개 사용: {tickers[:8]}...")

    # ---- 1) 데이터 수집: DB에서 가격 읽기 ----
    #  - config["start"], ["end"]는 'YYYY-MM-DD' 문자열로 받았다고 가정
    #  - price_data.date (DATE)와 동일 포맷이므로 그대로 전달
    use_adjusted = bool(config.get("use_adjusted_close", True))
    raw = _fetch_db_ohlcv_for_tickers(
        tickers=tickers,
        start_date=config["start"],
        end_date=config["end"],
        use_adjusted_close=use_adjusted,
        chunk_size=int(config.get("db_chunk_size", 200)),
    )

    if raw.empty:
        raise RuntimeError("[run_training] DB에서 아무 데이터도 읽히지 않았습니다. 기간/티커/DB 내용을 확인하세요.")

    # ---- 2) 학습 ----
    os.makedirs(os.path.dirname(config["model_out"]), exist_ok=True)
    os.makedirs(os.path.dirname(config["scaler_out"]), exist_ok=True)

    result = train_transformer_classifier(
        raw_data=raw,
        seq_len=config["seq_len"],
        pred_h=config["pred_h"],
        model_out_path=config["model_out"],
        scaler_out_path=config["scaler_out"],
        tickers=tickers,            # 실제 학습대상 기록
        run_date=config.get("run_date"),
        test_size=config["test_size"],
        hold_thr=config["hold_thr"],
        batch_size=config["batch_size"],
        epochs=config["epochs"],
    )

    # ---- 3) 요약 ----
    _log("[DONE] -------- Summary --------")
    _log(f"Samples: {result['n_samples']}")
    _log(f"Class dist (0:BUY,1:HOLD,2:SELL): {result['class_dist']}")
    _log(f"Weights: {result['model_path']}")
    _log(f"Scaler : {result['scaler_path']}")

if __name__ == "__main__":
    from datetime import datetime

    # 오늘 날짜 YYYYMMDD 문자열 생성
    today_str = datetime.now().strftime("%Y%m%d")

    config = {
        # --- 데이터/티커 소스 ---
        "tickers_source": "db",
        "use_adjusted_close": True,
        "db_chunk_size": 200,

        # --- 기간/빈도 ---
        "start": "2018-01-01",
        "end": "2024-10-31",

        # --- 시퀀스/라벨 ---
        "seq_len": 128,
        "pred_h": 7,
        "hold_thr": 0.004,

        # --- 학습/평가 ---
        "test_size": 0.2,
        "epochs": 50,
        "batch_size": 512,

        # --- 출력 경로 (오늘날짜 적용) ---
        "model_out": f"AI/transformer/weights/{today_str}.weights.h5",
        "scaler_out": "AI/transformer/scaler/scaler.pkl",

        # --- 기타 ---
        "run_date": None,
    }

    run_training(config)
