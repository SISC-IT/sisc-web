# AI/modules/signal/models/PatchTST/architecture.py
import torch
import torch.nn as nn

class RevIN(nn.Module):
    """
    Reverse Instance Normalization: 시계열 데이터의 분포 변화(Distribution Shift) 문제를 해결
    """
    def __init__(self, num_features: int, eps=1e-5, affine=True):
        super(RevIN, self).__init__()
        self.num_features = num_features
        self.eps = eps
        self.affine = affine
        if self.affine:
            self._init_params()

    def _init_params(self):
        self.affine_weight = nn.Parameter(torch.ones(self.num_features))
        self.affine_bias = nn.Parameter(torch.zeros(self.num_features))

    def forward(self, x, mode: str):
        if mode == 'norm':
            self._get_statistics(x)
            x = self._normalize(x)
        elif mode == 'denorm':
            x = self._denormalize(x)
        return x

    def _get_statistics(self, x):
        dim2reduce = tuple(range(1, x.ndim - 1))
        self.mean = torch.mean(x, dim=dim2reduce, keepdim=True).detach()
        self.stdev = torch.sqrt(torch.var(x, dim=dim2reduce, keepdim=True, unbiased=False) + self.eps).detach()

    def _normalize(self, x):
        x = x - self.mean
        x = x / self.stdev
        if self.affine:
            x = x * self.affine_weight + self.affine_bias
        return x

    def _denormalize(self, x):
        if self.affine:
            x = (x - self.affine_bias) / (self.affine_weight + 1e-9)
        x = x * self.stdev
        x = x + self.mean
        return x

class PatchTST_Model(nn.Module):
    """
    SISC 맞춤형 PatchTST 모델
    - 입력: [Batch, Seq_Len, Features] (예: 120일치 데이터)
    - 출력: [Batch, 1] (상승 확률 Logits)
    """
    def __init__(self, 
                 seq_len=120, 
                 enc_in=7,  # Feature 개수
                 patch_len=16, 
                 stride=8, 
                 d_model=128, 
                 n_heads=4, 
                 e_layers=3, 
                 d_ff=256, 
                 dropout=0.1):
        super(PatchTST_Model, self).__init__()
        
        self.seq_len = seq_len
        self.patch_len = patch_len
        self.stride = stride
        self.num_patches = int((seq_len - patch_len) / stride) + 1

        # 1. RevIN (입력 정규화)
        self.revin = RevIN(enc_in)

        # 2. Patching & Embedding
        self.patch_embedding = nn.Linear(patch_len, d_model)
        self.position_embedding = nn.Parameter(torch.randn(1, enc_in, self.num_patches, d_model))
        self.dropout = nn.Dropout(dropout)

        # 3. Transformer Encoder Backbone (Channel Independent)
        encoder_layer = nn.TransformerEncoderLayer(d_model, n_heads, d_ff, dropout, batch_first=True)
        self.encoder = nn.TransformerEncoder(encoder_layer, e_layers)

        # 4. Flatten & Head (Prediction)
        self.head = nn.Sequential(
            nn.Flatten(start_dim=1),
            nn.Linear(enc_in * self.num_patches * d_model, 256),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(256, 1) # 최종 출력: 상승 확률 Logit (Sigmoid 전)
        )

    def forward(self, x):
        # x shape: [Batch, Seq_Len, Features]
        
        # 1. Normalization
        x = self.revin(x, 'norm') # [B, S, F]
        
        # 2. Channel Independence handling: [B, S, F] -> [B, F, S] -> [B*F, S]
        B, S, F = x.shape
        x = x.permute(0, 2, 1).reshape(B * F, S)
        
        # 3. Patching: [B*F, S] -> [B*F, Num_Patches, Patch_Len]
        x = x.unfold(dimension=1, size=self.patch_len, step=self.stride)
        
        # 4. Embedding: [B*F, Num_Patches, d_model]
        x = self.patch_embedding(x)
        
        # Position Embedding 더하기
        # [B*F, N, D] 형태로 맞춤
        pos_emb = self.position_embedding.repeat(B, 1, 1, 1).reshape(B * F, self.num_patches, -1)
        x = x + pos_emb
        x = self.dropout(x)

        # 5. Transformer Encoder
        x = self.encoder(x) # [B*F, N, D]

        # 6. Reshape back: [B, F, N, D]
        x = x.reshape(B, F, self.num_patches, -1)
        
        # 7. Final Prediction Head
        # 모든 채널과 패치 정보를 합쳐서 하나의 확률값 예측
        out = self.head(x) # [B, 1]
        
        return out # Logits 반환 (BCEWithLogitsLoss 사용 권장)