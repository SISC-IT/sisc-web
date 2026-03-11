#AI/modules/signal/models/transformer/wrapper.py
"""
[Transformer 모델 래퍼] - Meta-Ensemble 업그레이드 버전
- BaseSignalModel 인터페이스를 구현한 실제 실행 클래스입니다.
- 기존 Numpy 텐서 기반의 추론과 신규 DataFrame 기반의 앙상블 추론을 모두 지원합니다.
"""

import os
import pickle
import numpy as np
import pandas as pd
import tensorflow as tf
from typing import Dict, Any, Optional, Union
from AI.modules.signal.core.base_model import BaseSignalModel
from .architecture import build_transformer_model 

class TransformerSignalModel(BaseSignalModel):
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.model_name = "transformer"
        
        self.seq_len = config.get("seq_len", 60)
        self.features = config.get("features", []) # 사용할 17개 피처 리스트
        self.scaler = None
        
    def load_scaler(self, filepath: str):
        """[추가] 추론 시 사용할 데이터 정규화 스케일러 로드"""
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"스케일러 파일이 없습니다: {filepath}")
        with open(filepath, "rb") as f:
            self.scaler = pickle.load(f)
        print(f"✅ 스케일러 로드 완료: {filepath}")

    def build(self, input_shape: tuple):
        """설정(config)에 따라 모델 아키텍처 생성"""
        if len(input_shape) != 2:
            if len(input_shape) == 3 and input_shape[0] is None:
                 input_shape = input_shape[1:]
            else:
                raise ValueError(f"입력 차원은 (timesteps, features) 2차원이어야 합니다. 현재: {input_shape}")

        self.model = build_transformer_model(
            input_shape=input_shape,
            n_tickers=self.config.get("n_tickers", 1000),
            n_sectors=self.config.get("n_sectors", 50),
            n_outputs=4,  # 1일, 3일, 5일, 7일 예측
            head_size=self.config.get("head_size", 256),
            num_heads=self.config.get("num_heads", 4),
            ff_dim=self.config.get("ff_dim", 4),    
            num_transformer_blocks=self.config.get("num_blocks", 4),
            mlp_units=self.config.get("mlp_units", [128]),
            dropout=self.config.get("dropout", 0.4),
            mlp_dropout=self.config.get("mlp_dropout", 0.25)
        )
        
        learning_rate = self.config.get("learning_rate", 1e-4)
        self.model.compile(
            loss="binary_crossentropy",
            optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
            metrics=["accuracy", "AUC"]
        )

    def train(self, X_train: np.ndarray, y_train: np.ndarray, X_val: Optional[np.ndarray] = None, y_val: Optional[np.ndarray] = None, **kwargs):
        """모델 학습 수행"""
        if self.model is None:
            raise ValueError("모델이 빌드되지 않았습니다. build()를 먼저 호출하세요.")

        epochs = int(kwargs.pop("epochs", self.config.get("epochs", 50)))
        batch_size = int(kwargs.pop("batch_size", self.config.get("batch_size", 32)))
        verbose = int(kwargs.pop("verbose", 1))
        callbacks = kwargs.pop("callbacks", [])
        validation_data = (X_val, y_val) if (X_val is not None and y_val is not None) else None

        history = self.model.fit(
            X_train, y_train,
            validation_data=validation_data,
            epochs=epochs,
            batch_size=batch_size,
            callbacks=callbacks,
            verbose=verbose,
            **kwargs 
        )
        return history

    def predict(self, X_input: np.ndarray, ticker_id: int = 0, sector_id: int = 0, **kwargs) -> np.ndarray:
        """
        [기본 계약 준수] 
        - 순수 Numpy 배열을 받아 예측을 수행하고 Numpy 배열을 반환합니다.
        """
        if self.model is None:
            raise ValueError("모델이 없습니다. load()하거나 build() 하세요.")
            
        if len(X_input.shape) == 2:
            X_input = np.expand_dims(X_input, axis=0)
            
        t_id_tensor = np.array([[ticker_id]])
        s_id_tensor = np.array([[sector_id]])
        
        return self.model.predict([X_input, t_id_tensor, s_id_tensor], **kwargs)

    def get_signals(self, df: pd.DataFrame, ticker_id: int = 0, sector_id: int = 0) -> Dict[str, float]:
        """
        [파이프라인 전용 메서드] 
        - DataFrame을 입력받아 전처리 후 포트폴리오 로직에 맞는 딕셔너리로 반환합니다.
        """
        if not self.features:
            raise ValueError("추론에 필요한 features(컬럼 리스트)가 설정되지 않았습니다.")
        if self.scaler is None:
            raise ValueError("스케일러가 로드되지 않았습니다. load_scaler()를 먼저 호출하세요.")

        # 1. 피처 추출 및 시퀀스 길이만큼 자르기
        data = df[self.features].iloc[-self.seq_len:].values
        
        # 2. 스케일링
        scaled_data = self.scaler.transform(data)
        
        # 3. 모델 예측 (내부의 predict 재사용)
        pred_array = self.predict(scaled_data, ticker_id=ticker_id, sector_id=sector_id, verbose=0)
        probs = pred_array[0]

        # 4. 딕셔너리로 반환
        return {
            f"{self.model_name}_1d": float(probs[0]),
            f"{self.model_name}_3d": float(probs[1]),
            f"{self.model_name}_5d": float(probs[2]),
            f"{self.model_name}_7d": float(probs[3])
        }
    def save(self, filepath: str):
        """모델 저장"""
        if self.model is None:
            print("저장할 모델이 없습니다.")
            return
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        self.model.save(filepath)
        print(f"✅ 모델 저장 완료: {filepath}")

    def load(self, filepath: str):
        """모델 로드"""
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"모델 파일이 없습니다: {filepath}")
        self.model = tf.keras.models.load_model(filepath)
        print(f"✅ 모델 로드 완료: {filepath}")