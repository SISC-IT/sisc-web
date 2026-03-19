# AI/modules/signal/models/itransformer/architecture.py

import tensorflow as tf
from tensorflow.keras import layers, models, Input


# =============================================================================
# 1. iTransformer Encoder Block
# =============================================================================
# 이 블록은 "변수(feature) 토큰들" 사이에서 self-attention을 수행합니다.
#
# 일반 Transformer:
#   - 시간축(time-step)을 토큰처럼 보고 attention
#
# iTransformer:
#   - 변수축(feature, variate)을 토큰처럼 보고 attention
#   - 즉, 입력을 [B, T, F] -> [B, F, T]로 뒤집은 뒤
#     "각 변수"가 하나의 토큰이 되어 서로 어떤 관계가 있는지 학습
#
# 여기서는 Keras 기본 레이어만 사용해서 저장/로드 호환성을 최대한 높였습니다.
# =============================================================================
def inverted_transformer_encoder(
    x,
    d_model,
    num_heads,
    ff_dim,
    dropout=0.1,
    block_name="itr_block"
):
    """
    iTransformer용 Encoder Block

    Parameters
    ----------
    x : Tensor
        shape = (batch, num_features, d_model)
        여기서 num_features 축이 "토큰 축" 역할을 합니다.
    d_model : int
        각 변수 토큰의 임베딩 차원
    num_heads : int
        Multi-Head Attention의 head 개수
    ff_dim : int
        Feed Forward Network의 hidden 차원
    dropout : float
        dropout 비율
    block_name : str
        레이어 이름 prefix

    Returns
    -------
    Tensor
        shape = (batch, num_features, d_model)
    """

    # -------------------------------------------------------------------------
    # (A) Attention sub-layer
    # -------------------------------------------------------------------------
    # Pre-Norm 구조:
    #   LayerNorm -> MHA -> Dropout -> Residual Add
    #
    # x.shape = (B, F, d_model)
    # 여기서 attention은 "F축(변수 토큰들)" 기준으로 수행됩니다.
    # -------------------------------------------------------------------------
    attn_input = layers.LayerNormalization(
        epsilon=1e-6,
        name=f"{block_name}_ln1"
    )(x)

    attn_output = layers.MultiHeadAttention(
        num_heads=num_heads,
        key_dim=d_model // num_heads,
        dropout=dropout,
        name=f"{block_name}_mha"
    )(
        attn_input,  # query
        attn_input   # key/value
    )

    attn_output = layers.Dropout(
        dropout,
        name=f"{block_name}_attn_dropout"
    )(attn_output)

    x = layers.Add(name=f"{block_name}_attn_residual")([x, attn_output])

    # -------------------------------------------------------------------------
    # (B) Feed Forward sub-layer
    # -------------------------------------------------------------------------
    # Pre-Norm 구조:
    #   LayerNorm -> Dense(ff_dim, GELU) -> Dropout ->
    #   Dense(d_model) -> Dropout -> Residual Add
    # -------------------------------------------------------------------------
    ffn_input = layers.LayerNormalization(
        epsilon=1e-6,
        name=f"{block_name}_ln2"
    )(x)

    ffn_output = layers.Dense(
        ff_dim,
        activation="gelu",
        name=f"{block_name}_ffn_dense1"
    )(ffn_input)

    ffn_output = layers.Dropout(
        dropout,
        name=f"{block_name}_ffn_dropout1"
    )(ffn_output)

    ffn_output = layers.Dense(
        d_model,
        name=f"{block_name}_ffn_dense2"
    )(ffn_output)

    ffn_output = layers.Dropout(
        dropout,
        name=f"{block_name}_ffn_dropout2"
    )(ffn_output)

    x = layers.Add(name=f"{block_name}_ffn_residual")([x, ffn_output])

    return x


# =============================================================================
# 2. Main Builder
# =============================================================================
# wrapper.py / train.py 에서 호출하는 메인 함수입니다.
#
# 현재 프로젝트 계약:
#   - 시계열 입력: (seq_len, n_features)
#   - ticker_id 입력: (1,)
#   - sector_id 입력: (1,)
#   - 출력: n_outputs 개의 sigmoid 값
#
# 이 계약은 그대로 유지하고, 내부 backbone만 iTransformer 방식으로 바꿉니다.
# =============================================================================
def build_itransformer_model(
    input_shape,
    n_tickers,
    n_sectors,
    head_size=128,
    num_heads=4,
    ff_dim=256,
    num_transformer_blocks=4,
    mlp_units=None,
    dropout=0.2,
    mlp_dropout=0.2,
    n_outputs=1
):
    """
    현재 프로젝트용 iTransformer 분류 모델

    이 builder는 train.py / wrapper.py 에서 거시/상관관계 피처 시퀀스를
    [batch, seq_len, n_features] 형태로 받아, 변수(feature) 축을 토큰으로 취급합니다.

    Parameters
    ----------
    input_shape : tuple
        (seq_len, n_features)
    n_tickers : int
        ticker embedding vocabulary size
    n_sectors : int
        sector embedding vocabulary size
    head_size : int
        iTransformer 내부 토큰 임베딩 차원(d_model)
    num_heads : int
        attention head 수
    ff_dim : int
        FFN hidden 차원
    num_transformer_blocks : int
        encoder block 수
    mlp_units : list[int]
        마지막 분류 head용 MLP 차원 리스트
    dropout : float
        backbone dropout
    mlp_dropout : float
        head dropout
    n_outputs : int
        출력 개수 (예: 4 -> 1d, 3d, 5d, 7d)

    Returns
    -------
    tf.keras.Model
    """

    # -------------------------------------------------------------------------
    # (0) 입력 shape 검사
    # -------------------------------------------------------------------------
    # input_shape는 반드시 (seq_len, n_features) 형태여야 합니다.
    # 예: (60, 17)
    # -------------------------------------------------------------------------
    if len(input_shape) != 2:
        raise ValueError(
            f"input_shape는 (seq_len, n_features) 형태여야 합니다. 현재: {input_shape}"
        )

    # seq_len은 시간축 길이, n_features는 "토큰 개수"가 될 변수 수입니다.
    # 즉 일반 Transformer와 달리 여기서는 n_features 축이 attention 대상입니다.
    seq_len, n_features = input_shape
    d_model = head_size
    mlp_units = list(mlp_units or [128, 64])

    if seq_len <= 0 or n_features <= 0:
        raise ValueError(
            f"input_shape의 각 차원은 양수여야 합니다. 현재: {input_shape}"
        )

    if n_tickers < 0 or n_sectors < 0:
        raise ValueError(
            f"n_tickers와 n_sectors는 음수가 될 수 없습니다. 현재: {n_tickers}, {n_sectors}"
        )

    if d_model <= 0 or ff_dim <= 0 or n_outputs <= 0:
        raise ValueError(
            "head_size, ff_dim, n_outputs는 모두 양수여야 합니다."
        )

    if num_heads <= 0:
        raise ValueError(f"num_heads는 1 이상이어야 합니다. 현재: {num_heads}")

    if num_transformer_blocks <= 0:
        raise ValueError(
            f"num_transformer_blocks는 1 이상이어야 합니다. 현재: {num_transformer_blocks}"
        )

    if not 0.0 <= dropout < 1.0 or not 0.0 <= mlp_dropout < 1.0:
        raise ValueError("dropout과 mlp_dropout은 0 이상 1 미만이어야 합니다.")

    if any(units <= 0 for units in mlp_units):
        raise ValueError(f"mlp_units의 모든 값은 양수여야 합니다. 현재: {mlp_units}")

    # MultiHeadAttention의 key_dim 계산을 위해 d_model이 num_heads로 나누어 떨어지게 체크
    if d_model % num_heads != 0:
        raise ValueError(
            f"head_size(d_model)={d_model}는 num_heads={num_heads}로 나누어 떨어져야 합니다."
        )

    # ff_dim이 너무 작게 들어오면 표현력이 지나치게 약해질 수 있어서
    # 최소한 d_model 이상으로 올려주는 방어 로직을 넣을 수도 있습니다.
    # 다만 현재는 사용자가 준 값을 그대로 존중하기 위해 자동 보정은 하지 않습니다.
    # 필요하면 아래 한 줄을 활성화:
    # ff_dim = max(ff_dim, d_model)

    # -------------------------------------------------------------------------
    # (1) 입력 정의
    # -------------------------------------------------------------------------
    # ts_input:
    #   shape = (B, T, F)
    #   B: batch size
    #   T: 시퀀스 길이(seq_len)
    #   F: feature 개수(n_features)
    #
    # ticker_input / sector_input:
    #   종목/섹터 ID 메타데이터
    # -------------------------------------------------------------------------
    ts_input = Input(shape=input_shape, name="ts_input")
    ticker_input = Input(shape=(1,), name="ticker_input")
    sector_input = Input(shape=(1,), name="sector_input")

    # -------------------------------------------------------------------------
    # (2) 메타데이터 임베딩
    # -------------------------------------------------------------------------
    # ticker, sector는 순수 macro 시계열만으로 부족할 수 있는
    # 종목/섹터 정체성 정보를 보조적으로 넣기 위해 유지합니다.
    #
    # 결과:
    #   ticker_embedding -> (B, 16)
    #   sector_embedding -> (B, 16)
    # -------------------------------------------------------------------------
    ticker_embedding = layers.Embedding(
        input_dim=n_tickers + 1,
        output_dim=16,
        name="ticker_embedding"
    )(ticker_input)
    ticker_embedding = layers.Flatten(name="ticker_flatten")(ticker_embedding)

    sector_embedding = layers.Embedding(
        input_dim=n_sectors + 1,
        output_dim=16,
        name="sector_embedding"
    )(sector_input)
    sector_embedding = layers.Flatten(name="sector_flatten")(sector_embedding)

    # -------------------------------------------------------------------------
    # (3) iTransformer 핵심: 축 뒤집기 (Inverted Dimension)
    # -------------------------------------------------------------------------
    # 원래 입력:
    #   ts_input.shape = (B, T, F)
    #
    # 일반 Transformer는 보통 시간축 T를 토큰처럼 사용합니다.
    # 하지만 iTransformer는 변수축 F를 토큰처럼 사용합니다.
    #
    # 그래서:
    #   (B, T, F) -> (B, F, T)
    #
    # 즉 각 거시/상관 feature가 자기 자신의 "길이 T짜리 시계열 벡터"를 가지게 됩니다.
    # -------------------------------------------------------------------------
    x = layers.Permute((2, 1), name="invert_time_and_feature")(ts_input)
    # 결과 shape = (B, F, T)

    # -------------------------------------------------------------------------
    # (4) 각 변수 토큰을 d_model 차원으로 projection
    # -------------------------------------------------------------------------
    # 현재 x는 각 변수마다 길이 T짜리 벡터를 가지고 있습니다.
    # Dense(d_model)를 적용하면 마지막 축(T)이 d_model로 투영됩니다.
    #
    # 즉:
    #   (B, F, T) -> (B, F, d_model)
    #
    # 이 상태에서 F축이 attention의 토큰 축이 됩니다.
    # -------------------------------------------------------------------------
    x = layers.Dense(
        d_model,
        name="variate_token_projection"
    )(x)

    x = layers.Dropout(
        dropout,
        name="input_dropout"
    )(x)

    # -------------------------------------------------------------------------
    # (5) Inverted Transformer Encoder Stack
    # -------------------------------------------------------------------------
    # 여러 개의 encoder block을 쌓아서
    # "금리, 스프레드, 변동성, breadth 같은 변수들 사이의 관계"를 깊게 학습합니다.
    # -------------------------------------------------------------------------
    for i in range(num_transformer_blocks):
        x = inverted_transformer_encoder(
            x=x,
            d_model=d_model,
            num_heads=num_heads,
            ff_dim=ff_dim,
            dropout=dropout,
            block_name=f"itr_block_{i+1}"
        )

    # -------------------------------------------------------------------------
    # (6) Variable Token Pooling
    # -------------------------------------------------------------------------
    # encoder stack 결과:
    #   x.shape = (B, F, d_model)
    #
    # 이제 변수 토큰들 전체를 하나의 벡터로 요약해야
    # 최종 분류 head로 보낼 수 있습니다.
    #
    # GlobalAveragePooling1D는 F축 전체를 평균내어
    #   (B, F, d_model) -> (B, d_model)
    # 로 바꿔줍니다.
    # -------------------------------------------------------------------------
    x = layers.LayerNormalization(
        epsilon=1e-6,
        name="final_ln"
    )(x)

    x = layers.GlobalAveragePooling1D(
        name="variate_pooling"
    )(x)

    # -------------------------------------------------------------------------
    # (7) 메타데이터 임베딩 concat
    # -------------------------------------------------------------------------
    # backbone 표현 + ticker/sector embedding 을 합칩니다.
    # 이렇게 하면 공통 macro regime 표현에 종목 고유 정보가 덧붙습니다.
    # -------------------------------------------------------------------------
    x = layers.Concatenate(name="concat_meta")([
        x,
        ticker_embedding,
        sector_embedding
    ])

    # -------------------------------------------------------------------------
    # (8) 최종 MLP Head
    # -------------------------------------------------------------------------
    # 여기서는 멀티 호라이즌 이진 분류용 head를 구성합니다.
    # 예:
    #   n_outputs=4 라면 [1d, 3d, 5d, 7d] 확률 출력
    # -------------------------------------------------------------------------
    for i, units in enumerate(mlp_units):
        x = layers.Dense(
            units,
            activation="relu",
            name=f"mlp_dense_{i+1}"
        )(x)

        x = layers.Dropout(
            mlp_dropout,
            name=f"mlp_dropout_{i+1}"
        )(x)

    # -------------------------------------------------------------------------
    # (9) 출력층
    # -------------------------------------------------------------------------
    # 멀티 라벨 이진 분류이므로 sigmoid 사용
    #
    # 출력 shape:
    #   (B, n_outputs)
    # -------------------------------------------------------------------------
    outputs = layers.Dense(
        n_outputs,
        activation="sigmoid",
        name="output"
    )(x)

    # -------------------------------------------------------------------------
    # (10) 최종 모델 생성
    # -------------------------------------------------------------------------
    model = models.Model(
        inputs=[ts_input, ticker_input, sector_input],
        outputs=outputs,
        name="iTransformerClassifier"
    )

    return model


def build_transformer_model(*args, **kwargs):
    """
    기존 호출부 호환용 alias.
    내부 구현은 iTransformer 전용 builder를 사용합니다.
    """
    return build_itransformer_model(*args, **kwargs)


# =============================================================================
# 3. Regression Variant (호환용)
# =============================================================================
# 혹시 다른 곳에서 regression builder를 부를 수도 있으니
# 최소 호환용으로 남겨둡니다.
# =============================================================================
def build_regression_model(input_shape, n_tickers, n_sectors):
    """
    단일 연속값 회귀용 호환 함수
    현재는 sigmoid 기반 분류 builder를 재사용하고 있으므로,
    정말 회귀로 쓸 거면 activation / loss를 따로 바꾸는 별도 builder가 필요합니다.
    """
    return build_itransformer_model(
        input_shape=input_shape,
        n_tickers=n_tickers,
        n_sectors=n_sectors,
        n_outputs=1
    )