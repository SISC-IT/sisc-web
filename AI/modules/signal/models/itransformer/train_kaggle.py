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

from AI.modules.signal.models.itransformer.architecture import build_itransformer_model

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
    "train_end_date"  : os.environ.get("TRAIN_END_DATE"),
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
    macro_df = (
        macro_df[available_macro]
        .sort_values("date")
        .drop_duplicates("date", keep="last")
    )

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

    # 학습 기간 필터: TRAIN_END_DATE가 없으면 parquet 최신 날짜까지 사용한다.
    if CONFIG["train_end_date"]:
        df = df[df["date"] <= pd.to_datetime(CONFIG["train_end_date"])]

    print(f">> 로드 완료: {len(df):,}행, {df['ticker'].nunique()}개 종목")
    return df


# ─────────────────────────────────────────────────────────────────────────────
# 시퀀스 생성
# ─────────────────────────────────────────────────────────────────────────────
def build_sequences(
    df: pd.DataFrame,
    scaler: StandardScaler,
    ticker_to_id: dict | None = None,
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

    X_list, ticker_id_list, sector_id_list, y_list = [], [], [], []

    for ticker, group in df.groupby("ticker"):
        group = group.sort_values("date").reset_index(drop=True)
        if len(group) < lookback + max_horizon + 10:
            continue

        ticker_id = ticker_to_id.get(ticker, 0) if ticker_to_id else 0
        feat_vals  = scaler.transform(group[available_feats].values.astype(np.float32))
        close_vals = group["close"].values

        for i in range(lookback, len(group) - max_horizon):
            X_list.append(feat_vals[i - lookback : i])
            ticker_id_list.append(ticker_id)
            sector_id_list.append(0)
            labels = []
            for h in horizons:
                future_ret = (close_vals[i + h] - close_vals[i]) / close_vals[i]
                labels.append(1.0 if future_ret > 0 else 0.0)
            y_list.append(labels)

    if not X_list:
        raise ValueError("시퀀스 생성 실패 - 데이터 부족")

    X = np.array(X_list, dtype=np.float32)
    X_ticker = np.array(ticker_id_list, dtype=np.int32).reshape(-1, 1)
    X_sector = np.array(sector_id_list, dtype=np.int32).reshape(-1, 1)
    y = np.array(y_list, dtype=np.float32)
    print(f">> 시퀀스: X={X.shape}, y={y.shape}, 피처={available_feats}")
    return X, X_ticker, X_sector, y, available_feats


# ─────────────────────────────────────────────────────────────────────────────
# 모델 구성
# ─────────────────────────────────────────────────────────────────────────────
def build_model(seq_len: int, n_features: int, n_outputs: int, n_tickers: int, n_sectors: int) -> tf.keras.Model:
    """추론 wrapper와 같은 3입력 모델 계약으로 iTransformer를 생성한다."""
    model = build_itransformer_model(
        input_shape=(seq_len, n_features),
        n_tickers=n_tickers,
        n_sectors=n_sectors,
        head_size=CONFIG["head_size"],
        num_heads=CONFIG["num_heads"],
        ff_dim=CONFIG["ff_dim"],
        num_transformer_blocks=CONFIG["num_blocks"],
        mlp_units=CONFIG["mlp_units"],
        dropout=CONFIG["dropout"],
        mlp_dropout=CONFIG["mlp_dropout"],
        n_outputs=n_outputs,
    )
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
    if len(tickers) < 2:
        raise ValueError(f"학습을 위한 ticker가 부족합니다. 현재 {len(tickers)}개")
    n_val        = min(max(1, int(len(tickers) * CONFIG["test_size"])), len(tickers) - 1)
    val_tickers  = tickers[-n_val:]
    train_tickers = tickers[:-n_val]

    train_df = df[df["ticker"].isin(train_tickers)].copy()
    val_df   = df[df["ticker"].isin(val_tickers)].copy()
    print(f"\n>> Train: {len(train_tickers)}개 종목 | Val: {len(val_tickers)}개 종목")

    # 3. 시퀀스 생성 (train만 scaler fit)
    scaler = StandardScaler()
    ticker_to_id = {ticker: idx for idx, ticker in enumerate(sorted(tickers))}
    X_train, X_tick_train, X_sec_train, y_train, feat_cols = build_sequences(
        train_df,
        scaler,
        ticker_to_id=ticker_to_id,
        fit_scaler=True,
    )
    X_val, X_tick_val, X_sec_val, y_val, _ = build_sequences(
        val_df,
        scaler,
        ticker_to_id=ticker_to_id,
        fit_scaler=False,
    )

    # 4. 모델 구성
    n_features = X_train.shape[2]
    n_outputs  = len(HORIZONS)
    model = build_model(
        CONFIG["lookback"],
        n_features,
        n_outputs,
        n_tickers=max(1, len(ticker_to_id)),
        n_sectors=1,
    )
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
        [X_train, X_tick_train, X_sec_train],
        y_train,
        validation_data=([X_val, X_tick_val, X_sec_val], y_val),
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
        "n_tickers"      : max(1, len(ticker_to_id)),
        "n_sectors"      : 1,
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
