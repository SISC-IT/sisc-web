"""PatchTST feature/metadata 계약.

학습, 추론, 평가가 같은 feature 순서와 metadata 필드를 쓰도록 한곳에 모은다.
"""

from __future__ import annotations

import json
import os
from collections.abc import Mapping
from typing import Any

import pandas as pd


PATCHTST_MODEL_NAME = "patchtst"
PATCHTST_FEATURE_SET_VER = "patchtst_technical_mtf_v1"
PATCHTST_METADATA_SCHEMA_VER = "patchtst_metadata_v1"
PATCHTST_METADATA_NAME = "metadata.json"
PATCHTST_DEFAULT_HORIZONS = [1, 3, 5, 7]

PATCHTST_FEATURE_COLUMNS = [
    "log_return",
    "ma5_ratio",
    "ma20_ratio",
    "ma60_ratio",
    "rsi",
    "bb_position",
    "macd_ratio",
    "open_ratio",
    "high_ratio",
    "low_ratio",
    "vol_change",
    "week_ma20_ratio",
    "week_rsi",
    "week_bb_pos",
    "week_vol_change",
    "month_ma12_ratio",
    "month_rsi",
]


def get_patchtst_feature_columns() -> list[str]:
    """PatchTST technical multi-timeframe v1 feature 순서를 반환한다."""
    return list(PATCHTST_FEATURE_COLUMNS)


def validate_patchtst_feature_columns(feature_columns: list[str]) -> None:
    """feature 목록이 현재 PatchTST 계약과 정확히 일치하는지 검증한다."""
    expected = get_patchtst_feature_columns()
    if list(feature_columns) != expected:
        raise ValueError(
            "PatchTST feature_columns가 계약과 일치하지 않습니다. "
            f"expected={expected}, actual={list(feature_columns)}"
        )


def _coerce_positive_int(value: Any, field_name: str) -> int:
    if isinstance(value, bool):
        raise ValueError(f"PatchTST metadata {field_name}는 양의 정수여야 합니다.")
    try:
        numeric = float(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"PatchTST metadata {field_name}는 양의 정수여야 합니다.") from exc
    if not numeric.is_integer() or numeric <= 0:
        raise ValueError(f"PatchTST metadata {field_name}는 양의 정수여야 합니다.")
    return int(numeric)


def validate_patchtst_model_shape_contract(
    *,
    seq_len: Any,
    patch_len: Any,
    stride: Any,
    horizons: list[Any],
) -> dict[str, Any]:
    """PatchTST v0 model shape metadata가 학습/추론 계약과 맞는지 검증한다."""
    resolved_seq_len = _coerce_positive_int(seq_len, "seq_len")
    resolved_patch_len = _coerce_positive_int(patch_len, "patch_len")
    resolved_stride = _coerce_positive_int(stride, "stride")
    if resolved_patch_len > resolved_seq_len:
        raise ValueError("PatchTST metadata patch_len은 seq_len보다 클 수 없습니다.")
    if resolved_stride > resolved_seq_len:
        raise ValueError("PatchTST metadata stride는 seq_len보다 클 수 없습니다.")

    resolved_horizons = [_coerce_positive_int(horizon, "horizons") for horizon in horizons]
    if resolved_horizons != PATCHTST_DEFAULT_HORIZONS:
        raise ValueError(
            "PatchTST v0 horizons는 [1, 3, 5, 7]이어야 합니다. "
            f"actual={resolved_horizons}"
        )
    return {
        "seq_len": resolved_seq_len,
        "patch_len": resolved_patch_len,
        "stride": resolved_stride,
        "horizons": resolved_horizons,
    }


def require_patchtst_feature_columns(
    frame: pd.DataFrame,
    *,
    feature_columns: list[str] | None = None,
) -> list[str]:
    """입력 frame에 필요한 feature가 모두 있는지 확인하고 feature 순서를 반환한다."""
    resolved_columns = list(feature_columns or PATCHTST_FEATURE_COLUMNS)
    missing = [column for column in resolved_columns if column not in frame.columns]
    if missing:
        raise ValueError(f"PatchTST 필수 피처가 누락되었습니다: {missing}")
    return resolved_columns


def resolve_patchtst_metadata_path(
    *,
    model_path: str | None = None,
    scaler_path: str | None = None,
    metadata_path: str | None = None,
    metadata_name: str = PATCHTST_METADATA_NAME,
) -> str:
    """명시 경로가 없으면 model/scaler와 같은 디렉터리의 metadata.json을 사용한다."""
    if metadata_path:
        return os.path.abspath(metadata_path)

    base_path = model_path or scaler_path
    if not base_path:
        raise ValueError("metadata_path를 추론하려면 model_path 또는 scaler_path가 필요합니다.")
    return os.path.abspath(os.path.join(os.path.dirname(base_path), metadata_name))


def build_patchtst_metadata(
    *,
    config: Mapping[str, Any],
    model_path: str,
    scaler_path: str,
    feature_columns: list[str] | None = None,
) -> dict[str, Any]:
    """학습 artifact와 함께 저장할 PatchTST metadata를 만든다."""
    resolved_features = list(feature_columns or PATCHTST_FEATURE_COLUMNS)
    validate_patchtst_feature_columns(resolved_features)

    shape_contract = validate_patchtst_model_shape_contract(
        seq_len=config.get("seq_len", 120),
        patch_len=config.get("patch_len", 16),
        stride=config.get("stride", 8),
        horizons=list(config.get("horizons") or PATCHTST_DEFAULT_HORIZONS),
    )
    metadata = {
        "metadata_schema_ver": PATCHTST_METADATA_SCHEMA_VER,
        "model_name": PATCHTST_MODEL_NAME,
        "feature_set_ver": str(config.get("feature_set_ver", PATCHTST_FEATURE_SET_VER)),
        "feature_columns": resolved_features,
        "feature_count": len(resolved_features),
        "seq_len": shape_contract["seq_len"],
        "patch_len": shape_contract["patch_len"],
        "stride": shape_contract["stride"],
        "horizons": shape_contract["horizons"],
        "scaler_path": os.path.abspath(scaler_path),
        "model_path": os.path.abspath(model_path),
    }
    return metadata


def validate_patchtst_metadata(metadata: Mapping[str, Any]) -> None:
    """metadata sidecar의 최소 계약을 검증한다."""
    required = [
        "feature_set_ver",
        "feature_columns",
        "feature_count",
        "seq_len",
        "patch_len",
        "stride",
        "horizons",
        "scaler_path",
        "model_path",
    ]
    missing = [key for key in required if key not in metadata]
    if missing:
        raise ValueError(f"PatchTST metadata 필수 필드가 누락되었습니다: {missing}")

    feature_columns = list(metadata["feature_columns"])
    feature_count = int(metadata["feature_count"])
    if len(feature_columns) != feature_count:
        raise ValueError(
            "PatchTST metadata feature_count가 feature_columns 길이와 다릅니다. "
            f"feature_count={feature_count}, columns={len(feature_columns)}"
        )
    validate_patchtst_feature_columns(feature_columns)
    validate_patchtst_model_shape_contract(
        seq_len=metadata["seq_len"],
        patch_len=metadata["patch_len"],
        stride=metadata["stride"],
        horizons=list(metadata["horizons"]),
    )


def save_patchtst_metadata(metadata_path: str, metadata: Mapping[str, Any]) -> None:
    """PatchTST metadata sidecar를 JSON으로 저장한다."""
    validate_patchtst_metadata(metadata)
    os.makedirs(os.path.dirname(os.path.abspath(metadata_path)), exist_ok=True)
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(dict(metadata), f, ensure_ascii=False, indent=2)


def load_patchtst_metadata(metadata_path: str | None) -> dict[str, Any] | None:
    """metadata가 있으면 읽고, 없으면 legacy artifact로 판단할 수 있게 None을 반환한다."""
    if not metadata_path or not os.path.exists(metadata_path):
        return None
    with open(metadata_path, "r", encoding="utf-8") as f:
        metadata = json.load(f)
    validate_patchtst_metadata(metadata)
    return metadata
