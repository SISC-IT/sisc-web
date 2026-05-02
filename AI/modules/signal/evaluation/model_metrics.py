"""모델 objective용 metric frame 생성기.

Signal Schema v0 예측과 realized return, leaderboard/backtest 결과를 묶어
`objectives.py`가 바로 사용할 수 있는 horizon별 metric frame을 만든다.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping
from typing import Any

import numpy as np
import pandas as pd

from AI.modules.signal.evaluation.metrics import (
    avoid_filter_metrics,
    calibration_metrics,
    classification_metrics,
    high_confidence_metrics,
    ranking_metrics,
)


MODEL_METRIC_FRAME_COLUMNS = [
    "model_name",
    "horizon",
    "run_id",
    "leaderboard_run_id",
    "metric_source",
    "count_rows",
    "missing_return_count",
    "missing_return_rate",
    "brier_score",
    "log_loss",
    "ece",
    "accuracy",
    "high_confidence_precision",
    "high_confidence_coverage",
    "high_confidence_accuracy",
    "rank_ic_mean",
    "top_bottom_spread",
    "top_k_mean_return",
    "buy_bucket_coverage",
    "sell_bucket_coverage",
    "buy_bucket_mean_return",
    "sell_bucket_mean_return",
    "avoid_filter_spread",
    "avoided_loss_mean",
    "net_return",
    "mdd",
    "calmar",
    "downside_return",
]

CLASSIFICATION_MODELS = {"transformer"}
HIGH_CONFIDENCE_MODELS = {"tcn"}
RANKING_MODELS = {"patchtst"}
PORTFOLIO_MODELS = {"itransformer"}


def build_model_metric_frame(
    signal_frame: pd.DataFrame,
    realized_returns: pd.DataFrame,
    *,
    leaderboard_frame: pd.DataFrame | None = None,
    backtest_results: Iterable[Mapping[str, Any]] | None = None,
    include_statuses: tuple[str, ...] = ("ok",),
    top_k: int = 5,
    classification_threshold: float = 0.5,
    high_confidence_threshold: float = 0.2,
    avoid_filter_buy_threshold: float = 0.6,
    avoid_filter_sell_threshold: float = 0.4,
    calibration_bins: int = 10,
    leaderboard_run_id: str | None = None,
) -> pd.DataFrame:
    """모델별 objective profile에 필요한 metric row를 생성한다.

    반환 row는 `model_name`과 `horizon`을 기본 key로 삼고, 신호 기반 지표와
    포트폴리오 지표의 출처를 `metric_source`에 남긴다.
    """
    signal = _prepare_signal_frame(signal_frame, include_statuses=include_statuses)
    returns = _prepare_realized_returns(realized_returns)
    leaderboard = _prepare_leaderboard_frame(leaderboard_frame)

    rows: list[dict[str, Any]] = []
    model_names = sorted(signal["model_name"].dropna().unique().tolist())
    for model_name in model_names:
        model_signal = signal[signal["model_name"] == model_name].copy()
        horizons = sorted(model_signal["horizon"].dropna().astype(int).unique().tolist())
        for horizon in horizons:
            scoped_signal = model_signal[model_signal["horizon"] == horizon].copy()
            scoped_returns = returns[returns["horizon"] == horizon].copy()
            if scoped_signal.empty:
                continue

            row = _base_metric_row(
                scoped_signal,
                model_name=model_name,
                horizon=horizon,
                leaderboard_run_id=leaderboard_run_id,
            )
            merged = _merge_signal_and_returns(scoped_signal, scoped_returns)
            row["count_rows"] = int(len(merged))
            row["missing_return_count"] = int(len(scoped_signal) - len(merged))
            row["missing_return_rate"] = (
                float(row["missing_return_count"] / len(scoped_signal))
                if len(scoped_signal) > 0
                else 0.0
            )

            if not merged.empty:
                y_true = (merged["forward_return"].astype(float) > 0.0).astype(int)
                prob_up = merged["prob_up"].astype(float)
                sources: list[str] = []

                if model_name in CLASSIFICATION_MODELS:
                    row.update(
                        _classification_metric_values(
                            y_true,
                            prob_up,
                            threshold=classification_threshold,
                            n_bins=calibration_bins,
                        )
                    )
                    sources.append("classification_calibration")

                if model_name in HIGH_CONFIDENCE_MODELS:
                    row.update(
                        _high_confidence_metric_values(
                            y_true,
                            prob_up,
                            confidence_threshold=high_confidence_threshold,
                            threshold=classification_threshold,
                        )
                    )
                    sources.append("high_confidence")

                if model_name in RANKING_MODELS:
                    row.update(
                        _ranking_metric_values(scoped_signal, scoped_returns, top_k=top_k)
                    )
                    row.update(
                        _avoid_filter_metric_values(
                            scoped_signal,
                            scoped_returns,
                            buy_threshold=avoid_filter_buy_threshold,
                            sell_threshold=avoid_filter_sell_threshold,
                            confidence_threshold=high_confidence_threshold,
                        )
                    )
                    sources.append("ranking")

                row["metric_source"] = "+".join(sources) if sources else row["metric_source"]

            portfolio_values = _portfolio_metric_values(
                model_name=model_name,
                horizon=horizon,
                leaderboard_frame=leaderboard,
                backtest_results=backtest_results,
            )
            if portfolio_values:
                row.update(portfolio_values)
                if model_name in PORTFOLIO_MODELS:
                    row["metric_source"] = _append_metric_source(row["metric_source"], "portfolio")

            rows.append(row)

    if not rows:
        raise ValueError("생성된 model metric row가 없습니다.")

    frame = pd.DataFrame(rows, columns=MODEL_METRIC_FRAME_COLUMNS)
    return frame.sort_values(["model_name", "horizon"]).reset_index(drop=True)


def _prepare_signal_frame(
    signal_frame: pd.DataFrame,
    *,
    include_statuses: tuple[str, ...],
) -> pd.DataFrame:
    if not isinstance(signal_frame, pd.DataFrame):
        raise TypeError("signal_frame은 pandas.DataFrame이어야 합니다.")
    required = ["asof_date", "ticker", "model_name", "horizon", "prob_up"]
    _require_columns(signal_frame, required, "signal_frame")

    frame = signal_frame.copy()
    frame["asof_date"] = pd.to_datetime(frame["asof_date"], errors="raise").dt.normalize()
    frame["model_name"] = frame["model_name"].astype(str).str.lower()
    frame["horizon"] = _coerce_int_series(frame["horizon"], "signal_frame.horizon")
    frame["prob_up"] = pd.to_numeric(frame["prob_up"], errors="raise")
    if "prediction_status" in frame.columns:
        frame = frame[frame["prediction_status"].isin(include_statuses)].copy()
    if frame.empty:
        raise ValueError("include_statuses 적용 후 signal_frame이 비었습니다.")
    return frame


def _prepare_realized_returns(realized_returns: pd.DataFrame) -> pd.DataFrame:
    if not isinstance(realized_returns, pd.DataFrame):
        raise TypeError("realized_returns는 pandas.DataFrame이어야 합니다.")
    required = ["asof_date", "ticker", "horizon", "forward_return"]
    _require_columns(realized_returns, required, "realized_returns")

    frame = realized_returns.copy()
    frame["asof_date"] = pd.to_datetime(frame["asof_date"], errors="raise").dt.normalize()
    frame["horizon"] = _coerce_int_series(frame["horizon"], "realized_returns.horizon")
    frame["forward_return"] = pd.to_numeric(frame["forward_return"], errors="raise")
    duplicated = frame.duplicated(subset=["asof_date", "ticker", "horizon"], keep=False)
    if duplicated.any():
        duplicate_keys = (
            frame.loc[duplicated, ["asof_date", "ticker", "horizon"]]
            .drop_duplicates()
            .to_dict("records")
        )
        raise ValueError(f"realized_returns key가 중복되었습니다: {duplicate_keys}")
    return frame


def _prepare_leaderboard_frame(leaderboard_frame: pd.DataFrame | None) -> pd.DataFrame | None:
    if leaderboard_frame is None:
        return None
    if not isinstance(leaderboard_frame, pd.DataFrame):
        raise TypeError("leaderboard_frame은 pandas.DataFrame이어야 합니다.")
    if leaderboard_frame.empty:
        return None
    required = ["model_name", "horizon"]
    _require_columns(leaderboard_frame, required, "leaderboard_frame")

    frame = leaderboard_frame.copy()
    frame = frame[frame["model_name"].notna()].copy()
    if frame.empty:
        return None
    frame["model_name"] = frame["model_name"].astype(str).str.lower()
    frame["horizon"] = _coerce_int_series(frame["horizon"], "leaderboard_frame.horizon")
    return frame


def _require_columns(frame: pd.DataFrame, columns: list[str], frame_name: str) -> None:
    missing = [column for column in columns if column not in frame.columns]
    if missing:
        raise ValueError(f"{frame_name}에 필요한 컬럼이 없습니다: {missing}")


def _coerce_int_series(values: pd.Series, name: str) -> pd.Series:
    numeric = pd.to_numeric(values, errors="coerce")
    if numeric.isna().any() or not numeric.map(lambda value: float(value).is_integer()).all():
        raise ValueError(f"{name}은 정수여야 합니다.")
    return numeric.astype(int)


def _base_metric_row(
    scoped_signal: pd.DataFrame,
    *,
    model_name: str,
    horizon: int,
    leaderboard_run_id: str | None,
) -> dict[str, Any]:
    run_id = None
    if "run_id" in scoped_signal.columns and not scoped_signal["run_id"].dropna().empty:
        run_id = scoped_signal["run_id"].dropna().astype(str).iloc[0]

    return {
        "model_name": model_name,
        "horizon": int(horizon),
        "run_id": run_id,
        "leaderboard_run_id": leaderboard_run_id,
        "metric_source": "signal_return",
        "count_rows": 0,
        "missing_return_count": 0,
        "missing_return_rate": 0.0,
        "brier_score": None,
        "log_loss": None,
        "ece": None,
        "accuracy": None,
        "high_confidence_precision": None,
        "high_confidence_coverage": None,
        "high_confidence_accuracy": None,
        "rank_ic_mean": None,
        "top_bottom_spread": None,
        "top_k_mean_return": None,
        "buy_bucket_coverage": None,
        "sell_bucket_coverage": None,
        "buy_bucket_mean_return": None,
        "sell_bucket_mean_return": None,
        "avoid_filter_spread": None,
        "avoided_loss_mean": None,
        "net_return": None,
        "mdd": None,
        "calmar": None,
        "downside_return": None,
    }


def _merge_signal_and_returns(signal_frame: pd.DataFrame, returns_frame: pd.DataFrame) -> pd.DataFrame:
    signal_columns = ["asof_date", "ticker", "horizon", "prob_up"]
    return_columns = ["asof_date", "ticker", "horizon", "forward_return"]
    return signal_frame[signal_columns].merge(
        returns_frame[return_columns],
        on=["asof_date", "ticker", "horizon"],
        how="inner",
    )


def _classification_metric_values(
    y_true: pd.Series,
    prob_up: pd.Series,
    *,
    threshold: float,
    n_bins: int,
) -> dict[str, Any]:
    classification = classification_metrics(y_true, prob_up, threshold=threshold)
    calibration = calibration_metrics(y_true, prob_up, n_bins=n_bins)
    return {
        "brier_score": classification["brier_score"],
        "log_loss": classification["log_loss"],
        "ece": calibration["ece"],
        "accuracy": classification["accuracy"],
    }


def _high_confidence_metric_values(
    y_true: pd.Series,
    prob_up: pd.Series,
    *,
    confidence_threshold: float,
    threshold: float,
) -> dict[str, Any]:
    metrics = high_confidence_metrics(
        y_true,
        prob_up,
        confidence_threshold=confidence_threshold,
        threshold=threshold,
    )
    return {
        "high_confidence_precision": metrics["precision"],
        "high_confidence_coverage": metrics["coverage"],
        "high_confidence_accuracy": metrics["accuracy"],
    }


def _ranking_metric_values(
    signal_frame: pd.DataFrame,
    returns_frame: pd.DataFrame,
    *,
    top_k: int,
) -> dict[str, Any]:
    try:
        metrics = ranking_metrics(signal_frame, returns_frame, k=top_k)
    except ValueError:
        return {
            "rank_ic_mean": None,
            "top_bottom_spread": None,
            "top_k_mean_return": None,
        }
    return {
        "rank_ic_mean": metrics["rank_ic_mean"],
        "top_bottom_spread": metrics["top_bottom_spread"],
        "top_k_mean_return": metrics["top_k_mean_return"],
    }


def _avoid_filter_metric_values(
    signal_frame: pd.DataFrame,
    returns_frame: pd.DataFrame,
    *,
    buy_threshold: float,
    sell_threshold: float,
    confidence_threshold: float,
) -> dict[str, Any]:
    try:
        metrics = avoid_filter_metrics(
            signal_frame,
            returns_frame,
            buy_threshold=buy_threshold,
            sell_threshold=sell_threshold,
            confidence_threshold=confidence_threshold,
        )
    except ValueError:
        return {
            "buy_bucket_coverage": None,
            "sell_bucket_coverage": None,
            "buy_bucket_mean_return": None,
            "sell_bucket_mean_return": None,
            "avoid_filter_spread": None,
            "avoided_loss_mean": None,
        }
    return {
        "buy_bucket_coverage": metrics["buy_bucket_coverage"],
        "sell_bucket_coverage": metrics["sell_bucket_coverage"],
        "buy_bucket_mean_return": metrics["buy_bucket_mean_return"],
        "sell_bucket_mean_return": metrics["sell_bucket_mean_return"],
        "avoid_filter_spread": metrics["avoid_filter_spread"],
        "avoided_loss_mean": metrics["avoided_loss_mean"],
    }


def _portfolio_metric_values(
    *,
    model_name: str,
    horizon: int,
    leaderboard_frame: pd.DataFrame | None,
    backtest_results: Iterable[Mapping[str, Any]] | None,
) -> dict[str, Any]:
    values: dict[str, Any] = {}

    if leaderboard_frame is not None:
        scoped = leaderboard_frame[
            (leaderboard_frame["model_name"] == model_name)
            & (leaderboard_frame["horizon"] == int(horizon))
        ].copy()
        if not scoped.empty:
            for column in ["net_return", "mdd", "calmar"]:
                if column in scoped.columns:
                    values[column] = _aggregate_numeric(scoped[column], column)
            if values.get("downside_return") is None and "net_return" in scoped.columns:
                net_return = _aggregate_numeric(scoped["net_return"], "net_return")
                if net_return is not None:
                    values["downside_return"] = float(min(float(net_return), 0.0))

    downside_from_backtest = _downside_return_from_backtests(
        backtest_results,
        model_name=model_name,
        horizon=horizon,
    )
    if downside_from_backtest is not None:
        values["downside_return"] = downside_from_backtest

    return values


def _downside_return_from_backtests(
    backtest_results: Iterable[Mapping[str, Any]] | None,
    *,
    model_name: str,
    horizon: int,
) -> float | None:
    if backtest_results is None:
        return None

    downside_values: list[float] = []
    for result in backtest_results:
        config = dict(result.get("config", {}))
        if str(config.get("model_name", "")).lower() != model_name:
            continue
        if int(config.get("horizon", -1)) != int(horizon):
            continue
        equity_curve = result.get("equity_curve")
        if not isinstance(equity_curve, pd.DataFrame) or "net_return" not in equity_curve.columns:
            continue
        net_returns = pd.to_numeric(equity_curve["net_return"], errors="coerce").dropna()
        if net_returns.empty:
            continue
        downside_values.append(float(np.minimum(net_returns.to_numpy(dtype=float), 0.0).mean()))

    if not downside_values:
        return None
    return float(np.mean(downside_values))


def _aggregate_numeric(values: pd.Series, metric_name: str) -> float | None:
    numeric = pd.to_numeric(values, errors="coerce").dropna()
    if numeric.empty:
        return None
    if metric_name == "mdd":
        return float(numeric.min())
    if metric_name == "missing_return_rate":
        return float(numeric.max())
    return float(numeric.mean())


def _append_metric_source(current: Any, source: str) -> str:
    parts = [part for part in str(current or "").split("+") if part]
    if source not in parts:
        parts.append(source)
    return "+".join(parts)
