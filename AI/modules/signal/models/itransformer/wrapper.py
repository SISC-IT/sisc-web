# AI/modules/signal/models/iTransformer/wrapper.py
import torch
import torch.nn as nn
import numpy as np
from typing import Optional, Dict, Any
from ...core.base_model import BaseSignalModel

# iTransformer 아키텍처 (약식)
class iTransformer(nn.Module):
    def __init__(self, num_variates, lookback_len, d_model, dropout):
        super().__init__()
        # 각 변수(Feature)를 독립된 토큰으로 취급하여 Attention 수행
        self.enc_embedding = nn.Linear(lookback_len, d_model)
        self.encoder = nn.TransformerEncoderLayer(d_model=d_model, nhead=4, batch_first=True)
        self.head = nn.Linear(d_model * num_variates, 1)

    def forward(self, x):
        # x: [Batch, Seq, Feat]
        # Transpose to [Batch, Feat, Seq] for iTransformer mechanism
        x = x.permute(0, 2, 1) 
        x = self.enc_embedding(x) # [Batch, Feat, d_model]
        x = self.encoder(x)
        x = x.reshape(x.shape[0], -1) # Flatten
        return self.head(x)

class ITransformerWrapper(BaseSignalModel):
    """
    [iTransformer 구현체] BaseSignalModel 인터페이스 준수
    - 용도: 다변량 상관관계 분석 (Correlation Expert)
    """
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.model = None

    def build(self, input_shape: tuple):
        # input_shape: (seq_len, num_features)
        self.model = iTransformer(
            num_variates=input_shape[1],
            lookback_len=input_shape[0],
            d_model=self.config.get('d_model', 64),
            dropout=self.config.get('dropout', 0.1)
        ).to(self.device)

    def train(self, X_train: np.ndarray, y_train: np.ndarray, **kwargs):
        if self.model is None: self.build(X_train.shape[1:])
        # (학습 루프 구현)
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
        if self.model is None:
             self.build((self.config.get('seq_len', 60), self.config.get('enc_in', 9)))
        self.model.load_state_dict(torch.load(filepath, map_location=self.device))