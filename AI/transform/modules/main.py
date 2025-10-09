# transform/modules/transform.py
from __future__ import annotations
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd
from sklearn.preprocessing import MinMaxScaler
import tensorflow as tf
from tensorflow.keras import layers, Model

from AI.libs.utils.io import _log
from .model import build_transformer_classifier  # <-- 모델 분리됨

# ===== 공개 상수 =====
FEATURES: List[str] = [
    "RSI",
    "MACD",
    "Bollinger_Bands_upper",
    "Bollinger_Bands_lower",
    "ATR",
    "OBV",
    "Stochastic",   # %K
    "MFI",
    "MA_5",
    "MA_20",
    "MA_50",
    "MA_200",
    "CLOSE_RAW",    # 마지막에 추가 (스케일 제외, 로그용)
]

CLASS_NAMES = ["BUY", "HOLD", "SELL"]

# ====== 기술지표 유틸 ======
def _ema(s: pd.Series, span: int) -> pd.Series:
    return s.ewm(span=span, adjust=False).mean()

def _rsi_wilder(close: pd.Series, period: int = 14) -> pd.Series:
    delta = close.diff()
    gain = delta.clip(lower=0.0)
    loss = -delta.clip(upper=0.0)
    avg_gain = gain.ewm(alpha=1/period, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1/period, adjust=False).mean()
    rs = avg_gain / avg_loss.replace(0, np.nan)
    return 100.0 - (100.0 / (1.0 + rs))

def _macd_line(close: pd.Series, fast: int = 12, slow: int = 26) -> pd.Series:
    return _ema(close, fast) - _ema(close, slow)

def _true_range(high: pd.Series, low: pd.Series, close: pd.Series) -> pd.Series:
    prev_close = close.shift(1)
    tr1 = high - low
    tr2 = (high - prev_close).abs()
    tr3 = (low - prev_close).abs()
    return pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)

def _atr_wilder(high: pd.Series, low: pd.Series, close: pd.Series, period: int = 14) -> pd.Series:
    tr = _true_range(high, low, close)
    return tr.ewm(alpha=1/period, adjust=False).mean()

def _obv(close: pd.Series, volume: pd.Series) -> pd.Series:
    sign = np.sign(close.diff().fillna(0.0))
    sign[sign == 0] = 0.0
    return (sign * volume).fillna(0.0).cumsum()

def _stochastic_k(high: pd.Series, low: pd.Series, close: pd.Series, period: int = 14) -> pd.Series:
    ll = low.rolling(period).min()
    hh = high.rolling(period).max()
    denom = (hh - ll).replace(0, np.nan)
    return (close - ll) / denom * 100.0

def _mfi(high: pd.Series, low: pd.Series, close: pd.Series, volume: pd.Series, period: int = 14) -> pd.Series:
    tp = (high + low + close) / 3.0
    rmf = tp * volume
    delta_tp = tp.diff()
    pos_mf = rmf.where(delta_tp > 0, 0.0)
    neg_mf = rmf.where(delta_tp < 0, 0.0).abs()
    pos_sum = pos_mf.rolling(period).sum()
    neg_sum = neg_mf.rolling(period).sum().replace(0, np.nan)
    mr = pos_sum / neg_sum
    return 100.0 - (100.0 / (1.0 + mr))

# ====== 피처 빌더 ======
def build_features(df: pd.DataFrame) -> pd.DataFrame:
    cols = {c.lower(): c for c in df.columns}
    need = ["open", "high", "low", "close", "volume"]
    mapping = {}
    for k in need:
        if k in cols:
            mapping[cols[k]] = k
    if mapping:
        df = df.rename(columns=mapping)

    O = df["open"].astype(float).squeeze()
    H = df["high"].astype(float).squeeze()
    L = df["low"].astype(float).squeeze()
    C = df["close"].astype(float).squeeze()
    V = df["volume"].astype(float).squeeze()

    feats = pd.DataFrame(index=df.index)
    feats["RSI"]  = _rsi_wilder(C, period=14)
    feats["MACD"] = _macd_line(C, fast=12, slow=26)

    ma20 = C.rolling(20).mean()
    std20 = C.rolling(20).std(ddof=0)
    feats["Bollinger_Bands_upper"] = ma20 + 2.0 * std20
    feats["Bollinger_Bands_lower"] = ma20 - 2.0 * std20
    feats["ATR"] = _atr_wilder(H, L, C, period=14)
    feats["OBV"] = _obv(C, V)
    feats["Stochastic"] = _stochastic_k(H, L, C, period=14)
    feats["MFI"]        = _mfi(H, L, C, V, period=14)
    feats["MA_5"]   = C.rolling(5).mean()
    feats["MA_20"]  = ma20
    feats["MA_50"]  = C.rolling(50).mean()
    feats["MA_200"] = C.rolling(200).mean()
    feats["CLOSE_RAW"] = C
    return feats.dropna()

# ====== 모델 로드 ======
def _load_or_build_model(seq_len: int, n_features: int, model_path: Optional[str]) -> Model:
    model = build_transformer_classifier(seq_len, n_features)
    if model_path:
        try:
            model.load_weights(model_path)
            _log(f"[TRANSFORM] Transformer weights loaded: {model_path}")
        except Exception as e:
            _log(f"[TRANSFORM][WARN] 모델 가중치 로드 실패 → 랜덤 초기화: {e}")
    else:
        _log("[TRANSFORM][WARN] model_path 미지정 → 랜덤 초기화로 진행")
    return model

# ====== 시퀀스/스케일링 ======
def _make_sequence(feats: pd.DataFrame, use_cols: List[str], seq_len: int) -> Optional[np.ndarray]:
    if len(feats) < seq_len:
        return None
    X = feats[use_cols].iloc[-seq_len:].copy()
    return X.values.astype("float32")

def _scale_per_ticker(seq_arr: np.ndarray) -> Tuple[np.ndarray, MinMaxScaler]:
    scaler = MinMaxScaler(feature_range=(0, 1), clip=True)
    X_scaled = scaler.fit_transform(seq_arr)
    return X_scaled.astype("float32"), scaler

# ====== 메인 엔트리포인트 ======
def run_transform(
    *,
    finder_df: pd.DataFrame,
    seq_len: int,
    pred_h: int,
    raw_data: pd.DataFrame,
    run_date: Optional[str] = None,
    config: Optional[dict] = None,
    interval: str = "1d",
) -> Dict[str, pd.DataFrame]:
    tickers = finder_df["ticker"].astype(str).tolist()
    if raw_data is None or raw_data.empty:
        _log("[TRANSFORM] raw_data empty -> empty logs")
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
        _log("[TRANSFORM] 대상 종목 데이터 없음")
        return {"logs": pd.DataFrame(columns=[
            "ticker","date","action","price","weight",
            "feature1","feature2","feature3","prob1","prob2","prob3"
        ])}

    if run_date is None:
        end_dt = pd.Timestamp.now(tz="Asia/Seoul").normalize() + pd.Timedelta(days=1) - pd.Timedelta(microseconds=1)
    else:
        end_dt = pd.to_datetime(run_date).tz_localize("Asia/Seoul", nonexistent="shift_forward").normalize()
        end_dt = end_dt + pd.Timedelta(days=1) - pd.Timedelta(microseconds=1)

    if df[ts_col].dt.tz is not None:
        end_cut = end_dt.tz_convert(df[ts_col].dt.tz)
    else:
        end_cut = end_dt.tz_localize(None)

    df = df[df[ts_col] <= end_cut]
    df = df.sort_values(["ticker", ts_col]).reset_index(drop=True)

    model_feats = [f for f in FEATURES if f != "CLOSE_RAW"]
    n_features = len(model_feats)

    model_path = None
    if config and "transform" in config and "model_path" in config["transform"]:
        model_path = str(config["transform"]["model_path"])

    model = _load_or_build_model(seq_len=seq_len, n_features=n_features, model_path=model_path)

    rows: List[dict] = []
    for t, g in df.groupby("ticker", sort=False):
        try:
            if g.empty:
                continue
            g = g.rename(columns={ts_col: "date"}).set_index("date")
            ohlcv = g[["open", "high", "low", "close", "volume"]].copy()

            feats = build_features(ohlcv)
            if feats.empty:
                _log(f"[TRANSFORM] {t} features empty -> skip")
                continue

            X_seq = _make_sequence(feats, model_feats, seq_len)
            if X_seq is None:
                _log(f"[TRANSFORM] {t} 부족한 길이(seq_len={seq_len}) -> skip")
                continue

            X_scaled, _ = _scale_per_ticker(X_seq)
            X_scaled = np.expand_dims(X_scaled, axis=0)

            try:
                probs = model.predict(X_scaled, verbose=0)[0]
                probs = np.clip(probs.astype(float), 1e-6, 1.0)
                probs = probs / probs.sum()
                buy_p, hold_p, sell_p = float(probs[0]), float(probs[1]), float(probs[2])
                action = CLASS_NAMES[int(np.argmax(probs))]
            except Exception as e:
                _log(f"[TRANSFORM][WARN] 모델 예측 실패({t}) → 룰기반 fallback: {e}")
                recent = feats.iloc[-1]
                rsi = float(recent["RSI"])
                macd = float(recent["MACD"])
                if rsi < 30 and macd > 0:
                    action = "BUY"; buy_p, hold_p, sell_p = 0.65, 0.30, 0.05
                elif rsi > 70 and macd < 0:
                    action = "SELL"; buy_p, hold_p, sell_p = 0.05, 0.30, 0.65
                else:
                    action = "HOLD"; buy_p, hold_p, sell_p = 0.33, 0.34, 0.33

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
            _log(f"[TRANSFORM][ERROR] {t}: {e}")
            continue

    logs_df = pd.DataFrame(rows, columns=[
        "ticker","date","action","price","weight",
        "feature1","feature2","feature3","prob1","prob2","prob3"
    ])
    return {"logs": logs_df}
