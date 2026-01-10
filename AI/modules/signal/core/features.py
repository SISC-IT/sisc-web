# AI/modules/signal/core/features.py
"""
[피처 엔지니어링 모듈]
- OHLCV 데이터를 입력받아 학습에 필요한 기술적 지표(RSI, MACD, 볼린저밴드 등)를 추가합니다.
- 데이터 로더(DataLoader)에서 이 함수를 호출하여 전처리를 수행합니다.
"""

import pandas as pd
import numpy as np

def add_technical_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """
    데이터프레임에 기술적 지표 컬럼을 추가합니다.
    
    Args:
        df (pd.DataFrame): OHLCV 데이터 (필수 컬럼: 'close', 'high', 'low', 'volume')
        
    Returns:
        pd.DataFrame: 지표가 추가된 데이터프레임
    """
    if df.empty:
        return df
        
    df = df.copy()
    
    # 1. 이동평균선 (Simple Moving Average)
    df['ma5'] = df['close'].rolling(window=5).mean()
    df['ma20'] = df['close'].rolling(window=20).mean()
    df['ma60'] = df['close'].rolling(window=60).mean()
    
    # 2. RSI (Relative Strength Index)
    delta = df['close'].diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=14).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=14).mean()
    rs = gain / loss
    # division by zero 방지
    rs = rs.fillna(0) 
    df['rsi'] = 100 - (100 / (1 + rs))
    
    # 3. 볼린저 밴드 (Bollinger Bands)
    df['std20'] = df['close'].rolling(window=20).std()
    df['upper_band'] = df['ma20'] + (df['std20'] * 2)
    df['lower_band'] = df['ma20'] - (df['std20'] * 2)
    
    # 4. MACD (Moving Average Convergence Divergence)
    exp12 = df['close'].ewm(span=12, adjust=False).mean()
    exp26 = df['close'].ewm(span=26, adjust=False).mean()
    df['macd'] = exp12 - exp26
    df['signal_line'] = df['macd'].ewm(span=9, adjust=False).mean()
    
    # 5. 거래량 변화율
    df['vol_change'] = df['volume'].pct_change()
    
    # 6. 결측치 처리 (지표 계산 초반 구간)
    df = df.fillna(method='bfill')
    df = df.fillna(0) # 앞부분 bfill로도 안 채워지는 경우 0 처리
    
    return df