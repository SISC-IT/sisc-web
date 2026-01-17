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