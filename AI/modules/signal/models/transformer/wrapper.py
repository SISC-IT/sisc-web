# AI/modules/signal/models/transformer/wrapper.py
"""
[Transformer 모델 래퍼]
- BaseSignalModel 인터페이스를 구현한 실제 실행 클래스입니다.
- architecture.py에서 정의한 모델을 빌드하고, 학습/예측/저장 로직을 수행합니다.
"""

import os
import numpy as np
import tensorflow as tf
from typing import Dict, Any, Optional
from AI.modules.signal.core.base_model import BaseSignalModel
from .architecture import build_transformer_model 

class TransformerSignalModel(BaseSignalModel):
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.model_name = "transformer_v1"
        
    def build(self, input_shape: tuple):
        """설정(config)에 따라 모델 아키텍처 생성"""
        # 차원 검증
        if len(input_shape) != 2:
             # input_shape가 (timesteps, features) 2차원이 아니라면 경고 또는 에러
             # 일부 환경에서 (None, timesteps, features)로 올 수 있으므로 유연하게 처리
            if len(input_shape) == 3 and input_shape[0] is None:
                 input_shape = input_shape[1:]
            else:
                raise ValueError(f"입력 차원은 (timesteps, features) 2차원이어야 합니다. 현재: {input_shape}")

        self.model = build_transformer_model(
            input_shape=input_shape,
            head_size=self.config.get("head_size", 256),
            num_heads=self.config.get("num_heads", 4),
            ff_dim=self.config.get("ff_dim", 4),
            num_transformer_blocks=self.config.get("num_blocks", 4),
            mlp_units=self.config.get("mlp_units", [128]),
            dropout=self.config.get("dropout", 0.4),
            mlp_dropout=self.config.get("mlp_dropout", 0.25)
        )
        
        # 컴파일
        learning_rate = self.config.get("learning_rate", 1e-4)
        self.model.compile(
            loss="binary_crossentropy",
            optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
            metrics=["accuracy", "AUC"]
        )

    def train(
        self,
        X_train: np.ndarray,
        y_train: np.ndarray,
        X_val: Optional[np.ndarray] = None,
        y_val: Optional[np.ndarray] = None,
        **kwargs
    ):
        """모델 학습 수행"""
        if self.model is None:
            raise ValueError("모델이 빌드되지 않았습니다. build()를 먼저 호출하세요.")

        # ✅ 호출자가 주면 우선, 없으면 config, 없으면 default
        epochs = int(kwargs.pop("epochs", self.config.get("epochs", 50)))
        batch_size = int(kwargs.pop("batch_size", self.config.get("batch_size", 32)))
        verbose = int(kwargs.pop("verbose", 1))

        # callbacks는 pop으로 빼서 중복 전달 방지
        callbacks = kwargs.pop("callbacks", [])

        # validation_data는 (X_val, y_val)이 둘 다 있을 때만
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




    def predict(self, X_input: np.ndarray, **kwargs) -> np.ndarray:
        """추론 수행"""
        if self.model is None:
            raise ValueError("모델이 없습니다. load()하거나 build() 하세요.")
            
        # Keras 모델은 (batch, time, feat) 형태를 기대하므로 차원 확인
        if len(X_input.shape) == 2:
            X_input = np.expand_dims(X_input, axis=0)
            
        return self.model.predict(X_input,  **kwargs)

    def save(self, filepath: str):
        """모델 저장"""
        if self.model is None:
            print("저장할 모델이 없습니다.")
            return
        
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        self.model.save(filepath)
        print(f"모델 저장 완료: {filepath}")

    def load(self, filepath: str):
        """모델 로드"""
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"모델 파일이 없습니다: {filepath}")
            
        self.model = tf.keras.models.load_model(filepath)