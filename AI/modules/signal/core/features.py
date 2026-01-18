# AI/modules/signal/core/features.py
"""
[피처 엔지니어링 모듈 - Adjusted Close 통합 버전]
- 데이터에 'adjusted_close'가 있다면 이를 'close'로 덮어씌웁니다.
- 이렇게 하면 모든 지표(RSI, MACD 등)가 자연스럽게 '조정 종가' 기준으로 계산됩니다.
- 학습 시 'close'와 'adjusted_close'가 중복되는 문제도 해결됩니다.
"""

import pandas as pd
import numpy as np

def add_technical_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """
    1. 조정 종가(Adjusted Close)를 종가(Close)로 통합합니다.
    2. 기술적 지표를 계산하여 추가합니다.
    """
    if df.empty:
        return df
        
    df = df.copy()
    
    # ★ [핵심 수정] 조정 종가 우선 정책
    # adjusted_close가 있으면, 이를 close에 덮어쓰고 adjusted_close 컬럼은 삭제합니다.
    if 'adjusted_close' in df.columns:
        # 결측치 방지 (혹시 adjusted_close가 비어있으면 close 값 사용)
        df['adjusted_close'] = df['adjusted_close'].fillna(df['close'])
        
        # 덮어쓰기
        df['close'] = df['adjusted_close']
        
        # 중복 방지를 위해 삭제 (이제 close가 adjusted_close 역할을 함)
        df.drop(columns=['adjusted_close'], inplace=True)
    
    # --- 이하 모든 계산은 'close'(실제로는 조정 종가)를 기준으로 수행됨 ---

    # 1. 이동평균선 (Simple Moving Average)
    df['ma5'] = df['close'].rolling(window=5).mean()
    df['ma20'] = df['close'].rolling(window=20).mean()
    df['ma60'] = df['close'].rolling(window=60).mean()  
    
    # 2. RSI (Relative Strength Index)
    delta = df['close'].diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=14).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=14).mean()
    
    with np.errstate(divide='ignore', invalid='ignore'):
        rs = gain / loss
    
    df['rsi'] = 100 - (100 / (1 + rs))
    
    # RSI 보정
    df.loc[(gain == 0) & (loss == 0), 'rsi'] = 50
    df.loc[(loss == 0) & (gain > 0), 'rsi'] = 100
    
    # 3. 볼린저 밴드 (Bollinger Bands)
    df['std20'] = df['close'].rolling(window=20).std()
    df['upper_band'] = df['ma20'] + (df['std20'] * 2)
    df['lower_band'] = df['ma20'] - (df['std20'] * 2)
    
    # 4. MACD
    exp12 = df['close'].ewm(span=12, adjust=False).mean()
    exp26 = df['close'].ewm(span=26, adjust=False).mean()
    df['macd'] = exp12 - exp26
    df['signal_line'] = df['macd'].ewm(span=9, adjust=False).mean()
    
    # 5. 거래량 변화율
    if 'volume' in df.columns:
        df['vol_change'] = df['volume'].pct_change()
        df['vol_change'] = df['vol_change'].replace([np.inf, -np.inf], 0)
    else:
        df['vol_change'] = 0
    
    # === [데이터 정제] ===
    df.replace([np.inf, -np.inf], np.nan, inplace=True)
    df = df.bfill()
    df = df.fillna(0)
    
    return df

def add_multi_timeframe_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    일봉 데이터(df)를 기반으로 주봉/월봉 지표를 계산하여 컬럼에 추가합니다.
    """
    if df.empty:
        return df

    # 원본 인덱스 보존 및 날짜 확인
    df = df.copy()
    if not isinstance(df.index, pd.DatetimeIndex):
         # 인덱스가 날짜가 아니면 날짜 컬럼을 인덱스로 (필요시)
         pass 

    # --- 1. 주봉(Weekly) 데이터 생성 및 지표 계산 ---
    # 'W-FRI': 금요일 기준 주봉 (주식 시장 기준)
    df_weekly = df.resample('W-FRI').agg({
        'open': 'first',
        'high': 'max',
        'low': 'min',
        'close': 'last',
        'volume': 'sum'
    })
    
    # 주봉 기준 이동평균선 (장기 추세용)
    df_weekly['week_ma20'] = df_weekly['close'].rolling(window=20).mean() # 20주 이평선
    df_weekly['week_rsi'] = compute_rsi(df_weekly['close'], 14)           # 주봉 RSI

    #볼린저 밴드 주봉 포함
    df_weekly['week_bollinger_ma20'] = df_weekly['close'].rolling(window=20).mean()  # 20주 이평선
    df_weekly['week_bollinger_std'] = df_weekly['close'].rolling(window=20).std()  # 20주 표준편차
    df_weekly['week_bollinger_upper'] = df_weekly['week_bollinger_ma20'] + (2 * df_weekly['week_bollinger_std'])  # 상한선
    df_weekly['week_bollinger_lower'] = df_weekly['week_bollinger_ma20'] - (2 * df_weekly['week_bollinger_std'])  # 하한선

    # **주봉 거래량 변화율** 계산
    df_weekly['week_volume_change'] = df_weekly['volume'].pct_change() * 100  # 거래량 변화율 (%) 계산

    # **주봉 MACD** 계산
    df_weekly['week_macd'] = df_weekly['close'].ewm(span=12, adjust=False).mean() - df_weekly['close'].ewm(span=26, adjust=False).mean()
    df_weekly['week_macd_signal'] = df_weekly['week_macd'].ewm(span=9, adjust=False).mean()  # MACD Signal Line
    

    # --- 2. 월봉(Monthly) 데이터 생성 및 지표 계산 ---
    df_monthly = df.resample('ME').agg({ # Pandas 2.1.0+ 에서는 'M' 대신 'ME' 권장
        'open': 'first',
        'high': 'max',
        'low': 'min',
        'close': 'last',
        'volume': 'sum'
    })
    
    # 월봉 기준 이동평균선 (초장기 추세)
    df_monthly['month_ma12'] = df_monthly['close'].rolling(window=12).mean() # 12개월(1년) 이평선

    # **월봉 볼린저 밴드** 계산
    df_monthly['month_bollinger_ma12'] = df_monthly['close'].rolling(window=12).mean()  # 12개월 이평선
    df_monthly['month_bollinger_std'] = df_monthly['close'].rolling(window=12).std()  # 12개월 표준편차
    df_monthly['month_bollinger_upper'] = df_monthly['month_bollinger_ma12'] + (2 * df_monthly['month_bollinger_std'])  # 상한선
    df_monthly['month_bollinger_lower'] = df_monthly['month_bollinger_ma12'] - (2 * df_monthly['month_bollinger_std'])  # 하한선

    # **월봉 거래량 변화율** 계산
    df_monthly['month_volume_change'] = df_monthly['volume'].pct_change() * 100  # 거래량 변화율 (%) 계산

    # **월봉 MACD** 계산
    df_monthly['month_macd'] = df_monthly['close'].ewm(span=12, adjust=False).mean() - df_monthly['close'].ewm(span=26, adjust=False).mean()
    df_monthly['month_macd_signal'] = df_monthly['month_macd'].ewm(span=9, adjust=False).mean()  # MACD Signal Line

    # --- 3. 일봉 데이터에 다시 매핑 (Merge) ---
    # 주봉/월봉 데이터를 일봉 날짜에 맞춰서 채워넣습니다 (ffill: 앞의 값으로 채움)
    # 예: 화요일 데이터에는 지난주 금요일에 확정된 주봉 지표가 들어갑니다. (Look-ahead Bias 방지)
    
    
    # 편의상 인덱스 정렬
    df = df.sort_index()
    df_weekly = df_weekly.sort_index()
    df_monthly = df_monthly.sort_index()

    # asof merge 등을 사용할 수도 있지만, reindex + ffill 방식이 직관적입니다.
    # 주봉 데이터 확장
    weekly_cols = [
        'week_ma20', 'week_rsi', 
        'week_bollinger_upper', 'week_bollinger_lower', 
        'week_volume_change', 'week_macd', 'week_macd_signal'
    ]
    
    monthly_cols = [
        'month_ma12', 
        'month_bollinger_upper', 'month_bollinger_lower', 
        'month_volume_change', 'month_macd', 'month_macd_signal'
    ]
    # Reindex로 일봉 날짜에 맞게 확장 (ffill 사용)
    weekly_features = df_weekly[weekly_cols].reindex(df.index, method='ffill')
    monthly_features = df_monthly[monthly_cols].reindex(df.index, method='ffill')

    # 컬럼 병합
    for col in weekly_cols:
        df[col] = weekly_features[col]
        
    for col in monthly_cols:
        df[col] = monthly_features[col]

    # 현재 가격과 장기 이평선 간의 괴리율 (Trend Strength)
    df['dist_week_ma20'] = (df['close'] - df['week_ma20']) / df['week_ma20']

    return df

# (참고) RSI 계산 헬퍼 함수 (기존 로직 활용)
def compute_rsi(series, period=14):
    delta = series.diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
    rs = gain / loss
    return 100 - (100 / (1 + rs))