# AI/modules/signal/models/TCN/wrapper.py
import torch
import torch.nn as nn
import numpy as np
from typing import Optional, Dict, Any
from ...core.base_model import BaseSignalModel

# (간단한 TCN 아키텍처 내부 클래스 혹은 별도 파일 import)
class SimpleTCN(nn.Module):
    def __init__(self, input_size, output_size, num_channels, kernel_size, dropout):
        super(SimpleTCN, self).__init__()
        # 예시: 1D Conv 레이어 스택
        self.net = nn.Sequential(
            nn.Conv1d(input_size, num_channels[0], kernel_size, padding=(kernel_size-1)//2),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.AdaptiveAvgPool1d(1), # Global Pooling
            nn.Flatten(),
            nn.Linear(num_channels[0], output_size)
        )
    def forward(self, x):
        # x: [Batch, Seq, Feat] -> [Batch, Feat, Seq] (Conv1d 입력)
        x = x.permute(0, 2, 1)
        return self.net(x)

class TCNWrapper(BaseSignalModel):
    """
    [TCN 구현체] BaseSignalModel 인터페이스 준수
    - 용도: 단기 패턴 포착 (Local Pattern)
    """
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.model = None

    def build(self, input_shape: tuple):
        # input_shape: (seq_len, num_features)
        self.model = SimpleTCN(
            input_size=input_shape[1], # features
            output_size=1, # binary classification
            num_channels=[self.config.get('hidden_dim', 64)],
            kernel_size=self.config.get('kernel_size', 3),
            dropout=self.config.get('dropout', 0.2)
        ).to(self.device)

    def train(self, X_train: np.ndarray, y_train: np.ndarray, **kwargs):
        if self.model is None: self.build(X_train.shape[1:])
        # (PatchTST와 유사한 학습 루프 - 생략 또는 공통화 가능)
        pass 

    def predict(self, X_input: np.ndarray) -> np.ndarray:
        if self.model is None: raise Exception("Model not built")
        self.model.eval()
        with torch.no_grad():
            tensor_x = torch.FloatTensor(X_input).to(self.device)
            out = self.model(tensor_x)
            return torch.sigmoid(out).cpu().numpy()

    def save(self, filepath: str):
        torch.save(self.model.state_dict(), filepath)

    def load(self, filepath: str):
        # 빌드 후 로드
        if self.model is None:
             self.build((self.config.get('seq_len', 30), self.config.get('enc_in', 7)))
        self.model.load_state_dict(torch.load(filepath, map_location=self.device))