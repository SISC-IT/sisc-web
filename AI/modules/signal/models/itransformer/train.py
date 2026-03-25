import json
import os
import sys
from typing import Dict, List, Tuple

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.preprocessing import StandardScaler
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau


current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.features.market_derived import add_macro_changes, add_market_changes
from AI.modules.features.processor import FeatureProcessor
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.signal.models.itransformer.architecture import build_itransformer_model


DEFAULT_SIGNAL_NAME = "signal_itrans"
DEFAULT_SIGNAL_HORIZON_WEIGHTS = [0.1, 0.2, 0.3, 0.4]
FEATURE_ALIASES = {
    "mkt_breadth_ma200": "ma200_pct",
    "mkt_breadth_nh_nl": "nh_nl_index",
}
DYNAMIC_FEATURE_PREFIXES = ("sector_return_",)

DEFAULT_ITRANSFORMER_FEATURES = [
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

OPTIONAL_CONTEXT_FEATURES = [
    "btc_close",
    "eth_close",
]

DEFAULT_CONFIG = {
    # iTransformer는 상대적으로 "변수 간 관계"를 보므로
    # 입력 길이는 과도하게 길기보다, 안정적으로 macro regime를 담는 60일 기본값을 둡니다.
    "lookback": 60,
    "horizons": [1, 3, 5, 7],
    "start_date": "2015-01-01",
    "train_end_date": "2023-12-31",
    "val_start_date": None,
    "test_size": 0.2,
    "random_state": 42,
    "epochs": 50,
    "batch_size": 32,
    "learning_rate": 1e-4,
    "head_size": 128,
    "num_heads": 4,
    "ff_dim": 256,
    "num_blocks": 4,
    "mlp_units": [128, 64],
    "dropout": 0.2,
    "mlp_dropout": 0.2,
    "feature_candidates": DEFAULT_ITRANSFORMER_FEATURES,
    "min_feature_count": 8,
    "signal_name": DEFAULT_SIGNAL_NAME,
    "signal_horizon_weights": DEFAULT_SIGNAL_HORIZON_WEIGHTS,
    "save_dir": os.path.join(project_root, "AI", "data", "weights", "itransformer"),
}


def canonicalize_feature_name(name: str) -> str:
    return FEATURE_ALIASES.get(name, name)


def normalize_feature_aliases(df: pd.DataFrame) -> pd.DataFrame:
    normalized = df.copy()
    for alias, canonical in FEATURE_ALIASES.items():
        if alias in normalized.columns and canonical not in normalized.columns:
            normalized[canonical] = normalized[alias]
    return normalized


def resolve_signal_horizon_weights(horizons: List[int], raw_weights=None) -> List[float]:
    weights = raw_weights if raw_weights is not None else DEFAULT_SIGNAL_HORIZON_WEIGHTS
    try:
        weights_array = np.asarray(weights, dtype=np.float32).reshape(-1)
    except Exception:
        weights_array = np.asarray(DEFAULT_SIGNAL_HORIZON_WEIGHTS, dtype=np.float32)

    if len(weights_array) != len(horizons) or np.any(weights_array < 0) or float(weights_array.sum()) <= 0.0:
        weights_array = np.ones(len(horizons), dtype=np.float32)

    weights_array = weights_array / weights_array.sum()
    return weights_array.tolist()


def _serialize_optional_array(value, dtype=np.float64) -> np.ndarray:
    if value is None:
        return np.asarray([], dtype=dtype)
    return np.asarray(value, dtype=dtype)


def save_scaler_artifact(filepath: str, scaler: StandardScaler) -> None:
    np.savez_compressed(
        filepath,
        mean_=_serialize_optional_array(getattr(scaler, "mean_", None)),
        scale_=_serialize_optional_array(getattr(scaler, "scale_", None)),
        var_=_serialize_optional_array(getattr(scaler, "var_", None)),
        n_features_in_=np.asarray([int(getattr(scaler, "n_features_in_", 0))], dtype=np.int64),
        n_samples_seen_=_serialize_optional_array(getattr(scaler, "n_samples_seen_", 0), dtype=np.float64),
        with_mean=np.asarray([int(bool(getattr(scaler, "with_mean", True)))], dtype=np.int8),
        with_scale=np.asarray([int(bool(getattr(scaler, "with_scale", True)))], dtype=np.int8),
    )


def configure_tensorflow():
    print("TensorFlow version:", tf.__version__)
    gpus = tf.config.list_physical_devices("GPU")
    print("GPU devices:", gpus)

    if not gpus:
        print("CPU 모드로 학습을 진행합니다.")
        return

    try:
        for gpu in gpus:
            tf.config.experimental.set_memory_growth(gpu, True)
        print(f"GPU memory growth enabled for {len(gpus)} device(s).")
    except RuntimeError as exc:
        print(f"GPU memory growth 설정 실패: {exc}")


def merge_common_context(sub_df: pd.DataFrame, loader: DataLoader) -> pd.DataFrame:
    # 공통 데이터 로더가 미리 캐싱해둔 macro / breadth 프레임을
    # 개별 종목 price frame에 붙여 iTransformer용 입력 후보를 만듭니다.
    merged = sub_df.copy().sort_values("date")

    if not loader.macro_df.empty:
        merged = pd.merge(merged, loader.macro_df, on="date", how="left")
    if not loader.breadth_df.empty:
        merged = pd.merge(merged, loader.breadth_df, on="date", how="left")

    merged["date"] = pd.to_datetime(merged["date"])
    return normalize_feature_aliases(merged.sort_values("date").reset_index(drop=True))


def prepare_itransformer_frame(sub_df: pd.DataFrame) -> pd.DataFrame:
    # 여기서는 "거시/상관관계 모델"에 맞게
    # 원본 가격 + macro raw values -> 변화율/파생지표까지 한 번에 생성합니다.
    prepared = sub_df.copy()
    prepared["raw_close"] = prepared["close"].astype(np.float32)

    prepared = add_market_changes(prepared)
    prepared = add_macro_changes(prepared)
    prepared = FeatureProcessor(prepared).execute_pipeline()

    prepared["date"] = pd.to_datetime(prepared["date"])
    prepared = prepared.sort_values("date").reset_index(drop=True)
    # 학습 경로에서는 미래값 역류를 막기 위해 forward fill까지만 허용합니다.
    prepared = prepared.replace([np.inf, -np.inf], np.nan).ffill().fillna(0)
    prepared["raw_close"] = prepared.get("raw_close", prepared["close"]).astype(np.float32)
    return normalize_feature_aliases(prepared)


def resolve_feature_columns(df: pd.DataFrame, config: Dict) -> List[str]:
    # 학습 피처 선택 규칙:
    # 1. feature_columns를 명시했다면 그대로 강제
    # 2. 아니면 기본 macro/correlation 후보 중 실제 존재하는 컬럼만 채택
    # 3. btc/eth, sector_return_* 같은 optional context 컬럼은 있으면 덧붙임
    normalized_df = normalize_feature_aliases(df)
    explicit_features = config.get("feature_columns")
    raw_candidate_features = explicit_features or config.get("feature_candidates", DEFAULT_ITRANSFORMER_FEATURES)
    candidate_features = list(dict.fromkeys(canonicalize_feature_name(column) for column in raw_candidate_features))

    if explicit_features:
        missing = [column for column in candidate_features if column not in normalized_df.columns]
        if missing:
            raise ValueError(f"학습 데이터에 필요한 컬럼이 없습니다: {missing}")
        return candidate_features

    selected = [column for column in candidate_features if column in normalized_df.columns]
    selected.extend(
        column
        for column in OPTIONAL_CONTEXT_FEATURES
        if column in normalized_df.columns and column not in selected
    )
    selected.extend(
        column
        for column in sorted(normalized_df.columns)
        if any(column.startswith(prefix) for prefix in DYNAMIC_FEATURE_PREFIXES) and column not in selected
    )

    if len(selected) < int(config.get("min_feature_count", 1)):
        raise ValueError(
            "iTransformer 학습에 사용할 거시/상관관계 피처가 너무 적습니다. "
            f"selected={selected}"
        )

    return selected


def prepare_macro_correlation_frame(
    loader: DataLoader,
    raw_df: pd.DataFrame,
    config: Dict,
) -> Tuple[pd.DataFrame, List[str]]:
    # DataLoader의 raw price 결과를 그대로 쓰지 않고,
    # iTransformer 전용 macro/correlation 프레임으로 다시 조립합니다.
    lookback = int(config["lookback"])
    horizons = list(config["horizons"])
    max_horizon = max(horizons)

    processed_frames = []
    for ticker in raw_df["ticker"].unique():
        sub_df = raw_df[raw_df["ticker"] == ticker].copy().sort_values("date")
        if len(sub_df) <= lookback + max_horizon:
            continue

        merged = merge_common_context(sub_df, loader)
        prepared = prepare_itransformer_frame(merged)
        prepared["ticker"] = ticker
        processed_frames.append(prepared)

    if not processed_frames:
        raise ValueError("iTransformer 학습용으로 전처리된 데이터가 없습니다.")

    full_df = pd.concat(processed_frames, ignore_index=True)
    full_df["date"] = pd.to_datetime(full_df["date"])
    full_df = normalize_feature_aliases(full_df)
    feature_columns = resolve_feature_columns(full_df, config)
    return full_df, feature_columns


def resolve_validation_start_date(full_df: pd.DataFrame, config: Dict) -> pd.Timestamp:
    explicit_date = config.get("val_start_date")
    if explicit_date:
        val_start_date = pd.to_datetime(explicit_date)
    else:
        unique_dates = np.sort(pd.to_datetime(full_df["date"]).dt.normalize().unique())
        if len(unique_dates) < 2:
            raise ValueError("validation 분할을 위한 날짜가 충분하지 않습니다.")

        split_idx = int(len(unique_dates) * (1.0 - float(config["test_size"])))
        split_idx = min(max(split_idx, 1), len(unique_dates) - 1)
        val_start_date = pd.Timestamp(unique_dates[split_idx])

    min_date = pd.to_datetime(full_df["date"]).min()
    max_date = pd.to_datetime(full_df["date"]).max()
    if val_start_date <= min_date or val_start_date > max_date:
        raise ValueError(
            f"val_start_date가 데이터 범위를 벗어났습니다. "
            f"val_start_date={val_start_date.date()}, data_range=({min_date.date()} ~ {max_date.date()})"
        )

    return val_start_date


def build_time_split_dataset(
    loader: DataLoader,
    full_df: pd.DataFrame,
    feature_columns: List[str],
    config: Dict,
):
    # time-series leakage를 줄이기 위해 날짜 기준으로 train/validation을 분리하고,
    # scaler는 train 기간 데이터에만 fit합니다.
    lookback = int(config["lookback"])
    horizons = list(config["horizons"])
    max_horizon = max(horizons)
    val_start_date = resolve_validation_start_date(full_df, config)

    train_rows = full_df[pd.to_datetime(full_df["date"]) < val_start_date].copy()
    if train_rows.empty:
        raise ValueError("train 기간 데이터가 비어 있어 scaler를 학습할 수 없습니다.")

    scaler = StandardScaler()
    scaler.fit(train_rows[feature_columns].astype(np.float32))

    scaled_df = full_df.copy()
    scaled_df.loc[:, feature_columns] = scaler.transform(
        scaled_df[feature_columns].astype(np.float32)
    ).astype(np.float32)

    train_ts_list, train_ticker_list, train_sector_list, train_y_list = [], [], [], []
    val_ts_list, val_ticker_list, val_sector_list, val_y_list = [], [], [], []

    for ticker in scaled_df["ticker"].unique():
        sub_df = scaled_df[scaled_df["ticker"] == ticker].copy().sort_values("date")
        if len(sub_df) <= lookback + max_horizon:
            continue

        feature_values = sub_df[feature_columns].to_numpy(dtype=np.float32)
        raw_closes = sub_df["raw_close"].to_numpy(dtype=np.float32)
        dates = pd.to_datetime(sub_df["date"]).to_numpy()
        sample_count = len(sub_df) - lookback - max_horizon + 1

        ticker_id = loader.ticker_to_id.get(ticker, 0)
        sector_id = loader.ticker_to_sector_id.get(ticker, 0)

        for start_idx in range(sample_count):
            end_idx = start_idx + lookback
            anchor_date = pd.Timestamp(dates[end_idx - 1])
            future_end_date = pd.Timestamp(dates[end_idx + max_horizon - 1])

            window = feature_values[start_idx:end_idx]
            current_price = raw_closes[end_idx - 1]
            labels = []
            for horizon in horizons:
                future_price = raw_closes[end_idx + horizon - 1]
                labels.append(1.0 if future_price > current_price else 0.0)

            if future_end_date < val_start_date:
                train_ts_list.append(window)
                train_ticker_list.append(ticker_id)
                train_sector_list.append(sector_id)
                train_y_list.append(labels)
            elif anchor_date >= val_start_date:
                val_ts_list.append(window)
                val_ticker_list.append(ticker_id)
                val_sector_list.append(sector_id)
                val_y_list.append(labels)

    def to_arrays(ts_list, ticker_list, sector_list, y_list):
        return (
            np.asarray(ts_list, dtype=np.float32),
            np.asarray(ticker_list, dtype=np.int32),
            np.asarray(sector_list, dtype=np.int32),
            np.asarray(y_list, dtype=np.float32),
        )

    X_ts_train, X_ticker_train, X_sector_train, y_train = to_arrays(
        train_ts_list,
        train_ticker_list,
        train_sector_list,
        train_y_list,
    )
    X_ts_val, X_ticker_val, X_sector_val, y_val = to_arrays(
        val_ts_list,
        val_ticker_list,
        val_sector_list,
        val_y_list,
    )

    if len(X_ts_train) == 0 or len(X_ts_val) == 0:
        raise ValueError(
            "time-based split 결과 train 또는 validation 시퀀스가 비어 있습니다. "
            "val_start_date / test_size / 데이터 기간을 확인하세요."
        )

    info = {
        "n_tickers": len(loader.ticker_to_id),
        "n_sectors": len(loader.sector_to_id),
        "ticker_to_id": {str(key): int(value) for key, value in loader.ticker_to_id.items()},
        "sector_to_id": {str(key): int(value) for key, value in loader.sector_to_id.items()},
        "ticker_to_sector_id": {
            str(key): int(value) for key, value in loader.ticker_to_sector_id.items()
        },
        "feature_names": feature_columns,
        "n_features": len(feature_columns),
        "horizons": horizons,
        "scaler": scaler,
        "feature_focus": "macro_correlation",
        "scaler_type": scaler.__class__.__name__,
        "val_start_date": val_start_date.strftime("%Y-%m-%d"),
        "signal_name": str(config.get("signal_name", DEFAULT_SIGNAL_NAME)),
        "signal_horizon_weights": resolve_signal_horizon_weights(
            horizons,
            config.get("signal_horizon_weights"),
        ),
        "train_samples": int(len(X_ts_train)),
        "val_samples": int(len(X_ts_val)),
    }
    return (
        X_ts_train,
        X_ticker_train,
        X_sector_train,
        y_train,
        X_ts_val,
        X_ticker_val,
        X_sector_val,
        y_val,
        info,
    )


def save_training_metadata(save_dir: str, info: Dict, config: Dict):
    # wrapper가 추론 시 똑같은 feature 순서와 seq_len을 재현할 수 있도록
    # 학습 메타데이터를 함께 저장합니다.
    metadata = {
        "model_name": "itransformer",
        "seq_len": int(config["lookback"]),
        "feature_names": info["feature_names"],
        "feature_focus": info["feature_focus"],
        "horizons": info["horizons"],
        "n_tickers": int(info["n_tickers"]),
        "n_sectors": int(info["n_sectors"]),
        "ticker_to_id": info["ticker_to_id"],
        "sector_to_id": info["sector_to_id"],
        "ticker_to_sector_id": info["ticker_to_sector_id"],
        "head_size": int(config["head_size"]),
        "num_heads": int(config["num_heads"]),
        "ff_dim": int(config["ff_dim"]),
        "num_blocks": int(config["num_blocks"]),
        "mlp_units": list(config["mlp_units"]),
        "dropout": float(config["dropout"]),
        "mlp_dropout": float(config["mlp_dropout"]),
        "scaler_type": info["scaler_type"],
        "val_start_date": info["val_start_date"],
        "signal_name": info["signal_name"],
        "signal_horizon_weights": info["signal_horizon_weights"],
    }

    metadata_path = os.path.join(save_dir, "metadata.json")
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    return metadata_path


def train_single_pipeline(config=None):
    config = {**DEFAULT_CONFIG, **(config or {})}
    configure_tensorflow()

    print("=" * 56)
    print("[Training] iTransformer Macro/Correlation Classifier")
    print("=" * 56)

    loader = DataLoader(
        lookback=int(config["lookback"]),
        horizons=list(config["horizons"]),
    )

    # load_data_from_db 내부에서 macro / breadth 공통 데이터도 함께 준비됩니다.
    print(">> 공통 데이터 로더로 과거 데이터 로딩 중...")
    full_df = loader.load_data_from_db(start_date=config["start_date"])
    raw_df = full_df[full_df["date"] <= config["train_end_date"]].copy()

    if raw_df.empty:
        raise ValueError("학습 가능한 데이터가 없습니다. 날짜 범위를 확인하세요.")

    print(f">> 학습 데이터 기간: {raw_df['date'].min()} ~ {raw_df['date'].max()}")
    print(f">> 총 데이터 행 수: {len(raw_df)} rows")

    prepared_df, feature_columns = prepare_macro_correlation_frame(loader, raw_df, config)
    (
        X_ts_train,
        X_ticker_train,
        X_sector_train,
        y_train,
        X_ts_val,
        X_ticker_val,
        X_sector_val,
        y_val,
        info,
    ) = build_time_split_dataset(loader, prepared_df, feature_columns, config)
    n_outputs = y_train.shape[1]

    print("\n" + "=" * 56)
    print(" [DEBUG] Selected macro/correlation features")
    print("=" * 56)
    print(info["feature_names"])
    print("=" * 56)
    print(
        f">> time split: train < {info['val_start_date']} | "
        f"validation >= {info['val_start_date']}"
    )
    print(f">> samples: train={info['train_samples']}, val={info['val_samples']}")

    y_all = np.concatenate([y_train, y_val], axis=0)
    for idx, horizon in enumerate(info["horizons"]):
        labels = y_all[:, idx]
        unique, counts = np.unique(labels, return_counts=True)
        dist = {int(key): int(value) for key, value in zip(unique, counts)}
        positive_ratio = dist.get(1, 0) / max(sum(dist.values()), 1) * 100
        print(f" - {horizon}d positive ratio: {positive_ratio:.2f}% | dist={dist}")

    X_tick_train = X_ticker_train.reshape(-1, 1)
    X_sec_train = X_sector_train.reshape(-1, 1)
    X_tick_val = X_ticker_val.reshape(-1, 1)
    X_sec_val = X_sector_val.reshape(-1, 1)

    print(
        f">> 모델 빌드 중 (seq_len={X_ts_train.shape[1]}, "
        f"features={X_ts_train.shape[2]}, outputs={n_outputs})..."
    )
    model = build_itransformer_model(
        input_shape=(X_ts_train.shape[1], X_ts_train.shape[2]),
        n_tickers=info["n_tickers"],
        n_sectors=info["n_sectors"],
        n_outputs=n_outputs,
        head_size=int(config["head_size"]),
        num_heads=int(config["num_heads"]),
        ff_dim=int(config["ff_dim"]),
        num_transformer_blocks=int(config["num_blocks"]),
        mlp_units=list(config["mlp_units"]),
        dropout=float(config["dropout"]),
        mlp_dropout=float(config["mlp_dropout"]),
    )

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=float(config["learning_rate"])),
        loss="binary_crossentropy",
        metrics=[
            tf.keras.metrics.BinaryAccuracy(name="binary_accuracy"),
            tf.keras.metrics.AUC(name="auc"),
        ],
    )

    save_dir = config["save_dir"]
    os.makedirs(save_dir, exist_ok=True)

    # 모델 본체(.keras), 스케일러(.npz), 메타데이터(.json)를 함께 남겨야
    # wrapper가 별도 추가 설정 없이 바로 서비스 추론에 들어갈 수 있습니다.
    model_path = os.path.join(save_dir, "multi_horizon_model.keras")
    scaler_path = os.path.join(save_dir, "multi_horizon_scaler.npz")

    callbacks = [
        ModelCheckpoint(
            filepath=model_path,
            monitor="val_loss",
            save_best_only=True,
            verbose=1,
        ),
        EarlyStopping(
            monitor="val_loss",
            patience=10,
            restore_best_weights=True,
        ),
        ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=5,
            min_lr=1e-6,
            verbose=1,
        ),
    ]

    print(">> 학습 시작...")
    history = model.fit(
        x=[X_ts_train, X_tick_train, X_sec_train],
        y=y_train,
        validation_data=([X_ts_val, X_tick_val, X_sec_val], y_val),
        epochs=int(config["epochs"]),
        batch_size=int(config["batch_size"]),
        shuffle=True,
        callbacks=callbacks,
        verbose=1,
    )

    save_scaler_artifact(scaler_path, info["scaler"])

    metadata_path = save_training_metadata(save_dir, info, config)

    print("\n[완료] iTransformer 학습 종료")
    print(f" - feature focus: {info['feature_focus']}")
    print(f" - signal name : {info['signal_name']}")
    print(f" - model       : {model_path}")
    print(f" - scaler      : {scaler_path}")
    print(f" - metadata    : {metadata_path}")

    return {
        "history": history.history,
        "feature_names": info["feature_names"],
        "signal_name": info["signal_name"],
        "model_path": model_path,
        "scaler_path": scaler_path,
        "metadata_path": metadata_path,
    }


if __name__ == "__main__":
    train_single_pipeline()
