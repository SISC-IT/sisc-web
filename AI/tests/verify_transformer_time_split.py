"""Offline regression checks for Transformer date splits; no DB or model training.

Run with the project's Python dependencies:
    python AI/tests/verify_transformer_time_split.py
"""

from __future__ import annotations

import io
import json
import pickle
import sys
import tempfile
import unittest
import warnings
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import Mock, patch

import numpy as np
import pandas as pd
from sklearn.preprocessing import MinMaxScaler, StandardScaler

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from AI.modules.signal.core import data_loader
from AI.modules.signal.core.time_split import chronological_split, resolve_validation_start
from AI.modules.signal.models import oos2024_kaggle_train_eval as oos


LOOKBACK = 60
HORIZONS = [1, 3, 5, 7]
DATES = pd.bdate_range("2023-09-01", periods=160)
BOUNDARY = DATES[100]


def fixture_frame():
    """Two tickers share a calendar boundary; B has missing trading observations."""
    frames = []
    for ticker, offset, holes in (
        ("A", 0, []),
        ("B", 100, [15, 48, 90, 91, 98, 102, 110, 119, 138, 150]),
    ):
        index = np.delete(np.arange(len(DATES)), holes)
        frame = pd.DataFrame({
            "date": DATES[index],
            "ticker": ticker,
            "sector": "Unknown",
            "close": 100 + offset + index * 0.2 + 3 * np.sin(index / 3),
            "log_return": index.astype(float) + offset,
        })
        frame.loc[frame["date"] >= BOUNDARY, "log_return"] += 10_000
        frames.append(frame)
    return pd.concat(frames, ignore_index=True)


def make_loader():
    loader = data_loader.DataLoader.__new__(data_loader.DataLoader)
    loader.db_name = "unused_offline_test"
    loader.lookback = LOOKBACK
    loader.horizons = HORIZONS
    loader.scaler = MinMaxScaler()
    loader.ticker_to_id = {"A": 0, "B": 1}
    loader.sector_to_id = {"Unknown": 0}
    loader.ticker_to_sector_id = {"A": 0, "B": 0}
    loader.macro_df = pd.DataFrame()
    loader.breadth_df = pd.DataFrame()
    return loader


def create_offline_dataset(frame):
    loader = make_loader()
    # Only external fetching and unrelated feature generation are replaced.
    # The production scaler, window generation, targets and split all execute.
    with (
        patch.object(data_loader, "get_engine", side_effect=AssertionError("DB access forbidden")),
        patch.object(data_loader, "fetch_news_sentiment", return_value=pd.DataFrame()),
        patch.object(data_loader, "fetch_fundamentals", return_value=pd.DataFrame()),
        patch.object(data_loader, "add_technical_indicators", side_effect=lambda x: x),
        patch.object(data_loader, "add_multi_timeframe_features", side_effect=lambda x: x),
        redirect_stdout(io.StringIO()),
        redirect_stderr(io.StringIO()),
    ):
        dataset = loader.create_dataset(
            frame, feature_columns=["log_return"], validation_start=BOUNDARY
        )
    return loader, dataset


class TimeSplitRegressionTests(unittest.TestCase):
    def assert_safe_split(self, name, rows, train_mask, val_mask):
        train_rows, val_rows = rows.loc[train_mask], rows.loc[val_mask]
        self.assertFalse(np.any(train_mask & val_mask))
        self.assertEqual(set(train_rows["ticker"]), {"A", "B"})
        self.assertEqual(set(val_rows["ticker"]), {"A", "B"})
        self.assertTrue((train_rows["asof_date"] < BOUNDARY).all())
        self.assertTrue((val_rows["asof_date"] >= BOUNDARY).all())
        violations = int((train_rows["label_end_max"] >= BOUNDARY).sum())
        self.assertEqual(violations, 0)
        # A: 94 windows, B: 84; each has seven pre-boundary labels to purge.
        self.assertEqual((len(rows), int(train_mask.sum()), int(val_mask.sum())), (178, 63, 101))
        self.assertEqual(int((~(train_mask | val_mask)).sum()), 14)
        for ticker, group in fixture_frame().groupby("ticker"):
            actual = rows.loc[rows["ticker"] == ticker].reset_index(drop=True)
            expected_asof = group["date"].iloc[LOOKBACK - 1:-max(HORIZONS)].to_numpy()
            expected_end = group["date"].iloc[LOOKBACK - 1 + max(HORIZONS):].to_numpy()
            np.testing.assert_array_equal(actual["asof_date"].to_numpy(), expected_asof)
            np.testing.assert_array_equal(actual["label_end_max"].to_numpy(), expected_end)
        print(
            f"{name}: boundary={BOUNDARY.date()} candidates={len(rows)} "
            f"train={train_mask.sum()} validation={val_mask.sum()} purged=14 "
            f"train_label_boundary_violations={violations}"
        )

    def test_default_boundary_uses_unique_dates_not_ticker_row_count(self):
        dates = pd.bdate_range("2024-01-01", periods=20)
        repeated = list(dates) + [dates[0]] * 100
        self.assertEqual(resolve_validation_start(repeated), dates[16])
        self.assertEqual(resolve_validation_start(repeated[::-1]), dates[16])
        self.assertEqual(resolve_validation_start(repeated, validation_start=dates[12]), dates[12])

    def test_invalid_dates_labels_and_empty_splits_fail(self):
        for values in ([pd.NaT, "2024-01-02"], ["not-a-date", "2024-01-02"], ["2024-01-01"]):
            with self.subTest(values=values), self.assertRaises(ValueError):
                resolve_validation_start(values)
        for fraction in (0, 1, -0.1):
            with self.subTest(fraction=fraction), self.assertRaises(ValueError):
                resolve_validation_start(DATES, validation_fraction=fraction)
        for boundary in (pd.NaT, "not-a-date", DATES[0], DATES[-1] + pd.Timedelta(days=1)):
            with self.subTest(boundary=boundary), self.assertRaises(ValueError):
                resolve_validation_start(DATES, validation_start=boundary)
        for asof, label_end, boundary in (
            ([pd.NaT], ["2024-01-04"], "2024-01-03"),
            (["2024-01-01"], [pd.NaT], "2024-01-03"),
            (["2024-01-01"], [], "2024-01-03"),
            (["2024-01-01"], ["2024-01-01"], "2024-01-03"),
            (["2024-01-02"], ["2024-01-01"], "2024-01-03"),
            (["2024-01-01"], ["2024-01-02"], pd.NaT),
            (["2024-01-01"], ["2024-01-02"], "2024-01-03"),
            (["2024-01-03"], ["2024-01-04"], "2024-01-03"),
        ):
            with self.subTest(asof=asof, label_end=label_end), self.assertRaises(ValueError):
                chronological_split(asof, label_end, boundary)

    def test_data_loader_scaler_targets_and_validation_history(self):
        frame = fixture_frame()
        loader, (x, ticker_ids, _, labels, returns, info) = create_offline_dataset(frame)
        rows, train, val = info["rows"], info["train_mask"], info["val_mask"]
        self.assert_safe_split("DataLoader", rows, train, val)
        self.assertEqual(x.shape, (178, LOOKBACK, 1))
        train_features = frame.loc[frame["date"] < BOUNDARY, ["log_return"]]
        self.assertEqual(int(loader.scaler.n_samples_seen_), len(train_features))
        np.testing.assert_allclose(loader.scaler.data_max_, train_features.max().to_numpy())
        self.assertLess(pd.Timestamp(info["split_summary"]["scaler_fit_max"]), BOUNDARY)

        for ticker, group in frame.groupby("ticker"):
            group = group.sort_values("date").reset_index(drop=True)
            positions = rows.index[rows["ticker"] == ticker]
            current = group["close"].to_numpy()[LOOKBACK - 1:-max(HORIZONS)]
            future = np.stack([
                group["close"].to_numpy()[LOOKBACK - 1 + h:len(group) - max(HORIZONS) + h]
                for h in HORIZONS
            ], axis=1)
            np.testing.assert_array_equal(labels[positions], (future > current[:, None]).astype(int))
            np.testing.assert_allclose(returns[positions], (future[:, -1] - current) / current)
            np.testing.assert_array_equal(ticker_ids[positions], loader.ticker_to_id[ticker])
            first_val = rows.index[(rows["ticker"] == ticker) & (rows["asof_date"] == BOUNDARY)][0]
            history = group.loc[group["date"] <= BOUNDARY].tail(LOOKBACK)
            self.assertEqual(int((history["date"] < BOUNDARY).sum()), LOOKBACK - 1)
            np.testing.assert_allclose(x[first_val], loader.scaler.transform(history[["log_return"]]))
            self.assertGreater(float(x[first_val, -1, 0]), 1)

        changed = frame.copy()
        changed.loc[changed["date"] >= BOUNDARY, "log_return"] *= 1_000_000
        changed.loc[changed["date"] >= BOUNDARY, "close"] *= 1_000
        changed_loader, changed_dataset = create_offline_dataset(changed)
        np.testing.assert_array_equal(loader.scaler.data_max_, changed_loader.scaler.data_max_)
        np.testing.assert_array_equal(x[train], changed_dataset[0][train])
        np.testing.assert_array_equal(labels[train], changed_dataset[3][train])
        np.testing.assert_array_equal(returns[train], changed_dataset[4][train])

    def test_kaggle_builder_keeps_context_and_never_refits_scaler(self):
        with redirect_stdout(io.StringIO()), warnings.catch_warnings():
            warnings.simplefilter("ignore", DeprecationWarning)
            from AI.modules.signal.models.transformer import train_kaggle as kaggle
        frame = fixture_frame()
        scaler = StandardScaler().fit(frame.loc[frame["date"] < BOUNDARY, ["log_return"]])
        with (
            patch.dict(kaggle.CONFIG, {"seq_len": LOOKBACK}),
            patch.object(scaler, "fit", side_effect=AssertionError("builder must not refit")),
        ):
            x, _, _, _, _, _, rows = kaggle.build_sequences_transformer(
                frame, scaler, {"A": 0, "B": 1}, {"Unknown": 0}
            )
        train, val = chronological_split(rows["asof_date"], rows["label_end_max"], BOUNDARY)
        self.assert_safe_split("Kaggle builder", rows, train, val)
        first_val = rows.index[(rows["ticker"] == "A") & (rows["asof_date"] == BOUNDARY)][0]
        history = frame.loc[(frame["ticker"] == "A") & (frame["date"] <= BOUNDARY)].tail(LOOKBACK)
        np.testing.assert_allclose(x[first_val], scaler.transform(history[["log_return"]]), rtol=1e-6)

    def test_oos_scaler_purge_and_empty_validation(self):
        frame = fixture_frame()
        config = oos.build_oos2024_config({
            "train_start": str(DATES[0].date()),
            "train_cutoff": str(DATES[-1].date()),
            "validation_start": str(BOUNDARY.date()),
            "seq_len": LOOKBACK,
        })
        scaler = oos._fit_scaler(frame, ["log_return"], config)
        train_features = frame.loc[frame["date"] < BOUNDARY, ["log_return"]]
        self.assertEqual(int(scaler.n_samples_seen_), len(train_features))
        np.testing.assert_allclose(scaler.mean_, train_features.mean().to_numpy())
        dataset = oos._build_supervised_sequences(
            frame, feature_columns=["log_return"], scaler=scaler,
            ticker_to_id={"A": 0, "B": 1}, sector_to_id={"Unknown": 0},
            config=config, purpose="train",
        )
        with redirect_stdout(io.StringIO()):
            fit, validation = oos._split_train_val(dataset, config)
        rows = dataset["rows"]
        train, val = chronological_split(rows["asof_date"], rows["label_end_max"], BOUNDARY)
        self.assert_safe_split("OOS2024", rows, train, val)
        np.testing.assert_array_equal(fit["x"], dataset["x"][train])
        np.testing.assert_array_equal(validation["x"], dataset["x"][val])
        self.assertEqual(fit["split_summary"]["n_purged"], 14)
        before_boundary = rows["asof_date"] < BOUNDARY
        no_validation = {
            key: dataset[key][before_boundary.to_numpy()]
            for key in ("x", "ticker", "sector", "y")
        }
        no_validation["rows"] = rows.loc[before_boundary].reset_index(drop=True)
        with self.assertRaises(ValueError):
            oos._split_train_val(no_validation, config)

    def test_kaggle_pipeline_persists_train_only_scaler_and_split_metadata(self):
        with redirect_stdout(io.StringIO()), warnings.catch_warnings():
            warnings.simplefilter("ignore", DeprecationWarning)
            from AI.modules.signal.models.transformer import train_kaggle as kaggle
        frame = fixture_frame()
        model = Mock()  # No neural-network construction, fitting or checkpoint writes.
        with (
            tempfile.TemporaryDirectory() as output_dir,
            patch.dict(kaggle.CONFIG, {
                "parquet_dir": output_dir,
                "weights_dir": output_dir,
                "seq_len": LOOKBACK,
                "validation_start": str(BOUNDARY.date()),
            }),
            patch.object(kaggle, "load_and_preprocess", return_value=frame),
            patch.object(kaggle, "build_transformer_model", return_value=model),
            redirect_stdout(io.StringIO()),
        ):
            kaggle.train_single_pipeline()
            split_path = Path(output_dir) / (Path(kaggle.CONFIG["model_name"]).stem + ".split.json")
            metadata = json.loads(split_path.read_text())
            with (Path(output_dir) / kaggle.CONFIG["scaler_name"]).open("rb") as file:
                scaler = pickle.load(file)
        self.assertEqual((metadata["n_train"], metadata["n_validation"], metadata["n_purged"]), (63, 101, 14))
        self.assertLess(pd.Timestamp(metadata["train_label_end_max"]), BOUNDARY)
        self.assertLess(pd.Timestamp(metadata["scaler_fit_date_max"]), BOUNDARY)
        train_features = frame.loc[frame["date"] < BOUNDARY, ["log_return"]]
        np.testing.assert_allclose(scaler.mean_, train_features.mean().to_numpy())
        self.assertEqual(int(scaler.n_samples_seen_), len(train_features))
        model.fit.assert_called_once()
        self.assertEqual(len(model.fit.call_args.kwargs["y"]), 63)
        self.assertEqual(len(model.fit.call_args.kwargs["validation_data"][1]), 101)
        self.assertTrue(model.fit.call_args.kwargs["shuffle"])

    def test_schema_compare_samples_each_date_partition_and_keeps_arrays_aligned(self):
        """Small caps retain both partitions, exclude purged rows and preserve row identity."""
        from AI.backtests import compare_transformer_feature_schemas as compare

        frame = fixture_frame()
        _, dataset = create_offline_dataset(frame)
        x, ticker, sector, labels, _, info = dataset
        # Encode row identity to check every model input against its original row.
        x[:, 0, 0] = np.arange(len(x))
        loader = Mock()
        loader.load_data_from_db.return_value = frame
        loader.create_dataset.return_value = dataset
        previous_ids = None
        for cap, seed in ((2, 0), (2, 1), (20, 0), (20, 1), (20, 1), (None, 0), (0, 0), (999, 0), (1, 0)):
            model = Mock()
            model.fit.return_value.history = {"loss": [0.7], "val_loss": [0.7]}
            with (
                self.subTest(cap=cap, seed=seed),
                tempfile.TemporaryDirectory() as output_dir,
                patch.object(compare, "DataLoader", return_value=loader),
                patch.object(compare, "build_transformer_model", return_value=model),
                redirect_stdout(io.StringIO()),
            ):
                arguments = dict(
                    schema_name="offline", requested_features=["log_return"],
                    case_output_dir=Path(output_dir), db_name="unused", lookback=LOOKBACK,
                    horizons=HORIZONS, data_start_date=str(DATES[0].date()),
                    train_end_date=str(DATES[-1].date()), test_size=0.2, split_seed=seed,
                    global_seed=42, epochs=1, batch_size=32, patience=1,
                    lr_patience=1, lr_factor=0.5, min_lr=1e-6, max_samples=cap,
                    verbose=0, deterministic=False,
                )
                if cap == 1:
                    with self.assertRaisesRegex(ValueError, "max_samples.*2"):
                        compare._fit_single_schema(**arguments)
                    model.fit.assert_not_called()
                    continue
                artifacts = compare._fit_single_schema(**arguments)
                metadata = json.loads((Path(output_dir) / "split.json").read_text())
                fit = model.fit.call_args.kwargs
                train_ids = fit["x"][0][:, 0, 0].astype(int)
                val_ids = fit["validation_data"][0][0][:, 0, 0].astype(int)
                expected_counts = {2: (1, 1), 20: (7, 13)}.get(cap, (63, 101))
                self.assertEqual((len(train_ids), len(val_ids)), expected_counts)
                self.assertTrue(info["train_mask"][train_ids].all())
                self.assertTrue(info["val_mask"][val_ids].all())
                self.assertEqual(len(set(train_ids) | set(val_ids)), sum(expected_counts))
                for ids, inputs, targets in (
                    (train_ids, fit["x"], fit["y"]),
                    (val_ids, *fit["validation_data"]),
                ):
                    for actual, original in zip(inputs, (x, ticker, sector)):
                        np.testing.assert_array_equal(actual, original[ids].astype(actual.dtype))
                    np.testing.assert_array_equal(targets, labels[ids])
                self.assertTrue(fit["shuffle"])
                self.assertEqual(artifacts.n_samples, sum(expected_counts))
                self.assertEqual(metadata["full_candidate_samples"], 178)
                self.assertEqual(metadata["full_purged_samples"], 14)
                self.assertEqual(metadata["selected_candidate_samples"], sum(expected_counts))
                self.assertEqual(metadata["purged_samples"], 0)
                self.assertEqual((metadata["train_samples"], metadata["validation_samples"]), expected_counts)
                if cap == 20 and seed == 1:
                    if previous_ids is not None:
                        np.testing.assert_array_equal(train_ids, previous_ids[0])
                        np.testing.assert_array_equal(val_ids, previous_ids[1])
                    previous_ids = (train_ids.copy(), val_ids.copy())

    def test_oos_evaluation_boundaries_and_train_independence(self):
        """Evaluation uses past context and the train scaler, without entering fit/validation."""
        frame = fixture_frame()
        frame["date"] += pd.Timedelta(days=350)
        config = oos.build_oos2024_config({
            "train_start": str(frame["date"].min().date()),
            "validation_start": "2024-11-25",
            "train_cutoff": "2024-12-13", "eval_start": "2024-12-16",
            "eval_end": "2024-12-31", "holdout_start": "2025-01-01",
            "seq_len": LOOKBACK,
        })
        scaler = oos._fit_scaler(frame, ["log_return"], config)
        def build(source, purpose):
            return oos._build_supervised_sequences(
                source, feature_columns=["log_return"], scaler=scaler,
                ticker_to_id={"A": 0, "B": 1}, sector_to_id={"Unknown": 0},
                config=config, purpose=purpose,
            )
        with patch.object(scaler, "fit", side_effect=AssertionError("must reuse train scaler")):
            dataset, evaluation = build(frame, "train"), build(frame, "eval")
        with redirect_stdout(io.StringIO()):
            train, validation = oos._split_train_val(dataset, config)
        for partition in (train, validation):
            self.assertTrue((partition["rows"]["label_end_max"] <= pd.Timestamp(config["train_cutoff"])).all())
        rows = evaluation["rows"]
        self.assertEqual(rows["asof_date"].min(), pd.Timestamp(config["eval_start"]))
        self.assertTrue((rows["label_end_max"] <= pd.Timestamp(config["eval_end"])).all())
        self.assertTrue((rows["label_end_max"] < pd.Timestamp(config["holdout_start"])).all())
        first = rows.iloc[0]
        history = frame.loc[(frame["ticker"] == first["ticker"]) & (frame["date"] <= first["asof_date"])].tail(LOOKBACK)
        self.assertEqual(int((history["date"] < pd.Timestamp(config["eval_start"])).sum()), LOOKBACK - 1)
        np.testing.assert_allclose(evaluation["x"][0], scaler.transform(history[["log_return"]]), rtol=1e-6)
        changed = frame.copy()
        changed.loc[changed["date"] >= config["eval_start"], ["close", "log_return"]] *= 1000
        changed_scaler = oos._fit_scaler(changed, ["log_return"], config)
        np.testing.assert_array_equal(scaler.mean_, changed_scaler.mean_)
        for key in ("x", "y"):
            np.testing.assert_array_equal(dataset[key], build(changed, "train")[key])
        for invalid in ({"train_cutoff": config["eval_start"]}, {"eval_end": config["holdout_start"]}):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                oos.validate_oos2024_config(dict(config, **invalid))


if __name__ == "__main__":
    unittest.main(verbosity=2)
