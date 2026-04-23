from __future__ import annotations

import argparse
import json
import os
import pickle
import random
import sys
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.model_selection import train_test_split
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau


PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.append(str(PROJECT_ROOT))

from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.signal.models.transformer.architecture import build_transformer_model


PRE307_DYNAMIC_23 = [
    "log_return",
    "open_ratio",
    "high_ratio",
    "low_ratio",
    "vol_change",
    # Historical potential_features used underscored names, but actual engineered
    # columns are ma5/ma20/ma60_ratio. Use executable names to avoid silent drops.
    "ma5_ratio",
    "ma20_ratio",
    "ma60_ratio",
    "rsi",
    "macd_ratio",
    "bb_position",
    "us10y",
    "yield_spread",
    "vix_close",
    "dxy_close",
    "credit_spread_hy",
    "nh_nl_index",
    "ma200_pct",
    "sentiment_score",
    "risk_keyword_cnt",
    "per",
    "pbr",
    "roe",
]

CURRENT_FIXED_17 = [
    "log_return",
    "open_ratio",
    "high_ratio",
    "low_ratio",
    "vol_change",
    "ma5_ratio",
    "ma20_ratio",
    "ma60_ratio",
    "rsi",
    "macd_ratio",
    "bb_position",
    "week_ma20_ratio",
    "week_rsi",
    "week_bb_pos",
    "week_vol_change",
    "month_ma12_ratio",
    "month_rsi",
]


@dataclass
class TrainArtifacts:
    schema_name: str
    requested_features: list[str]
    effective_features: list[str]
    missing_features: list[str]
    raw_n_samples: int
    n_features: int
    n_samples: int
    epochs_ran: int
    history_csv: str
    summary_json: str
    model_path: str
    scaler_path: str
    best_val_loss: float | None
    best_epoch: int | None
    final_val_loss: float | None


def _set_global_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    tf.keras.utils.set_random_seed(seed)
    try:
        tf.config.experimental.enable_op_determinism()
    except Exception:
        pass


def _safe_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _parse_horizons(raw_horizons: str) -> list[int]:
    horizons = [int(item.strip()) for item in raw_horizons.split(",") if item.strip()]
    if not horizons:
        raise ValueError("prediction horizons must not be empty.")
    return horizons


def _prepare_output_dir(output_dir: str | None) -> Path:
    if output_dir:
        target = Path(output_dir).resolve()
    else:
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        target = (PROJECT_ROOT / "AI" / "backtests" / "out" / f"transformer_schema_compare_{stamp}").resolve()
    target.mkdir(parents=True, exist_ok=True)
    return target


def _path_for_report(path: Path) -> str:
    try:
        return str(path.resolve().relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path.resolve())


def _fit_single_schema(
    *,
    schema_name: str,
    requested_features: list[str],
    case_output_dir: Path,
    db_name: str,
    lookback: int,
    horizons: list[int],
    data_start_date: str,
    train_end_date: str,
    test_size: float,
    split_seed: int,
    global_seed: int,
    epochs: int,
    batch_size: int,
    patience: int,
    lr_patience: int,
    lr_factor: float,
    min_lr: float,
    max_samples: int | None,
    verbose: int,
) -> TrainArtifacts:
    _set_global_seed(global_seed)
    tf.keras.backend.clear_session()

    loader = DataLoader(db_name=db_name, lookback=lookback, horizons=horizons)
    full_df = loader.load_data_from_db(start_date=data_start_date)
    raw_df = full_df[full_df["date"] <= train_end_date].copy()
    if raw_df.empty:
        raise ValueError(f"[{schema_name}] no rows found in selected training range.")

    X_ts, X_ticker, X_sector, y_class, _, info = loader.create_dataset(raw_df, feature_columns=requested_features)
    if len(y_class) == 0:
        raise ValueError(f"[{schema_name}] dataset is empty after preprocessing.")
    raw_n_samples = int(len(y_class))
    effective_features = list(info.get("feature_names", []))
    missing_features = [feature for feature in requested_features if feature not in set(effective_features)]
    if missing_features:
        print(
            f"[SchemaCompare][Warning] {schema_name}: requested features were not included in dataset: "
            f"{missing_features}"
        )
    if max_samples is not None and max_samples > 0 and raw_n_samples > max_samples:
        rng = np.random.default_rng(global_seed)
        sampled_indices = rng.choice(raw_n_samples, size=max_samples, replace=False)
        sampled_indices.sort()
        X_ts = X_ts[sampled_indices]
        X_ticker = X_ticker[sampled_indices]
        X_sector = X_sector[sampled_indices]
        y_class = y_class[sampled_indices]
        print(f"[SchemaCompare] {schema_name}: downsampled samples {raw_n_samples} -> {len(y_class)}")

    # Minimize extra conversion copies during model.fit.
    X_ts = np.asarray(X_ts, dtype=np.float32)
    X_ticker = np.asarray(X_ticker, dtype=np.int32)
    X_sector = np.asarray(X_sector, dtype=np.int32)
    y_class = np.asarray(y_class, dtype=np.float32)

    (
        X_ts_train,
        X_ts_val,
        X_tick_train,
        X_tick_val,
        X_sec_train,
        X_sec_val,
        y_train,
        y_val,
    ) = train_test_split(
        X_ts,
        X_ticker,
        X_sector,
        y_class,
        test_size=test_size,
        shuffle=True,
        random_state=split_seed,
    )

    model = build_transformer_model(
        input_shape=(X_ts.shape[1], X_ts.shape[2]),
        n_tickers=info["n_tickers"],
        n_sectors=info["n_sectors"],
        n_outputs=len(info.get("horizons", horizons)),
    )

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-4),
        loss="binary_crossentropy",
        metrics=["accuracy"],
    )

    case_output_dir.mkdir(parents=True, exist_ok=True)
    model_path = case_output_dir / "multi_horizon_model.keras"
    scaler_path = case_output_dir / "multi_horizon_scaler.pkl"

    callbacks = [
        ModelCheckpoint(
            filepath=str(model_path),
            monitor="val_loss",
            save_best_only=True,
            verbose=1 if verbose > 0 else 0,
        ),
        EarlyStopping(
            monitor="val_loss",
            patience=patience,
            restore_best_weights=True,
        ),
        ReduceLROnPlateau(
            monitor="val_loss",
            factor=lr_factor,
            patience=lr_patience,
            min_lr=min_lr,
            verbose=1 if verbose > 0 else 0,
        ),
    ]

    try:
        history = model.fit(
            x=[X_ts_train, X_tick_train, X_sec_train],
            y=y_train,
            validation_data=([X_ts_val, X_tick_val, X_sec_val], y_val),
            epochs=epochs,
            batch_size=batch_size,
            shuffle=True,
            callbacks=callbacks,
            verbose=verbose,
        )
    except MemoryError as memory_error:
        raise RuntimeError(
            f"[{schema_name}] Out of memory during fit. "
            "Try lowering --max-samples (e.g. 200000) and/or --batch-size."
        ) from memory_error
    except Exception as fit_error:
        message = str(fit_error)
        if "Unable to allocate" in message:
            raise RuntimeError(
                f"[{schema_name}] Out of memory during fit ({message}). "
                "Try lowering --max-samples (e.g. 200000) and/or --batch-size."
            ) from fit_error
        raise

    with scaler_path.open("wb") as handle:
        pickle.dump(info["scaler"], handle)

    history_df = pd.DataFrame(history.history)
    history_df.insert(0, "epoch", np.arange(1, len(history_df) + 1))
    history_csv = case_output_dir / "history.csv"
    history_df.to_csv(history_csv, index=False)

    val_loss_series = history_df["val_loss"] if "val_loss" in history_df.columns else pd.Series(dtype="float64")
    if val_loss_series.empty:
        best_val_loss = None
        best_epoch = None
        final_val_loss = None
    else:
        min_idx = int(val_loss_series.idxmin())
        best_val_loss = _safe_float(val_loss_series.iloc[min_idx])
        best_epoch = int(history_df.loc[min_idx, "epoch"])
        final_val_loss = _safe_float(val_loss_series.iloc[-1])

    summary = {
        "schema_name": schema_name,
        "requested_features": requested_features,
        "effective_features": effective_features,
        "missing_features": missing_features,
        "raw_n_samples": raw_n_samples,
        "n_features": int(info.get("n_features", 0)),
        "n_samples": int(len(y_class)),
        "lookback": int(lookback),
        "horizons": [int(h) for h in info.get("horizons", horizons)],
        "train_rows": int(len(raw_df)),
        "train_date_min": str(raw_df["date"].min()),
        "train_date_max": str(raw_df["date"].max()),
        "epochs_ran": int(len(history_df)),
        "best_val_loss": best_val_loss,
        "best_epoch": best_epoch,
        "final_val_loss": final_val_loss,
        "history_csv": _path_for_report(history_csv),
        "model_path": _path_for_report(model_path),
        "scaler_path": _path_for_report(scaler_path),
    }

    summary_json = case_output_dir / "summary.json"
    summary_json.write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")

    return TrainArtifacts(
        schema_name=schema_name,
        requested_features=list(requested_features),
        effective_features=effective_features,
        missing_features=missing_features,
        raw_n_samples=raw_n_samples,
        n_features=int(info.get("n_features", 0)),
        n_samples=int(len(y_class)),
        epochs_ran=int(len(history_df)),
        history_csv=str(history_csv),
        summary_json=str(summary_json),
        model_path=str(model_path),
        scaler_path=str(scaler_path),
        best_val_loss=best_val_loss,
        best_epoch=best_epoch,
        final_val_loss=final_val_loss,
    )


def _save_curve_plot(
    *,
    output_dir: Path,
    dynamic_df: pd.DataFrame,
    fixed_df: pd.DataFrame,
) -> str | None:
    try:
        import matplotlib.pyplot as plt
    except Exception:
        return None

    if "val_loss" not in dynamic_df.columns or "val_loss" not in fixed_df.columns:
        return None

    figure_path = output_dir / "val_loss_curve.png"
    plt.figure(figsize=(10, 6))
    plt.plot(dynamic_df["epoch"], dynamic_df["val_loss"], label="dynamic23")
    plt.plot(fixed_df["epoch"], fixed_df["val_loss"], label="fixed17")
    plt.xlabel("Epoch")
    plt.ylabel("Validation Loss")
    plt.title("Transformer Val Loss Curve: dynamic23 vs fixed17")
    plt.grid(alpha=0.3)
    plt.legend()
    plt.tight_layout()
    plt.savefig(figure_path, dpi=140)
    plt.close()
    return str(figure_path)


def _write_comparison_report(
    *,
    output_dir: Path,
    dynamic_artifacts: TrainArtifacts,
    fixed_artifacts: TrainArtifacts,
    curve_csv: Path,
    curve_plot: str | None,
) -> Path:
    dynamic_best = dynamic_artifacts.best_val_loss
    fixed_best = fixed_artifacts.best_val_loss
    if dynamic_best is None or fixed_best is None:
        best_gap = None
    else:
        best_gap = fixed_best - dynamic_best

    comparison = {
        "dynamic23": asdict(dynamic_artifacts),
        "fixed17": asdict(fixed_artifacts),
        "best_val_loss_gap_fixed17_minus_dynamic23": best_gap,
        "val_curve_csv": str(curve_csv),
        "val_curve_plot": curve_plot,
    }
    comparison_json = output_dir / "comparison_summary.json"
    comparison_json.write_text(json.dumps(comparison, indent=2, ensure_ascii=False), encoding="utf-8")

    lines = [
        "# Transformer Feature Schema Retrain Comparison",
        "",
        f"- dynamic23 best val_loss: {dynamic_artifacts.best_val_loss}",
        f"- fixed17 best val_loss: {fixed_artifacts.best_val_loss}",
        f"- best val_loss gap (fixed17 - dynamic23): {best_gap}",
        f"- dynamic23 epochs ran: {dynamic_artifacts.epochs_ran}",
        f"- fixed17 epochs ran: {fixed_artifacts.epochs_ran}",
        f"- dynamic23 best epoch: {dynamic_artifacts.best_epoch}",
        f"- fixed17 best epoch: {fixed_artifacts.best_epoch}",
        "",
        "## Artifacts",
        f"- curve csv: {curve_csv}",
        f"- curve plot: {curve_plot if curve_plot else 'not generated (matplotlib unavailable or val_loss missing)'}",
        f"- dynamic23 summary: {dynamic_artifacts.summary_json}",
        f"- fixed17 summary: {fixed_artifacts.summary_json}",
        f"- comparison json: {comparison_json}",
    ]
    report_path = output_dir / "comparison_report.md"
    report_path.write_text("\n".join(lines), encoding="utf-8")
    return report_path


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Retrain transformer with pre-#307 dynamic23 vs current fixed17 and compare val curves.",
    )
    parser.add_argument("--db-name", default="db", help="Database connection profile name.")
    parser.add_argument("--lookback", type=int, default=60, help="Sequence length.")
    parser.add_argument("--horizons", default="1,3,5,7", help="Prediction horizons, comma separated.")
    parser.add_argument("--data-start-date", default="2015-01-01", help="Data fetch start date (YYYY-MM-DD).")
    parser.add_argument("--train-end-date", default="2023-12-31", help="Training cutoff date (YYYY-MM-DD).")
    parser.add_argument("--epochs", type=int, default=50, help="Max epochs.")
    parser.add_argument("--batch-size", type=int, default=32, help="Batch size.")
    parser.add_argument("--test-size", type=float, default=0.2, help="Validation split ratio.")
    parser.add_argument("--split-seed", type=int, default=42, help="Seed for train/val split.")
    parser.add_argument("--global-seed", type=int, default=42, help="Global random seed.")
    parser.add_argument("--patience", type=int, default=10, help="EarlyStopping patience.")
    parser.add_argument("--lr-patience", type=int, default=5, help="ReduceLROnPlateau patience.")
    parser.add_argument("--lr-factor", type=float, default=0.5, help="ReduceLROnPlateau factor.")
    parser.add_argument("--min-lr", type=float, default=1e-6, help="Minimum learning rate.")
    parser.add_argument(
        "--max-samples",
        type=int,
        default=300000,
        help="Cap samples per schema to avoid OOM. <=0 disables capping.",
    )
    parser.add_argument("--verbose", type=int, default=2, choices=[0, 1, 2], help="Keras fit verbosity.")
    parser.add_argument(
        "--output-dir",
        default=None,
        help="Optional output directory. Default: AI/backtests/out/transformer_schema_compare_<timestamp>",
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    output_dir = _prepare_output_dir(args.output_dir)
    horizons = _parse_horizons(args.horizons)

    run_config = {
        "db_name": args.db_name,
        "lookback": args.lookback,
        "horizons": horizons,
        "data_start_date": args.data_start_date,
        "train_end_date": args.train_end_date,
        "epochs": args.epochs,
        "batch_size": args.batch_size,
        "test_size": args.test_size,
        "split_seed": args.split_seed,
        "global_seed": args.global_seed,
        "patience": args.patience,
        "lr_patience": args.lr_patience,
        "lr_factor": args.lr_factor,
        "min_lr": args.min_lr,
        "max_samples": args.max_samples,
        "verbose": args.verbose,
        "output_dir": _path_for_report(output_dir),
    }
    (output_dir / "run_config.json").write_text(json.dumps(run_config, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"[SchemaCompare] Output directory: {output_dir}")
    print("[SchemaCompare] Training case 1/2: dynamic23 (pre-#307 schema)")
    dynamic_artifacts = _fit_single_schema(
        schema_name="dynamic23",
        requested_features=PRE307_DYNAMIC_23,
        case_output_dir=output_dir / "dynamic23",
        db_name=args.db_name,
        lookback=args.lookback,
        horizons=horizons,
        data_start_date=args.data_start_date,
        train_end_date=args.train_end_date,
        test_size=args.test_size,
        split_seed=args.split_seed,
        global_seed=args.global_seed,
        epochs=args.epochs,
        batch_size=args.batch_size,
        patience=args.patience,
        lr_patience=args.lr_patience,
        lr_factor=args.lr_factor,
        min_lr=args.min_lr,
        max_samples=(None if args.max_samples <= 0 else args.max_samples),
        verbose=args.verbose,
    )

    print("[SchemaCompare] Training case 2/2: fixed17 (current schema)")
    fixed_artifacts = _fit_single_schema(
        schema_name="fixed17",
        requested_features=CURRENT_FIXED_17,
        case_output_dir=output_dir / "fixed17",
        db_name=args.db_name,
        lookback=args.lookback,
        horizons=horizons,
        data_start_date=args.data_start_date,
        train_end_date=args.train_end_date,
        test_size=args.test_size,
        split_seed=args.split_seed,
        global_seed=args.global_seed,
        epochs=args.epochs,
        batch_size=args.batch_size,
        patience=args.patience,
        lr_patience=args.lr_patience,
        lr_factor=args.lr_factor,
        min_lr=args.min_lr,
        max_samples=(None if args.max_samples <= 0 else args.max_samples),
        verbose=args.verbose,
    )

    dynamic_df = pd.read_csv(dynamic_artifacts.history_csv)
    fixed_df = pd.read_csv(fixed_artifacts.history_csv)

    curve_df = pd.DataFrame({"epoch": np.arange(1, max(len(dynamic_df), len(fixed_df)) + 1)})
    curve_df = curve_df.merge(
        dynamic_df[["epoch", "val_loss"]].rename(columns={"val_loss": "dynamic23_val_loss"}),
        on="epoch",
        how="left",
    )
    curve_df = curve_df.merge(
        fixed_df[["epoch", "val_loss"]].rename(columns={"val_loss": "fixed17_val_loss"}),
        on="epoch",
        how="left",
    )
    curve_csv = output_dir / "val_curve_comparison.csv"
    curve_df.to_csv(curve_csv, index=False)

    curve_plot = _save_curve_plot(output_dir=output_dir, dynamic_df=dynamic_df, fixed_df=fixed_df)
    report_path = _write_comparison_report(
        output_dir=output_dir,
        dynamic_artifacts=dynamic_artifacts,
        fixed_artifacts=fixed_artifacts,
        curve_csv=curve_csv,
        curve_plot=curve_plot,
    )

    print("[SchemaCompare] Completed.")
    print(f"[SchemaCompare] Comparison report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
