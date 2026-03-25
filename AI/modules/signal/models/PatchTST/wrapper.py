import os
from typing import Any, Dict, Optional

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.optim as optim

from AI.modules.signal.core.base_model import BaseSignalModel
from AI.modules.signal.models.PatchTST.architecture import PatchTST_Model


class PatchTSTWrapper(BaseSignalModel):
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.seq_len = int(config.get("seq_len", 120))
        self.features = list(config.get("feature_columns") or config.get("features") or [])
        self.model = None

    def build(self, input_shape: tuple):
        seq_len, num_features = input_shape
        self.model = PatchTST_Model(
            seq_len=seq_len,
            enc_in=num_features,
            patch_len=self.config.get("patch_len", 16),
            stride=self.config.get("stride", 8),
            d_model=self.config.get("d_model", 128),
            dropout=self.config.get("dropout", 0.1),
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
        optimizer = optim.AdamW(self.model.parameters(), lr=self.config.get("lr", 1e-4))
        epochs = int(self.config.get("epochs", 50))
        batch_size = int(self.config.get("batch_size", 32))

        X_tensor = torch.from_numpy(X_train).float().to(self.device)
        y_tensor = torch.from_numpy(y_train).float().view(-1, 1).to(self.device)

        self.model.train()
        for epoch in range(epochs):
            permutation = torch.randperm(X_tensor.size(0), device=self.device)
            epoch_loss = 0.0

            for i in range(0, X_tensor.size(0), batch_size):
                indices = permutation[i : i + batch_size]
                batch_x, batch_y = X_tensor[indices], y_tensor[indices]

                optimizer.zero_grad()
                output = self.model(batch_x)
                loss = criterion(output, batch_y)
                loss.backward()
                optimizer.step()
                epoch_loss += loss.item()

            if (epoch + 1) % 10 == 0:
                print(f"Epoch [{epoch + 1}/{epochs}] Loss: {epoch_loss:.4f}")

    def predict(self, X_input: np.ndarray) -> np.ndarray:
        if self.model is None:
            raise ValueError("Model not initialized. Call build() or load() first.")

        array_x = np.asarray(X_input, dtype=np.float32)
        if array_x.ndim == 2:
            array_x = np.expand_dims(array_x, axis=0)

        self.model.eval()
        with torch.no_grad():
            X_tensor = torch.from_numpy(array_x).float().to(self.device)
            logits = self.model(X_tensor)
            probs = torch.sigmoid(logits).cpu().numpy()

        return probs

    def get_signals(self, df: pd.DataFrame, ticker_id: int = 0, sector_id: int = 0) -> Dict[str, float]:
        if df is None or df.empty:
            raise ValueError("Input dataframe is empty.")

        if not self.features:
            numeric_columns = [col for col in df.columns if pd.api.types.is_numeric_dtype(df[col])]
            self.features = numeric_columns[: int(self.config.get("enc_in", 7))]

        if not self.features:
            raise ValueError("No features configured for PatchTST inference.")

        missing_features = [col for col in self.features if col not in df.columns]
        if missing_features:
            raise ValueError("Missing required PatchTST features: " + ", ".join(missing_features))

        if len(df) < self.seq_len:
            raise ValueError(
                f"Insufficient rows for PatchTST inference: required {self.seq_len}, got {len(df)}"
            )

        window = df[self.features].iloc[-self.seq_len :].to_numpy(dtype=np.float32)
        probs = self.predict(np.expand_dims(window, axis=0)).reshape(-1)
        score = float(probs[0]) if probs.size else 0.5
        return {"patchtst_1d": score}

    def save(self, filepath: str):
        if self.model is None:
            raise ValueError("No PatchTST model to save.")
        save_dir = os.path.dirname(filepath)
        if save_dir:
            os.makedirs(save_dir, exist_ok=True)
        torch.save(self.model.state_dict(), filepath)
        print(f"PatchTST saved to {filepath}")

    def load(self, filepath: str):
        if self.model is None:
            self.build((self.config.get("seq_len", 120), self.config.get("enc_in", 7)))

        self.model.load_state_dict(torch.load(filepath, map_location=self.device))
        self.model.eval()
        print(f"PatchTST loaded from {filepath}")
