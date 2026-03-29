# AI/modules/signal/models/itransformer/train_kaggle.py
"""
iTransformer Kaggle 학습 스크립트
-----------------------------------------------
- DB 연결 없이 parquet 파일로 학습
- train.py의 로직을 그대로 유지하되 DataLoader → parquet 로드로 교체
- Kaggle 데이터셋: jihyeongkimm/sisc-ai-trading-dataset
- 저장: /kaggle/working/multi_horizon_model.keras
        /kaggle/working/multi_horizon_scaler.pkl
        /kaggle/working/metadata.json
-----------------------------------------------
"""
import os

def _find_kaggle_parquet_dir() -> str:
    """Kaggle parquet 데이터셋 경로 자동 탐색"""
    import glob as _glob
    matches = _glob.glob("/kaggle/input/**/price_data.parquet", recursive=True)
    if matches:
        return os.path.dirname(matches[0])
    return os.environ.get("PARQUET_DIR", "/kaggle/input")

import sys
import json
import pickle
import warnings
warnings.filterwarnings("ignore")
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.preprocessing import StandardScaler

# Kaggle 경로 설정
def _find_kaggle_dataset_path() -> str:
    """Kaggle 입력 데이터셋 경로 자동 탐색"""
    base = "/kaggle/input"
    if os.path.exists(base):
        for root, dirs, files in os.walk(base):
            if any(f.endswith(".parquet") for f in files):
                return root
    return os.environ.get("PARQUET_DIR", base)

KAGGLE_DATA_DIR = _find_kaggle_dataset_path()
OUTPUT_DIR      = "/kaggle/working"

# ─────────────────────────────────────────────────────────────────────────────
# CONFIG
# ─────────────────────────────────────────────────────────────────────────────
CONFIG = {
    "lookback"        : 60,
    "horizons"        : [1, 3, 5, 7],
    "train_end_date"  : "2023-12-31",
    "epochs"          : 50,
    "batch_size"      : 32,
    "learning_rate"   : 1e-4,
    "head_size"       : 128,
    "num_heads"       : 4,
    "ff_dim"          : 256,
    "num_blocks"      : 4,
    "mlp_units"       : [128, 64],
    "dropout"         : 0.2,
    "mlp_dropout"     : 0.2,
    "test_size"       : 0.2,
    "model_name"      : "multi_horizon_model.keras",   # artifact_paths.py, wrapper.py와 일치
    "scaler_name"     : "multi_horizon_scaler.pkl",    # artifact_paths.py, wrapper.py와 일치
    "metadata_name"   : "metadata.json",               # artifact_paths.py, wrapper.py와 일치
}

# iTransformer 피처 - 거시경제 + 상관관계 중심
FEATURE_COLUMNS = [
    "us10y",
    "us10y_chg",
    "yield_spread",
    "vix_close",
    "vix_change_rate",
    "dxy_close",
    "dxy_chg",
    "credit_spread_hy",
    "wti_price",
    "gold_price",
    "nh_nl_index",
    "ma200_pct",
    "correlation_spike",
    "recent_loss_ema",
    "ret_1d",
    "intraday_vol",
    "log_return",
    "surprise_cpi",
]

HORIZONS = CONFIG["horizons"]


# ─────────────────────────────────────────────────────────────────────────────
# 데이터 로드
# ─────────────────────────────────────────────────────────────────────────────
def load_parquet_data() -> pd.DataFrame:
    """parquet에서 price_data + macroeconomic_indicators 로드 후 병합"""
    print(">> parquet 데이터 로드 중...")

    price_path = os.path.join(KAGGLE_DATA_DIR, "price_data.parquet")
    macro_path = os.path.join(KAGGLE_DATA_DIR, "macroeconomic_indicators.parquet")

    price_df = pd.read_parquet(price_path)
    price_df["date"] = pd.to_datetime(price_df["date"])

    # 기본 피처 계산
    price_df = price_df.sort_values(["ticker", "date"]).reset_index(drop=True)
    price_df["log_return"]    = price_df.groupby("ticker")["close"].transform(
        lambda x: np.log(x / x.shift(1))
    )
    price_df["ret_1d"]        = price_df.groupby("ticker")["close"].transform(
        lambda x: x.pct_change()
    )
    price_df["intraday_vol"]  = (price_df["high"] - price_df["low"]) / price_df["close"]
    price_df["ma200"]         = price_df.groupby("ticker")["close"].transform(
        lambda x: x.rolling(200, min_periods=1).mean()
    )
    price_df["ma200_pct"]     = (price_df["close"] - price_df["ma200"]) / price_df["ma200"]

    # EMA 최근 손실 (단순 근사)
    price_df["recent_loss_ema"] = price_df.groupby("ticker")["log_return"].transform(
        lambda x: x.clip(upper=0).ewm(span=20).mean().abs()
    )

    # 거시경제 데이터 병합
    macro_df = pd.read_parquet(macro_path)
    macro_df["date"] = pd.to_datetime(macro_df["date"])

    # 필요한 거시 컬럼만 선택 (있는 것만)
    macro_cols = ["date", "us10y", "yield_spread", "vix_close", "dxy_close",
                  "credit_spread_hy", "wti_price", "gold_price",
                  "nh_nl_index", "correlation_spike", "surprise_cpi"]
    available_macro = [c for c in macro_cols if c in macro_df.columns]
    macro_df = macro_df[available_macro].drop_duplicates("date")

    # 변화율 계산
    if "us10y" in macro_df.columns:
        macro_df["us10y_chg"] = macro_df["us10y"].diff()
    if "dxy_close" in macro_df.columns:
        macro_df["dxy_chg"] = macro_df["dxy_close"].pct_change()
    if "vix_close" in macro_df.columns:
        macro_df["vix_change_rate"] = macro_df["vix_close"].pct_change()

    df = pd.merge(price_df, macro_df, on="date", how="left")
    df = df.sort_values(["ticker", "date"]).reset_index(drop=True)

    # macro 컬럼만 티커별로 ffill (전역 ffill 시 티커 간 누수 발생)
    macro_cols = [c for c in macro_df.columns if c != "date"]
    df[macro_cols] = df.groupby("ticker")[macro_cols].transform(lambda x: x.ffill())
    df = df.fillna(0)

    # 학습 기간 필터
    df = df[df["date"] <= CONFIG["train_end_date"]]

    print(f">> 로드 완료: {len(df):,}행, {df['ticker'].nunique()}개 종목")
    return df


# ─────────────────────────────────────────────────────────────────────────────
# 시퀀스 생성
# ─────────────────────────────────────────────────────────────────────────────
def build_sequences(
    df: pd.DataFrame,
    scaler: StandardScaler,
    fit_scaler: bool = False,
) -> tuple:
    lookback    = CONFIG["lookback"]
    horizons    = CONFIG["horizons"]
    max_horizon = max(horizons)

    # 사용 가능한 피처만 추출
    available_feats = [f for f in FEATURE_COLUMNS if f in df.columns]
    if len(available_feats) < 8:
        raise ValueError(f"피처가 너무 적습니다: {available_feats}")

    # 스케일러는 루프 밖에서 전체 데이터로 한 번만 fit
    # 루프 안에서 fit하면 마지막 티커 통계만 남아 스케일 불일치 발생
    all_feat_vals = df[available_feats].values.astype(np.float32)
    if fit_scaler:
        scaler.fit(all_feat_vals)

    X_list, y_list = [], []

    for ticker, group in df.groupby("ticker"):
        group = group.sort_values("date").reset_index(drop=True)
        if len(group) < lookback + max_horizon + 10:
            continue

        feat_vals  = scaler.transform(group[available_feats].values.astype(np.float32))
        close_vals = group["close"].values

        for i in range(lookback, len(group) - max_horizon):
            X_list.append(feat_vals[i - lookback : i])
            labels = []
            for h in horizons:
                future_ret = (close_vals[i + h] - close_vals[i]) / close_vals[i]
                labels.append(1.0 if future_ret > 0 else 0.0)
            y_list.append(labels)

    if not X_list:
        raise ValueError("시퀀스 생성 실패 - 데이터 부족")

    X = np.array(X_list, dtype=np.float32)
    y = np.array(y_list, dtype=np.float32)
    print(f">> 시퀀스: X={X.shape}, y={y.shape}, 피처={available_feats}")
    return X, y, available_feats


# ─────────────────────────────────────────────────────────────────────────────
# 모델 구성
# ─────────────────────────────────────────────────────────────────────────────
def build_model(seq_len: int, n_features: int, n_outputs: int) -> tf.keras.Model:
    """iTransformer: 변수(feature) 축을 토큰으로 취급하는 Transformer"""
    from tensorflow.keras import layers

    # 입력
    seq_input = tf.keras.Input(shape=(seq_len, n_features), name="sequence_input")

    # Transpose: [batch, seq, feat] → [batch, feat, seq]
    x = layers.Lambda(lambda t: tf.transpose(t, perm=[0, 2, 1]))(seq_input)

    # Transformer Encoder 블록
    for block_idx in range(CONFIG["num_blocks"]):
        name = f"block{block_idx}"
        attn_in = layers.LayerNormalization(epsilon=1e-6, name=f"{name}_ln1")(x)
        attn_out = layers.MultiHeadAttention(
            num_heads=CONFIG["num_heads"],
            key_dim=CONFIG["head_size"] // CONFIG["num_heads"],
            dropout=CONFIG["dropout"],
            name=f"{name}_mha",
        )(attn_in, attn_in)
        attn_out = layers.Dropout(CONFIG["dropout"])(attn_out)
        x = layers.Add(name=f"{name}_attn_add")([x, attn_out])

        ffn_in = layers.LayerNormalization(epsilon=1e-6, name=f"{name}_ln2")(x)
        ffn = layers.Dense(CONFIG["ff_dim"], activation="gelu", name=f"{name}_ffn1")(ffn_in)
        ffn = layers.Dropout(CONFIG["dropout"])(ffn)
        ffn = layers.Dense(seq_len, name=f"{name}_ffn2")(ffn)
        ffn = layers.Dropout(CONFIG["dropout"])(ffn)
        x = layers.Add(name=f"{name}_ffn_add")([x, ffn])

    # Transpose back: [batch, feat, seq] → [batch, seq, feat]
    x = layers.Lambda(lambda t: tf.transpose(t, perm=[0, 2, 1]))(x)

    # Global Average Pooling
    x = layers.GlobalAveragePooling1D()(x)

    # MLP Head
    for units in CONFIG["mlp_units"]:
        x = layers.Dense(units, activation="relu")(x)
        x = layers.Dropout(CONFIG["mlp_dropout"])(x)

    output = layers.Dense(n_outputs, activation="sigmoid", name="output")(x)

    model = tf.keras.Model(inputs=seq_input, outputs=output)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=CONFIG["learning_rate"]),
        loss="binary_crossentropy",
        metrics=["accuracy"],
    )
    return model


# ─────────────────────────────────────────────────────────────────────────────
# 학습 메인
# ─────────────────────────────────────────────────────────────────────────────
def train():
    print("=" * 56)
    print(" iTransformer Kaggle 학습 시작")
    print(f" Horizons: {CONFIG['horizons']}일")
    print(f" Lookback: {CONFIG['lookback']}일")
    print("=" * 56)

    # 1. 데이터 로드
    df = load_parquet_data()

    # 2. Train / Val 분리 (티커 기준, 시간 순서 보존)
    tickers      = df["ticker"].unique()
    n_val        = max(1, int(len(tickers) * CONFIG["test_size"]))
    val_tickers  = tickers[-n_val:]
    train_tickers = tickers[:-n_val]

    train_df = df[df["ticker"].isin(train_tickers)].copy()
    val_df   = df[df["ticker"].isin(val_tickers)].copy()
    print(f"\n>> Train: {len(train_tickers)}개 종목 | Val: {len(val_tickers)}개 종목")

    # 3. 시퀀스 생성 (train만 scaler fit)
    scaler = StandardScaler()
    X_train, y_train, feat_cols = build_sequences(train_df, scaler, fit_scaler=True)
    X_val,   y_val,   _         = build_sequences(val_df,   scaler, fit_scaler=False)

    # 4. 모델 구성
    n_features = X_train.shape[2]
    n_outputs  = len(HORIZONS)
    model = build_model(CONFIG["lookback"], n_features, n_outputs)
    model.summary()

    # 5. 학습
    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss", patience=10, restore_best_weights=True
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss", factor=0.5, patience=5, verbose=1
        ),
    ]

    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=CONFIG["epochs"],
        batch_size=CONFIG["batch_size"],
        callbacks=callbacks,
        verbose=1,
    )

    # 6. 저장
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    model_path    = os.path.join(OUTPUT_DIR, CONFIG["model_name"])
    scaler_path   = os.path.join(OUTPUT_DIR, CONFIG["scaler_name"])
    metadata_path = os.path.join(OUTPUT_DIR, CONFIG["metadata_name"])

    model.save(model_path)
    with open(scaler_path, "wb") as f:
        pickle.dump(scaler, f)

    best_val_loss = min(history.history["val_loss"])
    best_val_acc  = max(history.history.get("val_accuracy", [0]))

    metadata = {
        "model_name"     : "itransformer",
        "seq_len"        : CONFIG["lookback"],
        "feature_names"  : feat_cols,
        "feature_columns": feat_cols,
        "horizons"       : HORIZONS,
        "head_size"      : CONFIG["head_size"],
        "num_heads"      : CONFIG["num_heads"],
        "ff_dim"         : CONFIG["ff_dim"],
        "num_blocks"     : CONFIG["num_blocks"],
        "mlp_units"      : CONFIG["mlp_units"],
        "dropout"        : CONFIG["dropout"],
        "mlp_dropout"    : CONFIG["mlp_dropout"],
        "best_val_loss"  : round(best_val_loss, 4),
        "best_val_acc"   : round(best_val_acc, 4),
        "n_train_samples": int(len(X_train)),
        "n_val_samples"  : int(len(X_val)),
    }
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print("\n학습 완료!")
    print(f"  모델  : {model_path}")
    print(f"  스케일러: {scaler_path}")
    print(f"  메타데이터: {metadata_path}")
    print(f"  Best val_loss: {best_val_loss:.4f}")
    print(f"  Best val_acc : {best_val_acc:.4f}")


if __name__ == "__main__":
    train()
