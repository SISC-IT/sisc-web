# AI/modules/features/technical.py
import pandas as pd
import numpy as np

def compute_z_score(series: pd.Series, window: int = 20) -> pd.Series:
    """VIX Z-score (Window=20~60)"""
    rolling_mean = series.rolling(window=window).mean()
    rolling_std = series.rolling(window=window).std()
    return (series - rolling_mean) / (rolling_std + 1e-9)

def compute_atr(high: pd.Series, low: pd.Series, close: pd.Series, window: int = 14) -> pd.Series:
    """ATR 계산"""
    tr = pd.concat([high - low, 
                    abs(high - close.shift(1)), 
                    abs(low - close.shift(1))], axis=1).max(axis=1)
    return tr.rolling(window=window).mean()


def compute_atr_rank(high: pd.Series, low: pd.Series, close: pd.Series, window: int = 14) -> pd.Series:
    """ATR 백분위수 (변동성 레벨)"""
    tr = pd.concat([high - low, 
                    abs(high - close.shift(1)), 
                    abs(low - close.shift(1))], axis=1).max(axis=1)
    atr = tr.rolling(window=window).mean()
    # 최근 252일 중 현재 ATR이 어느 정도 위치인지 백분위 계산
    return atr.rolling(window=252).apply(lambda x: pd.Series(x).rank(pct=True).iloc[-1])

def compute_ma_trend_score(close: pd.Series) -> pd.Series:
    """이평선 정배열 점수: 5 > 20 > 60일 기준 0~1 사이로 정규화"""
    ma5 = close.rolling(5).mean()
    ma20 = close.rolling(20).mean()
    ma60 = close.rolling(60).mean()
    score = (ma5 > ma20).astype(int) + (ma20 > ma60).astype(int)
    return score / 2.0

def compute_correlation_spike(series1: pd.Series, series2: pd.Series, window: int = 20) -> pd.Series:
    """자산 간 상관계수 급등 시 1 (correlation_spike_flag) """
    rolling_corr = series1.rolling(window=window).corr(series2)
    corr_mean = rolling_corr.rolling(window=60).mean()
    corr_std = rolling_corr.rolling(window=60).std()
    # 평균 대비 2표준편차 이상 급등 시 플래그 1
    return (rolling_corr > (corr_mean + 2 * corr_std)).astype(int)

def compute_recent_loss_ema(y_true: pd.Series, y_pred: pd.Series, span: int = 20) -> pd.Series:
    """최근 모델 예측 오차 EMA (게이팅용)"""
    error = (y_true - y_pred).abs()
    return error.ewm(span=span, adjust=False).mean()

def compute_rsi(series: pd.Series, period: int = 14) -> pd.Series:
    """RSI 계산 (0~100)"""
    delta = series.diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
    rs = gain / (loss + 1e-9)
    return 100 - (100 / (1 + rs))

def compute_bollinger_bands(series: pd.Series, window: int = 20):
    """볼린저 밴드 상단, 하단 계산"""
    ma = series.rolling(window=window).mean()
    std = series.rolling(window=window).std()
    return ma + (std * 2), ma - (std * 2)

def compute_macd(series: pd.Series):
    """MACD 및 시그널 라인 계산"""
    exp12 = series.ewm(span=12, adjust=False).mean()
    exp26 = series.ewm(span=26, adjust=False).mean()
    macd = exp12 - exp26
    signal = macd.ewm(span=9, adjust=False).mean()
    return macd, signal

