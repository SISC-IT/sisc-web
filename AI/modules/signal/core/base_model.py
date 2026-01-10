# AI/modules/signal/core/base_model.py
"""
[매매 신호 모델 인터페이스]
- 모든 AI 매매 모델(Transformer, LSTM, XGBoost 등)이 구현해야 하는 추상 기본 클래스입니다.
- 이 인터페이스를 따르는 모델은 workflows/train.py 등의 실행 스크립트에서 교체하여 사용할 수 있습니다.
"""

from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
import pandas as pd
import numpy as np

class BaseSignalModel(ABC):
    """모든 매매 신호 모델의 부모 클래스"""

    def __init__(self, config: Dict[str, Any]):
        """
        모델 초기화
        Args:
            config (Dict): 모델 하이퍼파라미터 및 설정값
        """
        self.config = config
        self.model = None

    @abstractmethod
    def build(self, input_shape: tuple):
        """
        모델 아키텍처(구조) 생성
        Args:
            input_shape (tuple): 입력 데이터 형태 (timesteps, features)
        """
        pass

    @abstractmethod
    def train(self, 
              X_train: np.ndarray, 
              y_train: np.ndarray, 
              X_val: Optional[np.ndarray] = None, 
              y_val: Optional[np.ndarray] = None, 
              **kwargs):
        """
        모델 학습 수행
        """
        pass

    @abstractmethod
    def predict(self, X_input: np.ndarray) -> np.ndarray:
        """
        추론 수행
        Args:
            X_input (np.ndarray): 입력 데이터
        Returns:
            np.ndarray: 예측 결과 (확률 또는 값)
        """
        pass

    @abstractmethod
    def save(self, filepath: str):
        """모델 가중치 저장"""
        pass

    @abstractmethod
    def load(self, filepath: str):
        """모델 가중치 로드"""
        pass