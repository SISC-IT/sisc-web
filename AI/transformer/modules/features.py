# transformer/modules/features.py
from __future__ import annotations
from typing import List
import numpy as np
import pandas as pd

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
    "CLOSE_RAW",    # 마지막에 추가 (스케일 제외, 로그/가격 참조용)
]

# ===== 기술지표 유틸 =====
def _ema(s: pd.Series, span: int) -> pd.Series:
    """지수이동평균(EMA)."""
    return s.ewm(span=span, adjust=False).mean()

def _rsi_wilder(close: pd.Series, period: int = 14) -> pd.Series:
    """Wilder RSI 계산."""
    delta = close.diff()
    gain = delta.clip(lower=0.0)
    loss = -delta.clip(upper=0.0)
    avg_gain = gain.ewm(alpha=1/period, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1/period, adjust=False).mean()
    rs = avg_gain / avg_loss.replace(0, np.nan)
    return 100.0 - (100.0 / (1.0 + rs))

def _macd_line(close: pd.Series, fast: int = 12, slow: int = 26) -> pd.Series:
    """MACD 라인(시그널은 미사용)."""
    return _ema(close, fast) - _ema(close, slow)

def _true_range(high: pd.Series, low: pd.Series, close: pd.Series) -> pd.Series:
    """TR(True Range)."""
    prev_close = close.shift(1)
    tr1 = high - low
    tr2 = (high - prev_close).abs()
    tr3 = (low - prev_close).abs()
    return pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)

def _atr_wilder(high: pd.Series, low: pd.Series, close: pd.Series, period: int = 14) -> pd.Series:
    """Wilder ATR."""
    tr = _true_range(high, low, close)
    return tr.ewm(alpha=1/period, adjust=False).mean()

def _obv(close: pd.Series, volume: pd.Series) -> pd.Series:
    """
    OBV(On-Balance Volume).
    - 상승일: +거래량, 하락일: -거래량, 보합: 0 → 누적합
    - NaN/보합 처리 안전성 강화
    """
    close = pd.Series(close)
    volume = pd.Series(volume).fillna(0)
    diff = close.diff()
    direction = np.where(diff.gt(0), 1, np.where(diff.lt(0), -1, 0))
    direction = pd.Series(direction, index=close.index)
    obv_series = (direction * volume).cumsum().fillna(0)
    obv_series.name = "OBV"
    return obv_series

def _stochastic_k(high: pd.Series, low: pd.Series, close: pd.Series, period: int = 14) -> pd.Series:
    """스토캐스틱 %K (단순형)."""
    ll = low.rolling(period).min()
    hh = high.rolling(period).max()
    denom = (hh - ll).replace(0, np.nan)
    return (close - ll) / denom * 100.0

def _mfi(high: pd.Series, low: pd.Series, close: pd.Series, volume: pd.Series, period: int = 14) -> pd.Series:
    """MFI (Money Flow Index)."""
    tp = ((high + low + close) / 3.0).astype(float)
    rmf = (tp * volume.astype(float)).astype(float)
    delta_tp: pd.Series = tp.diff().astype(float)
    pos_mf = rmf.where(delta_tp.gt(0), 0.0)
    neg_mf = rmf.where(delta_tp.lt(0), 0.0).abs()
    pos_sum = pos_mf.rolling(period).sum()
    neg_sum = neg_mf.rolling(period).sum().replace(0, np.nan)
    mr = pos_sum / neg_sum
    return 100.0 - (100.0 / (1.0 + mr))

def build_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    입력: OHLCV 컬럼을 가진 DataFrame (open, high, low, close, volume)
    출력: 모델 학습/추론용 피처 DataFrame
    - 기술지표 계산 후 NaN 행 제거
    - CLOSE_RAW는 스케일링 제외를 위해 마지막에 포함
    """
    # 컬럼 소문자 매핑
    cols = {c.lower(): c for c in df.columns}
    need = ["open", "high", "low", "close", "volume"]
    mapping = {}
    for k in need:
        if k in cols:
            mapping[cols[k]] = k
    if mapping:
        df = df.rename(columns=mapping)

    O = df["open"].astype(float)
    H = df["high"].astype(float)
    L = df["low"].astype(float)
    C = df["close"].astype(float)
    V = df["volume"].astype(float)

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
