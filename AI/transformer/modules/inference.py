# transformer/modules/inference.py
from __future__ import annotations
from typing import Dict, List, Optional, Tuple
import numpy as np
import pandas as pd
from sklearn.preprocessing import MinMaxScaler
from tensorflow.keras import Model 


from transformer.modules.models import build_transformer_classifier
from transformer.modules.features import FEATURES, build_features

CLASS_NAMES = ["BUY", "HOLD", "SELL"]

# ===== 내부 유틸 =====
def _make_sequence(feats: pd.DataFrame, use_cols: List[str], seq_len: int) -> Optional[np.ndarray]:
    """마지막 구간(seq_len)만 잘라서 (seq_len, n_features) 배열 생성."""
    if len(feats) < seq_len:
        return None
    X = feats[use_cols].iloc[-seq_len:].copy()
    return X.values.astype("float32")

def _scale_per_ticker(seq_arr: np.ndarray) -> Tuple[np.ndarray, MinMaxScaler]:
    """
    (중요) 추론 단계에서는 학습 시 저장한 스케일러 사용이 가장 바람직.
    - 다만, '티커별 미세 스케일링' 전략을 유지하고자 할 때는 아래처럼
      입력 시퀀스에 대해 개별 MinMax를 적용할 수 있음(일관성↓, 적응성↑).
    """
    scaler = MinMaxScaler(feature_range=(0, 1), clip=True)
    X_scaled = scaler.fit_transform(seq_arr)
    return X_scaled.astype("float32"), scaler

def _load_or_build_model(seq_len: int, n_features: int, weights_path: Optional[str]) -> Model:
    """가중치 로드 전용. 가중치 경로 없으면 경고 후 랜덤 초기화(추론 품질↓)."""
    model = build_transformer_classifier(seq_len, n_features)
    if weights_path:
        try:
            model.load_weights(weights_path)
            print(f"[INFER] Transformer weights loaded: {weights_path}")
        except Exception as e:
            print(f"[INFER][WARN] 가중치 로드 실패 → 랜덤 초기화: {e}")
    else:
        print("[INFER][WARN] weights_path 미지정 → 랜덤 초기화로 진행")
    return model

# ===== 공개 엔트리포인트 (추론) =====
def run_inference(
    *,
    finder_df: pd.DataFrame,
    raw_data: pd.DataFrame,
    seq_len: int,
    pred_h: int,  # (현재는 미사용; 로그/정책에 남겨두기용)
    weights_path: Optional[str],
    run_date: Optional[str] = None,
    interval: str = "1d",
) -> Dict[str, pd.DataFrame]:
    """
    ※ 추론 전용 함수
    - 입력: 선정된 종목 목록(finder_df), OHLCV 원천(raw_data)
    - 처리: 피처→시퀀스→스케일링→모델 예측
    - 출력: logs DataFrame (기존 포맷 유지)

    Parameters
    ----------
    finder_df : DataFrame
        ['ticker'] 컬럼 포함. 추론 대상 종목 목록.
    raw_data : DataFrame
        OHLCV 시계열. 필수 컬럼: ['ticker','open','high','low','close','volume', ('ts_local' or 'date')]
    seq_len : int
        모델 입력 시퀀스 길이.
    pred_h : int
        예측 지평 (현재 로깅 목적으로만 사용).
    weights_path : str
        학습된 가중치 파일 경로(.h5 또는 checkpoint).
    run_date : str, optional
        'YYYY-MM-DD'. 이 날짜(포함)까지의 데이터만 사용.
    interval : str
        '1d' 등. (로그 용도)
    """
    tickers = finder_df["ticker"].astype(str).tolist()
    if raw_data is None or raw_data.empty:
        print("[INFER] raw_data empty -> empty logs")
        return {"logs": pd.DataFrame(columns=[
            "ticker","date","action","price","weight",
            "feature1","feature2","feature3","prob1","prob2","prob3"
        ])}

    df = raw_data.copy()
    ts_col = "ts_local" if "ts_local" in df.columns else ("date" if "date" in df.columns else None)
    if ts_col is None:
        raise ValueError("raw_data에 'ts_local' 또는 'date' 컬럼이 필요합니다.")
    df[ts_col] = pd.to_datetime(df[ts_col])
    df = df.rename(columns={c: c.lower() for c in df.columns})
    df = df[df["ticker"].astype(str).isin(tickers)]
    if df.empty:
        print("[INFER] 대상 종목 데이터 없음")
        return {"logs": pd.DataFrame(columns=[
            "ticker","date","action","price","weight",
            "feature1","feature2","feature3","prob1","prob2","prob3"
        ])}

    # run_date 컷
    if run_date is None:
        end_dt = pd.Timestamp.now(tz="Asia/Seoul").normalize() + pd.Timedelta(days=1) - pd.Timedelta(microseconds=1)
    else:
        end_dt = pd.to_datetime(run_date).tz_localize("Asia/Seoul", nonexistent="shift_forward").normalize()
        end_dt = end_dt + pd.Timedelta(days=1) - pd.Timedelta(microseconds=1)

    if df[ts_col].dt.tz is not None:
        end_cut = end_dt.tz_convert(df[ts_col].dt.tz)
    else:
        end_cut = end_dt.tz_localize(None)

    df = df[df[ts_col] <= end_cut].sort_values(["ticker", ts_col]).reset_index(drop=True)

    model_feats = [f for f in FEATURES if f != "CLOSE_RAW"]
    n_features = len(model_feats)

    # ★ 추론: 반드시 학습 가중치를 로드
    model = _load_or_build_model(seq_len=seq_len, n_features=n_features, weights_path=weights_path)

    rows: List[dict] = []
    for t, g in df.groupby("ticker", sort=False):
        try:
            if g.empty:
                continue

            g = g.rename(columns={ts_col: "date"}).set_index("date")
            ohlcv = g[["open", "high", "low", "close", "volume"]].copy()

            feats = build_features(ohlcv)
            if feats.empty:
                print(f"[INFER] {t} features empty -> skip")
                continue

            X_seq = _make_sequence(feats, model_feats, seq_len)
            if X_seq is None:
                print(f"[INFER] {t} 부족한 길이(seq_len={seq_len}) -> skip")
                continue

            X_scaled, _ = _scale_per_ticker(X_seq)
            X_scaled = np.expand_dims(X_scaled, axis=0)  # (1, seq_len, n_features)

            try:
                probs = model.predict(X_scaled, verbose=0)[0]
                probs = np.clip(probs.astype(float), 1e-6, 1.0)
                probs = probs / probs.sum()
                buy_p, hold_p, sell_p = float(probs[0]), float(probs[1]), float(probs[2])
                action = ["BUY","HOLD","SELL"][int(np.argmax(probs))]
            except Exception as e:
                print(f"[INFER][WARN] 예측 실패({t}) → 룰기반 fallback: {e}")
                recent = feats.iloc[-1]
                rsi = float(recent["RSI"])
                macd = float(recent["MACD"])
                if rsi < 30 and macd > 0:
                    action = "BUY"; buy_p, hold_p, sell_p = 0.65, 0.30, 0.05
                elif rsi > 70 and macd < 0:
                    action = "SELL"; buy_p, hold_p, sell_p = 0.05, 0.30, 0.65
                else:
                    action = "HOLD"; buy_p, hold_p, sell_p = 0.33, 0.34, 0.33

            # 가중치(비중) 산출 로직(간단 정책 유지)
            p_max = max(buy_p, hold_p, sell_p)
            confidence = float(np.clip((p_max - 1/3) * 1.5, 0.0, 1.0))
            ret = 0.0
            if len(feats) > 2:
                c_now = float(feats["CLOSE_RAW"].iloc[-1])
                c_prev = float(feats["CLOSE_RAW"].iloc[-2])
                if c_prev:
                    ret = (c_now / c_prev) - 1.0
            weight = float(np.clip(0.05 + confidence * 0.20 + abs(ret) * 0.05, 0.05, 0.30))

            recent = feats.iloc[-1]
            close_price = float(recent["CLOSE_RAW"])

            rows.append({
                "ticker": str(t),
                "date": feats.index[-1].strftime("%Y-%m-%d"),
                "action": action,
                "price": close_price,
                "weight": weight,
                "feature1": float(recent["RSI"]),
                "feature2": float(recent["MACD"]),
                "feature3": float(recent["ATR"]),
                "prob1": float(buy_p),
                "prob2": float(hold_p),
                "prob3": float(sell_p),
            })
        except Exception as e:
            print(f"[INFER][ERROR] {t}: {e}")
            continue

    logs_df = pd.DataFrame(rows, columns=[
        "ticker","date","action","price","weight",
        "feature1","feature2","feature3","prob1","prob2","prob3"
    ])
    return {"logs": logs_df}
