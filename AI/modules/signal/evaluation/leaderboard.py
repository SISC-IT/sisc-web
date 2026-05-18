"""Signal 평가 결과를 비교 가능한 leaderboard row로 정규화한다."""

from __future__ import annotations

from pathlib import Path
from typing import Any

import pandas as pd


LEADERBOARD_V0_COLUMNS = [
    "leaderboard_run_id",
    "prediction_run_id",
    "model_name",
    "benchmark_name",
    "strategy_name",
    "model_ver",
    "feature_set_ver",
    "train_window",
    "validation_window",
    "horizon",
    "weighting",
    "top_k",
    "buy_threshold",
    "confidence_threshold",
    "cost_bps_per_side",
    "periods_per_year",
    "primary_metric_name",
    "primary_metric_value",
    "cumulative_return",
    "annualized_return",
    "annualized_volatility",
    "sharpe",
    "mdd",
    "calmar",
    "turnover",
    "gross_return",
    "net_return",
    "cost_paid",
    "missing_return_count",
    "missing_return_rate",
    "selected_periods",
    "total_periods",
    "avg_selected_count",
    "cash_period_rate",
    "start_equity",
    "end_equity",
    "note",
]


def _clean_scalar(value: Any) -> Any:
    """pandas/numpy 결측 스칼라를 leaderboard에서 쓰기 쉬운 None으로 바꾼다."""
    if value is None:
        return None
    try:
        is_missing = pd.isna(value)
    except (TypeError, ValueError):
        is_missing = False
    if isinstance(is_missing, bool) and is_missing:
        return None
    if hasattr(value, "item"):
        try:
            return value.item()
        except ValueError:
            return value
    return value


def _as_frame(value: Any, name: str) -> pd.DataFrame:
    if not isinstance(value, pd.DataFrame):
        raise TypeError(f"{name}은 pandas.DataFrame이어야 합니다.")
    return value


def _as_dict(value: Any, name: str) -> dict:
    if not isinstance(value, dict):
        raise TypeError(f"{name}은 dict여야 합니다.")
    return value


def _metadata_value(explicit_value: Any, config: dict, key: str, default: Any = "unknown") -> Any:
    if explicit_value is not None:
        return explicit_value
    return _config(config, key, default)


def _require_backtest_result(backtest_result: dict) -> tuple[pd.DataFrame, pd.DataFrame, dict, dict]:
    if not isinstance(backtest_result, dict):
        raise TypeError("backtest_result는 dict여야 합니다.")

    missing_keys = [
        key
        for key in ["equity_curve", "trades", "metrics", "config"]
        if key not in backtest_result
    ]
    if missing_keys:
        raise ValueError(f"backtest_result에 필요한 key가 없습니다: {missing_keys}")

    equity_curve = _as_frame(backtest_result["equity_curve"], "equity_curve")
    trades = _as_frame(backtest_result["trades"], "trades")
    metrics = _as_dict(backtest_result["metrics"], "metrics")
    config = _as_dict(backtest_result["config"], "config")

    if equity_curve.empty:
        raise ValueError("equity_curve는 비어 있을 수 없습니다.")
    if "horizon" not in config and "horizon" not in equity_curve.columns:
        raise ValueError("horizon은 config 또는 equity_curve에 있어야 합니다.")

    return equity_curve, trades, metrics, config


def _first_column_value(frame: pd.DataFrame, column: str) -> Any:
    if column not in frame.columns or frame.empty:
        return None
    return _clean_scalar(frame[column].iloc[0])


def _metric(metrics: dict, key: str, default: Any = None) -> Any:
    return _clean_scalar(metrics.get(key, default))


def _config(config: dict, key: str, default: Any = None) -> Any:
    return _clean_scalar(config.get(key, default))


def _compound_return(frame: pd.DataFrame, column: str) -> float | None:
    if column not in frame.columns or frame.empty:
        return None
    values = pd.to_numeric(frame[column], errors="coerce")
    if values.isna().any():
        return None
    return float((1.0 + values).prod() - 1.0)


def _sum_column(frame: pd.DataFrame, column: str) -> float | None:
    if column not in frame.columns:
        return None
    values = pd.to_numeric(frame[column], errors="coerce")
    if values.isna().any():
        return None
    return float(values.sum())


def _mean_column(frame: pd.DataFrame, column: str) -> float | None:
    if column not in frame.columns or frame.empty:
        return None
    values = pd.to_numeric(frame[column], errors="coerce")
    if values.isna().any():
        return None
    return float(values.mean())


def _count_selected_periods(equity_curve: pd.DataFrame) -> int | None:
    if "selected_count" not in equity_curve.columns:
        return None
    selected_count = pd.to_numeric(equity_curve["selected_count"], errors="coerce")
    if selected_count.isna().any():
        return None
    return int((selected_count > 0).sum())


def _cash_period_rate(equity_curve: pd.DataFrame) -> float | None:
    if equity_curve.empty:
        return None
    if "selected_count" in equity_curve.columns:
        selected_count = pd.to_numeric(equity_curve["selected_count"], errors="coerce")
        if selected_count.isna().any():
            return None
        return float((selected_count == 0).mean())
    if "cash_weight" in equity_curve.columns:
        cash_weight = pd.to_numeric(equity_curve["cash_weight"], errors="coerce")
        if cash_weight.isna().any():
            return None
        return float((cash_weight >= 1.0 - 1e-12).mean())
    return None


def _numeric_or_none(value: Any) -> float | None:
    value = _clean_scalar(value)
    if value is None:
        return None
    numeric = pd.to_numeric(pd.Series([value]), errors="coerce").iloc[0]
    if pd.isna(numeric):
        return None
    return float(numeric)


def _augment_note_with_diagnostics(note: str, row: dict) -> str:
    """무효에 가까운 평가 상태를 leaderboard note에 자동으로 남긴다."""
    parts = [part.strip() for part in str(note or "").split(";") if part.strip()]
    selected_periods = _numeric_or_none(row.get("selected_periods"))
    cash_period_rate = _numeric_or_none(row.get("cash_period_rate"))
    missing_return_rate = _numeric_or_none(row.get("missing_return_rate"))

    if selected_periods == 0:
        parts.append("전기간현금:선택기간0")
    elif cash_period_rate is not None and cash_period_rate >= 1.0 - 1e-12:
        parts.append("전기간현금")

    if missing_return_rate is not None and missing_return_rate > 0.0:
        parts.append(f"수익률누락률={missing_return_rate:.6g}")

    deduped = list(dict.fromkeys(parts))
    return "; ".join(deduped)


def _resolve_model_name(config: dict, equity_curve: pd.DataFrame) -> Any:
    model_name = _config(config, "model_name")
    if model_name is not None:
        return model_name
    if _config(config, "benchmark_name") is not None:
        return None
    return _first_column_value(equity_curve, "model_name")


def _resolve_strategy_name(config: dict, model_name: Any, benchmark_name: Any) -> str:
    strategy_name = _config(config, "strategy_name")
    if strategy_name:
        return str(strategy_name)
    if benchmark_name:
        return str(benchmark_name)
    if model_name:
        weighting = _config(config, "weighting", "unknown")
        top_k = _config(config, "top_k")
        if top_k is None:
            return f"{model_name}_{weighting}"
        return f"{model_name}_{weighting}_top{top_k}"
    return "unknown"


def _resolve_periods_per_year(metrics: dict, config: dict, equity_curve: pd.DataFrame) -> Any:
    value = _metric(metrics, "periods_per_year")
    if value is not None:
        return value
    value = _config(config, "periods_per_year")
    if value is not None:
        return value
    return _first_column_value(equity_curve, "periods_per_year")


def _resolve_primary_metric(row: dict, metrics: dict, config: dict, primary_metric_name: str | None) -> tuple[str, Any]:
    # v0 기본 정렬 기준은 비용 반영 후 누적 성과인 net_return이다.
    resolved_name = primary_metric_name or "net_return"
    value = row.get(resolved_name)
    if value is None:
        value = _metric(metrics, resolved_name)
    if value is None:
        value = _config(config, resolved_name)
    if value is None:
        raise ValueError(f"primary metric 값을 찾을 수 없습니다: {resolved_name}")
    return resolved_name, value


def build_leaderboard_row(
    backtest_result: dict,
    *,
    leaderboard_run_id: str,
    prediction_run_id: str | None = None,
    model_ver: str | None = None,
    feature_set_ver: str | None = None,
    train_window: str | None = None,
    validation_window: str | None = None,
    primary_metric_name: str | None = None,
    note: str = "",
) -> dict:
    """backtest 결과 1개를 leaderboard v0 row 1개로 요약한다."""
    if not str(leaderboard_run_id).strip():
        raise ValueError("leaderboard_run_id는 비어 있을 수 없습니다.")

    equity_curve, trades, metrics, config = _require_backtest_result(backtest_result)

    benchmark_name = _config(config, "benchmark_name")
    model_name = _resolve_model_name(config, equity_curve)
    if model_name is None and benchmark_name is None:
        raise ValueError("model_name 또는 benchmark_name 중 하나는 있어야 합니다.")

    horizon = _config(config, "horizon", _first_column_value(equity_curve, "horizon"))
    if horizon is None:
        raise ValueError("horizon을 확인할 수 없습니다.")

    cumulative_return = _metric(metrics, "cumulative_return")
    net_return = cumulative_return
    if net_return is None:
        net_return = _compound_return(equity_curve, "net_return")

    gross_return = _compound_return(equity_curve, "gross_return")
    cost_paid = _metric(metrics, "cost_paid")
    if cost_paid is None:
        cost_paid = _sum_column(trades, "cost")
    if cost_paid is None:
        cost_paid = _sum_column(equity_curve, "cost_paid")

    turnover = _metric(metrics, "turnover")
    if turnover is None:
        turnover = _sum_column(trades, "turnover")

    # 과거 결과나 universe benchmark에는 누락 수익률 필드가 없을 수 있다.
    # v0에서는 값이 없으면 누락이 없었던 결과로 보고 0을 채운다.
    missing_return_count = _metric(metrics, "missing_return_count", 0)
    missing_return_rate = _metric(metrics, "missing_return_rate", 0.0)

    row = {
        "leaderboard_run_id": str(leaderboard_run_id),
        "prediction_run_id": _metadata_value(prediction_run_id, config, "prediction_run_id"),
        "model_name": model_name,
        "benchmark_name": benchmark_name,
        "strategy_name": _resolve_strategy_name(config, model_name, benchmark_name),
        "model_ver": _metadata_value(model_ver, config, "model_ver"),
        "feature_set_ver": _metadata_value(feature_set_ver, config, "feature_set_ver"),
        "train_window": _metadata_value(train_window, config, "train_window"),
        "validation_window": _metadata_value(validation_window, config, "validation_window"),
        "horizon": horizon,
        "weighting": _config(config, "weighting", _first_column_value(equity_curve, "weighting")),
        "top_k": _config(config, "top_k"),
        "buy_threshold": _config(config, "buy_threshold"),
        "confidence_threshold": _config(config, "confidence_threshold"),
        "cost_bps_per_side": _config(config, "cost_bps_per_side"),
        "periods_per_year": _resolve_periods_per_year(metrics, config, equity_curve),
        "primary_metric_name": None,
        "primary_metric_value": None,
        "cumulative_return": cumulative_return,
        "annualized_return": _metric(metrics, "annualized_return"),
        "annualized_volatility": _metric(metrics, "annualized_volatility"),
        "sharpe": _metric(metrics, "sharpe"),
        "mdd": _metric(metrics, "mdd"),
        "calmar": _metric(metrics, "calmar"),
        "turnover": turnover,
        "gross_return": gross_return,
        "net_return": net_return,
        "cost_paid": cost_paid,
        "missing_return_count": missing_return_count,
        "missing_return_rate": missing_return_rate,
        "selected_periods": _count_selected_periods(equity_curve),
        "total_periods": int(len(equity_curve)),
        "avg_selected_count": _mean_column(equity_curve, "selected_count"),
        "cash_period_rate": _cash_period_rate(equity_curve),
        "start_equity": _metric(metrics, "start_equity"),
        "end_equity": _metric(metrics, "end_equity"),
        "note": note,
    }
    row["note"] = _augment_note_with_diagnostics(note, row)

    resolved_metric_name, resolved_metric_value = _resolve_primary_metric(
        row,
        metrics,
        config,
        primary_metric_name,
    )
    row["primary_metric_name"] = resolved_metric_name
    row["primary_metric_value"] = resolved_metric_value

    return {column: _clean_scalar(row.get(column)) for column in LEADERBOARD_V0_COLUMNS}


def build_leaderboard(
    backtest_results: list[dict],
    *,
    leaderboard_run_id: str,
    prediction_run_id: str | None = None,
    model_ver: str | None = None,
    feature_set_ver: str | None = None,
    train_window: str | None = None,
    validation_window: str | None = None,
    primary_metric_name: str | None = None,
) -> pd.DataFrame:
    """여러 backtest 결과를 leaderboard DataFrame으로 묶는다."""
    if not backtest_results:
        raise ValueError("backtest_results는 비어 있을 수 없습니다.")

    rows = [
        build_leaderboard_row(
            result,
            leaderboard_run_id=leaderboard_run_id,
            prediction_run_id=prediction_run_id,
            model_ver=model_ver,
            feature_set_ver=feature_set_ver,
            train_window=train_window,
            validation_window=validation_window,
            primary_metric_name=primary_metric_name,
        )
        for result in backtest_results
    ]
    frame = pd.DataFrame(rows, columns=LEADERBOARD_V0_COLUMNS)

    # v0 leaderboard는 비용 반영 후 primary metric이 큰 전략을 위에 둔다.
    sort_values = pd.to_numeric(frame["primary_metric_value"], errors="coerce")
    frame = frame.assign(_sort_value=sort_values)
    frame = frame.sort_values("_sort_value", ascending=False, na_position="last")
    return frame.drop(columns=["_sort_value"]).reset_index(drop=True)


def validate_leaderboard_frame(leaderboard_frame: pd.DataFrame) -> None:
    """저장 가능한 Leaderboard v0 스키마인지 검증한다."""
    if not isinstance(leaderboard_frame, pd.DataFrame):
        raise TypeError("leaderboard_frame은 pandas.DataFrame이어야 합니다.")
    if leaderboard_frame.empty:
        raise ValueError("leaderboard_frame은 비어 있을 수 없습니다.")

    columns = list(leaderboard_frame.columns)
    missing = [column for column in LEADERBOARD_V0_COLUMNS if column not in columns]
    extra = [column for column in columns if column not in LEADERBOARD_V0_COLUMNS]
    if missing:
        raise ValueError(f"leaderboard_frame에 필요한 컬럼이 없습니다: {missing}")
    if extra:
        raise ValueError(f"leaderboard_frame에 허용되지 않는 추가 컬럼이 있습니다: {extra}")


def save_leaderboard(
    leaderboard_frame,
    output_path: str,
) -> None:
    """leaderboard DataFrame을 CSV로 저장한다."""
    validate_leaderboard_frame(leaderboard_frame)

    path = Path(output_path)
    if path.suffix.lower() != ".csv":
        raise ValueError("leaderboard 저장 경로는 .csv 확장자여야 합니다.")

    path.parent.mkdir(parents=True, exist_ok=True)
    leaderboard_frame[LEADERBOARD_V0_COLUMNS].to_csv(path, index=False)
