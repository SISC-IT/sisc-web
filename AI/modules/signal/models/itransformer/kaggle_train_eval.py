# AI/modules/signal/models/itransformer/kaggle_train_eval.py
"""
iTransformer Kaggle 학습 직후 평가 산출물을 생성하는 헬퍼.

노트북과 trigger_training.py 모두 이 모듈을 호출할 수 있다. 로컬에서는 실제 학습을
실행하지 않고 py_compile 및 설정 smoke 용도로만 사용한다.
"""
from __future__ import annotations

import json
import os
import posixpath
from pathlib import Path
from typing import Any

import pandas as pd

from AI.modules.signal.evaluation.runner import (
    normalize_smoke_prediction_outputs,
    run_smoke_evaluation,
)
from AI.modules.signal.models.itransformer.feature_contract import (
    ITRANSFORMER_DEFAULT_HORIZONS,
    ITRANSFORMER_FEATURE_SET_VER,
    load_itransformer_metadata,
)
from AI.modules.signal.models.itransformer.train_kaggle import (
    CONFIG as TRAIN_CONFIG,
    OUTPUT_DIR,
    load_parquet_data,
    train,
    validate_training_window_policy,
)


BASE_OUTPUT_FILES = {
    "signal_frame": "itransformer_signal_frame.csv",
    "diagnostics_frame": "itransformer_diagnostics_frame.csv",
    "metric_frame": "itransformer_metric_frame.csv",
    "objective_frame": "itransformer_objective_frame.csv",
    "leaderboard_frame": "itransformer_leaderboard_frame.csv",
}

CONFIDENCE_020_OUTPUT_FILES = {
    "diagnostics_frame": "itransformer_conf020_diagnostics_frame.csv",
    "metric_frame": "itransformer_conf020_metric_frame.csv",
    "objective_frame": "itransformer_conf020_objective_frame.csv",
    "leaderboard_frame": "itransformer_conf020_leaderboard_frame.csv",
}

SUMMARY_FILE = "itransformer_eval_summary.json"


def _join_artifact_path(output_dir: str, file_name: str) -> str:
    """Kaggle 절대 경로는 Windows 로컬 smoke에서도 POSIX 표기로 유지한다."""
    if str(output_dir).startswith("/kaggle/"):
        return posixpath.join(str(output_dir), file_name)
    return os.path.join(str(output_dir), file_name)


def build_kaggle_eval_config(overrides: dict[str, Any] | None = None) -> dict[str, Any]:
    """Kaggle 학습+평가 공통 설정을 만든다."""
    output_dir = os.environ.get("OUTPUT_DIR") or os.environ.get("WEIGHTS_DIR") or OUTPUT_DIR
    config = {
        **TRAIN_CONFIG,
        "output_dir": output_dir,
        "model_path": _join_artifact_path(output_dir, str(TRAIN_CONFIG["model_name"])),
        "scaler_path": _join_artifact_path(output_dir, str(TRAIN_CONFIG["scaler_name"])),
        "metadata_path": _join_artifact_path(output_dir, str(TRAIN_CONFIG["metadata_name"])),
        "leaderboard_run_id": os.environ.get("LEADERBOARD_RUN_ID", "itransformer"),
        "prediction_run_id": os.environ.get("PREDICTION_RUN_ID", "itransformer_kaggle_eval_v0"),
        "top_k": int(os.environ.get("EVAL_TOP_K", "5")),
        "weighting": os.environ.get("EVAL_WEIGHTING", "equal"),
        "buy_threshold": float(os.environ.get("BUY_THRESHOLD", "0.6")),
        "cost_bps_per_side": float(os.environ.get("COST_BPS_PER_SIDE", "5.0")),
        "base_confidence_threshold": 0.0,
        "summary_confidence_threshold": 0.2,
        "missing_return_policy": "error",
        "include_statuses": ("ok",),
        "include_universe_benchmark": True,
    }
    if overrides:
        config.update(overrides)
        if "output_dir" in overrides:
            output_dir = str(config["output_dir"])
            if "model_path" not in overrides:
                config["model_path"] = _join_artifact_path(output_dir, str(TRAIN_CONFIG["model_name"]))
            if "scaler_path" not in overrides:
                config["scaler_path"] = _join_artifact_path(output_dir, str(TRAIN_CONFIG["scaler_name"]))
            if "metadata_path" not in overrides:
                config["metadata_path"] = _join_artifact_path(output_dir, str(TRAIN_CONFIG["metadata_name"]))
    validate_training_window_policy(config)
    return config


def _metadata_paths(config: dict[str, Any]) -> tuple[str, str, str]:
    return (
        str(config["model_path"]),
        str(config["scaler_path"]),
        str(config["metadata_path"]),
    )


def _require_training_artifacts(config: dict[str, Any]) -> None:
    missing = [path for path in _metadata_paths(config) if not os.path.exists(path)]
    if missing:
        raise FileNotFoundError(f"iTransformer 학습 artifact가 없습니다: {missing}")


def _load_trained_wrapper(config: dict[str, Any]) -> tuple[Any, dict[str, Any]]:
    from AI.modules.signal.models.itransformer.wrapper import ITransformerWrapper

    model_path, scaler_path, metadata_path = _metadata_paths(config)
    metadata = load_itransformer_metadata(metadata_path)
    if metadata is None:
        raise ValueError(f"metadata.json을 로드할 수 없습니다: {metadata_path}")

    wrapper = ITransformerWrapper(
        {
            "weights_dir": str(config["output_dir"]),
            "model_path": model_path,
            "scaler_path": scaler_path,
            "metadata_path": metadata_path,
            "feature_set_ver": ITRANSFORMER_FEATURE_SET_VER,
        }
    )
    wrapper.load(model_path)
    return wrapper, metadata


def _timestamp(value: Any, *, name: str) -> pd.Timestamp:
    try:
        return pd.to_datetime(value, errors="raise").normalize()
    except Exception as exc:
        raise ValueError(f"{name} 날짜를 해석할 수 없습니다: {value!r}") from exc


def _date_text(value: Any) -> str:
    return str(pd.Timestamp(value).date())


def _train_window(config: dict[str, Any]) -> str:
    return f"{config['train_start_date']}..{config['train_end_date']}"


def _eval_window(config: dict[str, Any]) -> str:
    return f"{config['eval_start_date']}..{config['eval_end_date']}"


def _candidate_label_dates(
    group_dates: pd.Series,
    *,
    row_index: int,
    horizons: list[int],
) -> dict[int, pd.Timestamp]:
    return {horizon: pd.Timestamp(group_dates.iloc[row_index + horizon]) for horizon in horizons}


def build_itransformer_eval_inputs(
    *,
    config: dict[str, Any] | None = None,
) -> tuple[pd.DataFrame, pd.DataFrame, dict[str, Any]]:
    """저장된 iTransformer artifact로 평가용 signal과 realized return을 만든다."""
    active_config = build_kaggle_eval_config(config)
    _require_training_artifacts(active_config)
    dates = validate_training_window_policy(active_config)
    wrapper, metadata = _load_trained_wrapper(active_config)

    df = load_parquet_data(config=active_config, apply_train_window=True)
    horizons = [int(horizon) for horizon in active_config.get("horizons", ITRANSFORMER_DEFAULT_HORIZONS)]
    max_horizon = max(horizons)
    seq_len = int(metadata.get("seq_len", active_config["lookback"]))
    ticker_to_id = {
        str(ticker): int(ticker_id)
        for ticker, ticker_id in dict(metadata.get("ticker_to_id", {})).items()
    }

    prediction_outputs: list[dict[str, Any]] = []
    realized_rows: list[dict[str, Any]] = []
    skipped = {
        "before_eval_window": 0,
        "after_eval_window": 0,
        "insufficient_history": 0,
        "insufficient_forward_label": 0,
        "label_after_cutoff": 0,
        "holdout_label": 0,
    }

    eval_start = dates["eval_start"]
    eval_end = dates["eval_end"]
    label_cutoff = dates["label_cutoff"]
    holdout_start = dates["holdout_start"]

    for ticker, group in df.groupby("ticker", sort=True):
        group = group.sort_values("date").reset_index(drop=True)
        group_dates = pd.to_datetime(group["date"]).dt.normalize()
        close_values = group["close"].astype(float).to_numpy()
        ticker_id = int(ticker_to_id.get(str(ticker), 0))

        for row_index in range(seq_len, len(group)):
            asof_date = pd.Timestamp(group_dates.iloc[row_index])
            if asof_date < eval_start:
                skipped["before_eval_window"] += 1
                continue
            if asof_date > eval_end:
                skipped["after_eval_window"] += 1
                continue
            if row_index + max_horizon >= len(group):
                skipped["insufficient_forward_label"] += 1
                continue

            label_dates = _candidate_label_dates(
                group_dates,
                row_index=row_index,
                horizons=horizons,
            )
            if max(label_dates.values()) > label_cutoff:
                skipped["label_after_cutoff"] += 1
                continue
            if asof_date >= holdout_start or max(label_dates.values()) >= holdout_start:
                skipped["holdout_label"] += 1
                continue

            history = group.iloc[:row_index].copy()
            if len(history) < seq_len:
                skipped["insufficient_history"] += 1
                continue

            prediction = wrapper.predict_with_status(history, ticker_id=ticker_id, sector_id=0)
            prediction_outputs.append(
                {
                    "output": prediction["output"],
                    "asof_date": asof_date,
                    "decision_time": asof_date,
                    "ticker": str(ticker),
                    "model_name": "itransformer",
                    "run_id": str(active_config["prediction_run_id"]),
                    "model_ver": str(metadata.get("model_ver", active_config["model_ver"])),
                    "feature_set_ver": str(metadata.get("feature_set_ver", ITRANSFORMER_FEATURE_SET_VER)),
                    "train_window": str(metadata.get("train_window", _train_window(active_config))),
                    "eval_window": _eval_window(active_config),
                    "seq_len": seq_len,
                    "scaler_ver": str(metadata.get("scaler_type", "StandardScaler")),
                    "artifact_path": str(active_config["model_path"]),
                    "feature_count": int(metadata.get("feature_count", len(wrapper.feature_columns))),
                    "prediction_status": str(prediction["prediction_status"]),
                    "error_message": str(prediction["error_message"]),
                }
            )

            for horizon in horizons:
                label_end_date = label_dates[horizon]
                forward_return = (
                    close_values[row_index + horizon] - close_values[row_index]
                ) / close_values[row_index]
                realized_rows.append(
                    {
                        "asof_date": asof_date,
                        "ticker": str(ticker),
                        "horizon": int(horizon),
                        "forward_return": float(forward_return),
                        "label_start_date": asof_date,
                        "label_end_date": label_end_date,
                    }
                )

    if not prediction_outputs:
        raise ValueError("평가용 iTransformer prediction row가 생성되지 않았습니다.")
    if not realized_rows:
        raise ValueError("평가용 realized return row가 생성되지 않았습니다.")

    signal_frame = normalize_smoke_prediction_outputs(
        prediction_outputs,
        run_id=str(active_config["prediction_run_id"]),
        model_ver=str(metadata.get("model_ver", active_config["model_ver"])),
        feature_set_ver=str(metadata.get("feature_set_ver", ITRANSFORMER_FEATURE_SET_VER)),
        train_window=str(metadata.get("train_window", _train_window(active_config))),
        eval_window=_eval_window(active_config),
        buy_threshold=float(active_config["buy_threshold"]),
        confidence_threshold=float(active_config["base_confidence_threshold"]),
    )
    realized_returns = pd.DataFrame(realized_rows).drop_duplicates(
        subset=["asof_date", "ticker", "horizon"],
        keep="last",
    )
    _validate_eval_label_policy(realized_returns, config=active_config)

    generation_summary = {
        "prediction_row_count": int(len(signal_frame)),
        "realized_return_row_count": int(len(realized_returns)),
        "skipped_candidates": skipped,
        "effective_eval_asof_min": _date_text(signal_frame["asof_date"].min()),
        "effective_eval_asof_max": _date_text(signal_frame["asof_date"].max()),
    }
    return signal_frame, realized_returns, generation_summary


def _validate_eval_label_policy(realized_returns: pd.DataFrame, *, config: dict[str, Any]) -> None:
    label_cutoff = _timestamp(config["label_cutoff_date"], name="label_cutoff_date")
    holdout_start = _timestamp(config["holdout_start_date"], name="holdout_start_date")
    label_end = pd.to_datetime(realized_returns["label_end_date"], errors="raise").dt.normalize()
    if (label_end > label_cutoff).any():
        raise ValueError("평가 label_end_date가 label_cutoff_date를 넘었습니다.")
    if (label_end >= holdout_start).any():
        raise ValueError("평가 label이 2025 이후 holdout을 침범했습니다.")


def _runner_config(config: dict[str, Any], metadata: dict[str, Any]) -> dict[str, Any]:
    return {
        "horizons": [int(horizon) for horizon in config["horizons"]],
        "require_all_horizons": True,
        "include_statuses": tuple(config["include_statuses"]),
        "include_universe_benchmark": bool(config["include_universe_benchmark"]),
        "prediction_run_id": str(config["prediction_run_id"]),
        "model_ver": str(metadata.get("model_ver", config["model_ver"])),
        "feature_set_ver": str(metadata.get("feature_set_ver", ITRANSFORMER_FEATURE_SET_VER)),
        "train_window": str(metadata.get("train_window", _train_window(config))),
        "validation_window": _eval_window(config),
        "save_signal_frame": False,
        "save_backtest_details": True,
    }


def _save_named_outputs(
    result: dict[str, Any],
    *,
    signal_frame: pd.DataFrame | None,
    output_dir: Path,
    file_map: dict[str, str],
) -> dict[str, str]:
    paths: dict[str, str] = {}
    if signal_frame is not None and "signal_frame" in file_map:
        path = output_dir / file_map["signal_frame"]
        signal_frame.to_csv(path, index=False)
        paths["signal_frame"] = str(path)

    frame_keys = {
        "diagnostics_frame": "diagnostics_frame",
        "metric_frame": "metric_frame",
        "objective_frame": "objective_frame",
        "leaderboard_frame": "leaderboard_frame",
    }
    for output_key, result_key in frame_keys.items():
        if output_key not in file_map:
            continue
        path = output_dir / file_map[output_key]
        result[result_key].to_csv(path, index=False)
        paths[output_key] = str(path)
    return paths


def _frame_records(frame: pd.DataFrame, max_rows: int = 20) -> list[dict[str, Any]]:
    return frame.head(max_rows).where(pd.notnull(frame), None).to_dict("records")


def _excess_vs_universe_equal(leaderboard_frame: pd.DataFrame) -> list[dict[str, Any]]:
    """horizon별 iTransformer net_return과 universe_equal 대비 excess를 요약한다."""
    if leaderboard_frame.empty:
        return []
    frame = leaderboard_frame.copy()
    rows: list[dict[str, Any]] = []
    for horizon in sorted(frame["horizon"].dropna().astype(int).unique().tolist()):
        model_rows = frame[
            (frame["horizon"].astype(int) == horizon)
            & (frame["model_name"].astype(str).str.lower() == "itransformer")
        ]
        benchmark_rows = frame[
            (frame["horizon"].astype(int) == horizon)
            & (frame["benchmark_name"].astype(str).str.lower() == "universe_equal")
        ]
        if model_rows.empty or benchmark_rows.empty:
            continue
        model_net = pd.to_numeric(model_rows["net_return"], errors="coerce").dropna()
        benchmark_net = pd.to_numeric(benchmark_rows["net_return"], errors="coerce").dropna()
        if model_net.empty or benchmark_net.empty:
            continue
        rows.append(
            {
                "horizon": int(horizon),
                "itransformer_net_return": float(model_net.iloc[0]),
                "universe_equal_net_return": float(benchmark_net.iloc[0]),
                "excess_net_return": float(model_net.iloc[0] - benchmark_net.iloc[0]),
            }
        )
    return rows


def _json_default(value: Any) -> Any:
    if isinstance(value, pd.Timestamp):
        return value.isoformat()
    if hasattr(value, "item"):
        return value.item()
    return str(value)


def run_itransformer_kaggle_evaluation(config: dict[str, Any] | None = None) -> dict[str, Any]:
    """학습된 artifact를 평가하고 요구된 CSV/JSON 산출물을 저장한다."""
    active_config = build_kaggle_eval_config(config)
    _require_training_artifacts(active_config)
    metadata = load_itransformer_metadata(str(active_config["metadata_path"])) or {}
    output_dir = Path(str(active_config["output_dir"]))
    output_dir.mkdir(parents=True, exist_ok=True)

    signal_frame, realized_returns, generation_summary = build_itransformer_eval_inputs(
        config=active_config
    )
    runner_common = {
        "signal_frame": signal_frame,
        "realized_returns": realized_returns,
        "output_dir": str(output_dir / "itransformer_eval_internal"),
        "top_k": int(active_config["top_k"]),
        "weighting": str(active_config["weighting"]),
        "buy_threshold": float(active_config["buy_threshold"]),
        "cost_bps_per_side": float(active_config["cost_bps_per_side"]),
        "missing_return_policy": str(active_config["missing_return_policy"]),
        "config": _runner_config(active_config, metadata),
    }

    base_result = run_smoke_evaluation(
        **runner_common,
        leaderboard_run_id=str(active_config["leaderboard_run_id"]),
        confidence_threshold=float(active_config["base_confidence_threshold"]),
    )
    base_paths = _save_named_outputs(
        base_result,
        signal_frame=signal_frame,
        output_dir=output_dir,
        file_map=BASE_OUTPUT_FILES,
    )

    confidence_result = run_smoke_evaluation(
        **runner_common,
        leaderboard_run_id="itransformer_conf020",
        confidence_threshold=float(active_config["summary_confidence_threshold"]),
    )
    confidence_paths = _save_named_outputs(
        confidence_result,
        signal_frame=None,
        output_dir=output_dir,
        file_map=CONFIDENCE_020_OUTPUT_FILES,
    )

    status_counts = signal_frame["prediction_status"].value_counts().to_dict()
    summary = {
        "model_name": "itransformer",
        "role": "risk/regime gate 후보",
        "feature_set_ver": str(metadata.get("feature_set_ver", ITRANSFORMER_FEATURE_SET_VER)),
        "horizons": [int(horizon) for horizon in active_config["horizons"]],
        "train_window": str(metadata.get("train_window", _train_window(active_config))),
        "requested_eval_window": _eval_window(active_config),
        "effective_eval_asof_min": generation_summary["effective_eval_asof_min"],
        "effective_eval_asof_max": generation_summary["effective_eval_asof_max"],
        "label_cutoff_date": str(active_config["label_cutoff_date"]),
        "holdout_start_date": str(active_config["holdout_start_date"]),
        "missing_return_policy": str(active_config["missing_return_policy"]),
        "base_confidence_threshold": float(active_config["base_confidence_threshold"]),
        "summary_confidence_threshold": float(active_config["summary_confidence_threshold"]),
        "artifact_paths": {
            "model_path": str(active_config["model_path"]),
            "scaler_path": str(active_config["scaler_path"]),
            "metadata_path": str(active_config["metadata_path"]),
        },
        "base_output_paths": base_paths,
        "confidence_020_output_paths": confidence_paths,
        "row_counts": {
            "signal_frame": int(len(signal_frame)),
            "realized_returns": int(len(realized_returns)),
            "prediction_status_ok": int(status_counts.get("ok", 0)),
            "prediction_status_fallback": int(status_counts.get("fallback", 0)),
            "prediction_status_error": int(status_counts.get("error", 0)),
        },
        "generation_summary": generation_summary,
        "base_excess_vs_universe_equal": _excess_vs_universe_equal(base_result["leaderboard_frame"]),
        "confidence_020_excess_vs_universe_equal": _excess_vs_universe_equal(
            confidence_result["leaderboard_frame"]
        ),
        "base_objective_preview": _frame_records(base_result["objective_frame"]),
        "confidence_020_objective_preview": _frame_records(confidence_result["objective_frame"]),
    }

    summary_path = output_dir / SUMMARY_FILE
    with summary_path.open("w", encoding="utf-8") as file:
        json.dump(summary, file, ensure_ascii=False, indent=2, default=_json_default)
        file.write("\n")
    summary["summary_path"] = str(summary_path)
    print(f">> iTransformer 평가 산출물 저장 완료: {summary_path}")
    return summary


def run_kaggle_train_eval(config: dict[str, Any] | None = None) -> dict[str, Any]:
    """Kaggle에서 학습과 평가를 연속 실행한다."""
    active_config = build_kaggle_eval_config(config)
    train_result = train(active_config)
    eval_summary = run_itransformer_kaggle_evaluation(active_config)
    return {
        "train_result": train_result,
        "eval_summary": eval_summary,
    }


def smoke_config_summary(config: dict[str, Any] | None = None) -> dict[str, Any]:
    """로컬 검증용 설정 요약을 반환한다. 학습이나 모델 로드는 하지 않는다."""
    active_config = build_kaggle_eval_config(config)
    dates = validate_training_window_policy(active_config)
    return {
        "model_name": "itransformer",
        "feature_set_ver": ITRANSFORMER_FEATURE_SET_VER,
        "horizons": [int(horizon) for horizon in active_config["horizons"]],
        "train_window": _train_window(active_config),
        "eval_window": _eval_window(active_config),
        "label_cutoff_date": str(dates["label_cutoff"].date()),
        "holdout_start_date": str(dates["holdout_start"].date()),
        "output_dir": str(active_config["output_dir"]),
        "required_artifacts": [
            str(active_config["model_path"]),
            str(active_config["scaler_path"]),
            str(active_config["metadata_path"]),
        ],
        "required_eval_outputs": list(BASE_OUTPUT_FILES.values()) + [SUMMARY_FILE],
    }


if __name__ == "__main__":
    run_kaggle_train_eval()
