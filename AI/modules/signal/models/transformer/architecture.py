import tensorflow as tf
from tensorflow.keras import layers, models, regularizers

def build_transformer_model(
    input_shape,
    n_tickers,
    n_sectors,
    head_size=256,
    num_heads=4,
    ff_dim=4,
    num_transformer_blocks=4,
    mlp_units=[128],
    dropout=0.1,
    mlp_dropout=0.1,
):
    """
    [Stage 1: Classification Model]
    - 시계열 데이터와 정적 데이터(종목/섹터)를 결합하여 '상승 확률'을 예측합니다.
    """
    
    # 1. 입력 레이어
    inputs_ts = layers.Input(shape=input_shape, name="time_series_input")
    inputs_ticker = layers.Input(shape=(1,), name="ticker_input")
    inputs_sector = layers.Input(shape=(1,), name="sector_input")

    # 2. 임베딩
    emb_ticker = layers.Embedding(input_dim=n_tickers + 1, output_dim=16, name="emb_ticker")(inputs_ticker)
    emb_sector = layers.Embedding(input_dim=n_sectors + 1, output_dim=8, name="emb_sector")(inputs_sector)
    static_features = layers.Concatenate(axis=-1)([emb_ticker, emb_sector])

    # 3. 데이터 융합
    time_steps = input_shape[0]
    static_features_flat = layers.Flatten()(static_features)
    static_features_repeated = layers.RepeatVector(time_steps)(static_features_flat)
    x = layers.Concatenate(axis=-1)([inputs_ts, static_features_repeated])

    # 4. Transformer Blocks
    for i in range(num_transformer_blocks):
        x1 = layers.MultiHeadAttention(key_dim=head_size, num_heads=num_heads, dropout=dropout)(x, x)
        x2 = layers.Add()([x, x1])
        x2 = layers.LayerNormalization(epsilon=1e-6)(x2)
        x3 = layers.Conv1D(filters=ff_dim, kernel_size=1, activation="relu")(x2)
        x3 = layers.Dropout(dropout)(x3)
        x3 = layers.Conv1D(filters=x.shape[-1], kernel_size=1)(x3)
        x = layers.Add()([x2, x3])
        x = layers.LayerNormalization(epsilon=1e-6)(x)

    # 5. Output Head (Classification)
    x = layers.GlobalAveragePooling1D(data_format="channels_last")(x)
    for dim in mlp_units:
        x = layers.Dense(dim, activation="relu")(x)
        x = layers.Dropout(mlp_dropout)(x)
    
    outputs = layers.Dense(1, activation="sigmoid", name="prediction")(x)

    model = models.Model(
        inputs=[inputs_ts, inputs_ticker, inputs_sector], 
        outputs=outputs,
        name="Universal_Transformer_Classfier"
    )
    
    return model  # <--- [중요] 이 부분이 빠져 있었습니다!


def build_regression_model(
    input_shape,
    n_tickers,
    n_sectors,
    head_size=256,
    num_heads=4,
    ff_dim=4,
    num_transformer_blocks=4,
    mlp_units=[128],
    dropout=0.1,
    mlp_dropout=0.1,
):
    """
    [Stage 2: Regression Model]
    - 1단계와 동일한 입력을 받아 '예상 수익률'을 예측합니다.
    """
    
    # 1. 입력 레이어
    inputs_ts = layers.Input(shape=input_shape, name="time_series_input")
    inputs_ticker = layers.Input(shape=(1,), name="ticker_input")
    inputs_sector = layers.Input(shape=(1,), name="sector_input")

    # 2. 임베딩
    emb_ticker = layers.Embedding(input_dim=n_tickers + 1, output_dim=16, name="emb_ticker")(inputs_ticker)
    emb_sector = layers.Embedding(input_dim=n_sectors + 1, output_dim=8, name="emb_sector")(inputs_sector)
    static_features = layers.Concatenate(axis=-1)([emb_ticker, emb_sector])

    # 3. 데이터 융합
    time_steps = input_shape[0]
    static_features_flat = layers.Flatten()(static_features)
    static_features_repeated = layers.RepeatVector(time_steps)(static_features_flat)
    x = layers.Concatenate(axis=-1)([inputs_ts, static_features_repeated])

    # 4. Transformer Blocks
    for i in range(num_transformer_blocks):
        x1 = layers.MultiHeadAttention(key_dim=head_size, num_heads=num_heads, dropout=dropout)(x, x)
        x2 = layers.Add()([x, x1])
        x2 = layers.LayerNormalization(epsilon=1e-6)(x2)
        x3 = layers.Conv1D(filters=ff_dim, kernel_size=1, activation="relu")(x2)
        x3 = layers.Dropout(dropout)(x3)
        x3 = layers.Conv1D(filters=x.shape[-1], kernel_size=1)(x3)
        x = layers.Add()([x2, x3])
        x = layers.LayerNormalization(epsilon=1e-6)(x)

    # 5. Output Head (Regression)
    x = layers.GlobalAveragePooling1D(data_format="channels_last")(x)
    for dim in mlp_units:
        x = layers.Dense(dim, activation="relu")(x)
        x = layers.Dropout(mlp_dropout)(x)
    
    # [핵심] 활성화 함수 없음 (Linear) -> 실수값 예측
    outputs = layers.Dense(1, activation=None, name="return_prediction")(x)

    model = models.Model(
        inputs=[inputs_ts, inputs_ticker, inputs_sector], 
        outputs=outputs,
        name="Universal_Transformer_Regressor"
    )
    
    return model