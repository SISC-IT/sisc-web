import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, Model

# 위치 인코딩
def positional_encoding(maxlen: int, d_model: int) -> tf.Tensor:
    angles = np.arange(maxlen)[:, None] / np.power(
        10000, (2 * (np.arange(d_model)[None, :] // 2)) / d_model
    )
    pos_encoding = np.zeros((maxlen, d_model))
    pos_encoding[:, 0::2] = np.sin(angles[:, 0::2])
    pos_encoding[:, 1::2] = np.cos(angles[:, 1::2])
    return tf.constant(pos_encoding, dtype=tf.float32)

# Transformer 분류기
def build_transformer_classifier(seq_len: int, n_features: int,
                                 d_model: int = 64, num_heads: int = 4,
                                 ff_dim: int = 128, num_layers: int = 2,
                                 dropout: float = 0.1) -> Model:
    inp = layers.Input(shape=(seq_len, n_features), name="inputs")

    # 입력 projection
    x = layers.Dense(d_model)(inp)
    x = x + positional_encoding(seq_len, d_model)

    for _ in range(num_layers):
        # MHA 블록
        attn_out = layers.MultiHeadAttention(
            num_heads=num_heads, key_dim=d_model // num_heads, dropout=dropout
        )(x, x, training=False)
        x = layers.LayerNormalization(epsilon=1e-5)(x + attn_out)

        # FFN 블록
        ffn = layers.Dense(ff_dim, activation="gelu")(x)
        ffn = layers.Dropout(dropout)(ffn, training=False)
        ffn = layers.Dense(d_model)(ffn)
        x = layers.LayerNormalization(epsilon=1e-5)(x + ffn)

    # 풀링 + 출력
    x = layers.GlobalAveragePooling1D()(x)
    x = layers.Dropout(dropout)(x, training=False)
    out = layers.Dense(3, activation="softmax", name="probs")(x)

    return Model(inp, out, name="transformer_classifier")
