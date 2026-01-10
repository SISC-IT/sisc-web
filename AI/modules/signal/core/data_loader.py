# AI/modules/signal/core/data_loader.py
"""
[데이터 로드 및 전처리 모듈]
- DB에서 OHLCV 데이터를 가져와 기술적 지표를 추가하고,
- LSTM/Transformer 학습에 맞게 시퀀스(Sequence) 데이터로 변환합니다.
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
from AI.modules.signal.core.features import add_technical_indicators

class SignalDataLoader:
    def __init__(self, sequence_length: int = 60):
        self.sequence_length = sequence_length
        self.scaler = MinMaxScaler()
        self.feature_columns = [] # 학습에 사용된 피처 이름 저장

    def load_data(self, ticker: str, start_date: str, end_date: str) -> pd.DataFrame:
        """DB에서 데이터 로드 및 지표 추가"""
        df = fetch_ohlcv(ticker, start_date, end_date)
        if df.empty:
            print(f"[DataLoader] {ticker} 데이터가 없습니다.")
            return df
            
        df = add_technical_indicators(df)
        return df

    def create_sequences(self, data: pd.DataFrame, target_col: str = 'close', prediction_horizon: int = 1) -> Tuple[np.ndarray, np.ndarray]:
        """
        시계열 데이터를 (Samples, Timesteps, Features) 형태의 시퀀스로 변환
        
        Args:
            data (pd.DataFrame): 전체 데이터프레임
            target_col (str): 예측 대상 컬럼 (보통 close 또는 수익률)
            prediction_horizon (int): 며칠 뒤를 예측할지 (1이면 다음날)
            
        Returns:
            X (np.ndarray): 입력 시퀀스
            y (np.ndarray): 정답 레이블
        """
        # 사용할 피처 선택 (날짜 제외 수치형 컬럼만)
        features = data.select_dtypes(include=[np.number]).columns.tolist()
        self.feature_columns = features
        
        # 데이터 스케일링 (0~1)
        scaled_data = self.scaler.fit_transform(data[features])
        
        X, y = [], []
        
        # 시퀀스 생성 로직
        # i: 현재 시점 (예측 기준일)
        # i - sequence_length : 입력 데이터 시작점
        # i + prediction_horizon : 정답 데이터 (미래)
        
        total_len = len(scaled_data)
        
        for i in range(self.sequence_length, total_len - prediction_horizon + 1):
            # 입력: 과거 N일치 데이터
            X.append(scaled_data[i - self.sequence_length : i])
            
            # 정답: 미래의 종가 등락 여부 (0: 하락/보합, 1: 상승) - 분류 모델용 예시
            # 회귀 모델이라면 실제 값을 사용
            
            # 여기서는 예시로 "다음날 종가가 오늘 종가보다 올랐나?"를 1/0으로 라벨링
            current_close = data[target_col].iloc[i-1] # 어제 종가
            future_close = data[target_col].iloc[i + prediction_horizon - 1] # 미래 종가
            
            label = 1 if future_close > current_close else 0
            y.append(label)
            
        return np.array(X), np.array(y)