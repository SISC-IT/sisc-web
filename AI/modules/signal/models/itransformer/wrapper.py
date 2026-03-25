from __future__ import annotations

import json
import os
import pickle
from typing import Any, Dict, Optional

import numpy as np
import pandas as pd
import torch
import torch.nn as nn

from AI.modules.signal.core.artifact_paths import resolve_model_artifacts
from AI.modules.signal.core.base_model import BaseSignalModel


DEFAULT_FEATURE_COLUMNS = [
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
]
DEFAULT_HORIZONS = [1, 3, 5, 7]


class iTransformer(nn.Module):
    def __init__(self, num_variates: int, lookback_len: int, d_model: int, output_size: int):
        super().__init__()
        self.enc_embedding = nn.Linear(lookback_len, d_model)
        self.encoder = nn.TransformerEncoderLayer(d_model=d_model, nhead=4, batch_first=True)
        self.head = nn.Linear(d_model * num_variates, output_size)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = x.permute(0, 2, 1)
        x = self.enc_embedding(x)
        x = self.encoder(x)
        x = x.reshape(x.shape[0], -1)
        return self.head(x)


class ITransformerWrapper(BaseSignalModel):
    supports_model_load_before_build = True

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.seq_len = int(config.get("seq_len", 60))
        self.feature_columns = list(config.get("feature_columns") or config.get("features") or DEFAULT_FEATURE_COLUMNS)
        self.horizons = list(config.get("horizons") or DEFAULT_HORIZONS)
        self.scaler = None
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

    def _is_within_weights_dir(self, filepath: str) -> bool:
        try:
            file_abs = os.path.abspath(filepath)
            base_abs = os.path.abspath(self.weights_dir)
            return os.path.commonpath([file_abs, base_abs]) == base_abs
        except ValueError:
            return False

    def _align_horizons_with_output_dim(self, output_dim: int) -> None:
        if output_dim <= 0:
            return
        if len(self.horizons) == output_dim:
            return
        if len(self.horizons) > output_dim:
            self.horizons = self.horizons[:output_dim]
            return

        if not self.horizons:
            self.horizons = list(range(1, output_dim + 1))
            return

        next_horizon = int(self.horizons[-1])
        while len(self.horizons) < output_dim:
            next_horizon += 1
            self.horizons.append(next_horizon)

    @staticmethod
    def _infer_output_dim_from_state_dict(state_dict: Dict[str, Any]) -> Optional[int]:
        head_weight = state_dict.get("head.weight")
        if isinstance(head_weight, torch.Tensor) and head_weight.ndim >= 2:
            return int(head_weight.shape[0])
        return None

    def _load_state_dict_safely(self, target_path: str) -> Dict[str, Any]:
        try:
            state_obj = torch.load(target_path, map_location=self.device, weights_only=True)
        except TypeError:
            state_obj = torch.load(target_path, map_location=self.device)

        if isinstance(state_obj, dict) and isinstance(state_obj.get("state_dict"), dict):
            state_obj = state_obj["state_dict"]
        if not isinstance(state_obj, dict):
            raise ValueError("Unsupported iTransformer checkpoint format.")
        return state_obj

    def _load_metadata(self) -> None:
        if not self.metadata_path or not os.path.exists(self.metadata_path):
            return
        try:
            with open(self.metadata_path, "r", encoding="utf-8") as f:
                metadata = json.load(f)
            self.seq_len = int(metadata.get("seq_len", self.seq_len))
            metadata_features = metadata.get("feature_columns")
            if isinstance(metadata_features, list) and metadata_features:
                self.feature_columns = list(metadata_features)
            metadata_horizons = metadata.get("horizons")
            if isinstance(metadata_horizons, list) and metadata_horizons:
                self.horizons = list(metadata_horizons)
        except Exception as metadata_error:
            print(f"[ITRANSFORMER] failed to load metadata: {metadata_error}")

    def load_scaler(self, filepath: str) -> None:
        scaler_path = os.path.abspath(filepath)
        if not os.path.exists(scaler_path):
            raise FileNotFoundError(f"Scaler file not found: {scaler_path}")
        if not self._explicit_scaler_path and not self._is_within_weights_dir(scaler_path):
            raise ValueError(
                f"Refusing to load scaler outside weights_dir: {scaler_path} (weights_dir={self.weights_dir})"
            )

        with open(scaler_path, "rb") as f:
            self.scaler = pickle.load(f)
        self.scaler_path = scaler_path

        scaler_features = getattr(self.scaler, "feature_names_in_", None)
        if scaler_features is not None and len(scaler_features) > 0:
            self.feature_columns = list(scaler_features)

    def build(self, input_shape: tuple):
        self.model = iTransformer(
            num_variates=input_shape[1],
            lookback_len=input_shape[0],
            d_model=int(self.config.get("d_model", 64)),
            output_size=max(1, len(self.horizons)),
        ).to(self.device)

    def train(
        self,
        X_train: np.ndarray,
        y_train: np.ndarray,
        X_val: Optional[np.ndarray] = None,
        y_val: Optional[np.ndarray] = None,
        **kwargs,
    ):
        if self.model is None:
            self.build(X_train.shape[1:])
        # Dedicated training pipeline for iTransformer is not implemented in this wrapper yet.
        return None

    def predict(self, X_input: np.ndarray) -> np.ndarray:
        if self.model is None:
            raise ValueError("Model not built. Call build()/load() first.")

        array_x = np.asarray(X_input, dtype=np.float32)
        if array_x.ndim == 2:
            array_x = np.expand_dims(array_x, axis=0)

        self.model.eval()
        with torch.no_grad():
            tensor_x = torch.from_numpy(array_x).to(self.device)
            out = self.model(tensor_x)
            return torch.sigmoid(out).cpu().numpy()

    def get_signals(self, df: pd.DataFrame, ticker_id: int = 0, sector_id: int = 0) -> Dict[str, float]:
        if not self.feature_columns:
            raise ValueError("feature_columns is empty.")
        if len(df) < self.seq_len:
            raise ValueError(
                f"Insufficient rows for iTransformer inference: required {self.seq_len}, got {len(df)}"
            )

        missing_features = [col for col in self.feature_columns if col not in df.columns]
        if missing_features:
            raise ValueError(
                "Missing required features for iTransformer inference: " + ", ".join(missing_features)
            )

        window = df[self.feature_columns].iloc[-self.seq_len :].to_numpy(dtype=np.float32)
        if self.scaler is not None:
            window = self.scaler.transform(window).astype(np.float32)

        probs = self.predict(window).reshape(-1)
        if probs.size == 0:
            return {f"itransformer_{self.horizons[0] if self.horizons else 1}d": 0.5}

        self._align_horizons_with_output_dim(int(probs.size))
        signals: Dict[str, float] = {}
        for idx, horizon in enumerate(self.horizons):
            if idx >= probs.size:
                break
            signals[f"itransformer_{horizon}d"] = float(probs[idx])
        return signals

    def save(self, filepath: str):
        if self.model is None:
            raise ValueError("No iTransformer model to save.")
        save_dir = os.path.dirname(filepath)
        if save_dir:
            os.makedirs(save_dir, exist_ok=True)
        torch.save(self.model.state_dict(), filepath)

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
        state_dict = self._load_state_dict_safely(target_path)
        output_dim = self._infer_output_dim_from_state_dict(state_dict)
        if output_dim is not None:
            self._align_horizons_with_output_dim(output_dim)

        if self.model is None:
            self.build((self.seq_len, len(self.feature_columns)))
        elif output_dim is not None and self.model.head.out_features != output_dim:
            self.build((self.seq_len, len(self.feature_columns)))

        try:
            self.model.load_state_dict(state_dict)
        except RuntimeError:
            self.build((self.seq_len, len(self.feature_columns)))
            self.model.load_state_dict(state_dict)
        self.model.eval()

        if self.scaler_path and os.path.exists(self.scaler_path):
            try:
                self.load_scaler(self.scaler_path)
            except Exception as scaler_error:
                print(f"[ITRANSFORMER] failed to load scaler: {scaler_error}")
