# AI/modules/signal/core/data_loader.py
"""
[데이터 로드 및 전처리 모듈]
- DB에서 OHLCV 데이터를 가져와 기술적 지표를 추가하고,
- 날짜 인덱스 설정 및 정렬을 수행하여 분석 준비를 마칩니다.
"""

import numpy as np
import pandas as pd
from typing import Tuple, List, Optional
from sklearn.preprocessing import MinMaxScaler
import sys
import os

# libs 모듈 import (절대 경로 보장)
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.fetcher import fetch_ohlcv
from AI.modules.signal.core.features import add_technical_indicators, add_multi_timeframe_features

class SignalDataLoader:
    def __init__(self, sequence_length: int = 60):
        self.sequence_length = sequence_length
        self.scaler = MinMaxScaler()
        self.feature_columns = [] # 학습에 사용된 피처 이름 저장

    def load_data(self, ticker: str, start_date: str, end_date: str) -> pd.DataFrame:
        """
        DB에서 데이터 로드 -> 지표 추가 -> 날짜 인덱스 설정 (표준화)
        """
        # 1. DB 조회
        df = fetch_ohlcv(ticker, start_date, end_date)
        
        if df.empty:
            # print(f"[DataLoader] {ticker} 데이터가 없습니다.") # 로그 너무 많으면 주석 처리
            return df
        
        # 2. 보조지표 추가
        df = add_technical_indicators(df)
        # 3. 주봉, 월봉 추가
        df = add_multi_timeframe_features(df)

        # 4. 날짜 인덱스 표준화 (Standardization)
        # 이미 DatetimeIndex라면 넘어감
        if not isinstance(df.index, pd.DatetimeIndex):
            # 대소문자 무관하게 날짜 컬럼 찾기
            date_col = next((col for col in df.columns if col.lower() in ['date', 'dates', '날짜']), None)
            
            if date_col:
                df[date_col] = pd.to_datetime(df[date_col])
                df.set_index(date_col, inplace=True)
            else:
                # 날짜 컬럼도 없고 인덱스도 날짜가 아니면 경고
                print(f"[WARN] {ticker}: 날짜 정보를 찾을 수 없습니다. (Index: {df.index.name})")
                return df # 또는 빈 DF 반환

        # 5. 정렬 및 타임존 제거 (필수)
        df.sort_index(inplace=True)
        
        if df.index.tz is not None:
            df.index = df.index.tz_localize(None)

        return df

    def create_sequences(self, data: pd.DataFrame, target_col: str = 'close', prediction_horizon: int = 5) -> Tuple[np.ndarray, np.ndarray]:
        """
        시계열 데이터를 (Samples, Timesteps, Features) 형태의 시퀀스로 변환
        """
        features = data.select_dtypes(include=[np.number]).columns.tolist()
        self.feature_columns = features
        
        # 주의: fit_transform은 학습 시에만 사용해야 함. 
        # 테스트 시에는 외부에서 주입된 scaler를 사용하거나 transform만 해야 함.
        scaled_data = self.scaler.fit_transform(data[features])
        
        X, y = [], []
        total_len = len(scaled_data)
        
        for i in range(self.sequence_length, total_len - prediction_horizon + 1):
            X.append(scaled_data[i - self.sequence_length : i])
            
            current_close = data[target_col].iloc[i-1]
            future_close = data[target_col].iloc[i + prediction_horizon - 1]
            
            # 미래 가격이 현재보다 '2%' 이상 커야 1 (임계값 0.02)
            threshold = 0.02 
            label = 1 if future_close > current_close * (1 + threshold) else 0
            y.append(label)
            
        return np.array(X), np.array(y)