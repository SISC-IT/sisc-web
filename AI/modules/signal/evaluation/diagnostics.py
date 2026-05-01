"""Signal/label 분포 진단표 생성 유틸리티."""

from __future__ import annotations

from typing import Any

import numpy as np
import pandas as pd

from AI.modules.signal.evaluation.schema import VALID_PREDICTION_STATUSES


DIAGNOSTIC_COLUMNS = [
    "model_name",
    "horizon",
    "row_count",
    "prediction_status_ok_count",
    "prediction_status_fallback_count",
    "prediction_status_error_count",
    "fallback_rate",
    "error_rate",
    "prob_up_mean",
    "prob_up_std",
    "prob_up_min",
    "prob_up_max",
    "prob_up_q05",
    "prob_up_q25",
    "prob_up_q50",
    "prob_up_q75",
    "prob_up_q95",
    "near_half_rate",
    "high_confidence_coverage",
    "buy_candidate_count",
    "sell_candidate_count",
    "label_positive_rate",
    "missing_return_count",
    "missing_return_rate",
    "diagnostic_status",
    "diagnostic_reasons",
]

SIGNAL_DIAGNOSTIC_REQUIRED_COLUMNS = [
    "asof_date",
    "ticker",
    "model_name",
    "horizon",
    "prob_up",
]

RETURNS_DIAGNOSTIC_REQUIRED_COLUMNS = [
    "asof_date",
    "ticker",
    "horizon",
    "forward_return",
]


def build_signal_diagnostics_frame(
    signal_frame: pd.DataFrame,
    realized_returns: pd.DataFrame,
    *,
    buy_threshold: float = 0.6,
    sell_threshold: float = 0.4,
    confidence_threshold: float = 0.2,
    near_half_width: float = 0.01,
    low_prob_std_threshold: float = 1e-3,
) -> pd.DataFrame:
    """모델/horizon별 signal, label, 예측 분포 진단표를 만든다.

    진단표는 실험을 중단시키기보다 문제를 표준 컬럼으로 드러내는 용도다.
    missing return, error status처럼 평가를 오염시키는 항목은 `fail`로 표시한다.
    """
    _validate_thresholds(
        buy_threshold=buy_threshold,
        sell_threshold=sell_threshold,
        confidence_threshold=confidence_threshold,
        near_half_width=near_half_width,
        low_prob_std_threshold=low_prob_std_threshold,
    )
    signal = _prepare_signal_frame(signal_frame)
    returns = _prepare_returns_frame(realized_returns)

    merged = signal.merge(
        returns,
        on=["asof_date", "ticker", "horizon"],
        how="left",
        validate="many_to_one",
    )

    rows: list[dict[str, Any]] = []
    for (model_name, horizon), group in merged.groupby(["model_name", "horizon"], sort=True):
        rows.append(
            _build_group_diagnostics(
                model_name=str(model_name),
                horizon=int(horizon),
                group=group,
                buy_threshold=buy_threshold,
                sell_threshold=sell_threshold,
                confidence_threshold=confidence_threshold,
                near_half_width=near_half_width,
                low_prob_std_threshold=low_prob_std_threshold,
            )
        )

    if not rows:
        raise ValueError("diagnostics를 만들 signal row가 없습니다.")
    return pd.DataFrame(rows, columns=DIAGNOSTIC_COLUMNS).reset_index(drop=True)


def _build_group_diagnostics(
    *,
    model_name: str,
    horizon: int,
    group: pd.DataFrame,
    buy_threshold: float,
    sell_threshold: float,
    confidence_threshold: float,
    near_half_width: float,
    low_prob_std_threshold: float,
) -> dict[str, Any]:
    row_count = int(len(group))
    prob_up = group["prob_up"].astype(float)
    confidence = group["confidence"].astype(float)
    status_counts = group["prediction_status"].value_counts().to_dict()

    fallback_count = int(status_counts.get("fallback", 0))
    error_count = int(status_counts.get("error", 0))
    missing_return = group["forward_return"].isna()
    matched_returns = pd.to_numeric(group.loc[~missing_return, "forward_return"], errors="coerce")
    matched_returns = matched_returns[np.isfinite(matched_returns)]

    prob_std = float(prob_up.std(ddof=0)) if row_count > 0 else None
    label_positive_rate = (
        float((matched_returns > 0.0).mean()) if len(matched_returns) > 0 else None
    )
    missing_return_count = int(missing_return.sum())
    missing_return_rate = float(missing_return_count / row_count) if row_count > 0 else 1.0
    high_confidence_mask = confidence >= confidence_threshold
    buy_candidate_mask = (prob_up >= buy_threshold) & high_confidence_mask
    sell_candidate_mask = (prob_up <= sell_threshold) & high_confidence_mask
    quantiles = prob_up.quantile([0.05, 0.25, 0.5, 0.75, 0.95]).to_dict()

    fail_reasons: list[str] = []
    warn_reasons: list[str] = []
    if row_count == 0:
        fail_reasons.append("row_count가 0입니다.")
    if fallback_count > 0:
        warn_reasons.append(f"fallback row가 있습니다: {fallback_count}")
    if error_count > 0:
        fail_reasons.append(f"error row가 있습니다: {error_count}")
    if label_positive_rate is None:
        fail_reasons.append("forward_return label이 모두 누락되었습니다.")
    elif label_positive_rate <= 0.0 or label_positive_rate >= 1.0:
        fail_reasons.append(f"label_positive_rate가 극단값입니다: {label_positive_rate:.4f}")
    elif label_positive_rate < 0.05 or label_positive_rate > 0.95:
        warn_reasons.append(f"label_positive_rate가 비정상 범위입니다: {label_positive_rate:.4f}")
    if float(high_confidence_mask.mean()) == 0.0:
        warn_reasons.append("high_confidence_coverage가 0입니다.")
    if int(buy_candidate_mask.sum()) == 0:
        warn_reasons.append("buy 후보가 없습니다.")
    if missing_return_rate > 0.0:
        fail_reasons.append(f"missing_return_rate가 0보다 큽니다: {missing_return_rate:.4f}")
    if prob_std is not None and prob_std < low_prob_std_threshold:
        warn_reasons.append(f"prob_up 표준편차가 너무 낮습니다: {prob_std:.6f}")

    near_half_rate = float((prob_up.sub(0.5).abs() <= near_half_width).mean())
    if near_half_rate >= 0.9:
        warn_reasons.append(f"prob_up이 0.5 근처에 과도하게 몰려 있습니다: {near_half_rate:.4f}")

    diagnostic_status = "pass"
    if fail_reasons:
        diagnostic_status = "fail"
    elif warn_reasons:
        diagnostic_status = "warn"

    return {
        "model_name": model_name,
        "horizon": horizon,
        "row_count": row_count,
        "prediction_status_ok_count": int(status_counts.get("ok", 0)),
        "prediction_status_fallback_count": fallback_count,
        "prediction_status_error_count": error_count,
        "fallback_rate": float(fallback_count / row_count) if row_count > 0 else None,
        "error_rate": float(error_count / row_count) if row_count > 0 else None,
        "prob_up_mean": float(prob_up.mean()) if row_count > 0 else None,
        "prob_up_std": prob_std,
        "prob_up_min": float(prob_up.min()) if row_count > 0 else None,
        "prob_up_max": float(prob_up.max()) if row_count > 0 else None,
        "prob_up_q05": _optional_float(quantiles.get(0.05)),
        "prob_up_q25": _optional_float(quantiles.get(0.25)),
        "prob_up_q50": _optional_float(quantiles.get(0.5)),
        "prob_up_q75": _optional_float(quantiles.get(0.75)),
        "prob_up_q95": _optional_float(quantiles.get(0.95)),
        "near_half_rate": near_half_rate,
        "high_confidence_coverage": float(high_confidence_mask.mean()) if row_count > 0 else None,
        "buy_candidate_count": int(buy_candidate_mask.sum()),
        "sell_candidate_count": int(sell_candidate_mask.sum()),
        "label_positive_rate": label_positive_rate,
        "missing_return_count": missing_return_count,
        "missing_return_rate": missing_return_rate,
        "diagnostic_status": diagnostic_status,
        "diagnostic_reasons": "; ".join(fail_reasons + warn_reasons),
    }


def _prepare_signal_frame(signal_frame: pd.DataFrame) -> pd.DataFrame:
    if not isinstance(signal_frame, pd.DataFrame):
        raise TypeError("signal_frame은 pandas.DataFrame이어야 합니다.")
    if signal_frame.empty:
        raise ValueError("signal_frame은 비어 있을 수 없습니다.")
    missing = [
        column for column in SIGNAL_DIAGNOSTIC_REQUIRED_COLUMNS if column not in signal_frame.columns
    ]
    if missing:
        raise ValueError(f"signal_frame에 필요한 컬럼이 없습니다: {missing}")

    frame = signal_frame.copy()
    frame["asof_date"] = pd.to_datetime(frame["asof_date"], errors="raise").dt.normalize()
    frame["model_name"] = frame["model_name"].astype(str).str.lower()
    frame["ticker"] = frame["ticker"].astype(str)
    frame["horizon"] = _coerce_integer_series(frame["horizon"], "signal_frame.horizon")
    frame["prob_up"] = pd.to_numeric(frame["prob_up"], errors="coerce")
    if frame["prob_up"].isna().any() or not frame["prob_up"].between(0.0, 1.0).all():
        raise ValueError("signal_frame.prob_up은 0 이상 1 이하의 숫자여야 합니다.")

    if "confidence" not in frame.columns:
        frame["confidence"] = frame["prob_up"].sub(0.5).abs() * 2.0
    else:
        frame["confidence"] = pd.to_numeric(frame["confidence"], errors="coerce")
    if frame["confidence"].isna().any() or not frame["confidence"].between(0.0, 1.0).all():
        raise ValueError("signal_frame.confidence는 0 이상 1 이하의 숫자여야 합니다.")

    if "prediction_status" not in frame.columns:
        frame["prediction_status"] = "ok"
    frame["prediction_status"] = frame["prediction_status"].astype(str).str.lower()
    invalid_statuses = set(frame["prediction_status"]) - VALID_PREDICTION_STATUSES
    if invalid_statuses:
        raise ValueError(f"prediction_status에 허용되지 않는 값이 있습니다: {invalid_statuses}")
    return frame


def _prepare_returns_frame(realized_returns: pd.DataFrame) -> pd.DataFrame:
    if not isinstance(realized_returns, pd.DataFrame):
        raise TypeError("realized_returns는 pandas.DataFrame이어야 합니다.")
    missing = [
        column
        for column in RETURNS_DIAGNOSTIC_REQUIRED_COLUMNS
        if column not in realized_returns.columns
    ]
    if missing:
        raise ValueError(f"realized_returns에 필요한 컬럼이 없습니다: {missing}")

    frame = realized_returns.copy()
    frame["asof_date"] = pd.to_datetime(frame["asof_date"], errors="raise").dt.normalize()
    frame["ticker"] = frame["ticker"].astype(str)
    frame["horizon"] = _coerce_integer_series(frame["horizon"], "realized_returns.horizon")
    frame["forward_return"] = pd.to_numeric(frame["forward_return"], errors="coerce")
    duplicated = frame.duplicated(["asof_date", "ticker", "horizon"], keep=False)
    if duplicated.any():
        duplicate_keys = (
            frame.loc[duplicated, ["asof_date", "ticker", "horizon"]]
            .drop_duplicates()
            .to_dict("records")
        )
        raise ValueError(f"realized_returns key가 중복되었습니다: {duplicate_keys}")
    return frame[["asof_date", "ticker", "horizon", "forward_return"]]


def _coerce_integer_series(values: pd.Series, name: str) -> pd.Series:
    numeric = pd.to_numeric(values, errors="coerce")
    if numeric.isna().any() or not numeric.map(lambda value: float(value).is_integer()).all():
        raise ValueError(f"{name}은 정수여야 합니다.")
    return numeric.astype(int)


def _validate_thresholds(
    *,
    buy_threshold: float,
    sell_threshold: float,
    confidence_threshold: float,
    near_half_width: float,
    low_prob_std_threshold: float,
) -> None:
    if not 0.0 <= float(sell_threshold) < float(buy_threshold) <= 1.0:
        raise ValueError("threshold는 0 <= sell_threshold < buy_threshold <= 1 조건이어야 합니다.")
    if not 0.0 <= float(confidence_threshold) <= 1.0:
        raise ValueError("confidence_threshold는 0 이상 1 이하여야 합니다.")
    if not 0.0 <= float(near_half_width) <= 0.5:
        raise ValueError("near_half_width는 0 이상 0.5 이하여야 합니다.")
    if float(low_prob_std_threshold) < 0.0:
        raise ValueError("low_prob_std_threshold는 0 이상이어야 합니다.")


def _optional_float(value: Any) -> float | None:
    if value is None or pd.isna(value):
        return None
    return float(value)
