# AI/modules/signal/models/transformer/architecture.py
"""
[Transformer 모델 아키텍처 정의]
- TensorFlow/Keras를 사용하여 시계열 예측을 위한 Transformer 모델 구조를 생성합니다.
- Multi-Head Attention, Feed Forward Network, Dropout, LayerNormalization 등을 포함합니다.
"""

import tensorflow as tf
from tensorflow.keras import layers, models, Input

def transformer_encoder(inputs, head_size, num_heads, ff_dim, dropout=0):
    """
    트랜스포머 인코더 블록
    
    Args:
        inputs: 입력 텐서
        head_size: 어텐션 헤드 크기
        num_heads: 어텐션 헤드 개수
        ff_dim: 피드포워드 네트워크의 은닉층 크기
        dropout: 드롭아웃 비율
    """
    # Normalization and Attention
    x = layers.LayerNormalization(epsilon=1e-6)(inputs)
    x = layers.MultiHeadAttention(
        key_dim=head_size, num_heads=num_heads, dropout=dropout
    )(x, x)
    x = layers.Dropout(dropout)(x)
    res = x + inputs  # Skip Connection

    # Feed Forward Part
    x = layers.LayerNormalization(epsilon=1e-6)(res)
    x = layers.Conv1D(filters=ff_dim, kernel_size=1, activation="relu")(x)
    x = layers.Dropout(dropout)(x)
    x = layers.Conv1D(filters=inputs.shape[-1], kernel_size=1)(x)
    return x + res  # Skip Connection

def build_transformer_model(
    input_shape,
    head_size=256,
    num_heads=4,
    ff_dim=4,
    num_transformer_blocks=4,
    mlp_units=[128],
    dropout=0.4,
    mlp_dropout=0.25,
):
    """
    전체 Transformer 모델 빌드
    
    Args:
        input_shape: (timesteps, features) 형태의 튜플
        head_size: 각 어텐션 헤드의 차원
        num_heads: 어텐션 헤드 수
        ff_dim: 트랜스포머 내부 FFN 차원
        num_transformer_blocks: 인코더 블록 쌓을 개수
        mlp_units: 마지막 분류기(MLP) 층의 노드 수 리스트
        dropout: 트랜스포머 내부 드롭아웃
        mlp_dropout: MLP 층 드롭아웃
        
    Returns:
        keras.Model: 컴파일되지 않은 모델 객체
    """
    inputs = Input(shape=input_shape)
    x = inputs
    
    # 트랜스포머 인코더 블록 쌓기
    for _ in range(num_transformer_blocks):
        x = transformer_encoder(x, head_size, num_heads, ff_dim, dropout)

    # Global Average Pooling (시퀀스를 하나의 벡터로 압축)
    x = layers.GlobalAveragePooling1D()(x)
    
    # MLP (Classification Head)
    for dim in mlp_units:
        x = layers.Dense(dim, activation="relu")(x)
        x = layers.Dropout(mlp_dropout)(x)
        
    # 최종 출력층 (이진 분류: 상승/하락 확률)
    outputs = layers.Dense(1, activation="sigmoid")(x)
    
    return models.Model(inputs, outputs, name="Transformer_Signal_Model")