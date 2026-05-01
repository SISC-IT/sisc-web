"""iTransformer feature/metadata 계약.

학습, Kaggle 학습, 추론, 평가가 같은 피처 순서와 metadata 필드를 공유하도록
최소 계약을 한 곳에 모은다.
"""

from __future__ import annotations

import json
import os
import posixpath
from collections.abc import Mapping
from typing import Any

import pandas as pd


ITRANSFORMER_MODEL_NAME = "itransformer"
ITRANSFORMER_FEATURE_SET_VER = "itransformer_regime_corr_v1"
ITRANSFORMER_METADATA_SCHEMA_VER = "itransformer_metadata_v1"
ITRANSFORMER_METADATA_NAME = "metadata.json"
ITRANSFORMER_DEFAULT_HORIZONS = [1, 3, 5, 7]

ITRANSFORMER_FEATURE_ALIASES = {
    "mkt_breadth_ma200": "ma200_pct",
    "mkt_breadth_nh_nl": "nh_nl_index",
}

ITRANSFORMER_DEFAULT_FEATURES = [
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

ITRANSFORMER_OPTIONAL_CONTEXT_FEATURES = [
    "btc_close",
    "eth_close",
]

ITRANSFORMER_DYNAMIC_FEATURE_PREFIXES = ("sector_return_",)


def get_itransformer_default_features() -> list[str]:
    """iTransformer regime/correlation v1 기본 피처 순서를 반환한다."""
    return list(ITRANSFORMER_DEFAULT_FEATURES)


def canonicalize_itransformer_feature_name(name: str) -> str:
    """과거 alias 이름을 현재 표준 피처명으로 변환한다."""
    return ITRANSFORMER_FEATURE_ALIASES.get(str(name), str(name))


def normalize_itransformer_feature_aliases(frame: pd.DataFrame) -> pd.DataFrame:
    """alias 컬럼만 있고 표준 컬럼이 없으면 표준 컬럼을 복사해 만든다."""
    normalized = frame.copy()
    for alias, canonical in ITRANSFORMER_FEATURE_ALIASES.items():
        if alias in normalized.columns and canonical not in normalized.columns:
            normalized[canonical] = normalized[alias]
    return normalized


def _dedupe_columns(columns: list[str]) -> list[str]:
    return list(dict.fromkeys(str(column) for column in columns))


def _normalize_artifact_path(path: str) -> str:
    """Kaggle 절대 경로는 로컬 OS와 무관하게 POSIX 표기로 보존한다."""
    raw_path = str(path)
    if raw_path.startswith("/kaggle/"):
        return posixpath.normpath(raw_path)
    return os.path.abspath(raw_path)


def resolve_itransformer_feature_columns(
    frame: pd.DataFrame,
    config: Mapping[str, Any] | None = None,
) -> list[str]:
    """학습용 frame에서 사용할 iTransformer feature 순서를 결정한다.

    명시 feature_columns가 있으면 모든 컬럼이 실제로 존재해야 한다. 명시 목록이
    없으면 기본 후보 중 존재하는 컬럼을 고르고, optional/dynamic context 컬럼은
    실제로 있을 때만 뒤에 붙인다.
    """
    config = dict(config or {})
    normalized = normalize_itransformer_feature_aliases(frame)
    explicit_features = config.get("feature_columns") or config.get("feature_names")

    if explicit_features:
        feature_columns = _dedupe_columns(
            [canonicalize_itransformer_feature_name(column) for column in explicit_features]
        )
        missing = [column for column in feature_columns if column not in normalized.columns]
        if missing:
            raise ValueError(f"iTransformer 필수 피처가 누락되었습니다: {missing}")
        return feature_columns

    raw_candidates = config.get("feature_candidates") or ITRANSFORMER_DEFAULT_FEATURES
    candidate_features = _dedupe_columns(
        [canonicalize_itransformer_feature_name(column) for column in raw_candidates]
    )
    selected = [column for column in candidate_features if column in normalized.columns]

    selected.extend(
        column
        for column in ITRANSFORMER_OPTIONAL_CONTEXT_FEATURES
        if column in normalized.columns and column not in selected
    )
    selected.extend(
        column
        for column in sorted(normalized.columns)
        if any(column.startswith(prefix) for prefix in ITRANSFORMER_DYNAMIC_FEATURE_PREFIXES)
        and column not in selected
    )

    min_feature_count = int(config.get("min_feature_count", 1))
    if len(selected) < min_feature_count:
        raise ValueError(
            "iTransformer 평가/학습에 사용할 regime/correlation 피처가 부족합니다. "
            f"selected={selected}, min_feature_count={min_feature_count}"
        )
    return selected


def require_itransformer_feature_columns(
    frame: pd.DataFrame,
    *,
    feature_columns: list[str],
) -> list[str]:
    """추론 frame에 필요한 피처가 모두 있는지 확인하고 같은 순서를 반환한다."""
    resolved_columns = _dedupe_columns(
        [canonicalize_itransformer_feature_name(column) for column in feature_columns]
    )
    missing = [column for column in resolved_columns if column not in frame.columns]
    if missing:
        raise ValueError(f"iTransformer 필수 피처가 누락되었습니다: {missing}")
    return resolved_columns


def resolve_itransformer_metadata_path(
    *,
    model_path: str | None = None,
    scaler_path: str | None = None,
    metadata_path: str | None = None,
    metadata_name: str = ITRANSFORMER_METADATA_NAME,
) -> str:
    """명시 경로가 없으면 model/scaler와 같은 디렉터리의 metadata.json을 사용한다."""
    if metadata_path:
        return _normalize_artifact_path(metadata_path)

    base_path = model_path or scaler_path
    if not base_path:
        raise ValueError("metadata_path를 추론하려면 model_path 또는 scaler_path가 필요합니다.")
    if str(base_path).startswith("/kaggle/"):
        return posixpath.join(posixpath.dirname(str(base_path)), metadata_name)
    return os.path.abspath(os.path.join(os.path.dirname(base_path), metadata_name))


def build_itransformer_metadata(
    *,
    config: Mapping[str, Any],
    model_path: str,
    scaler_path: str,
    feature_columns: list[str],
) -> dict[str, Any]:
    """학습 artifact와 함께 저장할 iTransformer metadata를 만든다."""
    resolved_features = _dedupe_columns(
        [canonicalize_itransformer_feature_name(column) for column in feature_columns]
    )
    horizons = [int(horizon) for horizon in config.get("horizons", ITRANSFORMER_DEFAULT_HORIZONS)]

    metadata = {
        "metadata_schema_ver": ITRANSFORMER_METADATA_SCHEMA_VER,
        "model_name": ITRANSFORMER_MODEL_NAME,
        "feature_set_ver": str(config.get("feature_set_ver", ITRANSFORMER_FEATURE_SET_VER)),
        "feature_columns": resolved_features,
        "feature_names": resolved_features,
        "feature_count": len(resolved_features),
        "seq_len": int(config.get("seq_len", config.get("lookback", 60))),
        "horizons": horizons,
        "scaler_path": _normalize_artifact_path(scaler_path),
        "model_path": _normalize_artifact_path(model_path),
        "head_size": int(config.get("head_size", 128)),
        "num_heads": int(config.get("num_heads", 4)),
        "ff_dim": int(config.get("ff_dim", 256)),
        "num_blocks": int(config.get("num_blocks", config.get("num_transformer_blocks", 4))),
        "mlp_units": list(config.get("mlp_units", [128, 64])),
        "dropout": float(config.get("dropout", 0.2)),
        "mlp_dropout": float(config.get("mlp_dropout", 0.2)),
        "n_tickers": int(config.get("n_tickers", 1000)),
        "n_sectors": int(config.get("n_sectors", 50)),
        "feature_focus": str(config.get("feature_focus", "macro_correlation")),
    }
    metadata["architecture_config"] = {
        "head_size": metadata["head_size"],
        "num_heads": metadata["num_heads"],
        "ff_dim": metadata["ff_dim"],
        "num_blocks": metadata["num_blocks"],
        "mlp_units": metadata["mlp_units"],
        "dropout": metadata["dropout"],
        "mlp_dropout": metadata["mlp_dropout"],
        "n_tickers": metadata["n_tickers"],
        "n_sectors": metadata["n_sectors"],
    }

    optional_keys = [
        "ticker_to_id",
        "sector_to_id",
        "ticker_to_sector_id",
        "scaler_type",
        "val_start_date",
        "signal_name",
        "signal_horizon_weights",
        "train_samples",
        "val_samples",
        "best_val_loss",
        "best_val_acc",
        "n_train_samples",
        "n_val_samples",
        "train_start_date",
        "train_end_date",
        "train_window",
        "validation_window",
        "eval_start_date",
        "eval_end_date",
        "label_cutoff_date",
        "holdout_start_date",
        "model_ver",
    ]
    for key in optional_keys:
        if key in config:
            metadata[key] = config[key]
    return metadata


def validate_itransformer_metadata(metadata: Mapping[str, Any]) -> None:
    """metadata sidecar의 최소 계약을 검증한다."""
    required = [
        "model_name",
        "feature_set_ver",
        "feature_columns",
        "feature_count",
        "seq_len",
        "horizons",
        "scaler_path",
        "model_path",
        "architecture_config",
    ]
    missing = [key for key in required if key not in metadata]
    if missing:
        raise ValueError(f"iTransformer metadata 필수 필드가 누락되었습니다: {missing}")

    feature_columns = list(metadata["feature_columns"])
    feature_count = int(metadata["feature_count"])
    if len(feature_columns) != feature_count:
        raise ValueError(
            "iTransformer metadata feature_count가 feature_columns 길이와 다릅니다. "
            f"feature_count={feature_count}, columns={len(feature_columns)}"
        )
    if int(metadata["seq_len"]) <= 0:
        raise ValueError("iTransformer metadata seq_len은 양수여야 합니다.")

    horizons = [int(horizon) for horizon in metadata["horizons"]]
    if horizons != ITRANSFORMER_DEFAULT_HORIZONS:
        raise ValueError(
            "iTransformer v1 horizons는 [1, 3, 5, 7]이어야 합니다. "
            f"actual={horizons}"
        )
    if str(metadata["model_name"]).lower() != ITRANSFORMER_MODEL_NAME:
        raise ValueError(
            "iTransformer metadata model_name이 올바르지 않습니다. "
            f"actual={metadata['model_name']}"
        )


def save_itransformer_metadata(metadata_path: str, metadata: Mapping[str, Any]) -> None:
    """iTransformer metadata sidecar를 JSON으로 저장한다."""
    validate_itransformer_metadata(metadata)
    os.makedirs(os.path.dirname(os.path.abspath(metadata_path)), exist_ok=True)
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(dict(metadata), f, ensure_ascii=False, indent=2)


def load_itransformer_metadata(metadata_path: str | None) -> dict[str, Any] | None:
    """metadata가 있으면 검증 후 반환하고, 없으면 legacy로 볼 수 있게 None을 반환한다."""
    if not metadata_path or not os.path.exists(metadata_path):
        return None
    with open(metadata_path, "r", encoding="utf-8") as f:
        metadata = json.load(f)
    validate_itransformer_metadata(metadata)
    return metadata
