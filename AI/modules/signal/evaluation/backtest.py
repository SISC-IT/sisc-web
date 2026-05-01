"""Signal Schema v0 기반 Top-k 백테스트 v0."""

from __future__ import annotations

from typing import Any

import numpy as np
import pandas as pd

from AI.modules.signal.evaluation.metrics import portfolio_metrics
from AI.modules.signal.evaluation.schema import (
    SIGNAL_SCHEMA_V0_REQUIRED_COLUMNS,
    VALID_PREDICTION_STATUSES,
    validate_signal_frame,
)


SIGNAL_BACKTEST_REQUIRED_COLUMNS = [
    "asof_date",
    "ticker",
    "model_name",
    "horizon",
    "prob_up",
    "confidence",
    "prediction_status",
]

RETURNS_REQUIRED_COLUMNS = [
    "asof_date",
    "ticker",
    "horizon",
    "forward_return",
]

SUPPORTED_WEIGHTINGS = {"equal", "confidence", "prob_excess"}
SUPPORTED_MISSING_RETURN_POLICIES = {"error", "drop"}


def _require_columns(frame: pd.DataFrame, required_columns: list[str], frame_name: str) -> None:
    missing = [column for column in required_columns if column not in frame.columns]
    if missing:
        raise ValueError(f"{frame_name}에 필요한 컬럼이 없습니다: {missing}")


def _validate_probability_column(frame: pd.DataFrame, column: str, frame_name: str) -> None:
    values = pd.to_numeric(frame[column], errors="coerce")
    if values.isna().any() or not values.between(0.0, 1.0, inclusive="both").all():
        raise ValueError(f"{frame_name}.{column}은 0 이상 1 이하의 숫자여야 합니다.")


def _validate_positive_integer(value: Any, name: str) -> None:
    if isinstance(value, bool):
        raise ValueError(f"{name}은 1 이상의 정수여야 합니다.")
    try:
        numeric = float(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{name}은 1 이상의 정수여야 합니다.") from exc
    if not np.isfinite(numeric) or not numeric.is_integer() or numeric <= 0:
        raise ValueError(f"{name}은 1 이상의 정수여야 합니다.")


def _validate_signal_input(signal_frame: pd.DataFrame) -> None:
    if not isinstance(signal_frame, pd.DataFrame):
        raise TypeError("signal_frame은 pandas.DataFrame이어야 합니다.")
    _require_columns(signal_frame, SIGNAL_BACKTEST_REQUIRED_COLUMNS, "signal_frame")

    if set(SIGNAL_SCHEMA_V0_REQUIRED_COLUMNS).issubset(signal_frame.columns):
        validate_signal_frame(signal_frame)
    elif signal_frame[SIGNAL_BACKTEST_REQUIRED_COLUMNS].isna().any().any():
        raise ValueError("signal_frame 필수 컬럼에는 결측값이 없어야 합니다.")

    _validate_probability_column(signal_frame, "prob_up", "signal_frame")
    _validate_probability_column(signal_frame, "confidence", "signal_frame")

    invalid_statuses = set(signal_frame["prediction_status"]) - VALID_PREDICTION_STATUSES
    if invalid_statuses:
        raise ValueError(
            "prediction_status 값은 ok, fallback, error 중 하나여야 합니다: "
            f"{invalid_statuses}"
        )

    duplicated = signal_frame.duplicated(
        subset=["asof_date", "ticker", "model_name", "horizon"],
        keep=False,
    )
    if duplicated.any():
        duplicate_keys = (
            signal_frame.loc[
                duplicated,
                ["asof_date", "ticker", "model_name", "horizon"],
            ]
            .drop_duplicates()
            .to_dict("records")
        )
        raise ValueError(f"signal_frame key가 중복되었습니다: {duplicate_keys}")


def _validate_returns_input(returns_frame: pd.DataFrame) -> None:
    if not isinstance(returns_frame, pd.DataFrame):
        raise TypeError("returns_frame은 pandas.DataFrame이어야 합니다.")
    _require_columns(returns_frame, RETURNS_REQUIRED_COLUMNS, "returns_frame")

    if returns_frame[RETURNS_REQUIRED_COLUMNS].isna().any().any():
        raise ValueError("returns_frame 필수 컬럼에는 결측값이 없어야 합니다.")

    forward_return = pd.to_numeric(returns_frame["forward_return"], errors="coerce")
    if forward_return.isna().any() or not np.isfinite(forward_return).all():
        raise ValueError("forward_return은 유한한 숫자여야 합니다.")

    duplicated = returns_frame.duplicated(
        subset=["asof_date", "ticker", "horizon"],
        keep=False,
    )
    if duplicated.any():
        duplicate_keys = (
            returns_frame.loc[duplicated, ["asof_date", "ticker", "horizon"]]
            .drop_duplicates()
            .to_dict("records")
        )
        raise ValueError(f"returns_frame key가 중복되었습니다: {duplicate_keys}")


def _validate_common_config(
    *,
    horizon: int,
    top_k: int | None = None,
    buy_threshold: float | None = None,
    confidence_threshold: float | None = None,
    weighting: str | None = None,
    max_weight_per_ticker: float | None = None,
    initial_equity: float = 1.0,
    cost_bps_per_side: float | None = None,
    include_statuses: tuple[str, ...] | None = None,
    missing_return_policy: str | None = None,
) -> None:
    _validate_positive_integer(horizon, "horizon")
    if top_k is not None:
        _validate_positive_integer(top_k, "top_k")
    if buy_threshold is not None and not 0.0 <= float(buy_threshold) <= 1.0:
        raise ValueError("buy_threshold는 0 이상 1 이하여야 합니다.")
    if confidence_threshold is not None and not 0.0 <= float(confidence_threshold) <= 1.0:
        raise ValueError("confidence_threshold는 0 이상 1 이하여야 합니다.")
    if weighting is not None and weighting not in SUPPORTED_WEIGHTINGS:
        raise ValueError(f"지원하지 않는 weighting입니다: {weighting!r}")
    if max_weight_per_ticker is not None:
        max_weight = float(max_weight_per_ticker)
        if not 0.0 < max_weight <= 1.0:
            raise ValueError("max_weight_per_ticker는 0보다 크고 1 이하여야 합니다.")
        if top_k is not None and int(top_k) * max_weight < 1.0:
            raise ValueError("top_k * max_weight_per_ticker가 1보다 작으면 전체 비중을 채울 수 없습니다.")
    initial_equity_value = float(initial_equity)
    if not np.isfinite(initial_equity_value) or initial_equity_value <= 0.0:
        raise ValueError("initial_equity는 0보다 커야 합니다.")
    if cost_bps_per_side is not None:
        cost_value = float(cost_bps_per_side)
        if not np.isfinite(cost_value) or cost_value < 0.0:
            raise ValueError("cost_bps_per_side는 0 이상이어야 합니다.")
    if include_statuses is not None:
        if not include_statuses:
            raise ValueError("include_statuses는 비어 있을 수 없습니다.")
        invalid = set(include_statuses) - VALID_PREDICTION_STATUSES
        if invalid:
            raise ValueError(f"include_statuses에 허용되지 않는 값이 있습니다: {invalid}")
    if (
        missing_return_policy is not None
        and missing_return_policy not in SUPPORTED_MISSING_RETURN_POLICIES
    ):
        raise ValueError(
            "missing_return_policy는 'error' 또는 'drop'이어야 합니다: "
            f"{missing_return_policy!r}"
        )


def _periods_per_year_for_horizon(horizon: int) -> float:
    return 252.0 / float(horizon)


def _build_metrics_equity_curve(equity_curve: pd.DataFrame, initial_equity: float) -> pd.DataFrame:
    """period row 기반 equity curve 앞에 초기 자본 row를 붙여 지표 계산 누락을 막는다."""
    equity_values = [float(initial_equity)] + equity_curve["equity"].astype(float).tolist()
    return pd.DataFrame(
        {
            "date": list(range(len(equity_values))),
            "equity": equity_values,
        }
    )


def _selection_summary_metrics(equity_curve: pd.DataFrame) -> dict[str, Any]:
    """basket 선택 없음이 평가 결과에서 보이도록 요약 지표를 계산한다."""
    if equity_curve.empty or "selected_count" not in equity_curve.columns:
        return {
            "selected_periods": None,
            "total_periods": int(len(equity_curve)),
            "avg_selected_count": None,
            "cash_period_rate": None,
            "all_cash_periods": None,
        }

    selected_count = pd.to_numeric(equity_curve["selected_count"], errors="coerce")
    if selected_count.isna().any():
        raise ValueError("equity_curve.selected_count는 유효한 숫자여야 합니다.")

    total_periods = int(len(equity_curve))
    selected_periods = int((selected_count > 0).sum())
    cash_period_rate = float((selected_count == 0).mean()) if total_periods > 0 else None
    return {
        "selected_periods": selected_periods,
        "total_periods": total_periods,
        "avg_selected_count": float(selected_count.mean()) if total_periods > 0 else None,
        "cash_period_rate": cash_period_rate,
        "all_cash_periods": bool(total_periods > 0 and selected_periods == 0),
    }


def _weight_scores(selected: pd.DataFrame, weighting: str) -> np.ndarray:
    if weighting == "equal":
        return np.ones(len(selected), dtype=float)
    if weighting == "confidence":
        return selected["confidence"].to_numpy(dtype=float)
    if weighting == "prob_excess":
        return np.maximum(selected["prob_up"].to_numpy(dtype=float) - 0.5, 0.0)
    raise ValueError(f"지원하지 않는 weighting입니다: {weighting!r}")


def _allocate_weights(
    selected: pd.DataFrame,
    *,
    weighting: str,
    max_weight_per_ticker: float | None,
) -> np.ndarray:
    if selected.empty:
        return np.array([], dtype=float)

    scores = _weight_scores(selected, weighting)
    if not np.isfinite(scores).all() or float(scores.sum()) <= 0.0:
        scores = np.ones(len(selected), dtype=float)

    weights = scores / scores.sum()
    if max_weight_per_ticker is None:
        return weights

    cap = float(max_weight_per_ticker)
    weights = np.zeros(len(selected), dtype=float)
    remaining = np.ones(len(selected), dtype=bool)
    remaining_budget = 1.0

    while remaining.any() and remaining_budget > 1e-12:
        remaining_scores = scores[remaining]
        if float(remaining_scores.sum()) <= 0.0:
            remaining_scores = np.ones(int(remaining.sum()), dtype=float)
        proposed = remaining_budget * remaining_scores / remaining_scores.sum()
        remaining_indices = np.flatnonzero(remaining)
        over_cap = proposed > cap

        if not over_cap.any():
            weights[remaining_indices] = proposed
            remaining_budget = 0.0
            break

        capped_indices = remaining_indices[over_cap]
        weights[capped_indices] = cap
        remaining[capped_indices] = False
        remaining_budget -= cap * len(capped_indices)

    # 선택 종목 수가 cap 때문에 100%를 채우지 못하는 경우 남는 비중은 현금으로 둔다.
    return weights


def _prepare_model_signal_frame(
    signal_frame: pd.DataFrame,
    *,
    model_name: str | None,
    horizon: int,
    include_statuses: tuple[str, ...],
) -> tuple[pd.DataFrame, str]:
    scoped = signal_frame[signal_frame["horizon"].astype(int) == int(horizon)].copy()
    if model_name is not None:
        scoped = scoped[scoped["model_name"] == model_name].copy()

    if scoped.empty:
        raise ValueError("조건에 맞는 signal row가 없습니다.")

    model_names = sorted(scoped["model_name"].dropna().unique().tolist())
    if model_name is None and len(model_names) != 1:
        raise ValueError("model_name 인자 없이 여러 model_name이 섞인 signal_frame은 평가할 수 없습니다.")
    resolved_model_name = str(model_name or model_names[0])

    status_filtered = scoped[scoped["prediction_status"].isin(include_statuses)].copy()
    return status_filtered, resolved_model_name


def _missing_return_keys(scoped_signals: pd.DataFrame, scoped_returns: pd.DataFrame) -> pd.DataFrame:
    """예측 row에는 있지만 forward_return에는 없는 조인 키를 찾는다."""
    if scoped_signals.empty:
        return pd.DataFrame(columns=["asof_date", "ticker", "horizon"])

    join_keys = ["asof_date", "ticker", "horizon"]
    signal_keys = scoped_signals[join_keys].drop_duplicates()
    return_keys = scoped_returns[join_keys].drop_duplicates()
    checked = signal_keys.merge(return_keys, on=join_keys, how="left", indicator=True)
    return checked.loc[checked["_merge"] == "left_only", join_keys].copy()


def backtest_top_k_signals(
    signal_frame,
    returns_frame,
    *,
    model_name: str | None = None,
    horizon: int,
    top_k: int = 3,
    buy_threshold: float = 0.6,
    confidence_threshold: float = 0.0,
    weighting: str = "equal",
    max_weight_per_ticker: float | None = None,
    initial_equity: float = 1.0,
    cost_bps_per_side: float = 20.0,
    include_statuses: tuple[str, ...] = ("ok",),
    missing_return_policy: str = "error",
) -> dict:
    """Signal Schema v0 예측을 날짜별 Top-k basket으로 평가한다."""
    _validate_signal_input(signal_frame)
    _validate_returns_input(returns_frame)
    _validate_common_config(
        horizon=horizon,
        top_k=top_k,
        buy_threshold=buy_threshold,
        confidence_threshold=confidence_threshold,
        weighting=weighting,
        max_weight_per_ticker=max_weight_per_ticker,
        initial_equity=initial_equity,
        cost_bps_per_side=cost_bps_per_side,
        include_statuses=include_statuses,
        missing_return_policy=missing_return_policy,
    )

    signal_frame = signal_frame.copy()
    returns_frame = returns_frame.copy()
    signal_frame["horizon"] = signal_frame["horizon"].astype(int)
    returns_frame["horizon"] = returns_frame["horizon"].astype(int)
    returns_frame["forward_return"] = pd.to_numeric(returns_frame["forward_return"], errors="raise")

    scoped_signals, resolved_model_name = _prepare_model_signal_frame(
        signal_frame,
        model_name=model_name,
        horizon=int(horizon),
        include_statuses=include_statuses,
    )
    scoped_returns = returns_frame[returns_frame["horizon"] == int(horizon)].copy()

    missing_returns = _missing_return_keys(scoped_signals, scoped_returns)
    missing_return_count = int(len(missing_returns))
    missing_return_rate = (
        float(missing_return_count / len(scoped_signals)) if len(scoped_signals) > 0 else 0.0
    )
    if missing_return_count > 0 and missing_return_policy == "error":
        missing_keys = missing_returns.to_dict("records")
        raise ValueError(f"forward_return이 없는 signal row가 있습니다: {missing_keys}")

    merged = scoped_signals.merge(
        scoped_returns[RETURNS_REQUIRED_COLUMNS],
        on=["asof_date", "ticker", "horizon"],
        how="inner",
    )

    asof_dates = sorted(signal_frame.loc[
        (signal_frame["horizon"] == int(horizon))
        & (signal_frame["model_name"] == resolved_model_name),
        "asof_date",
    ].dropna().unique().tolist())
    if not asof_dates:
        raise ValueError("평가 가능한 asof_date가 없습니다.")

    periods_per_year = _periods_per_year_for_horizon(int(horizon))
    round_trip_cost = 2.0 * float(cost_bps_per_side) / 10000.0
    equity = float(initial_equity)
    equity_rows: list[dict[str, Any]] = []
    trade_rows: list[dict[str, Any]] = []

    for asof_date in asof_dates:
        period_candidates = merged[merged["asof_date"] == asof_date].copy()
        period_candidates = period_candidates[
            (period_candidates["prob_up"] >= float(buy_threshold))
            & (period_candidates["confidence"] >= float(confidence_threshold))
        ].copy()
        period_candidates = period_candidates.sort_values(
            ["prob_up", "confidence", "ticker"],
            ascending=[False, False, True],
        ).head(int(top_k))

        equity_before = equity
        if period_candidates.empty:
            gross_return = 0.0
            cost_return = 0.0
            net_return = 0.0
            selected_count = 0
            cash_weight = 1.0
            cost_paid = 0.0
        else:
            weights = _allocate_weights(
                period_candidates,
                weighting=weighting,
                max_weight_per_ticker=max_weight_per_ticker,
            )
            invested_weight = float(weights.sum())
            cash_weight = max(0.0, 1.0 - invested_weight)
            selected_count = int(len(period_candidates))
            gross_contributions = weights * period_candidates["forward_return"].to_numpy(dtype=float)
            cost_contributions = weights * round_trip_cost
            net_contributions = gross_contributions - cost_contributions
            gross_return = float(gross_contributions.sum())
            cost_return = float(cost_contributions.sum())
            net_return = float(net_contributions.sum())
            cost_paid = float(cost_return * equity_before)

            for row, weight, gross_contribution, cost_contribution, net_contribution in zip(
                period_candidates.to_dict("records"),
                weights,
                gross_contributions,
                cost_contributions,
                net_contributions,
            ):
                trade_rows.append(
                    {
                        "asof_date": asof_date,
                        "ticker": row["ticker"],
                        "model_name": resolved_model_name,
                        "horizon": int(horizon),
                        "weight": float(weight),
                        "prob_up": float(row["prob_up"]),
                        "confidence": float(row["confidence"]),
                        "forward_return": float(row["forward_return"]),
                        "gross_contribution": float(gross_contribution),
                        "cost_contribution": float(cost_contribution),
                        "net_contribution": float(net_contribution),
                        "turnover": float(weight * 2.0),
                        "cost": float(cost_contribution * equity_before),
                    }
                )

        equity = equity_before * (1.0 + net_return)
        equity_rows.append(
            {
                "date": asof_date,
                "asof_date": asof_date,
                "model_name": resolved_model_name,
                "horizon": int(horizon),
                "weighting": weighting,
                "gross_return": gross_return,
                "net_return": net_return,
                "equity": equity,
                "selected_count": selected_count,
                "cash_weight": cash_weight,
                "cost_paid": cost_paid,
                "cost_return": cost_return,
                "periods_per_year": periods_per_year,
            }
        )

    equity_curve = pd.DataFrame(equity_rows)
    trades = pd.DataFrame(
        trade_rows,
        columns=[
            "asof_date",
            "ticker",
            "model_name",
            "horizon",
            "weight",
            "prob_up",
            "confidence",
            "forward_return",
            "gross_contribution",
            "cost_contribution",
            "net_contribution",
            "turnover",
            "cost",
        ],
    )
    metrics_equity_curve = _build_metrics_equity_curve(equity_curve, float(initial_equity))
    metrics = portfolio_metrics(metrics_equity_curve, trades, periods_per_year=periods_per_year)
    metrics["periods_per_year"] = periods_per_year
    metrics["missing_return_count"] = missing_return_count
    metrics["missing_return_rate"] = missing_return_rate
    metrics.update(_selection_summary_metrics(equity_curve))

    return {
        "equity_curve": equity_curve,
        "trades": trades,
        "metrics": metrics,
        "config": {
            "model_name": resolved_model_name,
            "horizon": int(horizon),
            "top_k": int(top_k),
            "buy_threshold": float(buy_threshold),
            "confidence_threshold": float(confidence_threshold),
            "weighting": weighting,
            "max_weight_per_ticker": max_weight_per_ticker,
            "initial_equity": float(initial_equity),
            "cost_bps_per_side": float(cost_bps_per_side),
            "round_trip_cost": round_trip_cost,
            "include_statuses": tuple(include_statuses),
            "missing_return_policy": missing_return_policy,
            "periods_per_year": periods_per_year,
        },
    }


def universe_equal_benchmark(
    returns_frame,
    *,
    horizon: int,
    initial_equity: float = 1.0,
) -> dict:
    """평가 universe 전체를 동일 비중으로 들고 간 기준선을 계산한다."""
    _validate_returns_input(returns_frame)
    _validate_common_config(horizon=horizon, initial_equity=initial_equity)

    returns_frame = returns_frame.copy()
    returns_frame["horizon"] = returns_frame["horizon"].astype(int)
    returns_frame["forward_return"] = pd.to_numeric(returns_frame["forward_return"], errors="raise")
    scoped_returns = returns_frame[returns_frame["horizon"] == int(horizon)].copy()
    if scoped_returns.empty:
        raise ValueError("조건에 맞는 returns row가 없습니다.")

    periods_per_year = _periods_per_year_for_horizon(int(horizon))
    equity = float(initial_equity)
    equity_rows: list[dict[str, Any]] = []

    for asof_date, group in scoped_returns.groupby("asof_date", sort=True):
        gross_return = float(group["forward_return"].mean())
        net_return = gross_return
        equity *= 1.0 + net_return
        equity_rows.append(
            {
                "date": asof_date,
                "asof_date": asof_date,
                "model_name": "universe_equal",
                "horizon": int(horizon),
                "weighting": "equal",
                "gross_return": gross_return,
                "net_return": net_return,
                "equity": equity,
                "selected_count": int(len(group)),
                "cash_weight": 0.0,
                "cost_paid": 0.0,
                "cost_return": 0.0,
                "periods_per_year": periods_per_year,
            }
        )

    equity_curve = pd.DataFrame(equity_rows)
    trades = pd.DataFrame()
    metrics_equity_curve = _build_metrics_equity_curve(equity_curve, float(initial_equity))
    metrics = portfolio_metrics(metrics_equity_curve, trades, periods_per_year=periods_per_year)
    metrics["periods_per_year"] = periods_per_year
    metrics.update(_selection_summary_metrics(equity_curve))

    return {
        "equity_curve": equity_curve,
        "trades": trades,
        "metrics": metrics,
        "config": {
            "benchmark_name": "universe_equal",
            "horizon": int(horizon),
            "initial_equity": float(initial_equity),
            "periods_per_year": periods_per_year,
        },
    }
