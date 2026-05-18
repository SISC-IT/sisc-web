"""공통 Signal Schema v0 정규화 도구.

각 모델 wrapper가 반환하는 horizon별 확률 딕셔너리를 row 기반 DataFrame으로 바꾼다.
평가/백테스트 코어의 첫 단계이므로 모델 내부 로직은 건드리지 않는다.
"""

from __future__ import annotations

import math
import re
from collections.abc import Mapping
from typing import Any

import numpy as np
import pandas as pd


SIGNAL_SCHEMA_V0_REQUIRED_COLUMNS = [
    "asof_date",
    "decision_time",
    "run_id",
    "model_ver",
    "ticker",
    "model_name",
    "horizon",
    "prob_up",
    "confidence",
    "raw_score",
    "signal",
    "feature_set_ver",
    "train_window",
    "eval_window",
]

SIGNAL_SCHEMA_V0_OPTIONAL_COLUMNS = [
    "fold_id",
    "seq_len",
    "scaler_ver",
    "artifact_path",
    "feature_count",
    "prediction_status",
    "error_message",
]

SIGNAL_SCHEMA_V0_COLUMNS = [
    *SIGNAL_SCHEMA_V0_REQUIRED_COLUMNS,
    *SIGNAL_SCHEMA_V0_OPTIONAL_COLUMNS,
]

VALID_SIGNALS = {"buy", "hold", "sell"}
VALID_PREDICTION_STATUSES = {"ok", "fallback", "error"}

_PREDICTION_KEY_PATTERN = re.compile(r"^(.+)_([0-9]+)d$")


def parse_prediction_key(key: str) -> tuple[str, int]:
    """`tcn_1d` 같은 예측 키에서 모델명과 horizon을 분리한다."""
    if not isinstance(key, str):
        raise ValueError(f"예측 키는 문자열이어야 합니다: {key!r}")

    normalized_key = key.strip()
    match = _PREDICTION_KEY_PATTERN.fullmatch(normalized_key)
    if not match:
        raise ValueError(
            "예측 키는 '<model_name>_<horizon>d' 형식이어야 합니다: "
            f"{key!r}"
        )

    model_prefix = match.group(1)
    horizon = int(match.group(2))
    if not model_prefix:
        raise ValueError(f"예측 키의 모델명이 비어 있습니다: {key!r}")
    if horizon <= 0:
        raise ValueError(f"horizon은 양수여야 합니다: {key!r}")

    return model_prefix, horizon


def _coerce_probability(prob_up: float) -> float:
    """확률값을 엄격하게 검증해 float로 변환한다."""
    try:
        value = float(prob_up)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"prob_up을 float로 변환할 수 없습니다: {prob_up!r}") from exc

    # v0에서는 잘못된 확률을 row error로 숨기지 않고 즉시 실패시킨다.
    # 평가 데이터의 오염을 빠르게 발견하는 쪽이 백테스트 안전성에 더 중요하기 때문이다.
    if not math.isfinite(value):
        raise ValueError(f"prob_up은 NaN 또는 무한대일 수 없습니다: {prob_up!r}")
    if not 0.0 <= value <= 1.0:
        raise ValueError(f"prob_up은 0 이상 1 이하여야 합니다: {prob_up!r}")

    return value


def _validate_thresholds(
    *,
    buy_threshold: float,
    sell_threshold: float,
    confidence_threshold: float,
) -> tuple[float, float, float]:
    buy = float(buy_threshold)
    sell = float(sell_threshold)
    confidence = float(confidence_threshold)

    if not (0.0 <= sell < buy <= 1.0):
        raise ValueError(
            "threshold는 0 <= sell_threshold < buy_threshold <= 1 조건을 "
            "만족해야 합니다."
        )
    if not 0.0 <= confidence <= 1.0:
        raise ValueError("confidence_threshold는 0 이상 1 이하여야 합니다.")

    return buy, sell, confidence


def _validate_prediction_status(status: str) -> str:
    """prediction_status가 schema v0 허용값인지 검증한다."""
    if not isinstance(status, str):
        raise ValueError(f"prediction_status는 문자열이어야 합니다: {status!r}")

    normalized_status = status.strip().lower()
    if normalized_status not in VALID_PREDICTION_STATUSES:
        raise ValueError(
            "prediction_status 값은 ok, fallback, error 중 하나여야 합니다: "
            f"{status!r}"
        )
    return normalized_status


def _resolve_status_for_key(
    key: str,
    *,
    default_status: str,
    prediction_status_map: Mapping[str, str] | None,
) -> str:
    """기본 상태값 위에 key별 상태값을 적용한다."""
    if prediction_status_map is not None and key in prediction_status_map:
        return _validate_prediction_status(prediction_status_map[key])
    return _validate_prediction_status(default_status)


def _resolve_error_message_for_key(
    key: str,
    *,
    default_message: str,
    error_message_map: Mapping[str, str] | None,
) -> str:
    """기본 오류 메시지 위에 key별 오류 메시지를 적용한다."""
    if error_message_map is not None and key in error_message_map:
        return str(error_message_map[key])
    return str(default_message)


def calculate_confidence(prob_up: float) -> float:
    """상승 확률을 0~1 범위의 확신도로 변환한다."""
    probability = _coerce_probability(prob_up)
    return abs(probability - 0.5) * 2.0


def calculate_signal(
    prob_up: float,
    *,
    buy_threshold: float = 0.6,
    sell_threshold: float = 0.4,
    confidence_threshold: float = 0.0,
) -> str:
    """확률과 threshold를 기준으로 buy, hold, sell 중 하나를 반환한다."""
    buy, sell, required_confidence = _validate_thresholds(
        buy_threshold=buy_threshold,
        sell_threshold=sell_threshold,
        confidence_threshold=confidence_threshold,
    )
    probability = _coerce_probability(prob_up)
    confidence = calculate_confidence(probability)

    if probability >= buy and confidence >= required_confidence:
        return "buy"
    if probability <= sell and confidence >= required_confidence:
        return "sell"
    return "hold"


def normalize_signal_output(
    output: dict,
    *,
    asof_date: Any,
    ticker: str,
    model_name: str | None = None,
    decision_time: Any = None,
    run_id: str = "manual",
    model_ver: str = "unknown",
    feature_set_ver: str = "unknown",
    train_window: str = "unknown",
    eval_window: str = "unknown",
    fold_id: str | None = None,
    seq_len: int | None = None,
    scaler_ver: str = "unknown",
    artifact_path: str = "",
    feature_count: int | None = None,
    buy_threshold: float = 0.6,
    sell_threshold: float = 0.4,
    confidence_threshold: float = 0.0,
    prediction_status: str = "ok",
    prediction_status_map: dict[str, str] | None = None,
    error_message: str = "",
    error_message_map: dict[str, str] | None = None,
) -> pd.DataFrame:
    """wrapper 출력 딕셔너리를 Signal Schema v0 DataFrame으로 정규화한다."""
    if not isinstance(output, Mapping):
        raise TypeError("output은 dict 또는 Mapping이어야 합니다.")
    if not output:
        raise ValueError("output은 최소 1개 이상의 예측 값을 포함해야 합니다.")

    _validate_thresholds(
        buy_threshold=buy_threshold,
        sell_threshold=sell_threshold,
        confidence_threshold=confidence_threshold,
    )

    explicit_model_name = model_name.strip() if isinstance(model_name, str) else model_name
    if explicit_model_name == "":
        raise ValueError("model_name이 주어졌다면 빈 문자열일 수 없습니다.")

    if decision_time is None:
        raise ValueError("decision_time은 명시적으로 전달해야 한다.")
    resolved_decision_time = (
        pd.Timestamp(decision_time) if isinstance(decision_time, str) else decision_time
    )

    _validate_prediction_status(prediction_status)
    if prediction_status_map is not None and not isinstance(prediction_status_map, Mapping):
        raise TypeError("prediction_status_map은 dict 또는 Mapping이어야 합니다.")
    if prediction_status_map is not None:
        for mapped_status in prediction_status_map.values():
            _validate_prediction_status(mapped_status)
    if error_message_map is not None and not isinstance(error_message_map, Mapping):
        raise TypeError("error_message_map은 dict 또는 Mapping이어야 합니다.")

    rows: list[dict[str, Any]] = []

    for key, raw_value in output.items():
        parsed_model_name, horizon = parse_prediction_key(key)
        if explicit_model_name is not None:
            if parsed_model_name.lower() != explicit_model_name.lower():
                raise ValueError(
                    "명시 model_name과 예측 키의 모델 prefix가 다릅니다: "
                    f"model_name={explicit_model_name!r}, key={key!r}"
                )
            resolved_model_name = explicit_model_name
        else:
            resolved_model_name = parsed_model_name

        probability = _coerce_probability(raw_value)
        confidence = calculate_confidence(probability)
        row_status = _resolve_status_for_key(
            key,
            default_status=prediction_status,
            prediction_status_map=prediction_status_map,
        )
        row_error_message = _resolve_error_message_for_key(
            key,
            default_message=error_message,
            error_message_map=error_message_map,
        )
        if row_status == "error" and not row_error_message.strip():
            raise ValueError(
                "prediction_status='error'인 row는 error_message가 필요합니다: "
                f"{key!r}"
            )

        rows.append(
            {
                "asof_date": asof_date,
                "decision_time": resolved_decision_time,
                "run_id": run_id,
                "model_ver": model_ver,
                "ticker": ticker,
                "model_name": resolved_model_name,
                "horizon": horizon,
                "prob_up": probability,
                "confidence": confidence,
                "raw_score": probability,
                "signal": calculate_signal(
                    probability,
                    buy_threshold=buy_threshold,
                    sell_threshold=sell_threshold,
                    confidence_threshold=confidence_threshold,
                ),
                "feature_set_ver": feature_set_ver,
                "train_window": train_window,
                "eval_window": eval_window,
                "fold_id": fold_id,
                "seq_len": seq_len,
                "scaler_ver": scaler_ver,
                "artifact_path": artifact_path,
                "feature_count": feature_count,
                "prediction_status": row_status,
                "error_message": row_error_message,
            }
        )

    frame = pd.DataFrame(rows, columns=SIGNAL_SCHEMA_V0_COLUMNS)
    validate_signal_frame(frame)
    return frame


def validate_signal_frame(frame: pd.DataFrame) -> None:
    """Signal Schema v0 DataFrame의 최소 계약을 검증한다."""
    if not isinstance(frame, pd.DataFrame):
        raise TypeError("frame은 pandas.DataFrame이어야 합니다.")

    missing_columns = [
        column for column in SIGNAL_SCHEMA_V0_REQUIRED_COLUMNS if column not in frame.columns
    ]
    if missing_columns:
        raise ValueError(f"필수 컬럼이 없습니다: {missing_columns}")

    if frame.empty:
        return

    null_required_columns = [
        column for column in SIGNAL_SCHEMA_V0_REQUIRED_COLUMNS if frame[column].isna().any()
    ]
    if null_required_columns:
        raise ValueError(f"필수 컬럼에 결측값이 있습니다: {null_required_columns}")

    if frame["decision_time"].isna().any():
        raise ValueError("decision_time은 결측 없이 명시적으로 전달해야 합니다.")

    blank_text_columns = [
        column
        for column in [
            "run_id",
            "model_ver",
            "ticker",
            "model_name",
            "feature_set_ver",
            "train_window",
            "eval_window",
        ]
        if frame[column].astype(str).str.strip().eq("").any()
    ]
    if blank_text_columns:
        raise ValueError(f"필수 문자열 컬럼에 빈 값이 있습니다: {blank_text_columns}")

    prob_up = pd.to_numeric(frame["prob_up"], errors="coerce")
    if prob_up.isna().any() or not prob_up.between(0.0, 1.0, inclusive="both").all():
        raise ValueError("prob_up은 모든 row에서 0 이상 1 이하의 숫자여야 합니다.")

    confidence = pd.to_numeric(frame["confidence"], errors="coerce")
    if confidence.isna().any() or not confidence.between(0.0, 1.0, inclusive="both").all():
        raise ValueError("confidence는 모든 row에서 0 이상 1 이하의 숫자여야 합니다.")

    invalid_horizons = [
        value
        for value in frame["horizon"].tolist()
        if isinstance(value, bool) or not isinstance(value, (int, np.integer)) or value <= 0
    ]
    if invalid_horizons:
        raise ValueError(f"horizon은 양수 int여야 합니다: {invalid_horizons}")

    duplicate_key_columns = ["run_id", "asof_date", "ticker", "model_name", "horizon"]
    duplicated_rows = frame.duplicated(subset=duplicate_key_columns, keep=False)
    if duplicated_rows.any():
        duplicate_keys = (
            frame.loc[duplicated_rows, duplicate_key_columns]
            .drop_duplicates()
            .to_dict("records")
        )
        raise ValueError(
            "Signal Schema v0 row 단위가 중복되었습니다: "
            f"{duplicate_keys}"
        )

    invalid_signals = set(frame.loc[~frame["signal"].isin(VALID_SIGNALS), "signal"])
    if invalid_signals:
        raise ValueError(f"signal 값은 buy, hold, sell 중 하나여야 합니다: {invalid_signals}")

    if "prediction_status" in frame.columns:
        invalid_statuses = set(
            frame.loc[
                ~frame["prediction_status"].isin(VALID_PREDICTION_STATUSES),
                "prediction_status",
            ]
        )
        if invalid_statuses:
            raise ValueError(
                "prediction_status 값은 ok, fallback, error 중 하나여야 합니다: "
                f"{invalid_statuses}"
            )

        error_rows = frame["prediction_status"].eq("error")
        if error_rows.any():
            if "error_message" not in frame.columns:
                raise ValueError("error row를 검증하려면 error_message 컬럼이 필요합니다.")
            missing_error_messages = frame.loc[
                error_rows, "error_message"
            ].astype(str).str.strip().eq("")
            if missing_error_messages.any():
                raise ValueError(
                    "prediction_status='error'인 row는 error_message가 필요합니다."
                )
