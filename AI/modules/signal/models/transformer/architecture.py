# AI/modules/signal/models/transformer/architecture.py
import tensorflow as tf
from tensorflow.keras import layers, models, Input

def transformer_encoder(inputs, head_size, num_heads, ff_dim, dropout=0):
    # Attention and Normalization
    x = layers.MultiHeadAttention(
        key_dim=head_size, num_heads=num_heads, dropout=dropout
    )(inputs, inputs)
    x = layers.Dropout(dropout)(x)
    x = layers.LayerNormalization(epsilon=1e-6)(x)
    res = x + inputs

    # Feed Forward Part
    x = layers.Conv1D(filters=ff_dim, kernel_size=1, activation="relu")(res)
    x = layers.Dropout(dropout)(x)
    x = layers.Conv1D(filters=inputs.shape[-1], kernel_size=1)(x)
    x = layers.LayerNormalization(epsilon=1e-6)(x)
    return x + res

def build_transformer_model(
    input_shape,
    n_tickers,
    n_sectors,
    head_size=256,
    num_heads=4,
    ff_dim=4,
    num_transformer_blocks=4,
    mlp_units=[128],
    dropout=0.2,
    mlp_dropout=0.2,
    n_outputs=1 
):
    # 1. 시계열 입력
    ts_input = Input(shape=input_shape, name="ts_input")
    
    # 2. 임베딩 입력 (Ticker, Sector)
    ticker_input = Input(shape=(1,), name="ticker_input")
    sector_input = Input(shape=(1,), name="sector_input")

    ticker_embedding = layers.Embedding(n_tickers + 1, 16)(ticker_input)
    ticker_embedding = layers.Flatten()(ticker_embedding)
    
    sector_embedding = layers.Embedding(n_sectors + 1, 16)(sector_input)
    sector_embedding = layers.Flatten()(sector_embedding)

    # 3. Transformer Block
    x = ts_input
    for _ in range(num_transformer_blocks):
        x = transformer_encoder(x, head_size, num_heads, ff_dim, dropout)

    # 4. Global Pooling & Concat
    x = layers.GlobalAveragePooling1D()(x)
    x = layers.Concatenate()([x, ticker_embedding, sector_embedding])

    # 5. MLP Head
    for dim in mlp_units:
        x = layers.Dense(dim, activation="relu")(x)
        x = layers.Dropout(mlp_dropout)(x)

    # 6. Output Layer
    outputs = layers.Dense(n_outputs, activation="sigmoid", name="output")(x)

    return models.Model(inputs=[ts_input, ticker_input, sector_input], outputs=outputs)

def build_regression_model(input_shape, n_tickers, n_sectors):
    return build_transformer_model(input_shape, n_tickers, n_sectors, n_outputs=1)