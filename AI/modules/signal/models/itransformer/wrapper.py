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
    def __init__(self, num_variates: int, lookback_len: int, d_model: int):
        super().__init__()
        self.enc_embedding = nn.Linear(lookback_len, d_model)
        self.encoder = nn.TransformerEncoderLayer(d_model=d_model, nhead=4, batch_first=True)
        self.head = nn.Linear(d_model * num_variates, 1)

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

        artifact_paths = resolve_model_artifacts(
            model_name="itransformer",
            mode=config.get("mode"),
            config_weights_dir=config.get("artifact_root"),
            model_dir=config.get("weights_dir"),
        )
        self.weights_dir = os.path.abspath(config.get("weights_dir", artifact_paths.model_dir))
        self.model_path = os.path.abspath(config.get("model_path", artifact_paths.model_path))
        self.scaler_path = os.path.abspath(config.get("scaler_path", artifact_paths.scaler_path))
        self.metadata_path = os.path.abspath(config.get("metadata_path", artifact_paths.metadata_path))
        self._load_metadata()

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
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"Scaler file not found: {filepath}")
        with open(filepath, "rb") as f:
            self.scaler = pickle.load(f)
        self.scaler_path = os.path.abspath(filepath)

        scaler_features = getattr(self.scaler, "feature_names_in_", None)
        if scaler_features is not None and len(scaler_features) > 0:
            self.feature_columns = list(scaler_features)

    def build(self, input_shape: tuple):
        self.model = iTransformer(
            num_variates=input_shape[1],
            lookback_len=input_shape[0],
            d_model=int(self.config.get("d_model", 64)),
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
        score = float(probs[0]) if probs.size else 0.5
        return {f"itransformer_{horizon}d": score for horizon in self.horizons}

    def save(self, filepath: str):
        if self.model is None:
            raise ValueError("No iTransformer model to save.")
        save_dir = os.path.dirname(filepath)
        if save_dir:
            os.makedirs(save_dir, exist_ok=True)
        torch.save(self.model.state_dict(), filepath)

    def load(self, filepath: Optional[str] = None):
        target_path = filepath or self.model_path
        if not os.path.exists(target_path):
            raise FileNotFoundError(f"iTransformer model file not found: {target_path}")

        target_dir = os.path.dirname(target_path)
        self.weights_dir = target_dir
        self.model_path = target_path
        self.scaler_path = os.path.join(target_dir, "multi_horizon_scaler.pkl")
        self.metadata_path = os.path.join(target_dir, "metadata.json")
        self._load_metadata()

        if self.model is None:
            self.build((self.seq_len, len(self.feature_columns)))
        self.model.load_state_dict(torch.load(target_path, map_location=self.device))
        self.model.eval()
