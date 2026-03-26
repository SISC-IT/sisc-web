from __future__ import annotations

import json
import os
import pickle
from typing import Any, Dict, Optional

import numpy as np
import pandas as pd
import tensorflow as tf

from AI.modules.signal.core.artifact_paths import resolve_model_artifacts
from AI.modules.signal.core.base_model import BaseSignalModel
from AI.modules.signal.models.itransformer.architecture import build_itransformer_model


DEFAULT_HORIZONS = [1, 3, 5, 7]
DEFAULT_FEATURE_COLUMNS = [
    "us10y",
    "us10y_chg",
    "yield_spread",
    "vix_close",
    "vix_change_rate",
    "dxy_close",
    "dxy_chg",
    "credit_spread_hy",
    "wti_price",
    "gold_price",
    "nh_nl_index",
    "ma200_pct",
    "correlation_spike",
    "recent_loss_ema",
    "ret_1d",
    "intraday_vol",
    "log_return",
    "surprise_cpi",
]


class ITransformerWrapper(BaseSignalModel):
    supports_model_load_before_build = True

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.model_name = "itransformer"
        self.seq_len = int(config.get("seq_len", 60))
        self.horizons = list(config.get("horizons") or DEFAULT_HORIZONS)

        configured_features = (
            config.get("feature_columns")
            or config.get("feature_names")
            or config.get("features")
            or DEFAULT_FEATURE_COLUMNS
        )
        self.features = list(configured_features)
        self.feature_columns = self.features

        self.scaler = None
        self.metadata: Dict[str, Any] = {}

        self._explicit_scaler_path = bool(config.get("scaler_path"))
        self._explicit_metadata_path = bool(config.get("metadata_path"))

        artifact_paths = resolve_model_artifacts(
            model_name="itransformer",
            mode=config.get("mode"),
            config_weights_dir=config.get("artifact_root"),
            model_dir=config.get("weights_dir"),
        )

        self.weights_dir = os.path.abspath(config.get("weights_dir", artifact_paths.model_dir))
        self.model_path = os.path.abspath(config.get("model_path", artifact_paths.model_path))
        self.scaler_path = self._abspath_or_none(config.get("scaler_path", artifact_paths.scaler_path))
        self.metadata_path = self._abspath_or_none(config.get("metadata_path", artifact_paths.metadata_path))

        self._load_metadata()

    @staticmethod
    def _abspath_or_none(filepath: Optional[str]) -> Optional[str]:
        if not filepath:
            return None
        return os.path.abspath(filepath)

    @staticmethod
    def _to_float_array(values: Any) -> np.ndarray:
        array = np.asarray(values, dtype=np.float32)
        if array.ndim == 1:
            array = np.expand_dims(array, axis=1)
        return array

    def _normalize_input_array(self, X_input: np.ndarray) -> np.ndarray:
        array_x = np.asarray(X_input, dtype=np.float32)
        if array_x.ndim == 2:
            array_x = np.expand_dims(array_x, axis=0)
        if array_x.ndim != 3:
            raise ValueError(f"Expected 2D/3D input for iTransformer, got shape={array_x.shape}")

        if array_x.shape[1] < self.seq_len:
            raise ValueError(
                f"Insufficient timesteps for iTransformer inference: required {self.seq_len}, got {array_x.shape[1]}"
            )
        if array_x.shape[1] > self.seq_len:
            array_x = array_x[:, -self.seq_len :, :]

        return array_x

    def _resolve_horizons(self, output_dim: int) -> list[int]:
        if output_dim <= 0:
            return []
        if len(self.horizons) == output_dim:
            return list(self.horizons)
        if len(self.horizons) > output_dim:
            return list(self.horizons[:output_dim])

        if not self.horizons:
            return list(range(1, output_dim + 1))

        resolved = list(self.horizons)
        next_horizon = int(resolved[-1])
        while len(resolved) < output_dim:
            next_horizon += 1
            resolved.append(next_horizon)
        return resolved

    def _load_metadata(self) -> None:
        if not self.metadata_path or not os.path.exists(self.metadata_path):
            return

        with open(self.metadata_path, "r", encoding="utf-8") as f:
            self.metadata = json.load(f)

        self.seq_len = int(self.metadata.get("seq_len", self.seq_len))
        metadata_horizons = self.metadata.get("horizons")
        if isinstance(metadata_horizons, list) and metadata_horizons:
            self.horizons = list(metadata_horizons)

        metadata_features = self.metadata.get("feature_names") or self.metadata.get("feature_columns")
        if isinstance(metadata_features, list) and metadata_features:
            self.features = list(metadata_features)
            self.feature_columns = self.features

        config_overrides = {
            "n_tickers": self.metadata.get("n_tickers"),
            "n_sectors": self.metadata.get("n_sectors"),
            "head_size": self.metadata.get("head_size"),
            "num_heads": self.metadata.get("num_heads"),
            "ff_dim": self.metadata.get("ff_dim"),
            "num_blocks": self.metadata.get("num_blocks"),
            "mlp_units": self.metadata.get("mlp_units"),
            "dropout": self.metadata.get("dropout"),
            "mlp_dropout": self.metadata.get("mlp_dropout"),
            "n_outputs": len(self.horizons),
        }
        for key, value in config_overrides.items():
            if value is not None and key not in self.config:
                self.config[key] = value

    def _load_artifacts(self, require_scaler: bool = False) -> None:
        if self.model is None:
            if not os.path.exists(self.model_path):
                raise FileNotFoundError(f"iTransformer model file not found: {self.model_path}")
            self.load(self.model_path)

        if require_scaler and self.scaler is None:
            if not self.scaler_path:
                raise FileNotFoundError("iTransformer scaler_path is not configured.")
            if not os.path.exists(self.scaler_path):
                raise FileNotFoundError(f"iTransformer scaler file not found: {self.scaler_path}")
            self.load_scaler(self.scaler_path)

    def load_scaler(self, filepath: str) -> None:
        scaler_path = os.path.abspath(filepath)
        if not os.path.exists(scaler_path):
            raise FileNotFoundError(f"Scaler file not found: {scaler_path}")

        with open(scaler_path, "rb") as f:
            self.scaler = pickle.load(f)
        self.scaler_path = scaler_path

        scaler_features = getattr(self.scaler, "feature_names_in_", None)
        if scaler_features is not None and len(scaler_features) > 0:
            self.features = [str(name) for name in scaler_features]
            self.feature_columns = self.features

    def _prepare_model_inputs(
        self,
        X_input: np.ndarray,
        ticker_id: int = 0,
        sector_id: int = 0,
    ) -> list[np.ndarray]:
        sequence_array = self._normalize_input_array(X_input)
        batch_size = int(sequence_array.shape[0])
        ticker_array = np.full((batch_size, 1), int(ticker_id), dtype=np.int32)
        sector_array = np.full((batch_size, 1), int(sector_id), dtype=np.int32)
        return [sequence_array, ticker_array, sector_array]

    def build(self, input_shape: tuple):
        if len(input_shape) != 2:
            if len(input_shape) == 3 and input_shape[0] is None:
                input_shape = input_shape[1:]
            else:
                raise ValueError(f"Expected input_shape=(timesteps, features), got {input_shape}")

        seq_len, feature_count = int(input_shape[0]), int(input_shape[1])
        self.seq_len = seq_len
        if len(self.features) != feature_count:
            self.features = [f"feature_{idx}" for idx in range(feature_count)]
            self.feature_columns = self.features

        n_outputs = int(self.config.get("n_outputs", max(1, len(self.horizons))))
        self.horizons = self._resolve_horizons(n_outputs)

        self.model = build_itransformer_model(
            input_shape=(seq_len, feature_count),
            n_tickers=int(self.config.get("n_tickers", 1000)),
            n_sectors=int(self.config.get("n_sectors", 50)),
            n_outputs=n_outputs,
            head_size=int(self.config.get("head_size", 128)),
            num_heads=int(self.config.get("num_heads", 4)),
            ff_dim=int(self.config.get("ff_dim", 256)),
            num_transformer_blocks=int(self.config.get("num_blocks", self.config.get("num_transformer_blocks", 4))),
            mlp_units=list(self.config.get("mlp_units", [128, 64])),
            dropout=float(self.config.get("dropout", 0.2)),
            mlp_dropout=float(self.config.get("mlp_dropout", 0.2)),
        )

        self.model.compile(
            optimizer=tf.keras.optimizers.Adam(learning_rate=float(self.config.get("learning_rate", 1e-4))),
            loss="binary_crossentropy",
            metrics=[
                tf.keras.metrics.BinaryAccuracy(name="binary_accuracy"),
                tf.keras.metrics.AUC(name="auc"),
            ],
        )

    def train(
        self,
        X_train: np.ndarray,
        y_train: np.ndarray,
        X_val: Optional[np.ndarray] = None,
        y_val: Optional[np.ndarray] = None,
        **kwargs,
    ):
        train_x = self._normalize_input_array(X_train)
        train_y = self._to_float_array(y_train)
        output_dim = int(train_y.shape[1])
        self.horizons = self._resolve_horizons(output_dim)
        self.config["n_outputs"] = output_dim

        if self.model is None:
            self.build((train_x.shape[1], train_x.shape[2]))

        train_inputs = self._prepare_model_inputs(
            train_x,
            ticker_id=int(kwargs.pop("ticker_id", 0)),
            sector_id=int(kwargs.pop("sector_id", 0)),
        )

        validation_data = None
        if X_val is not None and y_val is not None:
            val_x = self._normalize_input_array(X_val)
            val_y = self._to_float_array(y_val)
            val_inputs = self._prepare_model_inputs(
                val_x,
                ticker_id=int(kwargs.pop("val_ticker_id", 0)),
                sector_id=int(kwargs.pop("val_sector_id", 0)),
            )
            validation_data = (val_inputs, val_y)

        return self.model.fit(
            x=train_inputs,
            y=train_y,
            validation_data=validation_data,
            epochs=int(kwargs.pop("epochs", self.config.get("epochs", 50))),
            batch_size=int(kwargs.pop("batch_size", self.config.get("batch_size", 32))),
            callbacks=kwargs.pop("callbacks", []),
            verbose=int(kwargs.pop("verbose", 1)),
            **kwargs,
        )

    def predict(self, X_input: np.ndarray, ticker_id: int = 0, sector_id: int = 0, **kwargs) -> np.ndarray:
        self._load_artifacts(require_scaler=False)
        model_inputs = self._prepare_model_inputs(X_input, ticker_id=ticker_id, sector_id=sector_id)
        verbose = int(kwargs.pop("verbose", 0))
        return self.model.predict(model_inputs, verbose=verbose, **kwargs)

    def _prepare_feature_window(self, df: pd.DataFrame) -> np.ndarray:
        if df is None or df.empty:
            raise ValueError("Input dataframe is empty.")
        if not self.features:
            raise ValueError("No iTransformer feature schema is configured.")
        if len(df) < self.seq_len:
            raise ValueError(
                f"Insufficient rows for iTransformer inference: required {self.seq_len}, got {len(df)}"
            )

        missing_features = [column for column in self.features if column not in df.columns]
        if missing_features:
            raise ValueError(
                "Missing required features for iTransformer inference: " + ", ".join(missing_features)
            )

        window = df[self.features].tail(self.seq_len).to_numpy(dtype=np.float32)
        if self.scaler is not None:
            window = self.scaler.transform(window).astype(np.float32)
        return window

    def get_signals(self, df: pd.DataFrame, ticker_id: int = 0, sector_id: int = 0) -> Dict[str, float]:
        self._load_artifacts(require_scaler=bool(self.scaler_path))
        window = self._prepare_feature_window(df)
        probs = np.asarray(self.predict(window, ticker_id=ticker_id, sector_id=sector_id, verbose=0)[0]).reshape(-1)

        if probs.size == 0:
            return {"itransformer_1d": 0.5}

        horizons = self._resolve_horizons(int(probs.size))
        self.horizons = horizons
        return {f"itransformer_{horizon}d": float(prob) for horizon, prob in zip(horizons, probs)}

    def save(self, filepath: str):
        if self.model is None:
            raise ValueError("No iTransformer model to save.")
        target_path = os.path.abspath(filepath)
        target_dir = os.path.dirname(target_path)
        if target_dir:
            os.makedirs(target_dir, exist_ok=True)
        self.model.save(target_path)

    def load(self, filepath: Optional[str] = None):
        target_path = os.path.abspath(filepath or self.model_path)
        if not os.path.exists(target_path):
            raise FileNotFoundError(f"iTransformer model file not found: {target_path}")

        target_dir = os.path.dirname(target_path)
        self.weights_dir = target_dir
        self.model_path = target_path

        if not self._explicit_scaler_path:
            default_scaler_path = os.path.join(target_dir, "multi_horizon_scaler.pkl")
            self.scaler_path = default_scaler_path if os.path.exists(default_scaler_path) else None
        elif self.scaler_path:
            self.scaler_path = os.path.abspath(self.scaler_path)

        if not self._explicit_metadata_path:
            default_metadata_path = os.path.join(target_dir, "metadata.json")
            self.metadata_path = default_metadata_path if os.path.exists(default_metadata_path) else None
        elif self.metadata_path:
            self.metadata_path = os.path.abspath(self.metadata_path)

        self._load_metadata()
        self.model = tf.keras.models.load_model(target_path, compile=False)

        if self.scaler_path and os.path.exists(self.scaler_path):
            self.load_scaler(self.scaler_path)


ITransformerSignalModel = ITransformerWrapper
