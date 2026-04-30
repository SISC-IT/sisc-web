# AI/modules/signal/models/patchtst/architecture.py
import torch
import torch.nn as nn


class RevIN(nn.Module):
    """
    Reversible Instance Normalization
    - 입력 시: 샘플 단위로 정규화 (mean, stdev 저장)
    - 출력 시: 저장된 통계로 원래 분포 복원
    """
    def __init__(self, num_features: int, eps=1e-5, affine=True):
        super(RevIN, self).__init__()
        self.num_features = num_features
        self.eps = eps
        self.affine = affine
        if self.affine:
            # 피처마다 정규화 강도를 조절하는 학습 가능한 파라미터
            self.affine_weight = nn.Parameter(torch.ones(self.num_features))   # γ
            self.affine_bias   = nn.Parameter(torch.zeros(self.num_features))  # β

    def forward(self, x, mode: str):
        if mode == 'norm':
            self._get_statistics(x)
            x = self._normalize(x)
        elif mode == 'denorm':
            x = self._denormalize(x)
        return x

    def _get_statistics(self, x):
        # 시간축(dim=1)을 따라 평균/표준편차 계산 후 저장
        # detach(): 통계값은 gradient 계산에 포함시키지 않음
        dim2reduce = tuple(range(1, x.ndim - 1))
        self.mean  = torch.mean(x, dim=dim2reduce, keepdim=True).detach()
        self.stdev = torch.sqrt(
            torch.var(x, dim=dim2reduce, keepdim=True, unbiased=False) + self.eps
        ).detach()

    def _normalize(self, x):
        x = (x - self.mean) / self.stdev
        if self.affine:
            x = x * self.affine_weight + self.affine_bias
        return x

    def _denormalize(self, x):
        if self.affine:
            x = (x - self.affine_bias) / (self.affine_weight + 1e-9)
        x = x * self.stdev + self.mean
        return x


class PatchTST_Model(nn.Module):
    """
    SISC 맞춤형 PatchTST 모델
    - 입력 : [Batch, Seq_Len, Features]  예) [32, 120, 17]
    - 출력 : [Batch, 4]  → 1일/3일/5일/7일 상승 확률 logits
    """
    def __init__(self,
                 seq_len=120,
                 enc_in=17,       # 피처 수 (일봉 11 + 주봉 4 + 월봉 2)
                 patch_len=16,
                 stride=8,
                 d_model=128,
                 n_heads=4,
                 e_layers=3,
                 d_ff=256,
                 dropout=0.1,
                 n_outputs=4):    # 1일/3일/5일/7일 예측
        super(PatchTST_Model, self).__init__()

        self.seq_len    = seq_len
        self.patch_len  = patch_len
        self.stride     = stride
        self.num_patches = int((seq_len - patch_len) / stride) + 1

        # 1. RevIN
        self.revin = RevIN(enc_in)

        # 2. Patch Embedding: 패치 하나를 d_model 차원 벡터로 변환
        self.patch_embedding    = nn.Linear(patch_len, d_model)
        # 학습 가능한 위치 임베딩 (sin/cos 방식 아님, 논문 공식 구현 방식)
        self.position_embedding = nn.Parameter(
            torch.randn(1, enc_in, self.num_patches, d_model)
        )
        self.dropout = nn.Dropout(dropout)

        # 3. Transformer Encoder (Channel Independent: B*F 단위로 독립 처리)
        encoder_layer = nn.TransformerEncoderLayer(
            d_model, n_heads, d_ff, dropout, batch_first=True
        )
        self.encoder = nn.TransformerEncoder(encoder_layer, e_layers)

        # 4. Flatten + MLP Head
        # enc_in * num_patches * d_model → 256 → n_outputs
        self.head = nn.Sequential(
            nn.Flatten(start_dim=1),
            nn.Linear(enc_in * self.num_patches * d_model, 256),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(256, n_outputs)  # 1일/3일/5일/7일 logits
        )

    def forward(self, x):
        # x: [B, S, F]
        B, S, F = x.shape

        # 1. RevIN 정규화
        x = self.revin(x, 'norm')                          # [B, S, F]

        # 2. Channel Independence: 피처를 독립 처리하기 위해 차원 변환
        x = x.permute(0, 2, 1)                            # [B, F, S]
        x = x.reshape(B * F, S)                            # [B*F, S]

        # 3. Patching: 시계열을 구간으로 자르기
        x = x.unfold(dimension=1, size=self.patch_len, step=self.stride)
        # [B*F, num_patches, patch_len]

        # 4. Patch Embedding
        x = self.patch_embedding(x)                        # [B*F, N, d_model]

        # 5. Positional Embedding
        pos_emb = self.position_embedding.repeat(B, 1, 1, 1).reshape(B * F, self.num_patches, -1)
        x = self.dropout(x + pos_emb)                      # [B*F, N, d_model]

        # 6. Transformer Encoder
        x = self.encoder(x)                                # [B*F, N, d_model]

        # 7. 채널 복원
        x = x.reshape(B, F, self.num_patches, -1)          # [B, F, N, d_model]

        # 8. Head → 4개 logits 출력
        out = self.head(x)                                  # [B, n_outputs]

        return out  # BCEWithLogitsLoss 사용 → sigmoid는 loss 내부에서 처리
