# AI/modules/signal/core/features.py
"""
[Stationary Multi-Timeframe Features]
- 절대 가격(Price)은 모두 제거하고, '비율(Ratio)'과 '지표(Indicator)'만 남깁니다.
- 사용자 정의 멀티 타임프레임(주봉/월봉) 로직을 포함하되, 스케일링에 유리한 형태로 가공합니다.
"""

import pandas as pd
import numpy as np

def add_technical_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """일봉 기준 기술적 지표 (비율 기반)"""
    if df.empty: return df
    df = df.copy()

    # 1. 조정 종가 처리
    if 'adjusted_close' in df.columns:
        df['adjusted_close'] = df['adjusted_close'].fillna(df['close'])
        df['close'] = df['adjusted_close']
        df.drop(columns=['adjusted_close'], inplace=True)

    # --------------------------------------------------------
    # [일봉] 절대 가격 -> 비율로 변환
    # --------------------------------------------------------
    # (1) 로그 수익률 (핵심)
    df['log_return'] = np.log(df['close'] / df['close'].shift(1))

    # (2) 캔들 모양 (비율)
    df['open_ratio'] = (df['open'] - df['close'].shift(1)) / df['close'].shift(1)
    df['high_ratio'] = (df['high'] - df['close'].shift(1)) / df['close'].shift(1)
    df['low_ratio']  = (df['low'] - df['close'].shift(1)) / df['close'].shift(1)
    
    # (3) 이평선 이격도 (가격 대신 사용)
    for window in [5, 20, 60]:
        ma = df['close'].rolling(window=window).mean()
        df[f'ma{window}_ratio'] = (df['close'] - ma) / ma

    # (4) 거래량 변화율
    df['vol_change'] = df['volume'].pct_change()

    # (5) RSI (0~1 스케일링)
    df['rsi'] = compute_rsi(df['close'], 14) / 100.0

    # (6) MACD (가격 대비 비율)
    exp12 = df['close'].ewm(span=12, adjust=False).mean()
    exp26 = df['close'].ewm(span=26, adjust=False).mean()
    macd = exp12 - exp26
    df['macd_ratio'] = macd / df['close']

    # (7) 볼린저 밴드 포지션 (0~1)
    std20 = df['close'].rolling(window=20).std()
    ma20 = df['close'].rolling(window=20).mean()
    upper = ma20 + (std20 * 2)
    lower = ma20 - (std20 * 2)
    df['bb_position'] = (df['close'] - lower) / (upper - lower)

    df.replace([np.inf, -np.inf], 0, inplace=True)
    df = df.fillna(0)
    return df

def add_multi_timeframe_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    주봉/월봉 데이터를 생성하되, '절대 가격'은 버리고 '지표'만 가져와서 병합합니다.
    """
    if df.empty: return df
    
    # 원본 복사 및 정렬
    df_origin = df.copy()
    if 'date' in df_origin.columns:
        df_origin = df_origin.set_index('date').sort_index()
    
    # =========================================================
    # 1. 주봉 (Weekly) 처리
    # =========================================================
    df_weekly = df_origin.resample('W-FRI').agg({
        'open': 'first', 'high': 'max', 'low': 'min', 'close': 'last', 'volume': 'sum'
    })
    
    # [주봉 지표 계산] - 절대값 말고 '비율' 위주로
    # 1) 주봉 이격도 (현재 주가가 주봉 20선 대비 어디인가)
    w_ma20 = df_weekly['close'].rolling(window=20).mean()
    df_weekly['week_ma20_ratio'] = (df_weekly['close'] - w_ma20) / w_ma20
    
    # 2) 주봉 RSI
    df_weekly['week_rsi'] = compute_rsi(df_weekly['close'], 14) / 100.0
    
    # 3) 주봉 볼린저 포지션
    w_std20 = df_weekly['close'].rolling(window=20).std()
    w_upper = w_ma20 + (w_std20 * 2)
    w_lower = w_ma20 - (w_std20 * 2)
    df_weekly['week_bb_pos'] = (df_weekly['close'] - w_lower) / (w_upper - w_lower)
    
    # 4) 주봉 거래량 변화율
    df_weekly['week_vol_change'] = df_weekly['volume'].pct_change()

    # =========================================================
    # 2. 월봉 (Monthly) 처리
    # =========================================================
    df_monthly = df_origin.resample('ME').agg({
        'open': 'first', 'high': 'max', 'low': 'min', 'close': 'last', 'volume': 'sum'
    })
    
    # [월봉 지표 계산]
    # 1) 월봉 이격도 (12개월 이평선 대비)
    m_ma12 = df_monthly['close'].rolling(window=12).mean()
    df_monthly['month_ma12_ratio'] = (df_monthly['close'] - m_ma12) / m_ma12
    
    # 2) 월봉 RSI
    df_monthly['month_rsi'] = compute_rsi(df_monthly['close'], 14) / 100.0
    
    # =========================================================
    # 3. 병합 (Merge with ffill)
    # =========================================================
    # 필요한 컬럼만 선택 (가격 제외)
    weekly_cols = ['week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change']
    monthly_cols = ['month_ma12_ratio', 'month_rsi']
    
    # 인덱스 정렬 확인
    df_weekly = df_weekly.sort_index()
    df_monthly = df_monthly.sort_index()
    
    # Reindex로 일봉 날짜에 맞게 확장 (ffill: 직전 주봉/월봉 값 유지)
    weekly_features = df_weekly[weekly_cols].reindex(df_origin.index, method='ffill')
    monthly_features = df_monthly[monthly_cols].reindex(df_origin.index, method='ffill')
    
    # 원본에 붙이기
    for col in weekly_cols:
        df_origin[col] = weekly_features[col]
    for col in monthly_cols:
        df_origin[col] = monthly_features[col]
        
    # NaN 채우기 (앞부분 데이터 부족분)
    df_origin = df_origin.fillna(0)
    
    # 인덱스 리셋 (DataLoader 호환)
    if 'date' not in df_origin.columns:
        df_origin = df_origin.reset_index()
        
    return df_origin

def compute_rsi(series, period=14):
    delta = series.diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
    rs = gain / loss
    return 100 - (100 / (1 + rs))