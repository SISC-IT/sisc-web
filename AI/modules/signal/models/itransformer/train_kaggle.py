# AI/modules/signal/models/itransformer/train_kaggle.py
"""
iTransformer Kaggle 학습 스크립트.

Kaggle 입력 parquet만 사용해 iTransformer를 학습하고, wrapper/evaluation 계약에
맞는 model, scaler, metadata artifact를 /kaggle/working에 저장한다.
2025년 이후 데이터는 기본 설정에서 학습에 포함하지 않는다.
"""
from __future__ import annotations

import glob
import os
import posixpath
import pickle
import warnings
from typing import Any

warnings.filterwarnings("ignore")
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"

import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler

from AI.modules.signal.models.itransformer.feature_contract import (
    ITRANSFORMER_DEFAULT_FEATURES,
    ITRANSFORMER_FEATURE_SET_VER,
    build_itransformer_metadata,
    normalize_itransformer_feature_aliases,
    resolve_itransformer_feature_columns,
    save_itransformer_metadata,
)


def _env_int(name: str, default: int) -> int:
    value = os.environ.get(name)
    return int(value) if value not in {None, ""} else int(default)


def _env_float(name: str, default: float) -> float:
    value = os.environ.get(name)
    return float(value) if value not in {None, ""} else float(default)


def _find_kaggle_dataset_path() -> str:
    """Kaggle 입력 데이터셋 경로를 자동 탐색한다."""
    explicit_dir = os.environ.get("PARQUET_DIR")
    if explicit_dir:
        return explicit_dir

    base = "/kaggle/input"
    matches = glob.glob(f"{base}/**/price_data.parquet", recursive=True)
    if matches:
        return os.path.dirname(matches[0])
    return base


KAGGLE_DATA_DIR = _find_kaggle_dataset_path()
OUTPUT_DIR = os.environ.get("OUTPUT_DIR") or os.environ.get("WEIGHTS_DIR") or "/kaggle/working"

CONFIG: dict[str, Any] = {
    "lookback": _env_int("SEQ_LEN", 60),
    "horizons": [1, 3, 5, 7],
    "train_start_date": os.environ.get("TRAIN_START_DATE", "2021-01-01"),
    "train_end_date": os.environ.get("TRAIN_END_DATE", "2024-12-31"),
    "eval_start_date": os.environ.get("EVAL_START_DATE", "2024-10-01"),
    "eval_end_date": os.environ.get("EVAL_END_DATE", "2024-12-31"),
    "label_cutoff_date": os.environ.get(
        "LABEL_CUTOFF_DATE",
        os.environ.get("TRAIN_END_DATE", "2024-12-31"),
    ),
    "holdout_start_date": os.environ.get("HOLDOUT_START_DATE", "2025-01-01"),
    "epochs": _env_int("EPOCHS", 30),
    "batch_size": _env_int("BATCH_SIZE", 32),
    "learning_rate": _env_float("LEARNING_RATE", 1e-4),
    "head_size": _env_int("HEAD_SIZE", 128),
    "num_heads": _env_int("NUM_HEADS", 4),
    "ff_dim": _env_int("FF_DIM", 256),
    "num_blocks": _env_int("NUM_BLOCKS", 4),
    "mlp_units": [128, 64],
    "dropout": _env_float("DROPOUT", 0.2),
    "mlp_dropout": _env_float("MLP_DROPOUT", 0.2),
    "test_size": _env_float("TEST_SIZE", 0.2),
    "early_stopping_patience": _env_int("EARLY_STOPPING_PATIENCE", 8),
    "reduce_lr_patience": _env_int("REDUCE_LR_PATIENCE", 4),
    "model_name": "multi_horizon_model.keras",
    "scaler_name": "multi_horizon_scaler.pkl",
    "metadata_name": "metadata.json",
    "feature_set_ver": ITRANSFORMER_FEATURE_SET_VER,
    "feature_candidates": list(ITRANSFORMER_DEFAULT_FEATURES),
    "min_feature_count": 8,
    "feature_focus": "macro_correlation",
    "scaler_type": "StandardScaler",
    "model_ver": f"{ITRANSFORMER_FEATURE_SET_VER}_kaggle_full_train_v0",
}

FEATURE_COLUMNS = list(ITRANSFORMER_DEFAULT_FEATURES)
HORIZONS = CONFIG["horizons"]


def _date(value: Any, *, name: str) -> pd.Timestamp:
    try:
        return pd.to_datetime(value, errors="raise").normalize()
    except Exception as exc:
        raise ValueError(f"{name} 날짜를 해석할 수 없습니다: {value!r}") from exc


def _window_text(start_date: Any, end_date: Any) -> str:
    return f"{pd.Timestamp(start_date).date()}..{pd.Timestamp(end_date).date()}"


def _join_artifact_path(output_dir: str, file_name: str) -> str:
    """Kaggle 절대 경로는 Windows에서도 POSIX 표기로 유지한다."""
    if str(output_dir).startswith("/kaggle/"):
        return posixpath.join(str(output_dir), file_name)
    return os.path.join(str(output_dir), file_name)


def validate_training_window_policy(config: dict[str, Any] | None = None) -> dict[str, pd.Timestamp]:
    """학습 cutoff가 2025 이후 holdout을 침범하지 않는지 검증한다."""
    active_config = dict(CONFIG)
    if config:
        active_config.update(config)

    train_start = _date(active_config["train_start_date"], name="train_start_date")
    train_end = _date(active_config["train_end_date"], name="train_end_date")
    label_cutoff = _date(active_config["label_cutoff_date"], name="label_cutoff_date")
    holdout_start = _date(active_config["holdout_start_date"], name="holdout_start_date")
    eval_start = _date(active_config["eval_start_date"], name="eval_start_date")
    eval_end = _date(active_config["eval_end_date"], name="eval_end_date")

    if train_start > train_end:
        raise ValueError("train_start_date는 train_end_date보다 늦을 수 없습니다.")
    if eval_start > eval_end:
        raise ValueError("eval_start_date는 eval_end_date보다 늦을 수 없습니다.")
    if train_end >= holdout_start:
        raise ValueError(
            "2025 이후 holdout을 학습에 포함할 수 없습니다. "
            f"train_end_date={train_end.date()}, holdout_start_date={holdout_start.date()}"
        )
    if label_cutoff > train_end:
        raise ValueError(
            "label_cutoff_date는 train_end_date를 넘을 수 없습니다. "
            f"label_cutoff_date={label_cutoff.date()}, train_end_date={train_end.date()}"
        )
    return {
        "train_start": train_start,
        "train_end": train_end,
        "label_cutoff": label_cutoff,
        "holdout_start": holdout_start,
        "eval_start": eval_start,
        "eval_end": eval_end,
    }


def load_parquet_data(
    *,
    config: dict[str, Any] | None = None,
    data_dir: str | None = None,
    apply_train_window: bool = True,
) -> pd.DataFrame:
    """parquet에서 price_data와 macroeconomic_indicators를 로드해 피처를 만든다."""
    active_config = dict(CONFIG)
    if config:
        active_config.update(config)
    dates = validate_training_window_policy(active_config)

    parquet_dir = data_dir or _find_kaggle_dataset_path()
    price_path = os.path.join(parquet_dir, "price_data.parquet")
    macro_path = os.path.join(parquet_dir, "macroeconomic_indicators.parquet")
    if not os.path.exists(price_path):
        raise FileNotFoundError(f"price_data.parquet 파일이 없습니다: {price_path}")
    if not os.path.exists(macro_path):
        raise FileNotFoundError(f"macroeconomic_indicators.parquet 파일이 없습니다: {macro_path}")

    print(">> parquet 데이터 로드 중...")
    print(f"   입력 경로: {parquet_dir}")

    price_df = pd.read_parquet(price_path)
    price_df["date"] = pd.to_datetime(price_df["date"]).dt.normalize()
    price_df["ticker"] = price_df["ticker"].astype(str)
    price_df = price_df.sort_values(["ticker", "date"]).reset_index(drop=True)

    # 학습과 평가가 공유하는 가격 기반 피처를 생성한다.
    price_df["log_return"] = price_df.groupby("ticker")["close"].transform(
        lambda x: np.log(x / x.shift(1))
    )
    price_df["ret_1d"] = price_df.groupby("ticker")["close"].transform(lambda x: x.pct_change())
    price_df["intraday_vol"] = (price_df["high"] - price_df["low"]) / price_df["close"]
    price_df["ma200"] = price_df.groupby("ticker")["close"].transform(
        lambda x: x.rolling(200, min_periods=1).mean()
    )
    price_df["ma200_pct"] = (price_df["close"] - price_df["ma200"]) / price_df["ma200"]
    price_df["recent_loss_ema"] = price_df.groupby("ticker")["log_return"].transform(
        lambda x: x.clip(upper=0).ewm(span=20).mean().abs()
    )

    macro_df = pd.read_parquet(macro_path)
    macro_df["date"] = pd.to_datetime(macro_df["date"]).dt.normalize()
    macro_cols = [
        "date",
        "us10y",
        "yield_spread",
        "vix_close",
        "dxy_close",
        "credit_spread_hy",
        "wti_price",
        "gold_price",
        "nh_nl_index",
        "correlation_spike",
        "surprise_cpi",
    ]
    available_macro = [column for column in macro_cols if column in macro_df.columns]
    macro_df = (
        macro_df[available_macro]
        .sort_values("date")
        .drop_duplicates("date", keep="last")
        .reset_index(drop=True)
    )

    if "us10y" in macro_df.columns:
        macro_df["us10y_chg"] = macro_df["us10y"].diff()
    if "dxy_close" in macro_df.columns:
        macro_df["dxy_chg"] = macro_df["dxy_close"].pct_change()
    if "vix_close" in macro_df.columns:
        macro_df["vix_change_rate"] = macro_df["vix_close"].pct_change()

    df = pd.merge(price_df, macro_df, on="date", how="left")
    df = df.sort_values(["ticker", "date"]).reset_index(drop=True)

    macro_feature_cols = [column for column in macro_df.columns if column != "date"]
    if macro_feature_cols:
        df[macro_feature_cols] = df.groupby("ticker")[macro_feature_cols].transform(lambda x: x.ffill())
    df = normalize_itransformer_feature_aliases(df)
    df = df.replace([np.inf, -np.inf], np.nan).fillna(0)

    if apply_train_window:
        df = df[
            (df["date"] >= dates["train_start"])
            & (df["date"] <= dates["train_end"])
        ].copy()

    print(f">> 로드 완료: {len(df):,}행, {df['ticker'].nunique()}개 종목")
    if df.empty:
        raise ValueError("학습 기간 필터 적용 후 데이터가 비었습니다.")
    return df.reset_index(drop=True)


def build_sequences(
    df: pd.DataFrame,
    scaler: StandardScaler,
    ticker_to_id: dict[str, int] | None = None,
    fit_scaler: bool = False,
    feature_columns: list[str] | None = None,
    config: dict[str, Any] | None = None,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, list[str]]:
    """학습용 3입력 시퀀스와 multi-horizon label을 생성한다."""
    active_config = dict(CONFIG)
    if config:
        active_config.update(config)

    lookback = int(active_config["lookback"])
    horizons = [int(horizon) for horizon in active_config["horizons"]]
    max_horizon = max(horizons)
    available_feats = list(feature_columns or resolve_itransformer_feature_columns(df, active_config))

    if fit_scaler:
        scaler.fit(df[available_feats].astype(np.float32))

    x_list: list[np.ndarray] = []
    ticker_id_list: list[int] = []
    sector_id_list: list[int] = []
    y_list: list[list[float]] = []

    for ticker, group in df.groupby("ticker", sort=True):
        group = group.sort_values("date").reset_index(drop=True)
        if len(group) < lookback + max_horizon + 1:
            continue

        ticker_id = int(ticker_to_id.get(str(ticker), 0)) if ticker_to_id else 0
        feat_vals = scaler.transform(group[available_feats].astype(np.float32))
        close_vals = group["close"].to_numpy(dtype=np.float32)

        for idx in range(lookback, len(group) - max_horizon):
            x_list.append(feat_vals[idx - lookback : idx])
            ticker_id_list.append(ticker_id)
            sector_id_list.append(0)
            labels = []
            for horizon in horizons:
                future_ret = (close_vals[idx + horizon] - close_vals[idx]) / close_vals[idx]
                labels.append(1.0 if future_ret > 0.0 else 0.0)
            y_list.append(labels)

    if not x_list:
        raise ValueError("시퀀스 생성 실패: 학습 가능한 데이터가 부족합니다.")

    x = np.array(x_list, dtype=np.float32)
    x_ticker = np.array(ticker_id_list, dtype=np.int32).reshape(-1, 1)
    x_sector = np.array(sector_id_list, dtype=np.int32).reshape(-1, 1)
    y = np.array(y_list, dtype=np.float32)
    print(f">> 시퀀스 생성: X={x.shape}, y={y.shape}, 피처 수={len(available_feats)}")
    return x, x_ticker, x_sector, y, available_feats


def build_model(
    seq_len: int,
    n_features: int,
    n_outputs: int,
    n_tickers: int,
    n_sectors: int,
    config: dict[str, Any] | None = None,
) -> tf.keras.Model:
    """추론 wrapper와 같은 3입력 모델 계약으로 iTransformer를 생성한다."""
    import tensorflow as tf

    from AI.modules.signal.models.itransformer.architecture import build_itransformer_model

    active_config = dict(CONFIG)
    if config:
        active_config.update(config)

    model = build_itransformer_model(
        input_shape=(seq_len, n_features),
        n_tickers=n_tickers,
        n_sectors=n_sectors,
        head_size=int(active_config["head_size"]),
        num_heads=int(active_config["num_heads"]),
        ff_dim=int(active_config["ff_dim"]),
        num_transformer_blocks=int(active_config["num_blocks"]),
        mlp_units=list(active_config["mlp_units"]),
        dropout=float(active_config["dropout"]),
        mlp_dropout=float(active_config["mlp_dropout"]),
        n_outputs=n_outputs,
    )
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=float(active_config["learning_rate"])),
        loss="binary_crossentropy",
        metrics=["accuracy"],
    )
    return model


def train(config: dict[str, Any] | None = None) -> dict[str, Any]:
    """Kaggle 학습을 실행하고 저장된 artifact 경로와 metadata를 반환한다."""
    active_config = dict(CONFIG)
    if config:
        active_config.update(config)
    dates = validate_training_window_policy(active_config)

    print("=" * 56)
    print(" iTransformer Kaggle 학습 시작")
    print(f" Horizons: {active_config['horizons']}일")
    print(f" Lookback: {active_config['lookback']}일")
    print(f" Train window: {_window_text(dates['train_start'], dates['train_end'])}")
    print(f" Label cutoff: {dates['label_cutoff'].date()}")
    print("=" * 56)

    df = load_parquet_data(config=active_config)
    tickers = sorted(df["ticker"].dropna().astype(str).unique().tolist())
    if len(tickers) < 2:
        raise ValueError(f"학습을 위한 ticker가 부족합니다. 현재 {len(tickers)}개")

    n_val = min(max(1, int(len(tickers) * float(active_config["test_size"]))), len(tickers) - 1)
    val_tickers = set(tickers[-n_val:])
    train_tickers = [ticker for ticker in tickers if ticker not in val_tickers]

    train_df = df[df["ticker"].isin(train_tickers)].copy()
    val_df = df[df["ticker"].isin(val_tickers)].copy()
    print(f"\n>> Train ticker: {len(train_tickers)}개 | Val ticker: {len(val_tickers)}개")

    feature_columns = resolve_itransformer_feature_columns(train_df, active_config)
    scaler = StandardScaler()
    ticker_to_id = {ticker: idx for idx, ticker in enumerate(tickers)}

    x_train, x_tick_train, x_sec_train, y_train, feat_cols = build_sequences(
        train_df,
        scaler,
        ticker_to_id=ticker_to_id,
        fit_scaler=True,
        feature_columns=feature_columns,
        config=active_config,
    )
    x_val, x_tick_val, x_sec_val, y_val, _ = build_sequences(
        val_df,
        scaler,
        ticker_to_id=ticker_to_id,
        fit_scaler=False,
        feature_columns=feature_columns,
        config=active_config,
    )

    model = build_model(
        int(active_config["lookback"]),
        int(x_train.shape[2]),
        len(active_config["horizons"]),
        n_tickers=max(1, len(ticker_to_id)),
        n_sectors=1,
        config=active_config,
    )
    model.summary()

    import tensorflow as tf

    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=int(active_config["early_stopping_patience"]),
            restore_best_weights=True,
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=int(active_config["reduce_lr_patience"]),
            verbose=1,
        ),
    ]

    history = model.fit(
        [x_train, x_tick_train, x_sec_train],
        y_train,
        validation_data=([x_val, x_tick_val, x_sec_val], y_val),
        epochs=int(active_config["epochs"]),
        batch_size=int(active_config["batch_size"]),
        callbacks=callbacks,
        verbose=1,
    )

    output_dir = str(
        active_config.get("output_dir")
        or os.environ.get("OUTPUT_DIR")
        or os.environ.get("WEIGHTS_DIR")
        or OUTPUT_DIR
    )
    os.makedirs(output_dir, exist_ok=True)
    model_path = _join_artifact_path(output_dir, str(active_config["model_name"]))
    scaler_path = _join_artifact_path(output_dir, str(active_config["scaler_name"]))
    metadata_path = _join_artifact_path(output_dir, str(active_config["metadata_name"]))

    model.save(model_path)
    with open(scaler_path, "wb") as file:
        pickle.dump(scaler, file)

    best_val_loss = float(min(history.history["val_loss"]))
    best_val_acc = float(max(history.history.get("val_accuracy", [0.0])))
    train_window = _window_text(dates["train_start"], dates["train_end"])
    validation_window = (
        f"ticker_holdout:last_{len(val_tickers)}_of_{len(tickers)};"
        f"date_window={train_window}"
    )

    metadata = build_itransformer_metadata(
        config={
            **active_config,
            "seq_len": int(active_config["lookback"]),
            "n_tickers": max(1, len(ticker_to_id)),
            "n_sectors": 1,
            "best_val_loss": round(best_val_loss, 6),
            "best_val_acc": round(best_val_acc, 6),
            "n_train_samples": int(len(x_train)),
            "n_val_samples": int(len(x_val)),
            "ticker_to_id": ticker_to_id,
            "train_start_date": str(dates["train_start"].date()),
            "train_end_date": str(dates["train_end"].date()),
            "train_window": train_window,
            "validation_window": validation_window,
            "label_cutoff_date": str(dates["label_cutoff"].date()),
            "holdout_start_date": str(dates["holdout_start"].date()),
        },
        model_path=model_path,
        scaler_path=scaler_path,
        feature_columns=feat_cols,
    )
    save_itransformer_metadata(metadata_path, metadata)

    print("\n학습 완료")
    print(f"  모델: {model_path}")
    print(f"  스케일러: {scaler_path}")
    print(f"  메타데이터: {metadata_path}")
    print(f"  Best val_loss: {best_val_loss:.6f}")
    print(f"  Best val_acc: {best_val_acc:.6f}")

    return {
        "model_path": model_path,
        "scaler_path": scaler_path,
        "metadata_path": metadata_path,
        "metadata": metadata,
        "config": active_config,
        "feature_columns": feat_cols,
        "history": {
            "best_val_loss": best_val_loss,
            "best_val_acc": best_val_acc,
            "epochs_ran": int(len(history.history.get("loss", []))),
        },
    }


if __name__ == "__main__":
    train()
