# AI/modules/signal/models/PatchTST/wrapper.py
import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
import os
from typing import Optional, Dict, Any
from ...core.base_model import BaseSignalModel
from .architecture import PatchTST_Model

class PatchTSTWrapper(BaseSignalModel):
    """
    [PatchTST 구현체] BaseSignalModel 인터페이스 준수
    - 용도: 중장기 추세 예측 (Trend Specialist)
    """
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.model = None # build() 호출 시 생성됨

    def build(self, input_shape: tuple):
        """
        모델 아키텍처 생성
        Args:
            input_shape: (seq_len, num_features) 예: (120, 7)
        """
        seq_len, num_features = input_shape
        
        self.model = PatchTST_Model(
            seq_len=seq_len,
            enc_in=num_features,
            patch_len=self.config.get('patch_len', 16),
            stride=self.config.get('stride', 8),
            d_model=self.config.get('d_model', 128),
            dropout=self.config.get('dropout', 0.1)
        ).to(self.device)
        print(f"✅ PatchTST Built: Input {input_shape} -> Output [1] (Prob)")

    def train(self, X_train: np.ndarray, y_train: np.ndarray, 
              X_val: Optional[np.ndarray] = None, y_val: Optional[np.ndarray] = None, **kwargs):
        """모델 학습 수행 (BCE Loss)"""
        if self.model is None:
            self.build(X_train.shape[1:]) # (Seq, Feat)

        criterion = nn.BCEWithLogitsLoss()
        optimizer = optim.AdamW(self.model.parameters(), lr=self.config.get('lr', 0.0001))
        epochs = self.config.get('epochs', 50)
        batch_size = self.config.get('batch_size', 32)

        # Tensor 변환
        X_tensor = torch.FloatTensor(X_train).to(self.device)
        y_tensor = torch.FloatTensor(y_train).view(-1, 1).to(self.device)

        self.model.train()
        for epoch in range(epochs):
            # (간소화를 위해 배치 루프 생략하고 전체 주입 예시 - 실제론 DataLoader 사용 권장)
            permutation = torch.randperm(X_tensor.size(0))
            epoch_loss = 0
            
            for i in range(0, X_tensor.size(0), batch_size):
                indices = permutation[i:i+batch_size]
                batch_x, batch_y = X_tensor[indices], y_tensor[indices]

                optimizer.zero_grad()
                output = self.model(batch_x)
                loss = criterion(output, batch_y)
                loss.backward()
                optimizer.step()
                epoch_loss += loss.item()
            
            if (epoch + 1) % 10 == 0:
                print(f"Epoch [{epoch+1}/{epochs}] Loss: {epoch_loss:.4f}")

    def predict(self, X_input: np.ndarray) -> np.ndarray:
        """추론 수행: 상승 확률(0~1) 반환"""
        if self.model is None:
            raise Exception("Model not initialized. Call build() or load() first.")
            
        self.model.eval()
        with torch.no_grad():
            X_tensor = torch.FloatTensor(X_input).to(self.device)
            logits = self.model(X_tensor)
            probs = torch.sigmoid(logits).cpu().numpy() # Logit -> Probability
            
        return probs

    def save(self, filepath: str):
        torch.save(self.model.state_dict(), filepath)
        print(f"💾 PatchTST saved to {filepath}")

    def load(self, filepath: str):
        # 로드 시에는 config에 있는 shape 정보를 기반으로 build를 먼저 해야 함
        # (혹은 저장 시 shape 정보를 같이 저장하는 방식 사용)
        if self.model is None:
             # 임시: config에 저장된 shape 사용 (운영 시 보완 필요)
            self.build((self.config.get('seq_len', 120), self.config.get('enc_in', 7)))
            
        self.model.load_state_dict(torch.load(filepath, map_location=self.device))
        self.model.eval()
        print(f"📂 PatchTST loaded from {filepath}")