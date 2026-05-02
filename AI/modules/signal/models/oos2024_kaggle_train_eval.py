"""Transformer/iTransformer OOS2024 Kaggle 학습 및 평가 헬퍼.

이 모듈은 Kaggle에서만 실제 학습을 실행하는 일회성 개발용 패키지다.
로컬에서는 설정 검증, 문법 검사, 노트북 구조 검증까지만 수행한다.
"""

from __future__ import annotations

import glob
import json
import os
import pickle
import posixpath
import warnings
from collections.abc import Mapping
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler

from AI.modules.features.legacy.technical_features import (
    add_multi_timeframe_features,
    add_technical_indicators,
)
from AI.modules.signal.evaluation.backtest import (
    backtest_top_k_signals,
    universe_equal_benchmark,
)
from AI.modules.signal.evaluation.diagnostics import build_signal_diagnostics_frame
from AI.modules.signal.evaluation.leaderboard import build_leaderboard
from AI.modules.signal.evaluation.model_metrics import build_model_metric_frame
from AI.modules.signal.evaluation.objectives import build_model_objective_frame
from AI.modules.signal.evaluation.schema import (
    calculate_confidence,
    calculate_signal,
    validate_signal_frame,
)
from AI.modules.signal.models.itransformer.feature_contract import (
    ITRANSFORMER_DEFAULT_FEATURES,
    ITRANSFORMER_FEATURE_SET_VER,
    build_itransformer_metadata,
    normalize_itransformer_feature_aliases,
    resolve_itransformer_feature_columns,
    save_itransformer_metadata,
)


warnings.filterwarnings("ignore")

HORIZONS = [1, 3, 5, 7]
ARTIFACT_PURPOSE = "oos2024_dev"
TRANSFORMER_FEATURE_SET_VER = "transformer_technical_mtf_v1"
TRANSFORMER_MODEL_VER = "transformer_technical_mtf_v1_oos2024_dev_v0"
ITRANSFORMER_MODEL_VER = f"{ITRANSFORMER_FEATURE_SET_VER}_oos2024_dev_v0"

TRANSFORMER_TRAIN_FEATURES = [
    "log_return",
    "open_ratio",
    "high_ratio",
    "low_ratio",
    "vol_change",
    "ma5_ratio",
    "ma20_ratio",
    "ma60_ratio",
    "rsi",
    "macd_ratio",
    "bb_position",
    "week_ma20_ratio",
    "week_rsi",
    "week_bb_pos",
    "week_vol_change",
    "month_ma12_ratio",
    "month_rsi",
]


def _find_kaggle_dataset_path() -> str:
    explicit_dir = os.environ.get("PARQUET_DIR")
    if explicit_dir:
        return explicit_dir

    base = "/kaggle/input"
    matches = glob.glob(f"{base}/**/price_data.parquet", recursive=True)
    if matches:
        return os.path.dirname(matches[0])
    return base


def _env_int(name: str, default: int) -> int:
    value = os.environ.get(name)
    return int(value) if value not in {None, ""} else int(default)


def _env_float(name: str, default: float) -> float:
    value = os.environ.get(name)
    return float(value) if value not in {None, ""} else float(default)


def _join_path(*parts: str) -> str:
    first = str(parts[0])
    if first.startswith("/kaggle/"):
        return posixpath.join(*(str(part) for part in parts))
    return os.path.join(*(str(part) for part in parts))


def _timestamp(value: Any, *, name: str) -> pd.Timestamp:
    try:
        return pd.to_datetime(value, errors="raise").normalize()
    except Exception as exc:
        raise ValueError(f"{name} 날짜를 해석할 수 없습니다: {value!r}") from exc


def _date_text(value: Any) -> str:
    return str(pd.Timestamp(value).date())


def _window_text(start_date: Any, end_date: Any) -> str:
    return f"{_date_text(start_date)}..{_date_text(end_date)}"


def _jsonable(value: Any) -> Any:
    if isinstance(value, Mapping):
        return {str(key): _jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple, set)):
        return [_jsonable(item) for item in value]
    if isinstance(value, (pd.Timestamp,)):
        return _date_text(value)
    if isinstance(value, (np.integer,)):
        return int(value)
    if isinstance(value, (np.floating,)):
        return float(value)
    if isinstance(value, (np.bool_,)):
        return bool(value)
    return value


def _save_json(path: str, payload: Mapping[str, Any]) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as file:
        json.dump(_jsonable(dict(payload)), file, ensure_ascii=False, indent=2)


def build_oos2024_config(overrides: Mapping[str, Any] | None = None) -> dict[str, Any]:
    """Kaggle OOS2024 학습 및 평가 설정을 만든다."""
    output_root = (
        os.environ.get("OOS2024_OUTPUT_ROOT")
        or os.environ.get("OUTPUT_ROOT")
        or "/kaggle/working/oos2024"
    )
    config: dict[str, Any] = {
        "parquet_dir": os.environ.get("PARQUET_DIR", _find_kaggle_dataset_path()),
        "output_root": output_root,
        "train_start": os.environ.get("TRAIN_START_DATE", "2021-01-01"),
        "train_cutoff": os.environ.get("TRAIN_CUTOFF_DATE", "2024-06-30"),
        "validation_start": os.environ.get("VALIDATION_START_DATE", "2024-01-02"),
        "eval_start": os.environ.get("EVAL_START_DATE", "2024-09-03"),
        "eval_end": os.environ.get("EVAL_END_DATE", "2024-12-31"),
        "holdout_start": os.environ.get("HOLDOUT_START_DATE", "2025-01-01"),
        "horizons": list(HORIZONS),
        "seq_len": _env_int("SEQ_LEN", 60),
        "batch_size": _env_int("BATCH_SIZE", 32),
        "epochs": _env_int("EPOCHS", 30),
        "early_stopping_patience": _env_int("EARLY_STOPPING_PATIENCE", 8),
        "reduce_lr_patience": _env_int("REDUCE_LR_PATIENCE", 4),
        "learning_rate": _env_float("LEARNING_RATE", 1e-4),
        "top_k": _env_int("TOP_K", 2),
        "buy_threshold": _env_float("BUY_THRESHOLD", 0.5),
        "confidence_threshold": _env_float("CONFIDENCE_THRESHOLD", 0.0),
        "confidence_threshold_alt": _env_float("CONFIDENCE_THRESHOLD_ALT", 0.2),
        "cost_bps_per_side": _env_float("COST_BPS_PER_SIDE", 5.0),
        "missing_return_policy": "error",
        "transformer": {
            "model_name": "transformer",
            "feature_set_ver": TRANSFORMER_FEATURE_SET_VER,
            "model_ver": TRANSFORMER_MODEL_VER,
            "feature_columns": list(TRANSFORMER_TRAIN_FEATURES),
            "head_size": 256,
            "num_heads": 4,
            "ff_dim": 4,
            "num_blocks": 4,
            "mlp_units": [128],
            "dropout": 0.2,
            "mlp_dropout": 0.2,
        },
        "itransformer": {
            "model_name": "itransformer",
            "feature_set_ver": ITRANSFORMER_FEATURE_SET_VER,
            "model_ver": ITRANSFORMER_MODEL_VER,
            "feature_candidates": list(ITRANSFORMER_DEFAULT_FEATURES),
            "min_feature_count": 8,
            "feature_focus": "macro_correlation",
            "head_size": 128,
            "num_heads": 4,
            "ff_dim": 256,
            "num_blocks": 4,
            "mlp_units": [128, 64],
            "dropout": 0.2,
            "mlp_dropout": 0.2,
        },
    }
    if overrides:
        _deep_update(config, dict(overrides))
    validate_oos2024_config(config)
    return config


def _deep_update(target: dict[str, Any], overrides: dict[str, Any]) -> None:
    for key, value in overrides.items():
        if isinstance(value, Mapping) and isinstance(target.get(key), dict):
            _deep_update(target[key], dict(value))
        else:
            target[key] = value


def validate_oos2024_config(config: Mapping[str, Any]) -> dict[str, pd.Timestamp]:
    """날짜 split과 prod 경로 오염 위험을 검증한다."""
    train_start = _timestamp(config["train_start"], name="train_start")
    train_cutoff = _timestamp(config["train_cutoff"], name="train_cutoff")
    validation_start = _timestamp(config["validation_start"], name="validation_start")
    eval_start = _timestamp(config["eval_start"], name="eval_start")
    eval_end = _timestamp(config["eval_end"], name="eval_end")
    holdout_start = _timestamp(config["holdout_start"], name="holdout_start")

    if train_start > train_cutoff:
        raise ValueError("train_start는 train_cutoff보다 늦을 수 없습니다.")
    if not train_start < validation_start <= train_cutoff:
        raise ValueError("validation_start는 train_start 이후, train_cutoff 이하이어야 합니다.")
    if train_cutoff >= eval_start:
        raise ValueError("eval_start는 train_cutoff 이후이어야 합니다.")
    if eval_start < pd.Timestamp("2024-09-03"):
        raise ValueError("eval_start는 2024-09-03 이상이어야 합니다.")
    if eval_start > eval_end:
        raise ValueError("eval_start는 eval_end보다 늦을 수 없습니다.")
    if train_cutoff >= holdout_start or eval_end >= holdout_start:
        raise ValueError("2025 이후 holdout은 학습/평가에 포함할 수 없습니다.")
    if [int(horizon) for horizon in config["horizons"]] != HORIZONS:
        raise ValueError("OOS2024 노트북 horizon은 [1, 3, 5, 7]이어야 합니다.")

    output_root = str(config["output_root"])
    if "AI/data/weights" in output_root.replace("\\", "/"):
        raise ValueError("OOS2024 output_root가 prod artifact root를 가리키면 안 됩니다.")
    return {
        "train_start": train_start,
        "train_cutoff": train_cutoff,
        "validation_start": validation_start,
        "eval_start": eval_start,
        "eval_end": eval_end,
        "holdout_start": holdout_start,
    }


def smoke_config_summary(config: Mapping[str, Any] | None = None) -> dict[str, Any]:
    """로컬 dry-run에서 데이터 로드나 학습 없이 설정과 경로만 확인한다."""
    active_config = build_oos2024_config(config or {})
    dates = validate_oos2024_config(active_config)
    output_root = str(active_config["output_root"])
    return {
        "artifact_purpose": ARTIFACT_PURPOSE,
        "parquet_dir": active_config["parquet_dir"],
        "train_start": _date_text(dates["train_start"]),
        "train_cutoff": _date_text(dates["train_cutoff"]),
        "validation_start": _date_text(dates["validation_start"]),
        "eval_start": _date_text(dates["eval_start"]),
        "eval_end": _date_text(dates["eval_end"]),
        "holdout_start": _date_text(dates["holdout_start"]),
        "horizons": list(HORIZONS),
        "seq_len": int(active_config["seq_len"]),
        "top_k": int(active_config["top_k"]),
        "buy_threshold": float(active_config["buy_threshold"]),
        "confidence_threshold": float(active_config["confidence_threshold"]),
        "confidence_threshold_alt": float(active_config["confidence_threshold_alt"]),
        "cost_bps_per_side": float(active_config["cost_bps_per_side"]),
        "transformer_output_dir": _join_path(output_root, "transformer"),
        "itransformer_output_dir": _join_path(output_root, "itransformer"),
        "combined_output_dir": output_root,
        "prod_artifact_overwrite": False,
    }


def _require_input_files(parquet_dir: str) -> dict[str, str]:
    required = {
        "price": "price_data.parquet",
        "macro": "macroeconomic_indicators.parquet",
        "stock_info": "stock_info.parquet",
    }
    resolved = {key: os.path.join(parquet_dir, name) for key, name in required.items()}
    missing = [path for path in resolved.values() if not os.path.exists(path)]
    if missing:
        raise FileNotFoundError(f"OOS2024 필수 parquet 누락: {missing}")
    return resolved


def _load_price_data(config: Mapping[str, Any]) -> pd.DataFrame:
    dates = validate_oos2024_config(config)
    paths = _require_input_files(str(config["parquet_dir"]))
    price_df = pd.read_parquet(paths["price"])
    price_df["date"] = pd.to_datetime(price_df["date"]).dt.normalize()
    price_df["ticker"] = price_df["ticker"].astype(str)
    price_df = price_df[
        (price_df["date"] >= dates["train_start"])
        & (price_df["date"] <= dates["eval_end"])
    ].copy()
    price_df = price_df.sort_values(["ticker", "date"]).reset_index(drop=True)
    if price_df.empty:
        raise ValueError("OOS2024 기간에 해당하는 price_data가 없습니다.")
    return price_df


def _merge_stock_info(frame: pd.DataFrame, config: Mapping[str, Any]) -> pd.DataFrame:
    stock_info_path = os.path.join(str(config["parquet_dir"]), "stock_info.parquet")
    if not os.path.exists(stock_info_path):
        frame = frame.copy()
        frame["sector"] = "Unknown"
        return frame
    stock_info = pd.read_parquet(stock_info_path)
    columns = [column for column in ["ticker", "sector"] if column in stock_info.columns]
    if "ticker" not in columns:
        frame = frame.copy()
        frame["sector"] = "Unknown"
        return frame
    stock_info = stock_info[columns].copy()
    stock_info["ticker"] = stock_info["ticker"].astype(str)
    merged = frame.merge(stock_info.drop_duplicates("ticker"), on="ticker", how="left")
    if "sector" not in merged.columns:
        merged["sector"] = "Unknown"
    merged["sector"] = merged["sector"].fillna("Unknown").astype(str)
    return merged


def load_transformer_feature_frame(config: Mapping[str, Any]) -> pd.DataFrame:
    """Transformer 기술적 feature frame을 만든다."""
    price_df = _merge_stock_info(_load_price_data(config), config)
    processed: list[pd.DataFrame] = []
    fail_count = 0
    for ticker, group in price_df.groupby("ticker", sort=True):
        try:
            feature_group = add_technical_indicators(group.sort_values("date").copy())
            feature_group = add_multi_timeframe_features(feature_group)
            processed.append(feature_group)
        except Exception as exc:
            fail_count += 1
            if fail_count >= 20:
                raise RuntimeError("Transformer feature 계산 실패가 20개 종목을 초과했습니다.") from exc
    if not processed:
        raise ValueError("Transformer feature frame을 생성하지 못했습니다.")
    frame = pd.concat(processed, ignore_index=True)
    frame = _merge_stock_info(frame, config)
    frame = frame.replace([np.inf, -np.inf], np.nan).fillna(0)
    return frame.sort_values(["ticker", "date"]).reset_index(drop=True)


def load_itransformer_feature_frame(config: Mapping[str, Any]) -> pd.DataFrame:
    """iTransformer macro/regime feature frame을 만든다."""
    price_df = _load_price_data(config)
    price_df["log_return"] = price_df.groupby("ticker")["close"].transform(
        lambda values: np.log(values / values.shift(1))
    )
    price_df["ret_1d"] = price_df.groupby("ticker")["close"].transform(lambda values: values.pct_change())
    price_df["intraday_vol"] = (price_df["high"] - price_df["low"]) / price_df["close"]
    price_df["ma200"] = price_df.groupby("ticker")["close"].transform(
        lambda values: values.rolling(200, min_periods=1).mean()
    )
    price_df["ma200_pct"] = (price_df["close"] - price_df["ma200"]) / price_df["ma200"]
    price_df["recent_loss_ema"] = price_df.groupby("ticker")["log_return"].transform(
        lambda values: values.clip(upper=0).ewm(span=20).mean().abs()
    )

    macro_path = os.path.join(str(config["parquet_dir"]), "macroeconomic_indicators.parquet")
    macro_df = pd.read_parquet(macro_path)
    macro_df["date"] = pd.to_datetime(macro_df["date"]).dt.normalize()
    macro_columns = [
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
    available_macro = [column for column in macro_columns if column in macro_df.columns]
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

    frame = pd.merge(price_df, macro_df, on="date", how="left")
    macro_feature_columns = [column for column in macro_df.columns if column != "date"]
    if macro_feature_columns:
        frame[macro_feature_columns] = frame.groupby("ticker")[macro_feature_columns].transform(lambda x: x.ffill())
    frame = normalize_itransformer_feature_aliases(frame)
    frame = frame.replace([np.inf, -np.inf], np.nan).fillna(0)
    return frame.sort_values(["ticker", "date"]).reset_index(drop=True)


def _resolve_transformer_feature_columns(frame: pd.DataFrame, config: Mapping[str, Any]) -> list[str]:
    columns = list(config["transformer"]["feature_columns"])
    missing = [column for column in columns if column not in frame.columns]
    if missing:
        raise ValueError(f"Transformer feature 누락: {missing}")
    return columns


def _build_id_maps(frame: pd.DataFrame) -> tuple[dict[str, int], dict[str, int]]:
    tickers = sorted(frame["ticker"].dropna().astype(str).unique().tolist())
    sectors = sorted(frame["sector"].dropna().astype(str).unique().tolist()) if "sector" in frame.columns else ["Unknown"]
    if not sectors:
        sectors = ["Unknown"]
    return (
        {ticker: index for index, ticker in enumerate(tickers)},
        {sector: index for index, sector in enumerate(sectors)},
    )


def _fit_scaler(
    frame: pd.DataFrame,
    feature_columns: list[str],
    config: Mapping[str, Any],
) -> StandardScaler:
    dates = validate_oos2024_config(config)
    fit_frame = frame[
        (frame["date"] >= dates["train_start"])
        & (frame["date"] < dates["validation_start"])
    ].copy()
    fit_frame = fit_frame.dropna(subset=feature_columns)
    if fit_frame.empty:
        raise ValueError("scaler fit에 사용할 train feature row가 없습니다.")
    scaler = StandardScaler()
    scaler.fit(fit_frame[feature_columns].astype(np.float32))
    return scaler


def _build_supervised_sequences(
    frame: pd.DataFrame,
    *,
    feature_columns: list[str],
    scaler: StandardScaler,
    ticker_to_id: dict[str, int],
    sector_to_id: dict[str, int],
    config: Mapping[str, Any],
    purpose: str,
) -> dict[str, Any]:
    dates = validate_oos2024_config(config)
    seq_len = int(config["seq_len"])
    horizons = [int(horizon) for horizon in config["horizons"]]
    max_horizon = max(horizons)

    x_values: list[np.ndarray] = []
    ticker_values: list[int] = []
    sector_values: list[int] = []
    y_values: list[list[float]] = []
    sample_rows: list[dict[str, Any]] = []
    realized_rows: list[dict[str, Any]] = []

    clean_frame = frame.dropna(subset=feature_columns + ["close"]).copy()
    for ticker, group in clean_frame.groupby("ticker", sort=True):
        group = group.sort_values("date").reset_index(drop=True)
        if len(group) < seq_len + max_horizon:
            continue

        sector = str(group["sector"].iloc[0]) if "sector" in group.columns else "Unknown"
        ticker_id = int(ticker_to_id.get(str(ticker), 0))
        sector_id = int(sector_to_id.get(sector, 0))
        feature_values = scaler.transform(group[feature_columns].astype(np.float32))
        close_values = group["close"].to_numpy(dtype=np.float32)
        date_values = pd.to_datetime(group["date"]).dt.normalize().tolist()

        for index in range(seq_len - 1, len(group) - max_horizon):
            asof_date = pd.Timestamp(date_values[index]).normalize()
            label_end_max = pd.Timestamp(date_values[index + max_horizon]).normalize()

            if purpose == "train":
                if asof_date < dates["train_start"] or label_end_max > dates["train_cutoff"]:
                    continue
            elif purpose == "eval":
                if asof_date < dates["eval_start"] or asof_date > dates["eval_end"]:
                    continue
                if label_end_max > dates["eval_end"] or label_end_max >= dates["holdout_start"]:
                    continue
            else:
                raise ValueError(f"지원하지 않는 sequence 목적입니다: {purpose}")

            current_close = close_values[index]
            if not np.isfinite(current_close) or float(current_close) == 0.0:
                continue

            labels: list[float] = []
            sample_realized_rows: list[dict[str, Any]] = []
            for horizon in horizons:
                label_start_date = pd.Timestamp(date_values[index + 1]).normalize()
                label_end_date = pd.Timestamp(date_values[index + horizon]).normalize()
                future_close = close_values[index + horizon]
                forward_return = float((future_close - current_close) / current_close)
                labels.append(1.0 if forward_return > 0.0 else 0.0)
                sample_realized_rows.append(
                    {
                        "asof_date": asof_date,
                        "ticker": str(ticker),
                        "horizon": int(horizon),
                        "forward_return": forward_return,
                        "label_start_date": label_start_date,
                        "label_end_date": label_end_date,
                    }
                )

            x_values.append(feature_values[index - seq_len + 1 : index + 1])
            ticker_values.append(ticker_id)
            sector_values.append(sector_id)
            y_values.append(labels)
            sample_rows.append(
                {
                    "asof_date": asof_date,
                    "ticker": str(ticker),
                    "ticker_id": ticker_id,
                    "sector_id": sector_id,
                    "label_end_max": label_end_max,
                }
            )
            realized_rows.extend(sample_realized_rows)

    if not x_values:
        raise ValueError(f"{purpose} sequence가 생성되지 않았습니다.")

    rows_frame = pd.DataFrame(sample_rows)
    realized_frame = pd.DataFrame(realized_rows).drop_duplicates(
        subset=["asof_date", "ticker", "horizon"],
        keep="last",
    )
    return {
        "x": np.asarray(x_values, dtype=np.float32),
        "ticker": np.asarray(ticker_values, dtype=np.int32).reshape(-1, 1),
        "sector": np.asarray(sector_values, dtype=np.int32).reshape(-1, 1),
        "y": np.asarray(y_values, dtype=np.float32),
        "rows": rows_frame,
        "realized_returns": realized_frame,
    }


def _split_train_val(dataset: Mapping[str, Any], config: Mapping[str, Any]) -> tuple[dict[str, Any], dict[str, Any]]:
    dates = validate_oos2024_config(config)
    rows = dataset["rows"].copy()
    train_mask = rows["asof_date"] < dates["validation_start"]
    val_mask = rows["asof_date"] >= dates["validation_start"]
    split_method = f"time_split:{_date_text(dates['validation_start'])}"

    if not bool(val_mask.any()):
        ordered_rows = rows.sort_values(["asof_date", "ticker"]).reset_index()
        n_val = min(max(1, int(len(ordered_rows) * 0.2)), len(ordered_rows) - 1)
        if n_val <= 0:
            raise ValueError("validation sequence를 만들 수 있을 만큼 train sequence가 충분하지 않습니다.")
        val_indices = set(ordered_rows.tail(n_val)["index"].astype(int).tolist())
        val_mask = rows.index.to_series().isin(val_indices)
        train_mask = ~val_mask
        split_method = f"tail_time_fallback:last_{n_val}_of_{len(rows)}"

    if not bool(train_mask.any()):
        raise ValueError("fit train sequence가 비었습니다.")
    if not bool(val_mask.any()):
        raise ValueError("validation sequence가 비었습니다.")

    def subset(mask: pd.Series) -> dict[str, Any]:
        index = np.flatnonzero(mask.to_numpy(dtype=bool))
        return {
            "x": dataset["x"][index],
            "ticker": dataset["ticker"][index],
            "sector": dataset["sector"][index],
            "y": dataset["y"][index],
            "rows": rows.iloc[index].reset_index(drop=True),
            "split_method": split_method,
        }

    return subset(train_mask), subset(val_mask)


def _set_tf_runtime() -> None:
    os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")
    os.environ.setdefault("TF_ENABLE_ONEDNN_OPTS", "0")
    import tensorflow as tf

    for gpu in tf.config.list_physical_devices("GPU"):
        try:
            tf.config.experimental.set_memory_growth(gpu, True)
        except RuntimeError:
            pass


def _artifact_paths(output_dir: str) -> dict[str, str]:
    return {
        "model_path": _join_path(output_dir, "multi_horizon_model.keras"),
        "scaler_path": _join_path(output_dir, "multi_horizon_scaler.pkl"),
        "metadata_path": _join_path(output_dir, "metadata.json"),
    }


def _common_metadata(
    *,
    model_name: str,
    feature_set_ver: str,
    model_ver: str,
    feature_columns: list[str],
    model_path: str,
    scaler_path: str,
    architecture_config: Mapping[str, Any],
    ticker_to_id: Mapping[str, int],
    sector_to_id: Mapping[str, int],
    train_dataset: Mapping[str, Any],
    fit_dataset: Mapping[str, Any],
    val_dataset: Mapping[str, Any],
    eval_dataset: Mapping[str, Any],
    config: Mapping[str, Any],
) -> dict[str, Any]:
    train_label_end_max = pd.to_datetime(train_dataset["rows"]["label_end_max"]).max()
    eval_label_end_max = pd.to_datetime(eval_dataset["rows"]["label_end_max"]).max()
    return {
        "artifact_purpose": ARTIFACT_PURPOSE,
        "model_name": model_name,
        "feature_set_ver": feature_set_ver,
        "feature_columns": list(feature_columns),
        "feature_count": len(feature_columns),
        "seq_len": int(config["seq_len"]),
        "horizons": [int(horizon) for horizon in config["horizons"]],
        "train_start": str(config["train_start"]),
        "train_cutoff": str(config["train_cutoff"]),
        "train_label_end_max": _date_text(train_label_end_max),
        "validation_start": str(config["validation_start"]),
        "eval_start": str(config["eval_start"]),
        "eval_end": str(config["eval_end"]),
        "eval_label_end_max": _date_text(eval_label_end_max),
        "holdout_start": str(config["holdout_start"]),
        "model_path": model_path,
        "scaler_path": scaler_path,
        "architecture_config": dict(architecture_config),
        "model_ver": model_ver,
        "scaler_type": "StandardScaler",
        "n_tickers": len(ticker_to_id),
        "n_sectors": len(sector_to_id),
        "ticker_to_id": dict(ticker_to_id),
        "sector_to_id": dict(sector_to_id),
        "n_train_samples": int(len(fit_dataset["x"])),
        "n_val_samples": int(len(val_dataset["x"])),
        "n_eval_samples": int(len(eval_dataset["x"])),
        "train_window": _window_text(config["train_start"], config["train_cutoff"]),
        "validation_window": f"{config['validation_start']}..{config['train_cutoff']}",
        "validation_split_method": str(val_dataset.get("split_method", "unknown")),
        "eval_window": _window_text(config["eval_start"], config["eval_end"]),
        "label_policy": "training sample included only when every horizon label_end_date <= train_cutoff",
        "holdout_policy": "2025-01-01 이후 데이터는 학습/평가에서 제외",
    }


def _validate_split_summary(metadata: Mapping[str, Any], config: Mapping[str, Any]) -> None:
    train_label_end_max = _timestamp(metadata["train_label_end_max"], name="train_label_end_max")
    eval_label_end_max = _timestamp(metadata["eval_label_end_max"], name="eval_label_end_max")
    train_cutoff = _timestamp(config["train_cutoff"], name="train_cutoff")
    eval_end = _timestamp(config["eval_end"], name="eval_end")
    holdout_start = _timestamp(config["holdout_start"], name="holdout_start")
    if train_label_end_max > train_cutoff:
        raise ValueError("train_label_end_max가 train_cutoff를 초과했습니다.")
    if eval_label_end_max > eval_end:
        raise ValueError("eval_label_end_max가 eval_end를 초과했습니다.")
    if train_label_end_max >= holdout_start or eval_label_end_max >= holdout_start:
        raise ValueError("2025 이후 label이 학습/평가에 포함되었습니다.")


def train_transformer_oos2024(config: Mapping[str, Any]) -> dict[str, Any]:
    """Transformer OOS2024 artifact를 학습하고 평가 입력을 생성한다."""
    _set_tf_runtime()
    import tensorflow as tf
    from AI.modules.signal.models.transformer.architecture import build_transformer_model

    output_dir = _join_path(str(config["output_root"]), "transformer")
    paths = _artifact_paths(output_dir)
    Path(output_dir).mkdir(parents=True, exist_ok=True)

    frame = load_transformer_feature_frame(config)
    feature_columns = _resolve_transformer_feature_columns(frame, config)
    scaler = _fit_scaler(frame, feature_columns, config)
    ticker_to_id, sector_to_id = _build_id_maps(frame)
    train_dataset = _build_supervised_sequences(
        frame,
        feature_columns=feature_columns,
        scaler=scaler,
        ticker_to_id=ticker_to_id,
        sector_to_id=sector_to_id,
        config=config,
        purpose="train",
    )
    fit_dataset, val_dataset = _split_train_val(train_dataset, config)
    eval_dataset = _build_supervised_sequences(
        frame,
        feature_columns=feature_columns,
        scaler=scaler,
        ticker_to_id=ticker_to_id,
        sector_to_id=sector_to_id,
        config=config,
        purpose="eval",
    )

    model_config = dict(config["transformer"])
    architecture_config = {
        "head_size": int(model_config["head_size"]),
        "num_heads": int(model_config["num_heads"]),
        "ff_dim": int(model_config["ff_dim"]),
        "num_blocks": int(model_config["num_blocks"]),
        "mlp_units": list(model_config["mlp_units"]),
        "dropout": float(model_config["dropout"]),
        "mlp_dropout": float(model_config["mlp_dropout"]),
        "n_tickers": len(ticker_to_id),
        "n_sectors": len(sector_to_id),
    }
    model = build_transformer_model(
        input_shape=(int(config["seq_len"]), len(feature_columns)),
        n_tickers=len(ticker_to_id),
        n_sectors=len(sector_to_id),
        n_outputs=len(config["horizons"]),
        head_size=architecture_config["head_size"],
        num_heads=architecture_config["num_heads"],
        ff_dim=architecture_config["ff_dim"],
        num_transformer_blocks=architecture_config["num_blocks"],
        mlp_units=architecture_config["mlp_units"],
        dropout=architecture_config["dropout"],
        mlp_dropout=architecture_config["mlp_dropout"],
    )
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=float(config["learning_rate"])),
        loss="binary_crossentropy",
        metrics=["accuracy"],
    )
    callbacks = _keras_callbacks(paths["model_path"], config)
    history = model.fit(
        [fit_dataset["x"], fit_dataset["ticker"], fit_dataset["sector"]],
        fit_dataset["y"],
        validation_data=([val_dataset["x"], val_dataset["ticker"], val_dataset["sector"]], val_dataset["y"]),
        epochs=int(config["epochs"]),
        batch_size=int(config["batch_size"]),
        callbacks=callbacks,
        verbose=2,
    )
    model.save(paths["model_path"])
    with open(paths["scaler_path"], "wb") as file:
        pickle.dump(scaler, file)

    metadata = _common_metadata(
        model_name="transformer",
        feature_set_ver=TRANSFORMER_FEATURE_SET_VER,
        model_ver=TRANSFORMER_MODEL_VER,
        feature_columns=feature_columns,
        model_path=paths["model_path"],
        scaler_path=paths["scaler_path"],
        architecture_config=architecture_config,
        ticker_to_id=ticker_to_id,
        sector_to_id=sector_to_id,
        train_dataset=train_dataset,
        fit_dataset=fit_dataset,
        val_dataset=val_dataset,
        eval_dataset=eval_dataset,
        config=config,
    )
    metadata["best_val_loss"] = float(min(history.history.get("val_loss", [np.nan])))
    metadata["best_val_acc"] = float(max(history.history.get("val_accuracy", [0.0])))
    _validate_split_summary(metadata, config)
    _save_json(paths["metadata_path"], metadata)
    return {
        "model": model,
        "metadata": metadata,
        "paths": paths,
        "eval_dataset": eval_dataset,
        "history": {
            "best_val_loss": metadata["best_val_loss"],
            "best_val_acc": metadata["best_val_acc"],
            "epochs_ran": int(len(history.history.get("loss", []))),
        },
    }


def train_itransformer_oos2024(config: Mapping[str, Any]) -> dict[str, Any]:
    """iTransformer OOS2024 artifact를 학습하고 평가 입력을 생성한다."""
    _set_tf_runtime()
    import tensorflow as tf
    from AI.modules.signal.models.itransformer.train_kaggle import build_model

    output_dir = _join_path(str(config["output_root"]), "itransformer")
    paths = _artifact_paths(output_dir)
    Path(output_dir).mkdir(parents=True, exist_ok=True)

    frame = load_itransformer_feature_frame(config)
    model_config = dict(config["itransformer"])
    feature_columns = resolve_itransformer_feature_columns(frame, model_config)
    scaler = _fit_scaler(frame, feature_columns, config)
    ticker_to_id, sector_to_id = _build_id_maps(frame.assign(sector="Unknown"))
    train_dataset = _build_supervised_sequences(
        frame.assign(sector="Unknown"),
        feature_columns=feature_columns,
        scaler=scaler,
        ticker_to_id=ticker_to_id,
        sector_to_id=sector_to_id,
        config=config,
        purpose="train",
    )
    fit_dataset, val_dataset = _split_train_val(train_dataset, config)
    eval_dataset = _build_supervised_sequences(
        frame.assign(sector="Unknown"),
        feature_columns=feature_columns,
        scaler=scaler,
        ticker_to_id=ticker_to_id,
        sector_to_id=sector_to_id,
        config=config,
        purpose="eval",
    )

    active_model_config = {
        **model_config,
        "lookback": int(config["seq_len"]),
        "horizons": list(config["horizons"]),
        "learning_rate": float(config["learning_rate"]),
    }
    architecture_config = {
        "head_size": int(model_config["head_size"]),
        "num_heads": int(model_config["num_heads"]),
        "ff_dim": int(model_config["ff_dim"]),
        "num_blocks": int(model_config["num_blocks"]),
        "mlp_units": list(model_config["mlp_units"]),
        "dropout": float(model_config["dropout"]),
        "mlp_dropout": float(model_config["mlp_dropout"]),
        "n_tickers": len(ticker_to_id),
        "n_sectors": len(sector_to_id),
    }
    model = build_model(
        seq_len=int(config["seq_len"]),
        n_features=len(feature_columns),
        n_outputs=len(config["horizons"]),
        n_tickers=len(ticker_to_id),
        n_sectors=len(sector_to_id),
        config=active_model_config,
    )
    callbacks = _keras_callbacks(paths["model_path"], config)
    history = model.fit(
        [fit_dataset["x"], fit_dataset["ticker"], fit_dataset["sector"]],
        fit_dataset["y"],
        validation_data=([val_dataset["x"], val_dataset["ticker"], val_dataset["sector"]], val_dataset["y"]),
        epochs=int(config["epochs"]),
        batch_size=int(config["batch_size"]),
        callbacks=callbacks,
        verbose=1,
    )
    model.save(paths["model_path"])
    with open(paths["scaler_path"], "wb") as file:
        pickle.dump(scaler, file)

    metadata_base = _common_metadata(
        model_name="itransformer",
        feature_set_ver=ITRANSFORMER_FEATURE_SET_VER,
        model_ver=ITRANSFORMER_MODEL_VER,
        feature_columns=feature_columns,
        model_path=paths["model_path"],
        scaler_path=paths["scaler_path"],
        architecture_config=architecture_config,
        ticker_to_id=ticker_to_id,
        sector_to_id=sector_to_id,
        train_dataset=train_dataset,
        fit_dataset=fit_dataset,
        val_dataset=val_dataset,
        eval_dataset=eval_dataset,
        config=config,
    )
    metadata_base["feature_focus"] = model_config["feature_focus"]
    metadata_base["best_val_loss"] = float(min(history.history.get("val_loss", [np.nan])))
    metadata_base["best_val_acc"] = float(max(history.history.get("val_accuracy", [0.0])))
    metadata = build_itransformer_metadata(
        config={
            **active_model_config,
            **metadata_base,
            "seq_len": int(config["seq_len"]),
            "n_tickers": len(ticker_to_id),
            "n_sectors": len(sector_to_id),
            "model_ver": ITRANSFORMER_MODEL_VER,
        },
        model_path=paths["model_path"],
        scaler_path=paths["scaler_path"],
        feature_columns=feature_columns,
    )
    metadata.update(metadata_base)
    _validate_split_summary(metadata, config)
    save_itransformer_metadata(paths["metadata_path"], metadata)
    return {
        "model": model,
        "metadata": metadata,
        "paths": paths,
        "eval_dataset": eval_dataset,
        "history": {
            "best_val_loss": metadata["best_val_loss"],
            "best_val_acc": metadata["best_val_acc"],
            "epochs_ran": int(len(history.history.get("loss", []))),
        },
    }


def _keras_callbacks(model_path: str, config: Mapping[str, Any]) -> list[Any]:
    import tensorflow as tf

    return [
        tf.keras.callbacks.ModelCheckpoint(
            filepath=model_path,
            monitor="val_loss",
            save_best_only=True,
            verbose=1,
        ),
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=int(config["early_stopping_patience"]),
            restore_best_weights=True,
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=int(config["reduce_lr_patience"]),
            min_lr=1e-6,
            verbose=1,
        ),
    ]


def build_model_signal_frame(
    *,
    model_name: str,
    model: Any,
    train_result: Mapping[str, Any],
    config: Mapping[str, Any],
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """학습된 모델의 2024 OOS signal frame과 realized return frame을 만든다."""
    eval_dataset = train_result["eval_dataset"]
    metadata = train_result["metadata"]
    probs = model.predict(
        [eval_dataset["x"], eval_dataset["ticker"], eval_dataset["sector"]],
        batch_size=max(128, int(config["batch_size"]) * 4),
        verbose=0,
    )
    probs = np.asarray(probs, dtype=np.float32)
    if probs.ndim != 2 or probs.shape[1] != len(config["horizons"]):
        raise ValueError(f"{model_name} 예측 shape가 horizon 계약과 맞지 않습니다: {probs.shape}")
    if not np.isfinite(probs).all():
        raise ValueError(f"{model_name} 예측에 NaN 또는 무한대가 포함되었습니다.")
    probs = np.clip(probs, 0.0, 1.0)

    rows: list[dict[str, Any]] = []
    sample_rows = eval_dataset["rows"].reset_index(drop=True)
    for row_index, sample in sample_rows.iterrows():
        for horizon_index, horizon in enumerate(config["horizons"]):
            prob_up = float(probs[row_index, horizon_index])
            rows.append(
                {
                    "asof_date": sample["asof_date"],
                    "decision_time": sample["asof_date"],
                    "run_id": f"oos2024_{model_name}",
                    "model_ver": metadata["model_ver"],
                    "ticker": str(sample["ticker"]),
                    "model_name": model_name,
                    "horizon": int(horizon),
                    "prob_up": prob_up,
                    "confidence": calculate_confidence(prob_up),
                    "raw_score": prob_up,
                    "signal": calculate_signal(
                        prob_up,
                        buy_threshold=0.6,
                        sell_threshold=0.4,
                        confidence_threshold=0.0,
                    ),
                    "feature_set_ver": metadata["feature_set_ver"],
                    "train_window": metadata["train_window"],
                    "eval_window": metadata["eval_window"],
                    "fold_id": ARTIFACT_PURPOSE,
                    "seq_len": int(metadata["seq_len"]),
                    "scaler_ver": "StandardScaler",
                    "artifact_path": metadata["model_path"],
                    "feature_count": int(metadata["feature_count"]),
                    "prediction_status": "ok",
                    "error_message": "",
                }
            )
    signal_frame = pd.DataFrame(rows)
    validate_signal_frame(signal_frame)
    realized_returns = eval_dataset["realized_returns"].copy()
    return signal_frame, realized_returns


def _copy_frame(frame: pd.DataFrame) -> pd.DataFrame:
    return frame.copy().reset_index(drop=True)


def build_ensemble_mean_signal_frame(
    signal_frame: pd.DataFrame,
    *,
    config: Mapping[str, Any],
) -> pd.DataFrame:
    """Transformer와 iTransformer 평균 ensemble signal을 만든다."""
    required_models = {"transformer", "itransformer"}
    ok_frame = signal_frame[signal_frame["prediction_status"] == "ok"].copy()
    pivot = ok_frame.pivot_table(
        index=["asof_date", "decision_time", "ticker", "horizon"],
        columns="model_name",
        values="prob_up",
        aggfunc="first",
    ).reset_index()
    missing = required_models - set(pivot.columns)
    if missing:
        raise ValueError(f"ensemble_mean 생성에 필요한 모델 signal이 없습니다: {missing}")
    pivot = pivot.dropna(subset=["transformer", "itransformer"]).copy()
    rows: list[dict[str, Any]] = []
    for record in pivot.to_dict("records"):
        prob_up = float((record["transformer"] + record["itransformer"]) / 2.0)
        rows.append(_derived_signal_row(record, "ensemble_mean", prob_up, config))
    frame = pd.DataFrame(rows)
    validate_signal_frame(frame)
    return frame


def build_rule_based_gating_v1_signal_frame(
    signal_frame: pd.DataFrame,
    *,
    config: Mapping[str, Any],
) -> pd.DataFrame:
    """iTransformer risk-off 확률로 Transformer를 누르는 rule-based gating v1."""
    ok_frame = signal_frame[signal_frame["prediction_status"] == "ok"].copy()
    pivot = ok_frame.pivot_table(
        index=["asof_date", "decision_time", "ticker", "horizon"],
        columns="model_name",
        values="prob_up",
        aggfunc="first",
    ).reset_index()
    pivot = pivot.dropna(subset=["transformer", "itransformer"]).copy()
    rows: list[dict[str, Any]] = []
    for record in pivot.to_dict("records"):
        transformer_prob = float(record["transformer"])
        itransformer_prob = float(record["itransformer"])
        itransformer_confidence = calculate_confidence(itransformer_prob)
        if itransformer_prob < 0.5 and itransformer_confidence >= 0.2:
            prob_up = min(transformer_prob, itransformer_prob)
        else:
            prob_up = 0.65 * transformer_prob + 0.35 * itransformer_prob
        rows.append(_derived_signal_row(record, "rule_based_gating_v1", prob_up, config))
    frame = pd.DataFrame(rows)
    validate_signal_frame(frame)
    return frame


def _derived_signal_row(
    record: Mapping[str, Any],
    model_name: str,
    prob_up: float,
    config: Mapping[str, Any],
) -> dict[str, Any]:
    probability = float(np.clip(prob_up, 0.0, 1.0))
    return {
        "asof_date": record["asof_date"],
        "decision_time": record["decision_time"],
        "run_id": f"oos2024_{model_name}",
        "model_ver": f"{model_name}_oos2024_dev_v0",
        "ticker": str(record["ticker"]),
        "model_name": model_name,
        "horizon": int(record["horizon"]),
        "prob_up": probability,
        "confidence": calculate_confidence(probability),
        "raw_score": probability,
        "signal": calculate_signal(
            probability,
            buy_threshold=0.6,
            sell_threshold=0.4,
            confidence_threshold=0.0,
        ),
        "feature_set_ver": "oos2024_combined_v0",
        "train_window": _window_text(config["train_start"], config["train_cutoff"]),
        "eval_window": _window_text(config["eval_start"], config["eval_end"]),
        "fold_id": ARTIFACT_PURPOSE,
        "seq_len": int(config["seq_len"]),
        "scaler_ver": "derived",
        "artifact_path": str(config["output_root"]),
        "feature_count": 0,
        "prediction_status": "ok",
        "error_message": "",
    }


def evaluate_signal_frame(
    *,
    signal_frame: pd.DataFrame,
    realized_returns: pd.DataFrame,
    output_dir: str,
    file_prefix: str,
    leaderboard_run_id: str,
    config: Mapping[str, Any],
    confidence_threshold: float | None = None,
    write_outputs: bool = True,
) -> dict[str, Any]:
    """Signal frame을 평가하고 요구 CSV 이름으로 저장한다."""
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    threshold = (
        float(config["confidence_threshold"])
        if confidence_threshold is None
        else float(confidence_threshold)
    )
    signal = _copy_frame(signal_frame)
    returns = _copy_frame(realized_returns)
    diagnostics_frame = build_signal_diagnostics_frame(
        signal,
        returns,
        buy_threshold=float(config["buy_threshold"]),
        confidence_threshold=threshold,
    )

    backtest_results: list[dict[str, Any]] = []
    for model_name in sorted(signal["model_name"].dropna().astype(str).unique().tolist()):
        for horizon in HORIZONS:
            scoped = signal[(signal["model_name"] == model_name) & (signal["horizon"] == horizon)]
            if scoped.empty:
                continue
            backtest_results.append(
                backtest_top_k_signals(
                    signal,
                    returns,
                    model_name=model_name,
                    horizon=horizon,
                    top_k=int(config["top_k"]),
                    buy_threshold=float(config["buy_threshold"]),
                    confidence_threshold=threshold,
                    weighting="equal",
                    cost_bps_per_side=float(config["cost_bps_per_side"]),
                    include_statuses=("ok",),
                    missing_return_policy=str(config["missing_return_policy"]),
                )
            )
    backtest_results.extend(
        universe_equal_benchmark(returns, horizon=horizon)
        for horizon in HORIZONS
    )
    leaderboard_frame = build_leaderboard(
        backtest_results,
        leaderboard_run_id=leaderboard_run_id,
        prediction_run_id=leaderboard_run_id,
        model_ver=f"{leaderboard_run_id}_v0",
        feature_set_ver="oos2024_eval_v0",
        train_window=_window_text(config["train_start"], config["train_cutoff"]),
        validation_window=_window_text(config["eval_start"], config["eval_end"]),
    )
    metric_frame = build_model_metric_frame(
        signal,
        returns,
        leaderboard_frame=leaderboard_frame,
        backtest_results=backtest_results,
        include_statuses=("ok",),
        top_k=int(config["top_k"]),
        leaderboard_run_id=leaderboard_run_id,
    )
    objective_frame = build_model_objective_frame(
        leaderboard_frame,
        metric_frame=metric_frame,
        missing_model_policy="skip",
    )
    summary = {
        "leaderboard_run_id": leaderboard_run_id,
        "confidence_threshold": threshold,
        "signal_rows": int(len(signal)),
        "realized_return_rows": int(len(returns)),
        "diagnostics_rows": int(len(diagnostics_frame)),
        "metric_rows": int(len(metric_frame)),
        "objective_rows": int(len(objective_frame)),
        "leaderboard_rows": int(len(leaderboard_frame)),
        "top_k": int(config["top_k"]),
        "cost_bps_per_side": float(config["cost_bps_per_side"]),
        "missing_return_policy": str(config["missing_return_policy"]),
    }

    if write_outputs:
        paths = {
            "signal_frame": _join_path(output_dir, f"{file_prefix}_signal_frame.csv"),
            "diagnostics_frame": _join_path(output_dir, f"{file_prefix}_diagnostics_frame.csv"),
            "metric_frame": _join_path(output_dir, f"{file_prefix}_metric_frame.csv"),
            "objective_frame": _join_path(output_dir, f"{file_prefix}_objective_frame.csv"),
            "leaderboard_frame": _join_path(output_dir, f"{file_prefix}_leaderboard_frame.csv"),
            "summary": _join_path(output_dir, f"{file_prefix}_eval_summary.json"),
        }
        signal.to_csv(paths["signal_frame"], index=False)
        diagnostics_frame.to_csv(paths["diagnostics_frame"], index=False)
        metric_frame.to_csv(paths["metric_frame"], index=False)
        objective_frame.to_csv(paths["objective_frame"], index=False)
        leaderboard_frame.to_csv(paths["leaderboard_frame"], index=False)
        _save_json(paths["summary"], summary)
    else:
        paths = {}

    return {
        "signal_frame": signal,
        "diagnostics_frame": diagnostics_frame,
        "metric_frame": metric_frame,
        "objective_frame": objective_frame,
        "leaderboard_frame": leaderboard_frame,
        "backtest_results": backtest_results,
        "summary": summary,
        "paths": paths,
    }


def build_universe_excess_summary(leaderboard_frame: pd.DataFrame) -> pd.DataFrame:
    """모델별 universe_equal 대비 초과 수익 요약을 만든다."""
    frame = leaderboard_frame.copy()
    benchmark_mask = frame["benchmark_name"].astype("string").str.lower().eq("universe_equal")
    benchmarks = frame[benchmark_mask].copy()
    models = frame[~benchmark_mask & frame["model_name"].notna()].copy()
    rows: list[dict[str, Any]] = []
    for record in models.to_dict("records"):
        horizon = int(record["horizon"])
        benchmark = benchmarks[benchmarks["horizon"].astype(int) == horizon].copy()
        universe_return = None
        if not benchmark.empty:
            universe_return = float(pd.to_numeric(benchmark["net_return"], errors="coerce").mean())
        net_return = float(record["net_return"]) if pd.notna(record["net_return"]) else None
        excess = None if net_return is None or universe_return is None else net_return - universe_return
        rows.append(
            {
                "model_name": record["model_name"],
                "horizon": horizon,
                "strategy_name": record["strategy_name"],
                "confidence_threshold": record["confidence_threshold"],
                "net_return": net_return,
                "universe_equal_net_return": universe_return,
                "universe_excess_return": excess,
                "mdd": record.get("mdd"),
                "calmar": record.get("calmar"),
                "selected_periods": record.get("selected_periods"),
                "cash_period_rate": record.get("cash_period_rate"),
            }
        )
    return pd.DataFrame(rows).sort_values(["model_name", "horizon"]).reset_index(drop=True)


def _validate_output_is_oos2024(config: Mapping[str, Any]) -> None:
    output_root = str(config["output_root"]).replace("\\", "/").rstrip("/")
    required_suffix = "/oos2024"
    if not output_root.endswith(required_suffix):
        raise ValueError(f"output_root는 /oos2024로 끝나야 합니다: {output_root}")
    if "AI/data/weights" in output_root:
        raise ValueError("full prod artifact 경로를 덮어쓸 수 없습니다.")


def run_oos2024_kaggle_train_eval(config: Mapping[str, Any] | None = None) -> dict[str, Any]:
    """두 모델 학습, 단독 평가, ensemble/gating 평가를 한 번에 실행한다."""
    active_config = build_oos2024_config(config or {})
    _validate_output_is_oos2024(active_config)
    output_root = str(active_config["output_root"])
    Path(output_root).mkdir(parents=True, exist_ok=True)

    transformer_result = train_transformer_oos2024(active_config)
    itransformer_result = train_itransformer_oos2024(active_config)

    transformer_signal, transformer_returns = build_model_signal_frame(
        model_name="transformer",
        model=transformer_result["model"],
        train_result=transformer_result,
        config=active_config,
    )
    itransformer_signal, itransformer_returns = build_model_signal_frame(
        model_name="itransformer",
        model=itransformer_result["model"],
        train_result=itransformer_result,
        config=active_config,
    )

    transformer_eval = evaluate_signal_frame(
        signal_frame=transformer_signal,
        realized_returns=transformer_returns,
        output_dir=_join_path(output_root, "transformer"),
        file_prefix="transformer",
        leaderboard_run_id="oos2024_transformer",
        config=active_config,
    )
    itransformer_eval = evaluate_signal_frame(
        signal_frame=itransformer_signal,
        realized_returns=itransformer_returns,
        output_dir=_join_path(output_root, "itransformer"),
        file_prefix="itransformer",
        leaderboard_run_id="oos2024_itransformer",
        config=active_config,
    )

    base_combined_signal = pd.concat([transformer_signal, itransformer_signal], ignore_index=True)
    ensemble_signal = build_ensemble_mean_signal_frame(base_combined_signal, config=active_config)
    gating_signal = build_rule_based_gating_v1_signal_frame(base_combined_signal, config=active_config)
    combined_signal = pd.concat(
        [base_combined_signal, ensemble_signal, gating_signal],
        ignore_index=True,
    )
    combined_returns = pd.concat([transformer_returns, itransformer_returns], ignore_index=True)
    combined_returns = combined_returns.drop_duplicates(
        subset=["asof_date", "ticker", "horizon"],
        keep="last",
    ).reset_index(drop=True)
    validate_signal_frame(combined_signal)

    combined_eval = evaluate_signal_frame(
        signal_frame=combined_signal,
        realized_returns=combined_returns,
        output_dir=output_root,
        file_prefix="oos2024_combined",
        leaderboard_run_id="oos2024_combined",
        config=active_config,
    )
    confidence_eval = evaluate_signal_frame(
        signal_frame=combined_signal,
        realized_returns=combined_returns,
        output_dir=output_root,
        file_prefix="oos2024_confidence_0_2",
        leaderboard_run_id="oos2024_confidence_0_2",
        config=active_config,
        confidence_threshold=float(active_config["confidence_threshold_alt"]),
        write_outputs=False,
    )
    confidence_summary = build_universe_excess_summary(confidence_eval["leaderboard_frame"])
    confidence_summary_path = _join_path(output_root, "oos2024_confidence_0_2_summary.csv")
    confidence_summary.to_csv(confidence_summary_path, index=False)

    universe_excess_summary = build_universe_excess_summary(combined_eval["leaderboard_frame"])
    universe_excess_path = _join_path(output_root, "oos2024_universe_excess_summary.csv")
    universe_excess_summary.to_csv(universe_excess_path, index=False)

    combined_signal_path = _join_path(output_root, "oos2024_combined_signal_frame.csv")
    combined_leaderboard_path = _join_path(output_root, "oos2024_combined_leaderboard_frame.csv")
    combined_signal.to_csv(combined_signal_path, index=False)
    combined_eval["leaderboard_frame"].to_csv(combined_leaderboard_path, index=False)

    summary = {
        "artifact_purpose": ARTIFACT_PURPOSE,
        "config": smoke_config_summary(active_config),
        "split_validation": {
            "transformer_train_label_end_max": transformer_result["metadata"]["train_label_end_max"],
            "itransformer_train_label_end_max": itransformer_result["metadata"]["train_label_end_max"],
            "train_label_end_policy_pass": True,
            "eval_start_policy_pass": True,
            "holdout_2025_excluded": True,
            "prod_artifact_overwrite": False,
        },
        "transformer": {
            "paths": transformer_result["paths"],
            "history": transformer_result["history"],
            "eval_summary": transformer_eval["summary"],
        },
        "itransformer": {
            "paths": itransformer_result["paths"],
            "history": itransformer_result["history"],
            "eval_summary": itransformer_eval["summary"],
        },
        "combined_outputs": {
            "combined_signal_frame": combined_signal_path,
            "combined_leaderboard_frame": combined_leaderboard_path,
            "confidence_0_2_summary": confidence_summary_path,
            "universe_excess_summary": universe_excess_path,
        },
    }
    summary_path = _join_path(output_root, "oos2024_kaggle_train_summary.json")
    _save_json(summary_path, summary)
    summary["summary_path"] = summary_path
    return _jsonable(summary)


if __name__ == "__main__":
    result = run_oos2024_kaggle_train_eval()
    print(json.dumps(result, ensure_ascii=False, indent=2))
