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
        self.is_loaded = False #중복로딩 방지 플래그

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
        # 학습 메타데이터 기준 shape로 TCN 본체를 복원합니다.
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
        # wrapper 단독 테스트용 학습 루프입니다. 실제 대규모 학습은 train.py 사용을 기준으로 둡니다.
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
        # metadata -> scaler -> model 순서로 읽어 추론에 필요한 상태를 복원합니다.

        if self.is_loaded:
            return # 이미 로드된 상태라면 중복 로딩을 방지합니다.
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
        # 서비스 파이프라인이 넘겨준 원본 df에서 TCN용 기술지표를 생성합니다.
        if df is None or df.empty:
            raise ValueError("Input dataframe is empty.")

        prepared = get_standard_training_data(df.copy())
        missing = [col for col in self.feature_columns if col not in prepared.columns]
        if missing:
            raise ValueError(f"Missing required TCN feature columns: {missing}")

        prepared = prepared.replace([np.inf, -np.inf], np.nan).fillna(0)
        return prepared

    def _prepare_input_tensor(self, df: pd.DataFrame) -> torch.Tensor:
        # 최근 seq_len 구간만 잘라서 학습 때와 동일한 feature 순서/스케일로 맞춥니다.
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
        # DataFrame 입력이 기본 경로이며, 테스트 편의를 위해 ndarray도 허용합니다.
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

        # 포트폴리오 파이프라인이 바로 읽을 수 있도록 horizon별 dict 형태로 반환합니다.
        return {
            f"tcn_{horizon}d": float(prob)
            for horizon, prob in zip(self.horizons, probs)
        }

    def get_signals(self, df: pd.DataFrame, ticker_id: int = 0, sector_id: int = 0) -> Dict[str, float]:
        return self.predict(df)
    
    def predict_batch(self, ticker_data_map: Dict[str, pd.DataFrame]) -> Dict[str, Dict[str, float]]:
        """
        [Batch 추론] 여러 종목의 DataFrame을 받아 한 번의 GPU 연산으로 결과를 반환합니다.
        입력: {"AAPL": df_aapl, "MSFT": df_msft, ...}
        출력: {"AAPL": {"tcn_1d": 0.55, ...}, "MSFT": {"tcn_1d": 0.61, ...}}
        """
        self._load_artifacts()

        if self.model is None:
            raise ValueError("TCN model is not available.")

        valid_tickers = []
        tensor_list = []

        # 1. 딕셔너리로 받은 종목별 데이터를 순회하며 전처리 및 윈도우 추출
        for ticker, df in ticker_data_map.items():
            try:
                # dataset_builder의 다형성을 활용하여 전처리 (에러 발생 종목은 스킵)
                prepared = get_standard_training_data(df.copy())
                feature_frame = prepared[self.feature_columns]

                if len(feature_frame) < self.seq_len:
                    continue # 시퀀스 길이가 부족한 신규 상장 종목 등은 건너뜁니다.

                latest_window = feature_frame.tail(self.seq_len).to_numpy(dtype=np.float32)
                
                # [참고] Global Scaler를 가정하고 일괄 적용합니다.
                if self.scaler is not None:
                    latest_window = self.scaler.transform(latest_window).astype(np.float32)

                tensor_list.append(latest_window)
                valid_tickers.append(ticker)
                
            except Exception as e:
                # 특정 종목 데이터 불량 시 전체 파이프라인이 멈추지 않도록 예외 처리
                print(f"⚠️ [{ticker}] 전처리 실패로 배치 추론에서 제외됨: {e}")

        if not tensor_list:
            return {} # 유효한 종목이 없으면 빈 결과 반환

        # 2. 리스트에 모인 2D 배열들을 3D 텐서로 조립 [Batch, Seq, Features]
        batch_array = np.stack(tensor_list, axis=0)
        batch_tensor = torch.from_numpy(batch_array).float().to(self.device)

        # 3. GPU 병렬 추론 (단 1번의 호출로 전체 종목 예측)
        self.model.eval()
        with torch.no_grad():
            logits = self.model(batch_tensor)
            probs = torch.sigmoid(logits).cpu().numpy() # 형태: [Batch, Horizons]

        # 4. 결과를 포트폴리오 모듈이 인식할 수 있도록 딕셔너리로 매핑
        results = {}
        for i, ticker in enumerate(valid_tickers):
            results[ticker] = {
                f"tcn_{horizon}d": float(probs[i, j])
                for j, horizon in enumerate(self.horizons)
            }
            
        return results

    def save(self, filepath: str):
        # 수동 저장이 필요한 경우 wrapper에서도 state_dict 저장이 가능합니다.
        if self.model is None:
            raise ValueError("No TCN model to save.")
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        torch.save(self.model.state_dict(), filepath)

    def load(self, filepath: str):
        """
        외부 경로의 가중치를 불러옵니다.
        가중치가 위치한 동일 폴더 내의 scaler 및 metadata를 읽어오도록 경로를 동기화합니다.
        """
        self.model_path = filepath
        target_dir = os.path.dirname(filepath)
        self.weights_dir = target_dir
        self.scaler_path = os.path.join(target_dir, "scaler.pkl")
        self.metadata_path = os.path.join(target_dir, "metadata.json")
        
        self.is_loaded = False # 새 경로로 로드할 때는 중복 로딩 방지 플래그를 초기화합니다.
        self._load_artifacts()
