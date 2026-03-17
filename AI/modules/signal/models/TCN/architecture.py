import torch
import torch.nn as nn
from typing import List


class Chomp1d(nn.Module):
    # Causal padding 뒤에 생기는 미래 시점 누수를 잘라냅니다.
    def __init__(self, chomp_size: int):
        super().__init__()
        self.chomp_size = chomp_size

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        if self.chomp_size == 0:
            return x
        return x[:, :, :-self.chomp_size].contiguous()


class TemporalBlock(nn.Module):
    # 두 개의 dilated Conv1d와 residual connection으로 TCN의 기본 블록을 구성합니다.
    def __init__(
        self,
        in_channels: int,
        out_channels: int,
        kernel_size: int,
        dilation: int,
        dropout: float,
    ):
        super().__init__()
        padding = (kernel_size - 1) * dilation

        self.net = nn.Sequential(
            nn.Conv1d(
                in_channels,
                out_channels,
                kernel_size,
                padding=padding,
                dilation=dilation,
            ),
            Chomp1d(padding),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Conv1d(
                out_channels,
                out_channels,
                kernel_size,
                padding=padding,
                dilation=dilation,
            ),
            Chomp1d(padding),
            nn.ReLU(),
            nn.Dropout(dropout),
        )
        self.downsample = (
            nn.Conv1d(in_channels, out_channels, kernel_size=1)
            if in_channels != out_channels
            else None
        )
        self.activation = nn.ReLU()

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        residual = x if self.downsample is None else self.downsample(x)
        return self.activation(self.net(x) + residual)


class TCNClassifier(nn.Module):
    # 여러 개의 TemporalBlock을 쌓아 멀티 호라이즌 이진 분류 logits를 출력합니다.
    def __init__(
        self,
        input_size: int,
        output_size: int,
        num_channels: List[int],
        kernel_size: int = 3,
        dropout: float = 0.2,
    ):
        super().__init__()

        layers = []
        for i, out_channels in enumerate(num_channels):
            in_channels = input_size if i == 0 else num_channels[i - 1]
            dilation = 2 ** i
            layers.append(
                TemporalBlock(
                    in_channels=in_channels,
                    out_channels=out_channels,
                    kernel_size=kernel_size,
                    dilation=dilation,
                    dropout=dropout,
                )
            )

        self.backbone = nn.Sequential(*layers)
        self.head = nn.Sequential(
            nn.AdaptiveAvgPool1d(1),
            nn.Flatten(),
            nn.Linear(num_channels[-1], output_size),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # 입력은 [Batch, Seq, Features]이며 Conv1d에 맞게 [Batch, Features, Seq]로 바꿉니다.
        x = x.permute(0, 2, 1)
        x = self.backbone(x)
        return self.head(x)
