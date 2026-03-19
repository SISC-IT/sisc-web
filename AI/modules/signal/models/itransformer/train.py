import json
import os
import pickle
import sys
from typing import Dict, List, Tuple

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.model_selection import train_test_split
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
    "save_dir": os.path.join(project_root, "AI", "data", "weights", "itransformer"),
}


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
    return merged.sort_values("date").reset_index(drop=True)


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
    prepared = prepared.replace([np.inf, -np.inf], np.nan).ffill().bfill().fillna(0)
    prepared["raw_close"] = prepared.get("raw_close", prepared["close"]).astype(np.float32)
    return prepared


def resolve_feature_columns(df: pd.DataFrame, config: Dict) -> List[str]:
    # 학습 피처 선택 규칙:
    # 1. feature_columns를 명시했다면 그대로 강제
    # 2. 아니면 기본 macro/correlation 후보 중 실제 존재하는 컬럼만 채택
    # 3. btc/eth 같은 optional context 컬럼은 있으면 덧붙임
    explicit_features = config.get("feature_columns")
    candidate_features = list(explicit_features or config.get("feature_candidates", DEFAULT_ITRANSFORMER_FEATURES))

    if explicit_features:
        missing = [column for column in candidate_features if column not in df.columns]
        if missing:
            raise ValueError(f"학습 데이터에 필요한 컬럼이 없습니다: {missing}")
        return candidate_features

    selected = [column for column in candidate_features if column in df.columns]
    selected.extend(
        column
        for column in OPTIONAL_CONTEXT_FEATURES
        if column in df.columns and column not in selected
    )

    if len(selected) < int(config.get("min_feature_count", 1)):
        raise ValueError(
            "iTransformer 학습에 사용할 거시/상관관계 피처가 너무 적습니다. "
            f"selected={selected}"
        )

    return selected


def build_macro_correlation_dataset(
    loader: DataLoader,
    raw_df: pd.DataFrame,
    config: Dict,
) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, Dict]:
    # train.py의 핵심:
    # DataLoader의 raw price 결과를 그대로 쓰지 않고,
    # iTransformer 전용 macro/correlation 시퀀스로 다시 조립합니다.
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
    feature_columns = resolve_feature_columns(full_df, config)

    # macro 류 피처는 절대값 스케일 편차가 커서
    # MinMax보다 StandardScaler가 비교적 안정적입니다.
    scaler = StandardScaler()
    feature_matrix = full_df[feature_columns].astype(np.float32)
    full_df.loc[:, feature_columns] = scaler.fit_transform(feature_matrix).astype(np.float32)

    X_ts_list, X_ticker_list, X_sector_list, y_class_list = [], [], [], []

    for ticker in full_df["ticker"].unique():
        sub_df = full_df[full_df["ticker"] == ticker].copy().sort_values("date")
        if len(sub_df) <= lookback + max_horizon:
            continue

        feature_values = sub_df[feature_columns].to_numpy(dtype=np.float32)
        raw_closes = sub_df["raw_close"].to_numpy(dtype=np.float32)
        sample_count = len(sub_df) - lookback - max_horizon + 1

        ticker_id = loader.ticker_to_id.get(ticker, 0)
        sector_id = loader.ticker_to_sector_id.get(ticker, 0)

        for start_idx in range(sample_count):
            # 각 샘플은 [과거 lookback일의 macro/correlation window] 하나와
            # [1d, 3d, 5d, 7d 상승 여부] 라벨 묶음 하나로 구성됩니다.
            window = feature_values[start_idx : start_idx + lookback]
            current_price = raw_closes[start_idx + lookback - 1]

            labels = []
            for horizon in horizons:
                future_price = raw_closes[start_idx + lookback + horizon - 1]
                labels.append(1 if future_price > current_price else 0)

            X_ts_list.append(window)
            X_ticker_list.append(ticker_id)
            X_sector_list.append(sector_id)
            y_class_list.append(labels)

    X_ts = np.asarray(X_ts_list, dtype=np.float32)
    X_ticker = np.asarray(X_ticker_list, dtype=np.int32)
    X_sector = np.asarray(X_sector_list, dtype=np.int32)
    y_class = np.asarray(y_class_list, dtype=np.float32)

    if len(X_ts) == 0:
        raise ValueError("시퀀스 생성 결과가 비어 있습니다. lookback/horizons 또는 데이터 기간을 확인하세요.")

    info = {
        "n_tickers": len(loader.ticker_to_id),
        "n_sectors": len(loader.sector_to_id),
        "feature_names": feature_columns,
        "n_features": len(feature_columns),
        "horizons": horizons,
        "scaler": scaler,
        "feature_focus": "macro_correlation",
        "scaler_type": scaler.__class__.__name__,
    }
    return X_ts, X_ticker, X_sector, y_class, info


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
        "head_size": int(config["head_size"]),
        "num_heads": int(config["num_heads"]),
        "ff_dim": int(config["ff_dim"]),
        "num_blocks": int(config["num_blocks"]),
        "mlp_units": list(config["mlp_units"]),
        "dropout": float(config["dropout"]),
        "mlp_dropout": float(config["mlp_dropout"]),
        "scaler_type": info["scaler_type"],
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

    X_ts, X_ticker, X_sector, y_class, info = build_macro_correlation_dataset(loader, raw_df, config)
    n_outputs = y_class.shape[1]

    print("\n" + "=" * 56)
    print(" [DEBUG] Selected macro/correlation features")
    print("=" * 56)
    print(info["feature_names"])
    print("=" * 56)

    for idx, horizon in enumerate(info["horizons"]):
        labels = y_class[:, idx]
        unique, counts = np.unique(labels, return_counts=True)
        dist = {int(key): int(value) for key, value in zip(unique, counts)}
        positive_ratio = dist.get(1, 0) / max(sum(dist.values()), 1) * 100
        print(f" - {horizon}d positive ratio: {positive_ratio:.2f}% | dist={dist}")

    X_tick = X_ticker.reshape(-1, 1)
    X_sec = X_sector.reshape(-1, 1)

    # 메타 입력(ticker / sector)도 시계열과 같은 샘플 축을 유지한 채 분리합니다.
    X_ts_train, X_ts_val, X_tick_train, X_tick_val, X_sec_train, X_sec_val, y_train, y_val = train_test_split(
        X_ts,
        X_tick,
        X_sec,
        y_class,
        test_size=float(config["test_size"]),
        shuffle=True,
        random_state=int(config["random_state"]),
    )

    print(f">> 모델 빌드 중 (seq_len={X_ts.shape[1]}, features={X_ts.shape[2]}, outputs={n_outputs})...")
    model = build_itransformer_model(
        input_shape=(X_ts.shape[1], X_ts.shape[2]),
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

    # 모델 본체(.keras), 스케일러(.pkl), 메타데이터(.json)를 함께 남겨야
    # wrapper가 별도 추가 설정 없이 바로 서비스 추론에 들어갈 수 있습니다.
    model_path = os.path.join(save_dir, "multi_horizon_model.keras")
    scaler_path = os.path.join(save_dir, "multi_horizon_scaler.pkl")

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

    with open(scaler_path, "wb") as f:
        pickle.dump(info["scaler"], f)

    metadata_path = save_training_metadata(save_dir, info, config)

    print("\n[완료] iTransformer 학습 종료")
    print(f" - feature focus: {info['feature_focus']}")
    print(f" - model   : {model_path}")
    print(f" - scaler  : {scaler_path}")
    print(f" - metadata: {metadata_path}")

    return {
        "history": history.history,
        "feature_names": info["feature_names"],
        "model_path": model_path,
        "scaler_path": scaler_path,
        "metadata_path": metadata_path,
    }


if __name__ == "__main__":
    train_single_pipeline()
