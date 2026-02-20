# AI/modules/features/market_derived.py
import pandas as pd
import numpy as np
from features.technical import compute_rsi, compute_atr, compute_macd, compute_bollinger_bands

def add_market_changes(df: pd.DataFrame) -> pd.DataFrame:
    """가격 및 거래량 기반 변화율 계산 [명세서 준수]"""
    df['ret_1d'] = df['close'].pct_change()
    df['log_return'] = np.log(df['close'] / df['close'].shift(1))
    # 일중변동성비율 : (High - Low) / Close
    df['intraday_vol'] = (df['high'] - df['low']) / (df['close'] + 1e-9)
    return df

def add_macro_changes(df: pd.DataFrame) -> pd.DataFrame:
    """VIX, 금리, 달러 변화율 계산 [명세서 준수]"""
    # 원천 데이터 컬럼명(vix_close, us10y, dxy_close)이 존재한다고 가정
    if 'vix_close' in df.columns:
        df['vix_change_rate'] = df['vix_close'].pct_change()
    if 'us10y' in df.columns:
        df['us10y_chg'] = df['us10y'].diff() # 금리는 주로 bp 단위 변화량 사용
    if 'dxy_close' in df.columns:
        df['dxy_chg'] = df['dxy_close'].pct_change()
    return df

def add_relative_strength(df: pd.DataFrame, sector_col: str) -> pd.DataFrame:
    """상대강도: 종목_ret - 섹터_ret [명세서 준수]"""
    if sector_col in df.columns:
        df[f'sector_return_rel_{sector_col}'] = df['ret_1d'] - df[sector_col]
    return df

def add_standard_technical_features(df: pd.DataFrame) -> pd.DataFrame:
    """레거시 로직 + 명세서 신규 지표 통합"""
    epsilon = 1e-9
    
    # 1. RSI (rsi_14)
    df['rsi_14'] = compute_rsi(df['close'], 14) / 100.0
    
    # 2. MACD (macd, macd_signal)
    df['macd'], df['macd_signal'] = compute_macd(df['close'])
    # 모델 입력용 macd_ratio (Stationary)
    df['macd_ratio'] = df['macd'] / (df['close'] + epsilon)
    
    # 3. Bollinger Bands (ub, lb)
    std20 = df['close'].rolling(window=20).std()
    ma20 = df['close'].rolling(window=20).mean()
    df['bollinger_ub'] = ma20 + (std20 * 2)
    df['bollinger_lb'] = ma20 - (std20 * 2)
    # 모델 입력용 포지션
    df['bb_position'] = (df['close'] - df['bollinger_lb']) / ( (df['bollinger_ub'] - df['bollinger_lb']).replace(0, epsilon) )
    
    # 4. ATR (atr_14)
    df['atr_14'] = compute_atr(df['high'], df['low'], df['close'], 14)
    
    # 5. Moving Averages (ma_20, ma_60)
    for w in [20, 60]:
        df[f'ma_{w}'] = df['close'].rolling(window=w).mean()
        # 모델 입력용 이격도 (Standard Key: ma_trend_score 등에 활용)
        df[f'ma{w}_ratio'] = (df['close'] - df[f'ma_{w}']) / (df[f'ma_{w}'] + epsilon)

    return df

def add_multi_timeframe_features(df: pd.DataFrame) -> pd.DataFrame:
    """일봉 데이터에 주봉(Weekly) 및 월봉(Monthly) 피처 결합 [레거시 기능 복구]"""
    if df.empty: return df
    
    # 인덱스가 날짜여야 resample 가능
    df_origin = df.copy()
    if 'date' in df_origin.columns:
        df_origin = df_origin.set_index('date').sort_index()
    
    epsilon = 1e-9

    # --- 1. 주봉(Weekly) 계산 ---
    df_weekly = df_origin.resample('W-FRI').agg({
        'open': 'first', 'high': 'max', 'low': 'min', 'close': 'last', 'volume': 'sum'
    })
    # 명세서 키 적용: week_ma20_ratio, week_rsi, week_bb_pos
    w_ma20 = df_weekly['close'].rolling(window=20).mean()
    df_weekly['week_ma20_ratio'] = (df_weekly['close'] - w_ma20) / (w_ma20 + epsilon)
    df_weekly['week_rsi'] = compute_rsi(df_weekly['close'], 14) / 100.0
    
    w_upper, w_lower = compute_bollinger_bands(df_weekly['close'], 20)
    df_weekly['week_bb_pos'] = (df_weekly['close'] - w_lower) / ((w_upper - w_lower).replace(0, epsilon))
    df_weekly['week_vol_change'] = df_weekly['volume'].pct_change()

    # --- 2. 월봉(Monthly) 계산 ---
    df_monthly = df_origin.resample('ME').agg({
        'open': 'first', 'high': 'max', 'low': 'min', 'close': 'last', 'volume': 'sum'
    })
    # 명세서 키 적용: month_ma12_ratio, month_rsi
    m_ma12 = df_monthly['close'].rolling(window=12).mean()
    df_monthly['month_ma12_ratio'] = (df_monthly['close'] - m_ma12) / (m_ma12 + epsilon)
    df_monthly['month_rsi'] = compute_rsi(df_monthly['close'], 14) / 100.0

    # --- 3. 데이터 병합 (Reindex & ffill) ---
    weekly_cols = ['week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change']
    monthly_cols = ['month_ma12_ratio', 'month_rsi']
    
    # 일봉 날짜 인덱스에 맞춰 주/월봉 데이터를 앞으로 채움(ffill)
    df_origin = df_origin.join(df_weekly[weekly_cols].reindex(df_origin.index, method='ffill'))
    df_origin = df_origin.join(df_monthly[monthly_cols].reindex(df_origin.index, method='ffill'))
    
    return df_origin.reset_index()