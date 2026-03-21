import os
import pickle
import shutil
import tempfile
import zipfile
from typing import Any, Dict, Optional

import numpy as np
import pandas as pd
import tensorflow as tf

from AI.modules.signal.core.base_model import BaseSignalModel
from .architecture import build_transformer_model


class TransformerSignalModel(BaseSignalModel):
    supports_model_load_before_build = True

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.model_name = "transformer"
        self.seq_len = config.get("seq_len", 60)
        self.features = config.get("features", [])
        self.scaler = None

    def load_scaler(self, filepath: str):
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"Scaler file not found: {filepath}")
        with open(filepath, "rb") as f:
            self.scaler = pickle.load(f)

        scaler_features = getattr(self.scaler, "feature_names_in_", None)
        if scaler_features is not None and len(scaler_features) > 0:
            self.features = list(scaler_features)
            #print(f"[Transformer] Feature schema restored from scaler: {self.features}")
        elif hasattr(self.scaler, "n_features_in_") and len(self.features) != int(self.scaler.n_features_in_):
            self.features = list(self.features[: int(self.scaler.n_features_in_)])
            #print("[Transformer] Feature schema inferred from scaler width: "f"{len(self.features)} columns")

        #print(f"[Transformer] Scaler loaded: {filepath}")

    def build(self, input_shape: tuple):
        if len(input_shape) != 2:
            if len(input_shape) == 3 and input_shape[0] is None:
                input_shape = input_shape[1:]
            else:
                raise ValueError(
                    f"Expected input shape (timesteps, features), got: {input_shape}"
                )

        self.model = build_transformer_model(
            input_shape=input_shape,
            n_tickers=self.config.get("n_tickers", 1000),
            n_sectors=self.config.get("n_sectors", 50),
            n_outputs=4,
            head_size=self.config.get("head_size", 256),
            num_heads=self.config.get("num_heads", 4),
            ff_dim=self.config.get("ff_dim", 4),
            num_transformer_blocks=self.config.get("num_blocks", 4),
            mlp_units=self.config.get("mlp_units", [128]),
            dropout=self.config.get("dropout", 0.4),
            mlp_dropout=self.config.get("mlp_dropout", 0.25),
        )

        learning_rate = self.config.get("learning_rate", 1e-4)
        self.model.compile(
            loss="binary_crossentropy",
            optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
            metrics=["accuracy", "AUC"],
        )

    def train(
        self,
        X_train: np.ndarray,
        y_train: np.ndarray,
        X_val: Optional[np.ndarray] = None,
        y_val: Optional[np.ndarray] = None,
        **kwargs,
    ):
        if self.model is None:
            raise ValueError("Model is not built. Call build() first.")

        epochs = int(kwargs.pop("epochs", self.config.get("epochs", 50)))
        batch_size = int(kwargs.pop("batch_size", self.config.get("batch_size", 32)))
        verbose = int(kwargs.pop("verbose", 1))
        callbacks = kwargs.pop("callbacks", [])
        validation_data = (X_val, y_val) if (X_val is not None and y_val is not None) else None

        return self.model.fit(
            X_train,
            y_train,
            validation_data=validation_data,
            epochs=epochs,
            batch_size=batch_size,
            callbacks=callbacks,
            verbose=verbose,
            **kwargs,
        )

    def predict(
        self, X_input: np.ndarray, ticker_id: int = 0, sector_id: int = 0, **kwargs
    ) -> np.ndarray:
        if self.model is None:
            raise ValueError("Model is not loaded. Call build() and load() first.")

        if len(X_input.shape) == 2:
            X_input = np.expand_dims(X_input, axis=0)

        t_id_tensor = np.array([[ticker_id]])
        s_id_tensor = np.array([[sector_id]])
        return self.model.predict([X_input, t_id_tensor, s_id_tensor], **kwargs)

    def get_signals(self, df: pd.DataFrame, ticker_id: int = 0, sector_id: int = 0) -> Dict[str, float]:
        if not self.features:
            raise ValueError("features is empty.")
        if self.scaler is None:
            raise ValueError("Scaler is not loaded. Call load_scaler() first.")
        missing_features = [col for col in self.features if col not in df.columns]
        if missing_features:
            raise ValueError(
                "Missing required features for transformer inference: "
                + ", ".join(missing_features)
            )

        data = df[self.features].iloc[-self.seq_len:].values
        scaled_data = self.scaler.transform(data)
        pred_array = self.predict(scaled_data, ticker_id=ticker_id, sector_id=sector_id, verbose=0)
        probs = pred_array[0]

        return {
            f"{self.model_name}_1d": float(probs[0]),
            f"{self.model_name}_3d": float(probs[1]),
            f"{self.model_name}_5d": float(probs[2]),
            f"{self.model_name}_7d": float(probs[3]),
        }

    def save(self, filepath: str):
        if self.model is None:
            print("No model to save.")
            return
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        self.model.save(filepath)
        print(f"[Transformer] Model saved: {filepath}")

    def load(self, filepath: str):
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"Model file not found: {filepath}")
        if zipfile.is_zipfile(filepath):
            self.model = tf.keras.models.load_model(filepath, compile=False)
            print(f"[Transformer] Model loaded from Keras archive: {filepath}")
            return

        # Some legacy checkpoints were saved as HDF5 but named with a .keras extension.
        with tempfile.NamedTemporaryFile(suffix=".h5", delete=False) as temp_file:
            temp_path = temp_file.name

        try:
            shutil.copyfile(filepath, temp_path)

            try:
                self.model = tf.keras.models.load_model(temp_path, compile=False)
                print(f"[Transformer] Model loaded from legacy HDF5 checkpoint: {filepath}")
                return
            except Exception as full_model_error:
                if self.model is None:
                    raise ValueError(
                        "load() requires build() before loading a weights-only checkpoint."
                    ) from full_model_error

                try:
                    self.model.load_weights(temp_path)
                    print(f"[Transformer] Weights loaded from legacy checkpoint: {filepath}")
                    return
                except Exception as weights_error:
                    raise ValueError(
                        "Failed to load transformer checkpoint as either a full Keras model "
                        "or a weights-only checkpoint."
                    ) from weights_error
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)
