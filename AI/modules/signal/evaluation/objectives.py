"""모델별 목적 profile 기반 평가 요약."""

from __future__ import annotations

from collections.abc import Mapping
from copy import deepcopy
from typing import Any

import numpy as np
import pandas as pd


ALL_MODEL_HORIZONS = [1, 3, 5, 7]

MODEL_OBJECTIVE_PROFILES = {
    "transformer": {
        "display_name": "Transformer",
        "role": "범용 baseline / calibration anchor",
        "primary_objective": "calibration",
        "primary_metric": "ece",
        "primary_metric_direction": "lower",
        "horizons": ALL_MODEL_HORIZONS,
        "primary_hypothesis_horizons": [1, 3, 5, 7],
        "record_all_horizons": True,
        "metrics": ["brier_score", "log_loss", "ece", "accuracy"],
        "metric_aliases": {},
        "guardrails": {
            "require_all_horizons": True,
            "max_missing_return_rate": 0.0,
            "required_metrics": ["ece", "brier_score", "log_loss", "accuracy"],
            "max_metrics": {"cash_period_rate": 0.99},
        },
    },
    "tcn": {
        "display_name": "TCN",
        "role": "local price action expert",
        "primary_objective": "high_confidence_precision",
        "primary_metric": "high_confidence_precision",
        "primary_metric_direction": "higher",
        "horizons": ALL_MODEL_HORIZONS,
        "primary_hypothesis_horizons": [1, 3],
        "record_all_horizons": True,
        "metrics": [
            "high_confidence_precision",
            "high_confidence_coverage",
            "net_return",
            "universe_excess_return",
            "turnover",
            "selected_periods",
            "cash_period_rate",
        ],
        "metric_aliases": {
            "high_confidence_precision": ["high_confidence_precision", "precision"],
            "high_confidence_coverage": ["high_confidence_coverage", "coverage"],
        },
        "guardrails": {
            "require_all_horizons": True,
            "max_missing_return_rate": 0.0,
            "required_metrics": [
                "high_confidence_precision",
                "high_confidence_coverage",
                "net_return",
                "selected_periods",
                "universe_excess_return",
            ],
            "min_metrics": {
                "high_confidence_coverage": 0.05,
                "selected_periods": 1,
                "universe_excess_return": 0.0,
            },
            "max_metrics": {"cash_period_rate": 0.99},
        },
    },
    "patchtst": {
        "display_name": "PatchTST",
        "role": "short-swing/weekly pattern ranking expert",
        "primary_objective": "ranking",
        "primary_metric": "rank_ic_mean",
        "primary_metric_direction": "higher",
        "horizons": ALL_MODEL_HORIZONS,
        "primary_hypothesis_horizons": [5, 7],
        "record_all_horizons": True,
        "metrics": [
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
            "universe_excess_return",
        ],
        "metric_aliases": {
            "top_k_mean_return": ["top_k_mean_return", "top_k_return"],
        },
        "guardrails": {
            "require_all_horizons": True,
            "max_missing_return_rate": 0.0,
            "required_metrics": [
                "rank_ic_mean",
                "top_bottom_spread",
                "net_return",
                "universe_excess_return",
            ],
            "min_metrics": {
                "top_bottom_spread": 0.0,
                "universe_excess_return": 0.0,
            },
            "max_metrics": {"cash_period_rate": 0.99},
        },
    },
    "itransformer": {
        "display_name": "iTransformer",
        "role": "market regime / correlation / risk-state expert",
        "primary_objective": "risk_control",
        "primary_metric": "calmar",
        "primary_metric_direction": "higher",
        "horizons": ALL_MODEL_HORIZONS,
        "primary_hypothesis_horizons": [3, 5, 7],
        "record_all_horizons": True,
        "metrics": ["mdd", "calmar", "downside_return", "net_return"],
        "metric_aliases": {},
        "guardrails": {
            "require_all_horizons": True,
            "max_missing_return_rate": 0.0,
            "required_metrics": ["mdd", "calmar", "net_return"],
            "max_metrics": {"cash_period_rate": 0.99},
        },
    },
}

OBJECTIVE_FRAME_COLUMNS = [
    "model_name",
    "display_name",
    "strategy_name",
    "role",
    "primary_objective",
    "profile_horizons",
    "primary_hypothesis_horizons",
    "primary_hypothesis_evaluated_horizons",
    "primary_hypothesis_missing_horizons",
    "record_all_horizons",
    "evaluated_horizons",
    "missing_horizons",
    "primary_metric_name",
    "primary_metric_direction",
    "primary_metric_value",
    "best_horizon",
    "objective_score",
    "objective_score_scope",
    "guardrail_pass",
    "guardrail_reasons",
    "row_count",
    "primary_hypothesis_row_count",
    "leaderboard_run_id",
    "prediction_run_id",
    "model_ver",
    "feature_set_ver",
    "train_window",
    "validation_window",
    "brier_score",
    "log_loss",
    "ece",
    "accuracy",
    "high_confidence_precision",
    "high_confidence_coverage",
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
    "universe_equal_net_return",
    "universe_excess_return",
    "turnover",
    "selected_periods",
    "total_periods",
    "cash_period_rate",
    "mdd",
    "calmar",
    "downside_return",
    "missing_return_rate",
]

PORTFOLIO_METRIC_COLUMNS = {
    "net_return",
    "turnover",
    "mdd",
    "calmar",
    "missing_return_rate",
    "selected_periods",
    "total_periods",
    "cash_period_rate",
}

GROUP_KEY_CANDIDATES = [
    "leaderboard_run_id",
    "prediction_run_id",
    "model_ver",
    "feature_set_ver",
    "train_window",
    "validation_window",
    "strategy_name",
    "weighting",
    "top_k",
]

METRIC_AGGREGATION = {
    "mdd": "min",
    "missing_return_rate": "max",
    "cash_period_rate": "max",
    "selected_periods": "sum",
    "total_periods": "sum",
}


def get_model_objective_profile(
    model_name: str,
    *,
    profiles: Mapping[str, Mapping[str, Any]] | None = None,
) -> dict[str, Any]:
    """모델명에 해당하는 objective profile 복사본을 반환한다."""
    if not str(model_name).strip():
        raise ValueError("model_name은 비어 있을 수 없습니다.")
    active_profiles = dict(profiles or MODEL_OBJECTIVE_PROFILES)
    normalized_model_name = str(model_name).lower()
    if normalized_model_name not in active_profiles:
        raise KeyError(f"objective profile이 없는 model_name입니다: {model_name!r}")
    return deepcopy(dict(active_profiles[normalized_model_name]))


def build_model_objective_frame(
    leaderboard_frame: pd.DataFrame,
    *,
    metric_frame: pd.DataFrame | None = None,
    profiles: Mapping[str, Mapping[str, Any]] | None = None,
    missing_model_policy: str = "skip",
) -> pd.DataFrame:
    """공통 leaderboard 위에 모델별 목적 profile 평가 row를 만든다.

    `metric_frame`은 선택 입력이며 `model_name`, `horizon`과 calibration/ranking/
    high-confidence 지표 컬럼을 가진 DataFrame이면 된다.
    """
    if missing_model_policy not in {"skip", "error"}:
        raise ValueError("missing_model_policy는 'skip' 또는 'error'여야 합니다.")
    if not isinstance(leaderboard_frame, pd.DataFrame):
        raise TypeError("leaderboard_frame은 pandas.DataFrame이어야 합니다.")
    if leaderboard_frame.empty:
        raise ValueError("leaderboard_frame은 비어 있을 수 없습니다.")

    active_profiles = dict(profiles or MODEL_OBJECTIVE_PROFILES)
    _validate_profiles(active_profiles)

    full_leaderboard = _prepare_leaderboard_frame(leaderboard_frame, keep_benchmarks=True)
    leaderboard = full_leaderboard[full_leaderboard["model_name"].notna()].copy()
    if leaderboard.empty:
        raise ValueError("모델 row가 있는 leaderboard_frame이 필요합니다.")
    metrics = _prepare_metric_frame(metric_frame)

    unknown_models = sorted(set(leaderboard["model_name"]) - set(active_profiles))
    if unknown_models and missing_model_policy == "error":
        raise ValueError(f"objective profile이 없는 model_name이 있습니다: {unknown_models}")

    rows: list[dict[str, Any]] = []
    for model_name, model_frame in leaderboard.groupby("model_name", sort=True):
        if model_name not in active_profiles:
            continue
        profile = active_profiles[model_name]
        group_keys = _available_group_keys(model_frame)
        for _, strategy_frame in model_frame.groupby(group_keys, dropna=False, sort=True):
            row = _build_objective_row(
                model_name=str(model_name),
                profile=profile,
                leaderboard_subset=strategy_frame,
                full_leaderboard=full_leaderboard,
                metric_frame=metrics,
            )
            if row is not None:
                rows.append(row)

    present_models = set(leaderboard["model_name"]) & set(active_profiles)
    missing_profile_models = sorted(set(active_profiles) - present_models)
    if missing_profile_models and missing_model_policy == "error":
        raise ValueError(f"leaderboard에 없는 profile model이 있습니다: {missing_profile_models}")

    if not rows:
        raise ValueError("생성된 objective row가 없습니다.")

    frame = pd.DataFrame(rows, columns=OBJECTIVE_FRAME_COLUMNS)
    sort_values = pd.to_numeric(frame["objective_score"], errors="coerce")
    frame = frame.assign(_sort_value=sort_values)
    frame = frame.sort_values(
        ["model_name", "_sort_value"],
        ascending=[True, False],
        na_position="last",
    )
    return frame.drop(columns=["_sort_value"]).reset_index(drop=True)


def _validate_profiles(profiles: Mapping[str, Mapping[str, Any]]) -> None:
    if not profiles:
        raise ValueError("profiles는 비어 있을 수 없습니다.")
    for model_name, profile in profiles.items():
        missing = [
            key
            for key in [
                "role",
                "primary_objective",
                "primary_metric",
                "primary_metric_direction",
                "horizons",
                "primary_hypothesis_horizons",
                "record_all_horizons",
                "metrics",
            ]
            if key not in profile
        ]
        if missing:
            raise ValueError(f"{model_name} profile에 필요한 key가 없습니다: {missing}")
        if profile["primary_metric_direction"] not in {"higher", "lower"}:
            raise ValueError(f"{model_name} profile의 primary_metric_direction이 잘못되었습니다.")
        profile_horizons = {int(horizon) for horizon in profile["horizons"]}
        primary_horizons = {int(horizon) for horizon in profile["primary_hypothesis_horizons"]}
        if not profile_horizons:
            raise ValueError(f"{model_name} profile의 horizons는 비어 있을 수 없습니다.")
        if not primary_horizons:
            raise ValueError(f"{model_name} profile의 primary_hypothesis_horizons는 비어 있을 수 없습니다.")
        if not primary_horizons.issubset(profile_horizons):
            raise ValueError(
                f"{model_name} profile의 primary_hypothesis_horizons는 horizons에 포함되어야 합니다."
            )


def _prepare_leaderboard_frame(
    leaderboard_frame: pd.DataFrame,
    *,
    keep_benchmarks: bool = False,
) -> pd.DataFrame:
    required = ["model_name", "horizon", "strategy_name"]
    missing = [column for column in required if column not in leaderboard_frame.columns]
    if missing:
        raise ValueError(f"leaderboard_frame에 필요한 컬럼이 없습니다: {missing}")

    frame = leaderboard_frame.copy()
    model_name = frame["model_name"].astype("string").str.strip()
    has_model = model_name.notna() & (model_name != "")
    if keep_benchmarks and "benchmark_name" in frame.columns:
        benchmark_name = frame["benchmark_name"].astype("string").str.strip()
        has_benchmark = benchmark_name.notna() & (benchmark_name != "")
        frame = frame[has_model | has_benchmark].copy()
    else:
        frame = frame[has_model].copy()
    if frame.empty:
        raise ValueError("모델 row가 있는 leaderboard_frame이 필요합니다.")

    model_name = frame["model_name"].astype("string").str.strip()
    has_model = model_name.notna() & (model_name != "")
    frame.loc[has_model, "model_name"] = model_name[has_model].str.lower()
    frame.loc[~has_model, "model_name"] = pd.NA
    frame["horizon"] = _coerce_int_column(frame, "horizon", "leaderboard_frame")
    return frame


def _prepare_metric_frame(metric_frame: pd.DataFrame | None) -> pd.DataFrame | None:
    if metric_frame is None:
        return None
    if not isinstance(metric_frame, pd.DataFrame):
        raise TypeError("metric_frame은 pandas.DataFrame이어야 합니다.")
    if metric_frame.empty:
        return None
    required = ["model_name", "horizon"]
    missing = [column for column in required if column not in metric_frame.columns]
    if missing:
        raise ValueError(f"metric_frame에 필요한 컬럼이 없습니다: {missing}")

    frame = metric_frame.copy()
    frame["model_name"] = frame["model_name"].astype(str).str.lower()
    frame["horizon"] = _coerce_int_column(frame, "horizon", "metric_frame")
    return frame


def _coerce_int_column(frame: pd.DataFrame, column: str, frame_name: str) -> pd.Series:
    values = pd.to_numeric(frame[column], errors="coerce")
    if values.isna().any() or not values.map(lambda value: float(value).is_integer()).all():
        raise ValueError(f"{frame_name}.{column}은 정수여야 합니다.")
    return values.astype(int)


def _available_group_keys(frame: pd.DataFrame) -> list[str]:
    keys = [key for key in GROUP_KEY_CANDIDATES if key in frame.columns]
    return keys or ["model_name"]


def _build_objective_row(
    *,
    model_name: str,
    profile: Mapping[str, Any],
    leaderboard_subset: pd.DataFrame,
    full_leaderboard: pd.DataFrame,
    metric_frame: pd.DataFrame | None,
) -> dict[str, Any] | None:
    profile_horizons = [int(horizon) for horizon in profile["horizons"]]
    primary_hypothesis_horizons = [
        int(horizon)
        for horizon in profile.get("primary_hypothesis_horizons", profile_horizons)
    ]
    record_all_horizons = bool(profile.get("record_all_horizons", True))
    scoped_leaderboard = leaderboard_subset[
        leaderboard_subset["horizon"].isin(profile_horizons)
    ].copy()
    if scoped_leaderboard.empty:
        return None

    primary_leaderboard = scoped_leaderboard[
        scoped_leaderboard["horizon"].isin(primary_hypothesis_horizons)
    ].copy()
    metric_subset = _filter_metric_frame(
        metric_frame,
        model_name=model_name,
        horizons=primary_hypothesis_horizons,
        leaderboard_subset=scoped_leaderboard,
    )
    metrics = _collect_metrics(
        profile,
        primary_leaderboard,
        metric_subset,
        full_leaderboard=full_leaderboard,
    )
    evaluated_horizons = sorted(scoped_leaderboard["horizon"].unique().tolist())
    primary_hypothesis_evaluated_horizons = sorted(
        primary_leaderboard["horizon"].unique().tolist()
    )
    missing_horizons = sorted(set(profile_horizons) - set(evaluated_horizons))
    primary_hypothesis_missing_horizons = sorted(
        set(primary_hypothesis_horizons) - set(primary_hypothesis_evaluated_horizons)
    )
    primary_metric_name = str(profile["primary_metric"])
    direction = str(profile["primary_metric_direction"])
    best_horizon = _best_horizon(
        model_name=model_name,
        profile=profile,
        leaderboard_subset=primary_leaderboard,
        metric_frame=metric_frame,
    )
    primary_metric_value = _primary_metric_value_for_horizon(
        model_name=model_name,
        profile=profile,
        leaderboard_subset=primary_leaderboard,
        metric_frame=metric_frame,
        best_horizon=best_horizon,
    )
    if primary_metric_value is None:
        primary_metric_value = metrics.get(primary_metric_name)
    objective_score = _objective_score(primary_metric_value, direction)
    guardrail_pass, guardrail_reasons = _evaluate_guardrails(
        profile=profile,
        metrics=metrics,
        missing_horizons=primary_hypothesis_missing_horizons,
        primary_metric_value=primary_metric_value,
    )

    row = {
        "model_name": model_name,
        "display_name": profile.get("display_name", model_name),
        "strategy_name": _first_value(scoped_leaderboard, "strategy_name"),
        "role": profile["role"],
        "primary_objective": profile["primary_objective"],
        "profile_horizons": profile_horizons,
        "primary_hypothesis_horizons": primary_hypothesis_horizons,
        "primary_hypothesis_evaluated_horizons": primary_hypothesis_evaluated_horizons,
        "primary_hypothesis_missing_horizons": primary_hypothesis_missing_horizons,
        "record_all_horizons": record_all_horizons,
        "evaluated_horizons": evaluated_horizons,
        "missing_horizons": missing_horizons,
        "primary_metric_name": primary_metric_name,
        "primary_metric_direction": direction,
        "primary_metric_value": primary_metric_value,
        "best_horizon": best_horizon,
        "objective_score": objective_score,
        "objective_score_scope": "same_model_profile_only",
        "guardrail_pass": guardrail_pass,
        "guardrail_reasons": "; ".join(guardrail_reasons),
        "row_count": int(len(scoped_leaderboard)),
        "primary_hypothesis_row_count": int(len(primary_leaderboard)),
        "leaderboard_run_id": _first_value(scoped_leaderboard, "leaderboard_run_id"),
        "prediction_run_id": _first_value(scoped_leaderboard, "prediction_run_id"),
        "model_ver": _first_value(scoped_leaderboard, "model_ver"),
        "feature_set_ver": _first_value(scoped_leaderboard, "feature_set_ver"),
        "train_window": _first_value(scoped_leaderboard, "train_window"),
        "validation_window": _first_value(scoped_leaderboard, "validation_window"),
    }
    for metric_name in OBJECTIVE_FRAME_COLUMNS:
        if metric_name not in row:
            row[metric_name] = _clean_scalar(metrics.get(metric_name))
    return {column: _clean_scalar(row.get(column)) for column in OBJECTIVE_FRAME_COLUMNS}


def _filter_metric_frame(
    metric_frame: pd.DataFrame | None,
    *,
    model_name: str,
    horizons: list[int],
    leaderboard_subset: pd.DataFrame,
) -> pd.DataFrame | None:
    if metric_frame is None:
        return None
    scoped = metric_frame[
        (metric_frame["model_name"] == model_name)
        & (metric_frame["horizon"].isin(horizons))
    ].copy()
    if scoped.empty:
        return None

    # metric_frame이 strategy_name 같은 구분 컬럼을 갖고 있으면 같은 전략만 붙인다.
    for key in ["strategy_name", "leaderboard_run_id", "prediction_run_id", "weighting", "top_k"]:
        if key in scoped.columns and key in leaderboard_subset.columns:
            allowed_values = set(leaderboard_subset[key].dropna().unique().tolist())
            scoped = scoped[scoped[key].isin(allowed_values)].copy()
    if scoped.empty:
        return None
    return scoped


def _best_horizon(
    *,
    model_name: str,
    profile: Mapping[str, Any],
    leaderboard_subset: pd.DataFrame,
    metric_frame: pd.DataFrame | None,
) -> int | None:
    """전체 기록 horizon 중 primary metric이 가장 좋은 horizon을 찾는다."""
    primary_metric_name = str(profile["primary_metric"])
    direction = str(profile["primary_metric_direction"])
    aliases = profile.get("metric_aliases", {})
    candidates = aliases.get(primary_metric_name, [primary_metric_name])
    horizon_scores: list[tuple[int, float]] = []

    for horizon in sorted(leaderboard_subset["horizon"].unique().tolist()):
        horizon_leaderboard = leaderboard_subset[leaderboard_subset["horizon"] == horizon].copy()
        horizon_metric = _filter_metric_frame(
            metric_frame,
            model_name=model_name,
            horizons=[int(horizon)],
            leaderboard_subset=horizon_leaderboard,
        )
        value = _resolve_metric_value(
            metric_name=primary_metric_name,
            candidates=candidates,
            leaderboard_subset=horizon_leaderboard,
            metric_subset=horizon_metric,
        )
        numeric = pd.to_numeric(pd.Series([value]), errors="coerce").iloc[0]
        if not pd.isna(numeric):
            horizon_scores.append((int(horizon), float(numeric)))

    if not horizon_scores:
        return None
    if direction == "lower":
        return min(horizon_scores, key=lambda item: item[1])[0]
    return max(horizon_scores, key=lambda item: item[1])[0]


def _primary_metric_value_for_horizon(
    *,
    model_name: str,
    profile: Mapping[str, Any],
    leaderboard_subset: pd.DataFrame,
    metric_frame: pd.DataFrame | None,
    best_horizon: int | None,
) -> Any:
    if best_horizon is None:
        return None
    primary_metric_name = str(profile["primary_metric"])
    aliases = profile.get("metric_aliases", {})
    candidates = aliases.get(primary_metric_name, [primary_metric_name])
    horizon_leaderboard = leaderboard_subset[
        leaderboard_subset["horizon"] == int(best_horizon)
    ].copy()
    if horizon_leaderboard.empty:
        return None
    horizon_metric = _filter_metric_frame(
        metric_frame,
        model_name=model_name,
        horizons=[int(best_horizon)],
        leaderboard_subset=horizon_leaderboard,
    )
    return _resolve_metric_value(
        metric_name=primary_metric_name,
        candidates=candidates,
        leaderboard_subset=horizon_leaderboard,
        metric_subset=horizon_metric,
    )


def _collect_metrics(
    profile: Mapping[str, Any],
    leaderboard_subset: pd.DataFrame,
    metric_subset: pd.DataFrame | None,
    *,
    full_leaderboard: pd.DataFrame,
) -> dict[str, Any]:
    collected: dict[str, Any] = {}
    aliases = profile.get("metric_aliases", {})
    for metric_name in profile["metrics"]:
        candidates = aliases.get(metric_name, [metric_name])
        collected[metric_name] = _resolve_metric_value(
            metric_name=metric_name,
            candidates=candidates,
            leaderboard_subset=leaderboard_subset,
            metric_subset=metric_subset,
        )

    for metric_name in PORTFOLIO_METRIC_COLUMNS:
        if metric_name not in collected and metric_name in leaderboard_subset.columns:
            collected[metric_name] = _aggregate_numeric(leaderboard_subset[metric_name], metric_name)

    if "downside_return" in profile["metrics"] and collected.get("downside_return") is None:
        collected["downside_return"] = _downside_return(leaderboard_subset)
    if (
        "universe_equal_net_return" in profile["metrics"]
        or "universe_excess_return" in profile["metrics"]
    ):
        collected.update(_universe_excess_metrics(leaderboard_subset, full_leaderboard))
    return collected


def _resolve_metric_value(
    *,
    metric_name: str,
    candidates: list[str],
    leaderboard_subset: pd.DataFrame,
    metric_subset: pd.DataFrame | None,
) -> Any:
    if metric_name in PORTFOLIO_METRIC_COLUMNS and metric_name in leaderboard_subset.columns:
        return _aggregate_numeric(leaderboard_subset[metric_name], metric_name)

    if metric_subset is not None:
        for candidate in candidates:
            if candidate in metric_subset.columns:
                return _aggregate_numeric(metric_subset[candidate], metric_name)

    for candidate in candidates:
        if candidate in leaderboard_subset.columns:
            return _aggregate_numeric(leaderboard_subset[candidate], metric_name)
    return None


def _aggregate_numeric(values: pd.Series, metric_name: str) -> float | None:
    numeric = pd.to_numeric(values, errors="coerce").dropna()
    if numeric.empty:
        return None
    aggregation = METRIC_AGGREGATION.get(metric_name, "mean")
    if aggregation == "min":
        return float(numeric.min())
    if aggregation == "max":
        return float(numeric.max())
    if aggregation == "sum":
        return float(numeric.sum())
    return float(numeric.mean())


def _downside_return(leaderboard_subset: pd.DataFrame) -> float | None:
    if "net_return" not in leaderboard_subset.columns:
        return None
    net_return = pd.to_numeric(leaderboard_subset["net_return"], errors="coerce").dropna()
    if net_return.empty:
        return None
    return float(np.minimum(net_return.to_numpy(dtype=float), 0.0).mean())


def _has_value(value: Any) -> bool:
    cleaned = _clean_scalar(value)
    return cleaned is not None and str(cleaned).strip() != ""


def _universe_excess_metrics(
    model_subset: pd.DataFrame,
    full_leaderboard: pd.DataFrame,
) -> dict[str, Any]:
    """같은 run/horizon의 universe_equal 대비 초과수익을 계산한다."""
    required = {"benchmark_name", "horizon", "net_return"}
    if not required.issubset(full_leaderboard.columns) or "net_return" not in model_subset.columns:
        return {"universe_equal_net_return": None, "universe_excess_return": None}

    benchmark_name = full_leaderboard["benchmark_name"].astype("string").str.strip().str.lower()
    benchmarks = full_leaderboard[benchmark_name == "universe_equal"].copy()
    if benchmarks.empty:
        return {"universe_equal_net_return": None, "universe_excess_return": None}

    benchmark_values: list[float] = []
    excess_values: list[float] = []
    match_keys = [
        "leaderboard_run_id",
        "prediction_run_id",
        "train_window",
        "validation_window",
        "horizon",
    ]

    for _, model_row in model_subset.iterrows():
        model_return = pd.to_numeric(pd.Series([model_row.get("net_return")]), errors="coerce").iloc[0]
        if pd.isna(model_return):
            continue

        candidates = benchmarks.copy()
        for key in match_keys:
            if key not in candidates.columns or key not in model_subset.columns:
                continue
            value = model_row.get(key)
            if not _has_value(value):
                continue
            narrowed = candidates[candidates[key] == value].copy()
            if not narrowed.empty:
                candidates = narrowed

        if candidates.empty:
            continue
        benchmark_return = pd.to_numeric(candidates["net_return"], errors="coerce").dropna()
        if benchmark_return.empty:
            continue
        benchmark_mean = float(benchmark_return.mean())
        model_return_float = float(model_return)
        benchmark_values.append(benchmark_mean)
        excess_values.append(model_return_float - benchmark_mean)

    return {
        "universe_equal_net_return": float(np.mean(benchmark_values)) if benchmark_values else None,
        "universe_excess_return": float(np.mean(excess_values)) if excess_values else None,
    }


def _objective_score(value: Any, direction: str) -> float | None:
    if value is None:
        return None
    numeric = pd.to_numeric(pd.Series([value]), errors="coerce").iloc[0]
    if pd.isna(numeric):
        return None
    score = float(numeric)
    if direction == "lower":
        return -score
    return score


def _evaluate_guardrails(
    *,
    profile: Mapping[str, Any],
    metrics: Mapping[str, Any],
    missing_horizons: list[int],
    primary_metric_value: Any,
) -> tuple[bool, list[str]]:
    guardrails = profile.get("guardrails", {})
    reasons: list[str] = []
    if guardrails.get("require_all_horizons", True) and missing_horizons:
        reasons.append(f"필수 horizon 누락: {missing_horizons}")
    if primary_metric_value is None:
        reasons.append(f"primary metric 누락: {profile['primary_metric']}")

    for metric_name in guardrails.get("required_metrics", []):
        if metrics.get(metric_name) is None:
            reasons.append(f"필수 metric 누락: {metric_name}")

    for metric_name, minimum_value in guardrails.get("min_metrics", {}).items():
        metric_value = metrics.get(metric_name)
        if metric_value is None:
            reasons.append(f"최소값 guardrail metric 누락: {metric_name}")
            continue
        if float(metric_value) < float(minimum_value):
            reasons.append(
                "최소값 guardrail 미달: "
                f"{metric_name}={metric_value} < {minimum_value}"
            )

    for metric_name, maximum_value in guardrails.get("max_metrics", {}).items():
        metric_value = metrics.get(metric_name)
        if metric_value is None:
            reasons.append(f"최대값 guardrail metric 누락: {metric_name}")
            continue
        if float(metric_value) > float(maximum_value):
            reasons.append(
                "최대값 guardrail 초과: "
                f"{metric_name}={metric_value} > {maximum_value}"
            )

    max_missing_return_rate = guardrails.get("max_missing_return_rate")
    missing_return_rate = metrics.get("missing_return_rate")
    if (
        max_missing_return_rate is not None
        and missing_return_rate is not None
        and float(missing_return_rate) > float(max_missing_return_rate)
    ):
        reasons.append(
            "missing_return_rate 초과: "
            f"{missing_return_rate} > {max_missing_return_rate}"
        )
    return len(reasons) == 0, reasons


def _first_value(frame: pd.DataFrame, column: str) -> Any:
    if column not in frame.columns or frame.empty:
        return None
    value = frame[column].dropna()
    if value.empty:
        return None
    return _clean_scalar(value.iloc[0])


def _clean_scalar(value: Any) -> Any:
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
