# AI/modules/signal/core/features.py
"""
[Stationary Multi-Timeframe Features - Fixed]
- 절대 가격(Price)을 Ratio로 변환합니다.
- [수정] 주가 데이터를 파괴하던 clip(-10, 10) 로직을 제거했습니다.
- 무한대(inf)는 NaN -> 0 처리하여 안전하게 만듭니다.
"""

import pandas as pd
import numpy as np

def add_technical_indicators(df: pd.DataFrame) -> pd.DataFrame:
    if df.empty: return df
    df = df.copy()

    # 1. 조정 종가 처리
    if 'adjusted_close' in df.columns:
        df['adjusted_close'] = df['adjusted_close'].fillna(df['close'])
        df['close'] = df['adjusted_close']
        df.drop(columns=['adjusted_close'], inplace=True)

    epsilon = 1e-9

    # --------------------------------------------------------
    # [일봉] 절대 가격 -> 비율로 변환
    # --------------------------------------------------------
    # (1) 로그 수익률
    df['log_return'] = np.log(df['close'] / df['close'].shift(1))

    # (2) 캔들 모양
    prev_close = df['close'].shift(1)
    df['open_ratio'] = (df['open'] - prev_close) / (prev_close + epsilon)
    df['high_ratio'] = (df['high'] - prev_close) / (prev_close + epsilon)
    df['low_ratio']  = (df['low'] - prev_close) / (prev_close + epsilon)
    
    # (3) 이평선 이격도
    for window in [5, 20, 60]:
        ma = df['close'].rolling(window=window).mean()
        df[f'ma{window}_ratio'] = (df['close'] - ma) / (ma + epsilon)

    # (4) 거래량 변화율
    df['vol_change'] = df['volume'].pct_change()

    # (5) RSI
    df['rsi'] = compute_rsi(df['close'], 14) / 100.0

    # (6) MACD Ratio
    exp12 = df['close'].ewm(span=12, adjust=False).mean()
    exp26 = df['close'].ewm(span=26, adjust=False).mean()
    macd = exp12 - exp26
    df['macd_ratio'] = macd / (df['close'] + epsilon)

    # (7) 볼린저 밴드 포지션
    std20 = df['close'].rolling(window=20).std()
    ma20 = df['close'].rolling(window=20).mean()
    upper = ma20 + (std20 * 2)
    lower = ma20 - (std20 * 2)
    denominator = upper - lower
    df['bb_position'] = (df['close'] - lower) / (denominator.replace(0, epsilon))

    # --------------------------------------------------------
    # [데이터 정제] Clip 제거됨
    # --------------------------------------------------------
    df.replace([np.inf, -np.inf], np.nan, inplace=True)
    df = df.fillna(0)
    
    # [삭제됨] df[numeric_cols].clip(-10, 10) <-- 범인 제거 완료

    return df

def add_multi_timeframe_features(df: pd.DataFrame) -> pd.DataFrame:
    if df.empty: return df
    
    df_origin = df.copy()
    if 'date' in df_origin.columns:
        df_origin = df_origin.set_index('date').sort_index()
    
    epsilon = 1e-9

    # 1. 주봉
    df_weekly = df_origin.resample('W-FRI').agg({
        'open': 'first', 'high': 'max', 'low': 'min', 'close': 'last', 'volume': 'sum'
    })
    
    w_ma20 = df_weekly['close'].rolling(window=20).mean()
    df_weekly['week_ma20_ratio'] = (df_weekly['close'] - w_ma20) / (w_ma20 + epsilon)
    df_weekly['week_rsi'] = compute_rsi(df_weekly['close'], 14) / 100.0
    
    w_std20 = df_weekly['close'].rolling(window=20).std()
    w_upper = w_ma20 + (w_std20 * 2)
    w_lower = w_ma20 - (w_std20 * 2)
    w_denom = w_upper - w_lower
    df_weekly['week_bb_pos'] = (df_weekly['close'] - w_lower) / (w_denom.replace(0, epsilon))
    df_weekly['week_vol_change'] = df_weekly['volume'].pct_change()

    # 2. 월봉
    df_monthly = df_origin.resample('ME').agg({
        'open': 'first', 'high': 'max', 'low': 'min', 'close': 'last', 'volume': 'sum'
    })
    
    m_ma12 = df_monthly['close'].rolling(window=12).mean()
    df_monthly['month_ma12_ratio'] = (df_monthly['close'] - m_ma12) / (m_ma12 + epsilon)
    df_monthly['month_rsi'] = compute_rsi(df_monthly['close'], 14) / 100.0
    
    # 3. 병합
    weekly_cols = ['week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change']
    monthly_cols = ['month_ma12_ratio', 'month_rsi']
    
    df_weekly = df_weekly.sort_index()
    df_monthly = df_monthly.sort_index()
    
    weekly_features = df_weekly[weekly_cols].reindex(df_origin.index, method='ffill')
    monthly_features = df_monthly[monthly_cols].reindex(df_origin.index, method='ffill')
    
    for col in weekly_cols:
        df_origin[col] = weekly_features[col]
    for col in monthly_cols:
        df_origin[col] = monthly_features[col]
        
    df_origin.replace([np.inf, -np.inf], np.nan, inplace=True)
    df_origin = df_origin.fillna(0)


    if 'date' not in df_origin.columns:
        df_origin = df_origin.reset_index()
        
    return df_origin

def compute_rsi(series, period=14):
    delta = series.diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
    rs = gain / (loss + 1e-9)
    return 100 - (100 / (1 + rs))