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
    [Universal Transformer Model]
    - 시계열 데이터(가격/거래량)와 정적 데이터(종목/섹터 임베딩)를 결합하여 학습하는 모델입니다.
    
    Args:
        input_shape: (time_steps, n_features) 형태의 튜플 (예: (60, 15))
        n_tickers: 임베딩할 종목 ID의 최대 개수
        n_sectors: 임베딩할 섹터 ID의 최대 개수
    """
    
    # ------------------------------------------------------------------
    # 1. 입력 레이어 정의 (Multi-Input)
    # ------------------------------------------------------------------
    # (1) 시계열 데이터 입력: (Batch, TimeSteps, Features)
    inputs_ts = layers.Input(shape=input_shape, name="time_series_input")
    
    # (2) 티커 ID 입력: (Batch, 1) - 예: 삼성전자=0, 하이닉스=1...
    inputs_ticker = layers.Input(shape=(1,), name="ticker_input")
    
    # (3) 섹터 ID 입력: (Batch, 1) - 예: IT=0, 금융=1...
    inputs_sector = layers.Input(shape=(1,), name="sector_input")

    # ------------------------------------------------------------------
    # 2. 임베딩 (Embedding)
    # ------------------------------------------------------------------
    # 티커 임베딩: 각 종목을 16차원 벡터로 표현
    # output: (Batch, 1, 16)
    emb_ticker = layers.Embedding(input_dim=n_tickers + 1, output_dim=16, name="emb_ticker")(inputs_ticker)
    
    # 섹터 임베딩: 각 섹터를 8차원 벡터로 표현
    # output: (Batch, 1, 8)
    emb_sector = layers.Embedding(input_dim=n_sectors + 1, output_dim=8, name="emb_sector")(inputs_sector)
    
    # 두 임베딩을 결합 -> (Batch, 1, 24)
    static_features = layers.Concatenate(axis=-1)([emb_ticker, emb_sector])

    # ------------------------------------------------------------------
    # 3. 데이터 융합 (Feature Fusion)
    # ------------------------------------------------------------------
    # 정적 특징(Static)을 시계열 길이(TimeSteps)만큼 복제합니다.
    # 예: (Batch, 1, 24) -> (Batch, 60, 24)
    time_steps = input_shape[0]
    
    # 1) 먼저 2D로 평탄화 (Batch, 24)
    static_features_flat = layers.Flatten()(static_features)
    
    # 2) 시간 축으로 복제 (Batch, TimeSteps, 24)
    static_features_repeated = layers.RepeatVector(time_steps)(static_features_flat)
    
    # 3) 시계열 데이터와 결합: (Batch, 60, Features + 24)
    # 이제 모델은 각 시점마다 가격 정보와 함께 "이게 누구인지"를 알게 됩니다.
    x = layers.Concatenate(axis=-1)([inputs_ts, static_features_repeated])

    # ------------------------------------------------------------------
    # 4. Transformer Encoder Blocks
    # ------------------------------------------------------------------
    for i in range(num_transformer_blocks):
        # Attention Layer
        x1 = layers.MultiHeadAttention(
            key_dim=head_size, 
            num_heads=num_heads, 
            dropout=dropout
        )(x, x)
        
        # Residual Connection + Layer Norm
        x2 = layers.Add()([x, x1])
        x2 = layers.LayerNormalization(epsilon=1e-6)(x2)
        
        # Feed Forward Network (Conv1D 사용)
        x3 = layers.Conv1D(filters=ff_dim, kernel_size=1, activation="relu")(x2)
        x3 = layers.Dropout(dropout)(x3)
        x3 = layers.Conv1D(filters=x.shape[-1], kernel_size=1)(x3)
        
        # Residual Connection + Layer Norm
        x = layers.Add()([x2, x3])
        x = layers.LayerNormalization(epsilon=1e-6)(x)

    # ------------------------------------------------------------------
    # 5. Output Head (Prediction)
    # ------------------------------------------------------------------
    # 전체 시계열의 정보를 압축 (Average Pooling)
    x = layers.GlobalAveragePooling1D(data_format="channels_last")(x)
    
    # MLP Layers
    for dim in mlp_units:
        x = layers.Dense(dim, activation="relu")(x)
        x = layers.Dropout(mlp_dropout)(x)
    
    # 최종 출력: 상승 확률 (0~1)
    outputs = layers.Dense(1, activation="sigmoid", name="prediction")(x)

    # 모델 생성
    model = models.Model(
        inputs=[inputs_ts, inputs_ticker, inputs_sector], 
        outputs=outputs,
        name="Universal_Transformer"
    )
    
    return model