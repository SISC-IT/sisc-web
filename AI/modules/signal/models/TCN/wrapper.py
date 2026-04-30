import json
import os
import pickle
from typing import Any, Dict, Optional, Union

import numpy as np
import pandas as pd
import torch
import torch.nn as nn

from AI.modules.signal.core.dataset_builder import get_standard_training_data
from AI.modules.signal.core.base_model import BaseSignalModel
from AI.modules.signal.core.artifact_paths import resolve_model_artifacts
from AI.modules.signal.models.TCN.architecture import TCNClassifier


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


class TCNWrapper(BaseSignalModel):
    """
    TCN inference adapter for the service pipeline.
    predict(df)가 호출되면 feature 생성, scaling, window slicing, 추론까지 내부에서 처리합니다.
    """

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.seq_len = int(config.get("seq_len", 60))
        self.feature_columns = config.get("feature_columns", DEFAULT_FEATURE_COLUMNS)
        self.horizons = config.get("horizons", DEFAULT_HORIZONS)
        self.channels = config.get("channels", [32, 64, 64])
        self.kernel_size = int(config.get("kernel_size", 3))
        self.dropout = float(config.get("dropout", 0.2))
        self.scaler = None
        self.metadata = {}
        self.is_loaded = False

        artifact_paths = resolve_model_artifacts(
            model_name="tcn",
            mode=config.get("mode"),
            config_weights_dir=config.get("artifact_root"),
            model_dir=config.get("weights_dir"),
        )
        self.weights_dir = os.path.abspath(config.get("weights_dir", artifact_paths.model_dir))
        self.model_path = os.path.abspath(config.get("model_path", artifact_paths.model_path))
        self.scaler_path = os.path.abspath(config.get("scaler_path", artifact_paths.scaler_path))
        self.metadata_path = os.path.abspath(config.get("metadata_path", artifact_paths.metadata_path))

    def build(self, input_shape: tuple):
        self.model = TCNClassifier(
            input_size=input_shape[1],
            output_size=len(self.horizons),
            num_channels=self.channels,
            kernel_size=self.kernel_size,
            dropout=self.dropout,
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

        criterion = nn.BCEWithLogitsLoss()
        optimizer = torch.optim.Adam(
            self.model.parameters(),
            lr=float(kwargs.get("learning_rate", self.config.get("learning_rate", 1e-3))),
        )
        epochs = int(kwargs.get("epochs", self.config.get("epochs", 20)))
        batch_size = int(kwargs.get("batch_size", self.config.get("batch_size", 64)))

        X_tensor = torch.from_numpy(X_train).float().to(self.device)
        y_tensor = torch.from_numpy(y_train).float().to(self.device)

        for epoch in range(epochs):
            self.model.train()
            permutation = torch.randperm(X_tensor.size(0), device=self.device)
            epoch_loss = 0.0

            for i in range(0, X_tensor.size(0), batch_size):
                indices = permutation[i : i + batch_size]
                batch_x = X_tensor[indices]
                batch_y = y_tensor[indices]

                optimizer.zero_grad()
                logits = self.model(batch_x)
                loss = criterion(logits, batch_y)
                loss.backward()
                optimizer.step()
                epoch_loss += loss.item()

            if (epoch + 1) % 5 == 0:
                print(f"Epoch [{epoch + 1}/{epochs}] Loss: {epoch_loss:.4f}")

    def _load_artifacts(self):
        if self.is_loaded:
            return

        if self.metadata_path and os.path.exists(self.metadata_path):
            with open(self.metadata_path, "r", encoding="utf-8") as f:
                self.metadata = json.load(f)
            self.feature_columns = self.metadata.get("feature_columns", self.feature_columns)
            self.horizons = self.metadata.get("horizons", self.horizons)
            self.seq_len = int(self.metadata.get("seq_len", self.seq_len))
            self.channels = self.metadata.get("channels", self.channels)
            self.kernel_size = int(self.metadata.get("kernel_size", self.kernel_size))
            self.dropout = float(self.metadata.get("dropout", self.dropout))

        if self.scaler is None and os.path.exists(self.scaler_path):
            with open(self.scaler_path, "rb") as f:
                self.scaler = pickle.load(f)

        if self.model is None:
            self.build((self.seq_len, len(self.feature_columns)))

        if self.model is not None and os.path.exists(self.model_path):
            state_dict = torch.load(self.model_path, map_location=self.device)

            # [수정] Kaggle DataParallel 학습 시 생기는 'module.' 접두사 제거
            if all(k.startswith("module.") for k in state_dict.keys()):
                state_dict = {k[len("module."):]: v for k, v in state_dict.items()}

            self.model.load_state_dict(state_dict)
            self.model.eval()

        self.is_loaded = True

    def load_scaler(self, filepath: str):
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"TCN scaler file not found: {filepath}")
        with open(filepath, "rb") as f:
            self.scaler = pickle.load(f)
        self.scaler_path = os.path.abspath(filepath)

    def _prepare_dataframe(self, df: pd.DataFrame) -> pd.DataFrame:
        if df is None or df.empty:
            raise ValueError("Input dataframe is empty.")
        prepared = get_standard_training_data(df.copy())
        missing = [col for col in self.feature_columns if col not in prepared.columns]
        if missing:
            raise ValueError(f"Missing required TCN feature columns: {missing}")
        prepared = prepared.replace([np.inf, -np.inf], np.nan).fillna(0)
        return prepared

    def _prepare_input_tensor(self, df: pd.DataFrame) -> torch.Tensor:
        prepared = self._prepare_dataframe(df)
        feature_frame = prepared[self.feature_columns]

        if len(feature_frame) < self.seq_len:
            raise ValueError(
                f"Not enough rows for TCN inference. Required {self.seq_len}, got {len(feature_frame)}."
            )

        latest_window = feature_frame.tail(self.seq_len).to_numpy(dtype=np.float32)
        if self.scaler is not None:
            latest_window = self.scaler.transform(latest_window).astype(np.float32)

        batch = np.expand_dims(latest_window, axis=0)
        return torch.from_numpy(batch).float().to(self.device)

    def predict(self, X_input: Union[pd.DataFrame, np.ndarray]) -> Dict[str, float]:
        self._load_artifacts()

        if self.model is None:
            raise ValueError("TCN model is not available.")

        if isinstance(X_input, pd.DataFrame):
            tensor_x = self._prepare_input_tensor(X_input)
        else:
            array_x = np.asarray(X_input, dtype=np.float32)
            if array_x.ndim == 2:
                array_x = np.expand_dims(array_x, axis=0)
            tensor_x = torch.from_numpy(array_x).float().to(self.device)

        self.model.eval()
        with torch.no_grad():
            logits = self.model(tensor_x)
            probs = torch.sigmoid(logits).cpu().numpy().flatten()

        return {
            f"tcn_{horizon}d": float(prob)
            for horizon, prob in zip(self.horizons, probs)
        }

    def get_signals(self, df: pd.DataFrame, ticker_id: int = 0, sector_id: int = 0) -> Dict[str, float]:
        return self.predict(df)

    def predict_batch(self, ticker_data_map: Dict[str, pd.DataFrame]) -> Dict[str, Dict[str, float]]:
        self._load_artifacts()

        if self.model is None:
            raise ValueError("TCN model is not available.")

        valid_tickers = []
        tensor_list = []

        for ticker, df in ticker_data_map.items():
            try:
                prepared = get_standard_training_data(df.copy())
                feature_frame = prepared[self.feature_columns]

                if len(feature_frame) < self.seq_len:
                    continue

                latest_window = feature_frame.tail(self.seq_len).to_numpy(dtype=np.float32)
                if self.scaler is not None:
                    latest_window = self.scaler.transform(latest_window).astype(np.float32)

                tensor_list.append(latest_window)
                valid_tickers.append(ticker)

            except Exception as e:
                print(f"[{ticker}] 전처리 실패로 배치 추론에서 제외됨: {e}")

        if not tensor_list:
            return {}

        batch_array = np.stack(tensor_list, axis=0)
        batch_tensor = torch.from_numpy(batch_array).float().to(self.device)

        self.model.eval()
        with torch.no_grad():
            logits = self.model(batch_tensor)
            probs = torch.sigmoid(logits).cpu().numpy()

        results = {}
        for i, ticker in enumerate(valid_tickers):
            results[ticker] = {
                f"tcn_{horizon}d": float(probs[i, j])
                for j, horizon in enumerate(self.horizons)
            }

        return results

    def save(self, filepath: str):
        if self.model is None:
            raise ValueError("No TCN model to save.")
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        torch.save(self.model.state_dict(), filepath)

    def load(self, filepath: str):
        self.model_path = filepath
        target_dir = os.path.dirname(filepath)
        self.weights_dir = target_dir
        self.scaler_path = os.path.join(target_dir, "scaler.pkl")
        self.metadata_path = os.path.join(target_dir, "metadata.json")
        self.is_loaded = False
        self._load_artifacts()
