"""Signal evaluation smoke runner v0.

작은 signal frame과 realized forward return으로 schema, backtest, leaderboard를
한 번에 연결해 보는 검증용 runner다. 학습, MoE, wrapper 내부 수정은 하지 않는다.
"""

from __future__ import annotations

import json
import re
from collections.abc import Iterable, Mapping
from pathlib import Path
from typing import Any

import pandas as pd

from AI.modules.signal.evaluation.backtest import (
    backtest_top_k_signals,
    universe_equal_benchmark,
)
from AI.modules.signal.evaluation.diagnostics import build_signal_diagnostics_frame
from AI.modules.signal.evaluation.leaderboard import (
    build_leaderboard,
    save_leaderboard,
)
from AI.modules.signal.evaluation.model_metrics import build_model_metric_frame
from AI.modules.signal.evaluation.objectives import build_model_objective_frame
from AI.modules.signal.evaluation.schema import (
    normalize_signal_output,
    validate_signal_frame,
)


DEFAULT_SMOKE_CONFIG = {
    "horizons": [1, 3, 5, 7],
    "require_all_horizons": True,
    "include_statuses": ("ok",),
    "include_universe_benchmark": True,
    "prediction_run_id": "unknown",
    "model_ver": "unknown",
    "feature_set_ver": "unknown",
    "train_window": "unknown",
    "validation_window": "unknown",
    "save_signal_frame": True,
    "save_backtest_details": True,
}

SIGNAL_RUNNER_REQUIRED_COLUMNS = [
    "asof_date",
    "decision_time",
    "ticker",
    "model_name",
    "horizon",
    "prob_up",
    "confidence",
    "prediction_status",
]

REALIZED_RETURNS_REQUIRED_COLUMNS = [
    "asof_date",
    "ticker",
    "horizon",
    "forward_return",
]

_SAFE_FILENAME_PATTERN = re.compile(r"[^A-Za-z0-9_.-]+")


def load_smoke_config(config: Mapping[str, Any] | str | Path | None = None) -> dict:
    """기본 smoke 설정 위에 dict 또는 JSON 파일 설정을 덮어쓴다."""
    loaded = dict(DEFAULT_SMOKE_CONFIG)
    if config is not None:
        if isinstance(config, (str, Path)):
            path = Path(config)
            if path.suffix.lower() != ".json":
                raise ValueError("smoke config 파일은 JSON만 지원합니다.")
            with path.open("r", encoding="utf-8") as file:
                user_config = json.load(file)
        elif isinstance(config, Mapping):
            user_config = dict(config)
        else:
            raise TypeError("config는 dict, Mapping, JSON 경로 또는 None이어야 합니다.")

        loaded.update(user_config)

    loaded["horizons"] = _coerce_horizons(loaded.get("horizons"))
    loaded["include_statuses"] = _coerce_include_statuses(
        loaded.get("include_statuses", ("ok",))
    )
    loaded["require_all_horizons"] = bool(loaded.get("require_all_horizons", True))
    loaded["include_universe_benchmark"] = bool(loaded.get("include_universe_benchmark", True))
    loaded["save_signal_frame"] = bool(loaded.get("save_signal_frame", True))
    loaded["save_backtest_details"] = bool(loaded.get("save_backtest_details", True))
    return loaded


def normalize_smoke_prediction_outputs(
    prediction_outputs: Iterable[Mapping[str, Any]],
    *,
    run_id: str,
    model_ver: str = "unknown",
    feature_set_ver: str = "unknown",
    train_window: str = "unknown",
    eval_window: str = "unknown",
    buy_threshold: float = 0.6,
    confidence_threshold: float = 0.0,
) -> pd.DataFrame:
    """저장된 wrapper 출력 record들을 Signal Schema v0 frame으로 바꾼다.

    각 record는 최소 `output`, `asof_date`, `decision_time`, `ticker`를 가져야 한다.
    `output`은 `{"tcn_1d": 0.61}` 같은 wrapper 출력 dict다.
    """
    frames: list[pd.DataFrame] = []
    for index, record in enumerate(prediction_outputs):
        if not isinstance(record, Mapping):
            raise TypeError(f"prediction_outputs[{index}]는 Mapping이어야 합니다.")
        missing = [
            key
            for key in ["output", "asof_date", "decision_time", "ticker"]
            if key not in record
        ]
        if missing:
            raise ValueError(f"prediction_outputs[{index}]에 필요한 key가 없습니다: {missing}")

        frames.append(
            normalize_signal_output(
                dict(record["output"]),
                asof_date=record["asof_date"],
                decision_time=record["decision_time"],
                ticker=str(record["ticker"]),
                model_name=record.get("model_name"),
                run_id=str(record.get("run_id", run_id)),
                model_ver=str(record.get("model_ver", model_ver)),
                feature_set_ver=str(record.get("feature_set_ver", feature_set_ver)),
                train_window=str(record.get("train_window", train_window)),
                eval_window=str(record.get("eval_window", eval_window)),
                fold_id=record.get("fold_id"),
                seq_len=record.get("seq_len"),
                scaler_ver=str(record.get("scaler_ver", "unknown")),
                artifact_path=str(record.get("artifact_path", "")),
                feature_count=record.get("feature_count"),
                buy_threshold=buy_threshold,
                confidence_threshold=confidence_threshold,
                prediction_status=str(record.get("prediction_status", "ok")),
                prediction_status_map=record.get("prediction_status_map"),
                error_message=str(record.get("error_message", "")),
                error_message_map=record.get("error_message_map"),
            )
        )

    if not frames:
        raise ValueError("prediction_outputs는 최소 1개 이상의 record가 필요합니다.")

    signal_frame = pd.concat(frames, ignore_index=True)
    signal_frame = _prepare_signal_frame(signal_frame)
    validate_signal_frame(signal_frame)
    return signal_frame


def run_smoke_evaluation(
    *,
    signal_frame: pd.DataFrame | None = None,
    prediction_outputs: Iterable[Mapping[str, Any]] | None = None,
    realized_returns: pd.DataFrame,
    leaderboard_run_id: str,
    output_dir: str,
    top_k: int = 5,
    weighting: str = "equal",
    buy_threshold: float = 0.6,
    confidence_threshold: float = 0.0,
    cost_bps_per_side: float = 5.0,
    missing_return_policy: str = "error",
    config: Mapping[str, Any] | str | Path | None = None,
) -> dict:
    """정규화된 signal과 realized return으로 end-to-end smoke 평가를 실행한다."""
    smoke_config = load_smoke_config(config)
    if not str(leaderboard_run_id).strip():
        raise ValueError("leaderboard_run_id는 비어 있을 수 없습니다.")
    if signal_frame is not None and prediction_outputs is not None:
        raise ValueError("signal_frame과 prediction_outputs는 동시에 지정할 수 없습니다.")

    if signal_frame is None:
        if prediction_outputs is None:
            raise ValueError("signal_frame 또는 prediction_outputs 중 하나는 필요합니다.")
        signal_frame = normalize_smoke_prediction_outputs(
            prediction_outputs,
            run_id=leaderboard_run_id,
            model_ver=str(smoke_config.get("model_ver", "unknown")),
            feature_set_ver=str(smoke_config.get("feature_set_ver", "unknown")),
            train_window=str(smoke_config.get("train_window", "unknown")),
            eval_window=str(smoke_config.get("validation_window", "unknown")),
            buy_threshold=buy_threshold,
            confidence_threshold=confidence_threshold,
        )
    else:
        signal_frame = _prepare_signal_frame(signal_frame)
        validate_signal_frame(signal_frame)

    _validate_signal_for_runner(signal_frame)
    realized_returns = _prepare_realized_returns(realized_returns)
    _validate_no_lookahead_label_columns(realized_returns)
    horizons = _resolve_eval_horizons(signal_frame, realized_returns, smoke_config)
    scoped_signal_frame = signal_frame[signal_frame["horizon"].isin(horizons)].copy()
    scoped_realized_returns = realized_returns[realized_returns["horizon"].isin(horizons)].copy()

    output_root = Path(output_dir)
    output_root.mkdir(parents=True, exist_ok=True)

    diagnostics_frame = build_signal_diagnostics_frame(
        scoped_signal_frame,
        scoped_realized_returns,
        buy_threshold=buy_threshold,
        confidence_threshold=confidence_threshold,
    )

    backtest_results = _run_model_backtests(
        signal_frame=scoped_signal_frame,
        realized_returns=scoped_realized_returns,
        horizons=horizons,
        top_k=top_k,
        weighting=weighting,
        buy_threshold=buy_threshold,
        confidence_threshold=confidence_threshold,
        cost_bps_per_side=cost_bps_per_side,
        missing_return_policy=missing_return_policy,
        include_statuses=tuple(smoke_config["include_statuses"]),
    )

    if smoke_config["include_universe_benchmark"]:
        backtest_results.extend(
            universe_equal_benchmark(scoped_realized_returns, horizon=horizon)
            for horizon in horizons
        )

    leaderboard_frame = build_leaderboard(
        backtest_results,
        leaderboard_run_id=leaderboard_run_id,
        prediction_run_id=str(smoke_config.get("prediction_run_id", "unknown")),
        model_ver=str(smoke_config.get("model_ver", "unknown")),
        feature_set_ver=str(smoke_config.get("feature_set_ver", "unknown")),
        train_window=str(smoke_config.get("train_window", "unknown")),
        validation_window=str(smoke_config.get("validation_window", "unknown")),
    )
    metric_frame = build_model_metric_frame(
        scoped_signal_frame,
        scoped_realized_returns,
        leaderboard_frame=leaderboard_frame,
        backtest_results=backtest_results,
        include_statuses=tuple(smoke_config["include_statuses"]),
        top_k=top_k,
        leaderboard_run_id=leaderboard_run_id,
    )
    objective_frame = build_model_objective_frame(
        leaderboard_frame,
        metric_frame=metric_frame,
    )

    output_paths = _write_outputs(
        output_root=output_root,
        leaderboard_run_id=leaderboard_run_id,
        signal_frame=scoped_signal_frame,
        diagnostics_frame=diagnostics_frame,
        metric_frame=metric_frame,
        objective_frame=objective_frame,
        leaderboard_frame=leaderboard_frame,
        backtest_results=backtest_results,
        save_signal_frame=smoke_config["save_signal_frame"],
        save_backtest_details=smoke_config["save_backtest_details"],
    )

    return {
        "signal_frame": scoped_signal_frame,
        "diagnostics_frame": diagnostics_frame,
        "metric_frame": metric_frame,
        "objective_frame": objective_frame,
        "backtest_results": backtest_results,
        "leaderboard_frame": leaderboard_frame,
        "output_paths": output_paths,
        "config": smoke_config,
    }


def _coerce_horizons(values: Any) -> list[int]:
    if values is None:
        values = DEFAULT_SMOKE_CONFIG["horizons"]
    if isinstance(values, (str, bytes)) or not isinstance(values, Iterable):
        raise TypeError("horizons는 1 이상의 정수 목록이어야 합니다.")

    horizons: list[int] = []
    for value in values:
        if isinstance(value, bool):
            raise ValueError("horizons는 모두 1 이상의 정수여야 합니다.")
        numeric = pd.to_numeric(value, errors="coerce")
        if pd.isna(numeric) or not float(numeric).is_integer() or float(numeric) <= 0:
            raise ValueError("horizons는 모두 1 이상의 정수여야 합니다.")
        horizons.append(int(numeric))

    if not horizons:
        raise ValueError("horizons는 비어 있을 수 없습니다.")
    return sorted(set(horizons))


def _coerce_include_statuses(values: Any) -> tuple[str, ...]:
    if isinstance(values, str):
        values = (values,)
    elif isinstance(values, (bytes,)) or not isinstance(values, Iterable):
        raise TypeError("include_statuses는 문자열 목록이어야 합니다.")

    statuses = tuple(str(value) for value in values)
    if not statuses:
        raise ValueError("include_statuses는 비어 있을 수 없습니다.")
    if any(not status.strip() for status in statuses):
        raise ValueError("include_statuses에는 빈 문자열을 넣을 수 없습니다.")
    return statuses


def _coerce_horizon_column(frame: pd.DataFrame, frame_name: str) -> pd.DataFrame:
    frame = frame.copy()
    horizon = pd.to_numeric(frame["horizon"], errors="coerce")
    if horizon.isna().any() or not horizon.map(lambda value: float(value).is_integer()).all():
        raise ValueError(f"{frame_name}.horizon은 정수여야 합니다.")
    frame["horizon"] = horizon.astype(int)
    return frame


def _coerce_asof_date_column(frame: pd.DataFrame, frame_name: str) -> pd.DataFrame:
    frame = frame.copy()
    try:
        frame["asof_date"] = pd.to_datetime(frame["asof_date"], errors="raise").dt.normalize()
    except Exception as exc:
        raise ValueError(f"{frame_name}.asof_date를 날짜로 변환할 수 없습니다.") from exc
    return frame


def _prepare_signal_frame(signal_frame: pd.DataFrame) -> pd.DataFrame:
    if not isinstance(signal_frame, pd.DataFrame):
        raise TypeError("signal_frame은 pandas.DataFrame이어야 합니다.")
    missing = [column for column in SIGNAL_RUNNER_REQUIRED_COLUMNS if column not in signal_frame.columns]
    if missing:
        raise ValueError(f"signal_frame에 필요한 컬럼이 없습니다: {missing}")
    signal_frame = _coerce_horizon_column(signal_frame, "signal_frame")
    signal_frame = _coerce_asof_date_column(signal_frame, "signal_frame")
    return signal_frame.sort_values(["model_name", "asof_date", "ticker", "horizon"]).reset_index(drop=True)


def _prepare_realized_returns(realized_returns: pd.DataFrame) -> pd.DataFrame:
    if not isinstance(realized_returns, pd.DataFrame):
        raise TypeError("realized_returns는 pandas.DataFrame이어야 합니다.")
    missing = [
        column for column in REALIZED_RETURNS_REQUIRED_COLUMNS if column not in realized_returns.columns
    ]
    if missing:
        raise ValueError(f"realized_returns에 필요한 컬럼이 없습니다: {missing}")
    realized_returns = _coerce_horizon_column(realized_returns, "realized_returns")
    realized_returns = _coerce_asof_date_column(realized_returns, "realized_returns")
    realized_returns = realized_returns.copy()
    realized_returns["forward_return"] = pd.to_numeric(
        realized_returns["forward_return"],
        errors="raise",
    )
    duplicated = realized_returns.duplicated(subset=["asof_date", "ticker", "horizon"], keep=False)
    if duplicated.any():
        duplicate_keys = (
            realized_returns.loc[duplicated, ["asof_date", "ticker", "horizon"]]
            .drop_duplicates()
            .to_dict("records")
        )
        raise ValueError(f"realized_returns key가 중복되었습니다: {duplicate_keys}")
    return realized_returns.sort_values(["asof_date", "ticker", "horizon"]).reset_index(drop=True)


def _validate_signal_for_runner(signal_frame: pd.DataFrame) -> None:
    if signal_frame["decision_time"].isna().any():
        raise ValueError("decision_time은 smoke evaluation에서 반드시 명시되어야 합니다.")
    invalid_status_rows = signal_frame["prediction_status"].astype(str).str.strip().eq("")
    if invalid_status_rows.any():
        raise ValueError("prediction_status는 빈 문자열일 수 없습니다.")


def _validate_no_lookahead_label_columns(realized_returns: pd.DataFrame) -> None:
    """label 기간 컬럼이 있으면 asof_date 이후 수익률인지 검증한다."""
    label_dates: dict[str, pd.Series] = {}
    for column in ["label_start_date", "label_end_date"]:
        if column in realized_returns.columns:
            label_date = pd.to_datetime(realized_returns[column], errors="raise").dt.normalize()
            if label_date.isna().any():
                raise ValueError(f"{column}에는 결측값이 없어야 합니다.")
            label_dates[column] = label_date

    if "label_start_date" in label_dates:
        invalid = label_dates["label_start_date"] < realized_returns["asof_date"]
        if invalid.any():
            bad_rows = realized_returns.loc[invalid, ["asof_date", "ticker", "horizon", "label_start_date"]]
            raise ValueError(
                "label_start_date는 asof_date 이전일 수 없습니다: "
                f"{bad_rows.to_dict('records')}"
            )

    if "label_end_date" in label_dates:
        invalid = label_dates["label_end_date"] <= realized_returns["asof_date"]
        if invalid.any():
            bad_rows = realized_returns.loc[invalid, ["asof_date", "ticker", "horizon", "label_end_date"]]
            raise ValueError(
                "label_end_date는 asof_date 이후여야 합니다: "
                f"{bad_rows.to_dict('records')}"
            )

    if {"label_start_date", "label_end_date"}.issubset(label_dates):
        invalid = label_dates["label_end_date"] < label_dates["label_start_date"]
        if invalid.any():
            bad_rows = realized_returns.loc[
                invalid,
                ["asof_date", "ticker", "horizon", "label_start_date", "label_end_date"],
            ]
            raise ValueError(
                "label_end_date는 label_start_date보다 빠를 수 없습니다: "
                f"{bad_rows.to_dict('records')}"
            )


def _resolve_eval_horizons(
    signal_frame: pd.DataFrame,
    realized_returns: pd.DataFrame,
    config: dict,
) -> list[int]:
    configured_horizons = set(_coerce_horizons(config.get("horizons")))
    signal_horizons = set(signal_frame["horizon"].astype(int).unique().tolist())
    return_horizons = set(realized_returns["horizon"].astype(int).unique().tolist())

    unexpected_signal_horizons = signal_horizons - configured_horizons
    if config.get("require_all_horizons", True) and unexpected_signal_horizons:
        raise ValueError(f"설정에 없는 signal horizon이 있습니다: {sorted(unexpected_signal_horizons)}")

    if config.get("require_all_horizons", True):
        missing_signal_horizons = configured_horizons - signal_horizons
        missing_return_horizons = configured_horizons - return_horizons
        if missing_signal_horizons:
            raise ValueError(f"signal_frame에 필요한 horizon이 없습니다: {sorted(missing_signal_horizons)}")
        if missing_return_horizons:
            raise ValueError(f"realized_returns에 필요한 horizon이 없습니다: {sorted(missing_return_horizons)}")
        return sorted(configured_horizons)

    horizons = sorted(signal_horizons & return_horizons & configured_horizons)
    if not horizons:
        raise ValueError("평가 가능한 signal/return horizon 교집합이 없습니다.")
    return horizons


def _run_model_backtests(
    *,
    signal_frame: pd.DataFrame,
    realized_returns: pd.DataFrame,
    horizons: list[int],
    top_k: int,
    weighting: str,
    buy_threshold: float,
    confidence_threshold: float,
    cost_bps_per_side: float,
    missing_return_policy: str,
    include_statuses: tuple[str, ...],
) -> list[dict]:
    model_names = sorted(signal_frame["model_name"].dropna().unique().tolist())
    if not model_names:
        raise ValueError("평가할 model_name이 없습니다.")

    results: list[dict] = []
    for model_name in model_names:
        for horizon in horizons:
            scoped = signal_frame[
                (signal_frame["model_name"] == model_name)
                & (signal_frame["horizon"] == horizon)
            ]
            if scoped.empty:
                continue
            results.append(
                backtest_top_k_signals(
                    signal_frame,
                    realized_returns,
                    model_name=str(model_name),
                    horizon=horizon,
                    top_k=top_k,
                    buy_threshold=buy_threshold,
                    confidence_threshold=confidence_threshold,
                    weighting=weighting,
                    cost_bps_per_side=cost_bps_per_side,
                    include_statuses=include_statuses,
                    missing_return_policy=missing_return_policy,
                )
            )

    if not results:
        raise ValueError("생성된 모델 백테스트 결과가 없습니다.")
    return results


def _write_outputs(
    *,
    output_root: Path,
    leaderboard_run_id: str,
    signal_frame: pd.DataFrame,
    diagnostics_frame: pd.DataFrame,
    metric_frame: pd.DataFrame,
    objective_frame: pd.DataFrame,
    leaderboard_frame: pd.DataFrame,
    backtest_results: list[dict],
    save_signal_frame: bool,
    save_backtest_details: bool,
) -> dict:
    output_paths: dict[str, Any] = {}
    safe_run_id = _safe_filename_stem(leaderboard_run_id, fallback="leaderboard")

    leaderboard_path = output_root / f"{safe_run_id}_leaderboard.csv"
    save_leaderboard(leaderboard_frame, str(leaderboard_path))
    output_paths["leaderboard"] = str(leaderboard_path)

    if save_signal_frame:
        signal_path = output_root / f"{safe_run_id}_signal_frame.csv"
        _sort_for_output(signal_frame).to_csv(signal_path, index=False)
        output_paths["signal_frame"] = str(signal_path)

    diagnostics_path = output_root / f"{safe_run_id}_diagnostics.csv"
    _sort_for_output(diagnostics_frame).to_csv(diagnostics_path, index=False)
    output_paths["diagnostics"] = str(diagnostics_path)

    metric_path = output_root / f"{safe_run_id}_metric_frame.csv"
    _sort_for_output(metric_frame).to_csv(metric_path, index=False)
    output_paths["metric_frame"] = str(metric_path)

    objective_path = output_root / f"{safe_run_id}_objective_frame.csv"
    _sort_for_output(objective_frame).to_csv(objective_path, index=False)
    output_paths["objective_frame"] = str(objective_path)

    if save_backtest_details:
        backtest_dir = output_root / "backtests"
        backtest_dir.mkdir(parents=True, exist_ok=True)
        detail_paths = []
        for index, result in enumerate(backtest_results):
            config = result.get("config", {})
            name = _backtest_result_name(config, index)
            equity_path = backtest_dir / f"{name}_equity_curve.csv"
            trades_path = backtest_dir / f"{name}_trades.csv"
            _sort_for_output(result["equity_curve"]).to_csv(equity_path, index=False)
            _sort_for_output(result["trades"]).to_csv(trades_path, index=False)
            detail_paths.append(
                {
                    "equity_curve": str(equity_path),
                    "trades": str(trades_path),
                }
            )
        output_paths["backtests"] = detail_paths

    return output_paths


def _backtest_result_name(config: Mapping[str, Any], index: int) -> str:
    if "benchmark_name" in config:
        raw_name = f"{config.get('benchmark_name')}_h{config.get('horizon', 'unknown')}_{index:02d}"
    else:
        raw_name = (
            f"{config.get('model_name', 'unknown')}_"
            f"{config.get('weighting', 'unknown')}_"
            f"h{config.get('horizon', 'unknown')}_{index:02d}"
        )
    return _safe_filename_stem(raw_name, fallback=f"result_{index:02d}")


def _safe_filename_stem(value: Any, *, fallback: str) -> str:
    stem = _SAFE_FILENAME_PATTERN.sub("_", str(value)).strip("._")
    return stem or fallback


def _sort_for_output(frame: pd.DataFrame) -> pd.DataFrame:
    if frame.empty:
        return frame.copy()
    preferred_order = [
        "model_name",
        "benchmark_name",
        "horizon",
        "asof_date",
        "date",
        "ticker",
        "weighting",
    ]
    sort_columns = [column for column in preferred_order if column in frame.columns]
    if not sort_columns:
        return frame.copy()
    return frame.sort_values(sort_columns).reset_index(drop=True)
