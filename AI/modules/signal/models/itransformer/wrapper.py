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
from AI.modules.signal.models.itransformer.feature_contract import (
    ITRANSFORMER_DEFAULT_HORIZONS,
    ITRANSFORMER_FEATURE_SET_VER,
    build_itransformer_metadata,
    canonicalize_itransformer_feature_name,
    get_itransformer_default_features,
    load_itransformer_metadata,
    normalize_itransformer_feature_aliases,
    require_itransformer_feature_columns,
    resolve_itransformer_metadata_path,
    save_itransformer_metadata,
)


DEFAULT_HORIZONS = list(ITRANSFORMER_DEFAULT_HORIZONS)
DEFAULT_FEATURE_COLUMNS = get_itransformer_default_features()


class ITransformerWrapper(BaseSignalModel):
    """iTransformer 추론 wrapper.

    `predict()`는 기존 배열 기반 호출 호환성을 유지한다. 평가 경로에서는
    `predict_with_status()`를 사용해 metadata 없는 legacy artifact나 오류 예측을
    `prediction_status="fallback"`으로 분리한다.
    """

    supports_model_load_before_build = True

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.model_name = "itransformer"
        self.seq_len = int(config.get("seq_len", config.get("lookback", 60)))
        self.horizons = list(config.get("horizons") or DEFAULT_HORIZONS)
        self.feature_set_ver = str(config.get("feature_set_ver", ITRANSFORMER_FEATURE_SET_VER))

        configured_features = (
            config.get("feature_columns")
            or config.get("feature_names")
            or config.get("features")
            or DEFAULT_FEATURE_COLUMNS
        )
        self.feature_columns = [
            canonicalize_itransformer_feature_name(column) for column in configured_features
        ]
        self.features = self.feature_columns

        self.scaler = None
        self.metadata: Dict[str, Any] = {}
        self.legacy_artifact = False
        self.artifact_status = "metadata"
        self.metadata_error = ""
        self.last_prediction_status = "ok"
        self.last_error_message = ""

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

    @property
    def feature_count(self) -> int:
        """현재 wrapper가 기대하는 feature 수를 반환한다."""
        return len(self.feature_columns)

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

    def _default_output(self) -> Dict[str, float]:
        """fallback 상황에서 중립 확률을 반환한다."""
        return {f"itransformer_{horizon}d": 0.5 for horizon in self.horizons}

    def _artifact_path(self) -> str:
        return str(self.model_path or self.config.get("model_path") or "")

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
        resolved = list(self.horizons or DEFAULT_HORIZONS)
        next_horizon = int(resolved[-1]) if resolved else 1
        while len(resolved) < output_dim:
            next_horizon += 1
            resolved.append(next_horizon)
        return resolved

    def _apply_metadata(self, metadata: dict[str, Any], *, strict: bool) -> None:
        self.metadata = dict(metadata)
        self.feature_set_ver = str(metadata.get("feature_set_ver", self.feature_set_ver))

        metadata_features = metadata.get("feature_columns") or metadata.get("feature_names")
        if isinstance(metadata_features, list) and metadata_features:
            self.feature_columns = [
                canonicalize_itransformer_feature_name(column) for column in metadata_features
            ]
            self.features = self.feature_columns

        if strict:
            expected_count = int(metadata.get("feature_count", len(self.feature_columns)))
            if expected_count != len(self.feature_columns):
                raise ValueError(
                    "iTransformer metadata feature_count가 feature_columns 길이와 다릅니다. "
                    f"feature_count={expected_count}, columns={len(self.feature_columns)}"
                )

        self.seq_len = int(metadata.get("seq_len", self.seq_len))
        metadata_horizons = metadata.get("horizons")
        if isinstance(metadata_horizons, list) and metadata_horizons:
            self.horizons = [int(horizon) for horizon in metadata_horizons]

        if metadata.get("model_path") and not self.model_path:
            self.model_path = os.path.abspath(str(metadata["model_path"]))
        if metadata.get("scaler_path") and not self.scaler_path and not self._explicit_scaler_path:
            self.scaler_path = os.path.abspath(str(metadata["scaler_path"]))

        config_overrides = {
            "n_tickers": metadata.get("n_tickers"),
            "n_sectors": metadata.get("n_sectors"),
            "head_size": metadata.get("head_size"),
            "num_heads": metadata.get("num_heads"),
            "ff_dim": metadata.get("ff_dim"),
            "num_blocks": metadata.get("num_blocks"),
            "mlp_units": metadata.get("mlp_units"),
            "dropout": metadata.get("dropout"),
            "mlp_dropout": metadata.get("mlp_dropout"),
            "n_outputs": len(self.horizons),
            "feature_set_ver": self.feature_set_ver,
            "feature_columns": list(self.feature_columns),
        }
        for key, value in config_overrides.items():
            if value is not None and key not in self.config:
                self.config[key] = value

    def _load_metadata(self) -> None:
        if not self.metadata_path or not os.path.exists(self.metadata_path):
            self.legacy_artifact = True
            self.artifact_status = "legacy"
            self.metadata = {}
            return

        try:
            metadata = load_itransformer_metadata(self.metadata_path)
        except Exception as exc:
            self.legacy_artifact = True
            self.artifact_status = "legacy_metadata_error"
            self.metadata_error = str(exc)
            try:
                with open(self.metadata_path, "r", encoding="utf-8") as f:
                    raw_metadata = json.load(f)
                self._apply_metadata(raw_metadata, strict=False)
            except Exception:
                self.metadata = {}
            return

        if metadata is None:
            self.legacy_artifact = True
            self.artifact_status = "legacy"
            self.metadata = {}
            return

        self.legacy_artifact = False
        self.artifact_status = "metadata"
        self.metadata_error = ""
        self._apply_metadata(metadata, strict=True)

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

    def _validate_scaler_contract(self) -> None:
        if self.scaler is None:
            return

        scaler_features = getattr(self.scaler, "feature_names_in_", None)
        if scaler_features is not None and len(scaler_features) > 0:
            scaler_feature_columns = [str(column) for column in scaler_features]
            if self.legacy_artifact and not self.metadata:
                self.feature_columns = scaler_feature_columns
                self.features = self.feature_columns
            elif scaler_feature_columns != self.feature_columns:
                raise ValueError(
                    "iTransformer scaler feature 순서가 metadata와 다릅니다. "
                    f"scaler={scaler_feature_columns}, metadata={self.feature_columns}"
                )

        scaler_feature_count = getattr(self.scaler, "n_features_in_", None)
        if scaler_feature_count is not None and int(scaler_feature_count) != len(self.feature_columns):
            raise ValueError(
                "iTransformer scaler feature 수가 metadata와 다릅니다. "
                f"scaler={int(scaler_feature_count)}, metadata={len(self.feature_columns)}"
            )

    def load_scaler(self, filepath: str) -> None:
        scaler_path = os.path.abspath(filepath)
        if not os.path.exists(scaler_path):
            raise FileNotFoundError(f"Scaler file not found: {scaler_path}")

        with open(scaler_path, "rb") as f:
            self.scaler = pickle.load(f)
        self.scaler_path = scaler_path
        self._validate_scaler_contract()

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
        if len(self.feature_columns) != feature_count:
            if self.metadata and not self.legacy_artifact:
                raise ValueError(
                    "iTransformer metadata feature 수가 model input shape와 다릅니다. "
                    f"metadata={len(self.feature_columns)}, model={feature_count}"
                )
            self.feature_columns = [f"feature_{idx}" for idx in range(feature_count)]
            self.features = self.feature_columns

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
            raise ValueError("iTransformer 입력 DataFrame이 비어 있습니다.")
        if not self.feature_columns:
            raise ValueError("iTransformer feature schema가 설정되지 않았습니다.")
        if len(df) < self.seq_len:
            raise ValueError(
                f"iTransformer 추론에 필요한 row가 부족합니다: required={self.seq_len}, got={len(df)}"
            )
        if self.scaler is None:
            raise ValueError("iTransformer scaler가 로드되지 않았습니다.")

        normalized = normalize_itransformer_feature_aliases(df)
        feature_columns = require_itransformer_feature_columns(
            normalized,
            feature_columns=self.feature_columns,
        )
        window_frame = normalized[feature_columns].tail(self.seq_len).astype(np.float32)
        if not np.isfinite(window_frame.to_numpy(dtype=np.float32)).all():
            raise ValueError("iTransformer 입력 feature에 NaN 또는 무한대 값이 있습니다.")
        return self.scaler.transform(window_frame).astype(np.float32)

    def _predict_probabilities(self, df: pd.DataFrame, ticker_id: int = 0, sector_id: int = 0) -> np.ndarray:
        self._load_artifacts(require_scaler=True)
        window = self._prepare_feature_window(df)
        probs = np.asarray(
            self.predict(window, ticker_id=ticker_id, sector_id=sector_id, verbose=0)[0]
        ).reshape(-1)
        return probs.astype(np.float32)

    def predict_with_status(
        self,
        df: pd.DataFrame,
        ticker_id: int = 0,
        sector_id: int = 0,
    ) -> Dict[str, Any]:
        """평가 경로에서 사용할 status 포함 예측 결과를 반환한다."""
        try:
            probs = self._predict_probabilities(df, ticker_id=ticker_id, sector_id=sector_id)
            if probs.size == 0:
                raise ValueError("iTransformer output dimension이 0입니다.")
            horizons = self._resolve_horizons(int(probs.size))
            self.horizons = horizons
            output = {
                f"itransformer_{horizon}d": float(prob)
                for horizon, prob in zip(horizons, probs)
            }
            if self.legacy_artifact:
                status = "fallback"
                error_message = (
                    "metadata sidecar가 없는 legacy artifact입니다. "
                    "평가 기본 집계에서는 제외해야 합니다."
                )
                if self.metadata_error:
                    error_message = f"{error_message} metadata_error={self.metadata_error}"
            else:
                status = "ok"
                error_message = ""
        except Exception as exc:
            output = self._default_output()
            status = "fallback"
            error_message = str(exc)

        self.last_prediction_status = status
        self.last_error_message = error_message
        return {
            "output": output,
            "prediction_status": status,
            "error_message": error_message,
            "feature_set_ver": self.feature_set_ver,
            "seq_len": self.seq_len,
            "feature_count": self.feature_count,
            "artifact_path": self._artifact_path(),
            "metadata_path": str(self.metadata_path or ""),
            "legacy_artifact": self.legacy_artifact,
            "artifact_status": self.artifact_status,
        }

    def get_signals(self, df: pd.DataFrame, ticker_id: int = 0, sector_id: int = 0) -> Dict[str, float]:
        return dict(self.predict_with_status(df, ticker_id=ticker_id, sector_id=sector_id)["output"])

    def save(self, filepath: str):
        if self.model is None:
            raise ValueError("저장할 iTransformer model이 없습니다.")
        target_path = os.path.abspath(filepath)
        target_dir = os.path.dirname(target_path)
        if target_dir:
            os.makedirs(target_dir, exist_ok=True)
        self.model.save(target_path)
        self.model_path = target_path

        scaler_path = self.scaler_path or os.path.join(target_dir, "multi_horizon_scaler.pkl")
        if self.scaler is None:
            raise ValueError("저장할 iTransformer scaler가 없습니다.")
        with open(scaler_path, "wb") as f:
            pickle.dump(self.scaler, f)
        self.scaler_path = os.path.abspath(scaler_path)
        metadata_path = resolve_itransformer_metadata_path(
            model_path=target_path,
            scaler_path=self.scaler_path,
            metadata_path=self.metadata_path,
        )
        metadata = build_itransformer_metadata(
            config={
                **self.config,
                "feature_set_ver": self.feature_set_ver,
                "feature_columns": list(self.feature_columns),
                "horizons": list(self.horizons),
                "seq_len": self.seq_len,
            },
            model_path=target_path,
            scaler_path=self.scaler_path,
            feature_columns=self.feature_columns,
        )
        save_itransformer_metadata(metadata_path, metadata)
        self.metadata_path = metadata_path
        self.metadata = metadata
        self.legacy_artifact = False
        self.artifact_status = "metadata"

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
