import json
import os
import pickle
from typing import Any, Dict, Optional, Union

import numpy as np
import pandas as pd
import tensorflow as tf

from AI.modules.features.market_derived import add_macro_changes, add_market_changes
from AI.modules.features.processor import FeatureProcessor
from AI.modules.signal.core.base_model import BaseSignalModel
from .architecture import build_itransformer_model


DEFAULT_HORIZONS = [1, 3, 5, 7]

# iTransformer는 시계열 변수 간 상관구조를 보는 모델이므로
# 거시/상관관계 성격이 강한 컬럼들을 기본 우선순위로 둡니다.
DEFAULT_ITRANSFORMER_FEATURES = [
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
]

OPTIONAL_CONTEXT_FEATURES = [
    "btc_close",
    "eth_close",
]

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../../.."))


class ITransformerSignalModel(BaseSignalModel):
    """
    iTransformer 추론 어댑터.
    - 학습 시 저장한 모델/스케일러/메타데이터를 자동 복원합니다.
    - predict(df) 호출 시 피처 선택 -> 스케일링 -> [B, T, F] 변환 -> 추론 -> dict 반환까지 처리합니다.
    """

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        # 추론 시 필요한 기본 메타 설정을 config에서 먼저 읽고,
        # metadata.json이 있으면 학습 당시 값으로 다시 덮어씁니다.
        self.model_name = "itransformer"
        self.seq_len = int(config.get("seq_len", 60))
        self.horizons = list(config.get("horizons", DEFAULT_HORIZONS))
        self.auto_prepare = bool(config.get("auto_prepare", True))
        self.features_are_explicit = "features" in config or "feature_names" in config
        self.feature_candidates = list(
            config.get(
                "features",
                config.get("feature_names", DEFAULT_ITRANSFORMER_FEATURES),
            )
        )
        self.features = list(self.feature_candidates)
        self.metadata: Dict[str, Any] = {}
        self.scaler = None

        base_dir = config.get(
            "weights_dir",
            os.path.join(PROJECT_ROOT, "AI", "data", "weights", "itransformer"),
        )
        self.weights_dir = base_dir
        self.model_path = config.get("model_path", os.path.join(base_dir, "multi_horizon_model.keras"))
        self.scaler_path = config.get("scaler_path", os.path.join(base_dir, "multi_horizon_scaler.pkl"))
        self.metadata_path = config.get("metadata_path", os.path.join(base_dir, "metadata.json"))

        if os.path.exists(self.metadata_path):
            self.load_metadata(self.metadata_path)

    def load_scaler(self, filepath: str):
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"스케일러 파일이 없습니다: {filepath}")

        with open(filepath, "rb") as f:
            self.scaler = pickle.load(f)

        print(f"✅ iTransformer scaler loaded: {filepath}")

    def load_metadata(self, filepath: str):
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"메타데이터 파일이 없습니다: {filepath}")

        with open(filepath, "r", encoding="utf-8") as f:
            self.metadata = json.load(f)

        self.seq_len = int(self.metadata.get("seq_len", self.seq_len))
        self.horizons = list(self.metadata.get("horizons", self.horizons))
        self.features = list(self.metadata.get("feature_names", self.features))

        config_fallbacks = {
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
        for key, value in config_fallbacks.items():
            if value is not None and key not in self.config:
                self.config[key] = value

        print(f"✅ iTransformer metadata loaded: {filepath}")

    def _load_artifacts(self, require_scaler: bool = False):
        # wrapper의 핵심 역할:
        # 서비스 코드가 단순히 predict(df)만 호출해도
        # model / scaler / metadata를 알아서 복원하도록 만듭니다.
        if not self.metadata and os.path.exists(self.metadata_path):
            self.load_metadata(self.metadata_path)

        if require_scaler and self.scaler is None and os.path.exists(self.scaler_path):
            self.load_scaler(self.scaler_path)

        if self.model is None:
            if os.path.exists(self.model_path):
                self.load(self.model_path)
            else:
                raise FileNotFoundError(f"모델 파일이 없습니다: {self.model_path}")

        if require_scaler and self.scaler is None:
            raise FileNotFoundError(f"스케일러 파일이 없습니다: {self.scaler_path}")

    def _normalize_input_shape(self, input_shape: tuple) -> tuple:
        if len(input_shape) == 3 and input_shape[0] is None:
            return tuple(input_shape[1:])
        if len(input_shape) != 2:
            raise ValueError(
                f"입력 차원은 (timesteps, features) 형태여야 합니다. 현재: {input_shape}"
            )
        return tuple(input_shape)

    def _prepare_targets(self, y_input: np.ndarray) -> np.ndarray:
        targets = np.asarray(y_input, dtype=np.float32)
        if targets.ndim == 1:
            targets = targets.reshape(-1, 1)
        if targets.ndim != 2:
            raise ValueError(f"타깃 shape은 1D 또는 2D여야 합니다. 현재: {targets.shape}")
        return targets

    def _prepare_metadata_input(
        self,
        values: Optional[np.ndarray],
        batch_size: int,
        name: str,
    ) -> np.ndarray:
        if values is None:
            return np.zeros((batch_size, 1), dtype=np.int32)

        array = np.asarray(values, dtype=np.int32)
        if array.ndim == 0:
            return np.full((batch_size, 1), int(array), dtype=np.int32)
        if array.ndim == 1:
            if array.shape[0] != batch_size:
                raise ValueError(
                    f"{name} 길이가 batch 크기와 다릅니다. batch={batch_size}, {name}={array.shape[0]}"
                )
            return array.reshape(-1, 1)
        if array.ndim == 2 and array.shape == (batch_size, 1):
            return array

        raise ValueError(
            f"{name} shape은 (), (batch,), (batch, 1) 중 하나여야 합니다. 현재: {array.shape}"
        )

    def _prepare_model_inputs(
        self,
        X_input,
        ticker_values: Optional[np.ndarray] = None,
        sector_values: Optional[np.ndarray] = None,
    ):
        # Keras 모델 계약은 항상 3입력:
        # [시계열, ticker_id, sector_id] 이므로 여기서 shape을 강제로 맞춥니다.
        if isinstance(X_input, (list, tuple)):
            if len(X_input) != 3:
                raise ValueError("입력은 [X_ts, X_ticker, X_sector] 형태여야 합니다.")
            X_ts, ticker_values, sector_values = X_input
        else:
            X_ts = X_input

        X_ts = np.asarray(X_ts, dtype=np.float32)
        if X_ts.ndim == 2:
            X_ts = np.expand_dims(X_ts, axis=0)
        if X_ts.ndim != 3:
            raise ValueError(
                f"시계열 입력 shape은 (batch, seq_len, n_features)여야 합니다. 현재: {X_ts.shape}"
            )

        batch_size = X_ts.shape[0]
        X_ticker = self._prepare_metadata_input(ticker_values, batch_size, "ticker_ids")
        X_sector = self._prepare_metadata_input(sector_values, batch_size, "sector_ids")
        return [X_ts, X_ticker, X_sector]

    def _coerce_dataframe(self, df: pd.DataFrame) -> pd.DataFrame:
        # 추론 입력이 index 기반 날짜를 쓰든, date 컬럼을 쓰든
        # wrapper 입장에서는 최종적으로 date 컬럼 하나로 표준화합니다.
        if df is None or df.empty:
            raise ValueError("입력 DataFrame이 비어 있습니다.")

        prepared = df.copy()
        if "date" not in prepared.columns:
            if isinstance(prepared.index, pd.DatetimeIndex):
                prepared = prepared.reset_index()
                first_column = prepared.columns[0]
                prepared = prepared.rename(columns={first_column: "date"})
            else:
                raise ValueError("입력 DataFrame에는 'date' 컬럼 또는 DatetimeIndex가 필요합니다.")

        prepared["date"] = pd.to_datetime(prepared["date"])
        prepared = prepared.sort_values("date").reset_index(drop=True)
        return prepared

    def _engineer_features(self, df: pd.DataFrame) -> pd.DataFrame:
        # 원본 df에 iTransformer 전용 피처가 직접 없을 때의 보정 경로입니다.
        # 가격 변화율 + 거시 변화율 + FeatureProcessor 파생 피처를 순서대로 생성합니다.
        prepared = self._coerce_dataframe(df)
        prepared = add_market_changes(prepared)
        prepared = add_macro_changes(prepared)
        prepared = FeatureProcessor(prepared).execute_pipeline()
        prepared = prepared.replace([np.inf, -np.inf], np.nan).ffill().bfill().fillna(0)
        prepared["date"] = pd.to_datetime(prepared["date"])
        return prepared.sort_values("date").reset_index(drop=True)

    def _resolve_feature_columns(self, df: pd.DataFrame) -> list[str]:
        # 1. metadata가 있으면 학습 당시 피처 순서를 무조건 우선합니다.
        # 2. 사용자가 명시한 features가 있으면 그 집합을 그대로 강제합니다.
        # 3. 아무 설정이 없으면 기본 macro/correlation 후보 중 실제 존재하는 컬럼만 고릅니다.
        if self.metadata.get("feature_names"):
            missing = [column for column in self.features if column not in df.columns]
            if missing:
                raise ValueError(f"입력 DataFrame에 필요한 컬럼이 없습니다: {missing}")
            return list(self.features)

        if self.features_are_explicit:
            missing = [column for column in self.feature_candidates if column not in df.columns]
            if missing:
                raise ValueError(f"입력 DataFrame에 필요한 컬럼이 없습니다: {missing}")
            self.features = list(self.feature_candidates)
            return list(self.features)

        selected = [column for column in self.feature_candidates if column in df.columns]
        selected.extend(
            column
            for column in OPTIONAL_CONTEXT_FEATURES
            if column in df.columns and column not in selected
        )

        if not selected:
            raise ValueError("iTransformer가 사용할 거시/상관관계 피처를 찾지 못했습니다.")

        self.features = selected
        return selected

    def _prepare_feature_window(self, df: pd.DataFrame) -> np.ndarray:
        # 최종 목표:
        # DataFrame -> 필요한 피처만 선택 -> 결측치 보정 -> 최근 seq_len만 절단
        # -> scaler 적용 -> 모델 입력용 [seq_len, n_features] 배열 생성
        prepared = self._coerce_dataframe(df)
        required_columns = self.features if self.metadata.get("feature_names") else self.feature_candidates

        if not set(required_columns).issubset(prepared.columns):
            if not self.auto_prepare:
                missing = [column for column in required_columns if column not in prepared.columns]
                raise ValueError(f"입력 DataFrame에 필요한 컬럼이 없습니다: {missing}")
            try:
                prepared = self._engineer_features(prepared)
            except Exception as exc:
                raise ValueError(
                    "iTransformer용 피처 자동 생성에 실패했습니다. "
                    "원본 df에 OHLCV와 거시 컬럼이 충분한지 확인하세요."
                ) from exc

        feature_columns = self._resolve_feature_columns(prepared)
        feature_frame = prepared[feature_columns].replace([np.inf, -np.inf], np.nan).ffill().bfill().fillna(0)

        if len(feature_frame) < self.seq_len:
            raise ValueError(
                f"추론에 필요한 최소 시퀀스 길이가 부족합니다. required={self.seq_len}, got={len(feature_frame)}"
            )

        window = feature_frame.tail(self.seq_len).to_numpy(dtype=np.float32)
        if self.scaler is not None:
            # 학습 시 저장한 scaler를 그대로 써야
            # inference에서도 feature 분포가 동일하게 유지됩니다.
            window = self.scaler.transform(window).astype(np.float32)
        return window

    def _predict_array(self, X_input, ticker_id: int = 0, sector_id: int = 0, **kwargs) -> np.ndarray:
        # ndarray 경로는 학습/디버깅용 raw predict 함수입니다.
        # 서비스 추론은 아래 predict(df) 분기를 주로 사용합니다.
        self._load_artifacts(require_scaler=False)
        inputs = self._prepare_model_inputs(
            X_input,
            ticker_values=kwargs.pop("ticker_ids", ticker_id),
            sector_values=kwargs.pop("sector_ids", sector_id),
        )
        verbose = int(kwargs.pop("verbose", 0))
        return self.model.predict(inputs, verbose=verbose, **kwargs)

    def build(self, input_shape: tuple):
        input_shape = self._normalize_input_shape(input_shape)
        self.seq_len = int(input_shape[0])

        head_size = int(self.config.get("head_size", self.config.get("d_model", 128)))
        num_heads = int(self.config.get("num_heads", 4))
        ff_dim = int(self.config.get("ff_dim", head_size * 2))
        num_blocks = int(self.config.get("num_blocks", self.config.get("num_transformer_blocks", 4)))
        n_outputs = int(self.config.get("n_outputs", max(len(self.horizons), 1)))

        self.model = build_itransformer_model(
            input_shape=input_shape,
            n_tickers=int(self.config.get("n_tickers", 1000)),
            n_sectors=int(self.config.get("n_sectors", 50)),
            n_outputs=n_outputs,
            head_size=head_size,
            num_heads=num_heads,
            ff_dim=ff_dim,
            num_transformer_blocks=num_blocks,
            mlp_units=self.config.get("mlp_units", [128, 64]),
            dropout=float(self.config.get("dropout", 0.2)),
            mlp_dropout=float(self.config.get("mlp_dropout", 0.2)),
        )

        self.model.compile(
            loss="binary_crossentropy",
            optimizer=tf.keras.optimizers.Adam(
                learning_rate=float(self.config.get("learning_rate", 1e-4))
            ),
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
        train_inputs = self._prepare_model_inputs(
            X_train,
            ticker_values=kwargs.pop("ticker_ids", None),
            sector_values=kwargs.pop("sector_ids", None),
        )
        train_targets = self._prepare_targets(y_train)

        if self.model is None:
            self.build(train_inputs[0].shape[1:])

        validation_data = None
        if X_val is not None and y_val is not None:
            val_inputs = self._prepare_model_inputs(
                X_val,
                ticker_values=kwargs.pop("val_ticker_ids", None),
                sector_values=kwargs.pop("val_sector_ids", None),
            )
            validation_data = (val_inputs, self._prepare_targets(y_val))

        history = self.model.fit(
            x=train_inputs,
            y=train_targets,
            validation_data=validation_data,
            epochs=int(kwargs.pop("epochs", self.config.get("epochs", 50))),
            batch_size=int(kwargs.pop("batch_size", self.config.get("batch_size", 32))),
            callbacks=kwargs.pop("callbacks", []),
            verbose=int(kwargs.pop("verbose", 1)),
            **kwargs,
        )
        return history

    def predict(
        self,
        X_input: Union[pd.DataFrame, np.ndarray],
        ticker_id: int = 0,
        sector_id: int = 0,
        **kwargs,
    ) -> Union[Dict[str, float], np.ndarray]:
        """
        DataFrame 입력이면 dict 시그널을, ndarray 입력이면 raw probability array를 반환합니다.
        """
        if isinstance(X_input, pd.DataFrame):
            # 서비스 파이프라인이 기대하는 메인 경로:
            # 큰 df를 받아 내부에서 필요한 윈도우만 잘라 dict로 되돌립니다.
            self._load_artifacts(require_scaler=True)
            window = self._prepare_feature_window(X_input)
            probs = np.asarray(
                self._predict_array(window, ticker_id=ticker_id, sector_id=sector_id, **kwargs)[0],
                dtype=np.float32,
            ).reshape(-1)
            horizons = self.horizons if len(self.horizons) >= len(probs) else list(range(1, len(probs) + 1))
            return {
                f"{self.model_name}_{horizon}d": float(prob)
                for horizon, prob in zip(horizons, probs)
            }

        return self._predict_array(X_input, ticker_id=ticker_id, sector_id=sector_id, **kwargs)

    def get_signals(self, df: pd.DataFrame, ticker_id: int = 0, sector_id: int = 0) -> Dict[str, float]:
        signals = self.predict(df, ticker_id=ticker_id, sector_id=sector_id, verbose=0)
        if not isinstance(signals, dict):
            raise ValueError("DataFrame 입력 추론은 dict 형태의 시그널을 반환해야 합니다.")
        return signals

    def save(self, filepath: str):
        if self.model is None:
            print("저장할 모델이 없습니다.")
            return

        directory = os.path.dirname(filepath)
        if directory:
            os.makedirs(directory, exist_ok=True)

        self.model.save(filepath)
        print(f"✅ iTransformer model saved: {filepath}")

    def load(self, filepath: str):
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"모델 파일이 없습니다: {filepath}")

        self.model = tf.keras.models.load_model(filepath)
        print(f"✅ iTransformer model loaded: {filepath}")


ITransformerWrapper = ITransformerSignalModel
