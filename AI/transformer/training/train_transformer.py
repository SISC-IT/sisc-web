# transformer/training/train_transformer.py
from __future__ import annotations
from typing import Dict, List, Optional
import os
import time
import pickle
import sys
import requests  # ← yfinance SSL 이슈 회피용: REST 직접 호출
import numpy as np
import pandas as pd
from sklearn.preprocessing import MinMaxScaler
from sklearn.model_selection import train_test_split
import tensorflow as tf
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau


# --- 프로젝트 루트 경로 설정 ---------------------------------------------------
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(project_root)
# ---------------------------------------------------------------------------

from modules.features import FEATURES, build_features
from modules.models import build_transformer_classifier

# from AI.libs.utils.io import _log
_log = print  # TODO: io._log 로 교체

CLASS_NAMES = ["BUY", "HOLD", "SELL"]

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

    buy  = (r >  hold_thr).astype(int)
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
) -> Dict[str, any]:
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
    # 컬럼 소문자화(혼용 방지)
    df = df.rename(columns={c: c.lower() for c in df.columns})
    ts_col = "ts_local" if "ts_local" in df.columns else ("date" if "date" in df.columns else None)
    if ts_col is None:
        raise ValueError("raw_data에 'ts_local' 또는 'date' 컬럼이 필요합니다.")
    df[ts_col] = pd.to_datetime(df[ts_col], utc=True, errors="coerce")
    if df[ts_col].isna().any():
        raise ValueError("타임스탬프 파싱 중 NaT가 발생했습니다. 원본 데이터를 확인하세요.")

    if tickers is not None:
        df["ticker"] = df["ticker"].astype(str)
        df = df[df["ticker"].isin([str(t) for t in tickers])]

    # run_date 컷 (Asia/Seoul 기준)
    if run_date is not None:
        # Asia/Seoul 자정까지 포함되도록 끝점 계산
        end_dt = pd.to_datetime(run_date).tz_localize("Asia/Seoul", nonexistent="shift_forward").normalize()
        end_dt = end_dt + pd.Timedelta(days=1) - pd.Timedelta(microseconds=1)
        # df는 UTC → 동일 기준으로 비교
        end_cut_utc = end_dt.tz_convert("UTC")
        df = df[df[ts_col] <= end_cut_utc]

    df = df.sort_values(["ticker", ts_col]).reset_index(drop=True)

    # ---------- 피처 + 라벨 ----------
    # 모델 입력 피처 후보 (CLOSE_RAW는 라벨링용으로만 사용하고 입력 피처에서는 제외)
    model_feats = [f for f in FEATURES if f != "CLOSE_RAW"]
    X_all, y_all = [], []

    for t, g in df.groupby("ticker", sort=False):
        g = g.rename(columns={ts_col: "date"}).set_index("date")
        ohlcv = g[["open", "high", "low", "close", "volume"]].copy()

        # 사용자 정의 피처 빌드
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

    # 클래스 불균형이 심할 수 있으니 stratify 분할
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

    _log(f"[TRAIN] Weights saved(best): {model_out_path}")
    return {
        "history": history.history,
        "n_samples": int(len(X)),
        "class_dist": {int(k): int(v) for k, v in zip(*np.unique(y, return_counts=True))},
        "model_path": model_out_path,
        "scaler_path": scaler_out_path
    }

# =============================================================================
# 4) 야후 파이낸스 REST 폴백: OHLCV 수집 (requests)
#     - yfinance SSL/차단 이슈를 피해 직접 엔드포인트 호출
# =============================================================================
def _yahoo_interval_str(interval: str) -> str:
    """
    야후 차트 API interval 명세 검증/정규화.
    - 허용: '1d','1h','1wk','1mo' 등
    """
    allowed = {"1m","2m","5m","15m","30m","60m","90m","1h","1d","5d","1wk","1mo","3mo"}
    if interval not in allowed:
        raise ValueError(f"지원하지 않는 interval: {interval} (허용: {sorted(allowed)})")
    return interval

def _fetch_yahoo_ohlcv(
    ticker: str,
    start: pd.Timestamp,
    end: pd.Timestamp,
    interval: str = "1d",
    retries: int = 3,
    sleep_sec: float = 1.0,
) -> pd.DataFrame:
    """
    야후 파이낸스 차트 API(v8)에서 OHLCV를 수집하여 DataFrame 반환.
    - 요청 URL: https://query2.finance.yahoo.com/v8/finance/chart/{ticker}
    - 파라미터: period1(UNIX), period2(UNIX), interval
    - 반환 컬럼: ['ticker','date','open','high','low','close','volume','ts_local(Asia/Seoul)']

    주의
    ----
    * period1/period2는 초 단위 UNIX 타임스탬프.
    * 반환 timeZone은 종목 거래소 기준이므로, ts_local은 Asia/Seoul로 별도 변환해서 제공.
    * 프리마켓/서머타임 등 미세한 체결 시간 차이에 따른 분봉은 케이스별 확인 필요.
    """
    interval = _yahoo_interval_str(interval)
    base = "https://query2.finance.yahoo.com/v8/finance/chart/{}".format(ticker)
    params = {
        "period1": int(pd.Timestamp(start).tz_convert("UTC").timestamp()),
        "period2": int(pd.Timestamp(end).tz_convert("UTC").timestamp()),
        "interval": interval,
        "events": "div,splits"
    }
    headers = {
        "User-Agent": "Mozilla/5.0",
        "Accept": "application/json, text/plain, */*",
        "Connection": "keep-alive",
    }

    last_err = None
    for _ in range(retries):
        try:
            resp = requests.get(base, params=params, headers=headers, timeout=10)
            resp.raise_for_status()
            data = resp.json()
            result = data.get("chart", {}).get("result")
            if not result:
                raise ValueError(f"Yahoo 응답에 result가 없습니다: {data}")
            result = result[0]

            ts_list = result["timestamp"]  # 초 단위 UNIX
            ind = pd.to_datetime(ts_list, unit="s", utc=True)

            q = result["indicators"]["quote"][0]
            df = pd.DataFrame({
                "open": q.get("open"),
                "high": q.get("high"),
                "low":  q.get("low"),
                "close": q.get("close"),
                "volume": q.get("volume"),
            }, index=ind)

            # 기본 정리
            df = df.dropna(subset=["open","high","low","close"])  # 완전결측 제거
            df["ticker"] = str(ticker)

            # 로컬(Asia/Seoul) 타임스탬프 컬럼 별도 생성
            df["ts_local"] = df.index.tz_convert("Asia/Seoul")

            # date(UTC), ts_local 둘 다 보유 (학습코드는 ts_local/ date 둘 중 하나만 있으면 동작)
            df = df.reset_index().rename(columns={"index": "date"})
            return df[["ticker","date","open","high","low","close","volume","ts_local"]]
        except Exception as e:
            last_err = e
            time.sleep(sleep_sec)
    raise RuntimeError(f"야후 차트 API 호출 실패: {last_err}")

# =============================================================================
# 5) 단독 실행(초기 가중치 생성)용 CLI 엔트리포인트
# =============================================================================
def run_training(config: dict):
    """config 딕셔너리 기반 Transformer 학습 실행"""

    # ---- 1) 데이터 수집 ----
    start = pd.Timestamp(config["start"], tz="Asia/Seoul").tz_convert("UTC")
    end = pd.Timestamp(config["end"], tz="Asia/Seoul").tz_convert("UTC") + pd.Timedelta(days=1)

    frames = []
    for t in config["tickers"]:
        _log(f"[FETCH] {t} {config['interval']} {config['start']}→{config['end']}")
        df_t = _fetch_yahoo_ohlcv(
            ticker=t,
            start=start,
            end=end,
            interval=config["interval"]
        )
        frames.append(df_t)
    raw = pd.concat(frames, ignore_index=True)

    # ---- 2) 학습 ----
    os.makedirs(os.path.dirname(config["model_out"]), exist_ok=True)
    os.makedirs(os.path.dirname(config["scaler_out"]), exist_ok=True)

    result = train_transformer_classifier(
        raw_data=raw,
        seq_len=config["seq_len"],
        pred_h=config["pred_h"],
        model_out_path=config["model_out"],
        scaler_out_path=config["scaler_out"],
        tickers=config["tickers"],
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
    # ⚙️ 여기에 원하는 설정만 바꾸면 됨
    config = {
        "tickers": ["AAPL", "MSFT"],      # 학습 대상 종목
        "start": "2018-01-01",            # 시작일
        "end": "2025-10-31",              # 종료일
        "interval": "1d",                 # 일봉
        "seq_len": 64,                    # 시퀀스 길이
        "pred_h": 5,                      # 예측 지평(미래 5일)
        "hold_thr": 0.003,                # HOLD 임계치
        "test_size": 0.2,                 # 검증셋 비율
        "epochs": 3,                      # 에폭 수
        "batch_size": 128,                # 배치 크기
        "model_out": "transformer/weights/initial.weights.h5",  # 가중치 저장
        "scaler_out": "transformer/scaler/scaler.pkl",     # 스케일러 저장
      "run_date": None,                 # 특정 날짜까지만 사용할 경우 지정
    }

    run_training(config)
