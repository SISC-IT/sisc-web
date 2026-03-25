import json
import os
import pickle
from typing import Any, Dict, Optional, Union

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.preprocessing import StandardScaler

from AI.modules.features.market_derived import add_macro_changes, add_market_changes
from AI.modules.features.processor import FeatureProcessor
from AI.modules.signal.core.base_model import BaseSignalModel
from .architecture import build_itransformer_model


DEFAULT_HORIZONS = [1, 3, 5, 7]
DEFAULT_SIGNAL_NAME = "signal_itrans"
DEFAULT_SIGNAL_HORIZON_WEIGHTS = [0.1, 0.2, 0.3, 0.4]
FEATURE_ALIASES = {
    "mkt_breadth_ma200": "ma200_pct",
    "mkt_breadth_nh_nl": "nh_nl_index",
}
DYNAMIC_FEATURE_PREFIXES = ("sector_return_",)

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
    "surprise_cpi",
]

OPTIONAL_CONTEXT_FEATURES = [
    "btc_close",
    "eth_close",
]

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../../.."))


def canonicalize_feature_name(name: str) -> str:
    return FEATURE_ALIASES.get(name, name)


def normalize_feature_aliases(df: pd.DataFrame) -> pd.DataFrame:
    normalized = df.copy()
    for alias, canonical in FEATURE_ALIASES.items():
        if alias in normalized.columns and canonical not in normalized.columns:
            normalized[canonical] = normalized[alias]
    return normalized


def resolve_signal_horizon_weights(horizons, raw_weights=None) -> list[float]:
    weights = raw_weights if raw_weights is not None else DEFAULT_SIGNAL_HORIZON_WEIGHTS
    try:
        weights_array = np.asarray(weights, dtype=np.float32).reshape(-1)
    except Exception:
        weights_array = np.asarray(DEFAULT_SIGNAL_HORIZON_WEIGHTS, dtype=np.float32)

    if len(weights_array) != len(horizons) or np.any(weights_array < 0) or float(weights_array.sum()) <= 0.0:
        weights_array = np.ones(len(horizons), dtype=np.float32)

    weights_array = weights_array / weights_array.sum()
    return weights_array.tolist()


class ITransformerSignalModel(BaseSignalModel):
    """
    iTransformer 추론 어댑터.
    - 학습 시 저장한 모델/스케일러/메타데이터를 자동 복원합니다.
    - predict(df) 호출 시 피처 선택 -> 스케일링 -> [B, T, F] 변환 -> 추론까지 처리합니다.
    - get_signals(df)는 horizon별 출력과 signal_itrans 집계 점수를 함께 반환합니다.
    """

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        # 추론 시 필요한 기본 메타 설정을 config에서 먼저 읽고,
        # metadata.json이 있으면 학습 당시 값으로 다시 덮어씁니다.
        self.model_name = "itransformer"
        self.signal_name = str(config.get("signal_name", DEFAULT_SIGNAL_NAME))
        self.seq_len = int(config.get("seq_len", 60))
        self.horizons = list(config.get("horizons", DEFAULT_HORIZONS))
        self.signal_horizon_weights = resolve_signal_horizon_weights(
            self.horizons,
            config.get("signal_horizon_weights"),
        )
        self.allow_unsafe_pickle_scaler = bool(config.get("allow_unsafe_pickle_scaler", False))
        self.auto_prepare = bool(config.get("auto_prepare", True))
        self.features_are_explicit = "features" in config or "feature_names" in config
        raw_feature_candidates = config.get(
            "features",
            config.get("feature_names", DEFAULT_ITRANSFORMER_FEATURES),
        )
        self.feature_candidates = list(
            dict.fromkeys(canonicalize_feature_name(column) for column in raw_feature_candidates)
        )
        self.features = list(self.feature_candidates)
        self.metadata: Dict[str, Any] = {}
        self.scaler = None
        self.ticker_to_id: Dict[str, int] = {}
        self.sector_to_id: Dict[str, int] = {}
        self.ticker_to_sector_id: Dict[str, int] = {}

        base_dir = config.get(
            "weights_dir",
            os.path.join(PROJECT_ROOT, "AI", "data", "weights", "itransformer"),
        )
        self.weights_dir = base_dir
        self.model_path = config.get("model_path", os.path.join(base_dir, "multi_horizon_model.keras"))
        self.scaler_path = config.get("scaler_path", os.path.join(base_dir, "multi_horizon_scaler.npz"))
        self.metadata_path = config.get("metadata_path", os.path.join(base_dir, "metadata.json"))

        if os.path.exists(self.metadata_path):
            self.load_metadata(self.metadata_path)

    def load_scaler(self, filepath: str):
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"스케일러 파일이 없습니다: {filepath}")

        if filepath.endswith(".pkl") and not self.allow_unsafe_pickle_scaler:
            raise ValueError(
                "pickle scaler 자동 로드는 보안상 비활성화되어 있습니다. "
                "안전한 .npz scaler를 다시 생성하거나, 마이그레이션 목적일 때만 "
                "allow_unsafe_pickle_scaler=True를 명시하세요."
            )

        if filepath.endswith(".pkl"):
            with open(filepath, "rb") as f:
                self.scaler = pickle.load(f)
        else:
            with np.load(filepath, allow_pickle=False) as data:
                scaler = StandardScaler(
                    with_mean=bool(int(data["with_mean"][0])),
                    with_scale=bool(int(data["with_scale"][0])),
                )
                mean_ = data["mean_"].astype(np.float64) if data["mean_"].size else None
                scale_ = data["scale_"].astype(np.float64) if data["scale_"].size else None
                var_ = data["var_"].astype(np.float64) if data["var_"].size else None
                n_samples_seen = data["n_samples_seen_"]

                scaler.mean_ = mean_
                scaler.scale_ = scale_
                scaler.var_ = var_
                scaler.n_features_in_ = int(data["n_features_in_"][0])
                if n_samples_seen.size <= 1:
                    scaler.n_samples_seen_ = int(n_samples_seen[0]) if n_samples_seen.size == 1 else 0
                else:
                    scaler.n_samples_seen_ = n_samples_seen.astype(np.float64)

                self.scaler = scaler

        print(f"✅ iTransformer scaler loaded: {filepath}")

    def load_metadata(self, filepath: str):
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"메타데이터 파일이 없습니다: {filepath}")

        with open(filepath, "r", encoding="utf-8") as f:
            self.metadata = json.load(f)

        self.seq_len = int(self.metadata.get("seq_len", self.seq_len))
        self.horizons = list(self.metadata.get("horizons", self.horizons))
        self.signal_name = str(self.metadata.get("signal_name", self.signal_name))
        self.signal_horizon_weights = resolve_signal_horizon_weights(
            self.horizons,
            self.metadata.get("signal_horizon_weights", self.signal_horizon_weights),
        )
        self.features = list(
            dict.fromkeys(
                canonicalize_feature_name(column)
                for column in self.metadata.get("feature_names", self.features)
            )
        )
        self.ticker_to_id = {
            str(key): int(value)
            for key, value in self.metadata.get("ticker_to_id", {}).items()
        }
        self.sector_to_id = {
            str(key): int(value)
            for key, value in self.metadata.get("sector_to_id", {}).items()
        }
        self.ticker_to_sector_id = {
            str(key): int(value)
            for key, value in self.metadata.get("ticker_to_sector_id", {}).items()
        }

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
            "signal_name": self.signal_name,
            "signal_horizon_weights": self.signal_horizon_weights,
        }
        for key, value in config_fallbacks.items():
            if value is not None and key not in self.config:
                self.config[key] = value

        print(f"✅ iTransformer metadata loaded: {filepath}")

    def _has_explicit_dataframe_contract(self) -> bool:
        # metadata가 없더라도 seq_len / horizons / features가 모두 명시되면
        # DataFrame 추론 계약을 복원할 수 있습니다.
        return (
            "seq_len" in self.config
            and "horizons" in self.config
            and self.features_are_explicit
        )

    def _load_artifacts(self, require_scaler: bool = False, require_metadata: bool = False):
        # wrapper의 핵심 역할:
        # 서비스 코드가 단순히 predict(df)만 호출해도
        # model / scaler / metadata를 알아서 복원하도록 만듭니다.
        if not self.metadata and os.path.exists(self.metadata_path):
            self.load_metadata(self.metadata_path)

        if require_metadata and not self.metadata and not self._has_explicit_dataframe_contract():
            raise ValueError(
                "DataFrame 추론에는 metadata.json 또는 동등한 명시 설정 "
                "(seq_len, horizons, features)이 필요합니다."
            )

        if require_scaler and self.scaler is None:
            if os.path.exists(self.scaler_path):
                self.load_scaler(self.scaler_path)
            elif self.allow_unsafe_pickle_scaler:
                legacy_scaler_path = os.path.join(
                    os.path.dirname(self.scaler_path),
                    "multi_horizon_scaler.pkl",
                )
                if os.path.exists(legacy_scaler_path):
                    self.load_scaler(legacy_scaler_path)

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

        if "ticker" in prepared.columns:
            unique_tickers = prepared["ticker"].dropna().astype(str).unique()
            if len(unique_tickers) > 1:
                raise ValueError(
                    "DataFrame 추론은 단일 ticker 입력만 허용합니다. "
                    f"현재 여러 ticker가 섞여 있습니다: {unique_tickers.tolist()}"
                )

        prepared["date"] = pd.to_datetime(prepared["date"])
        prepared = prepared.sort_values("date").reset_index(drop=True)
        return normalize_feature_aliases(prepared)

    def _engineer_features(self, df: pd.DataFrame) -> pd.DataFrame:
        # 원본 df에 iTransformer 전용 피처가 직접 없을 때의 보정 경로입니다.
        # 가격 변화율 + 거시 변화율 + FeatureProcessor 파생 피처를 순서대로 생성합니다.
        prepared = self._coerce_dataframe(df)
        prepared = add_market_changes(prepared)
        prepared = add_macro_changes(prepared)
        prepared = FeatureProcessor(prepared).execute_pipeline()
        prepared = prepared.replace([np.inf, -np.inf], np.nan).ffill().fillna(0)
        prepared["date"] = pd.to_datetime(prepared["date"])
        return normalize_feature_aliases(prepared.sort_values("date").reset_index(drop=True))

    def _resolve_feature_columns(self, df: pd.DataFrame) -> list[str]:
        # 1. metadata가 있으면 학습 당시 피처 순서를 우선합니다.
        # 2. 사용자가 명시한 features가 있으면 그 집합을 그대로 강제합니다.
        # 3. 아무 설정이 없으면 기본 macro/correlation 후보 + optional prefix 컬럼을 고릅니다.
        normalized_df = normalize_feature_aliases(df)

        if self.metadata.get("feature_names"):
            missing = [column for column in self.features if column not in normalized_df.columns]
            if missing:
                raise ValueError(f"입력 DataFrame에 필요한 컬럼이 없습니다: {missing}")
            return list(self.features)

        if self.features_are_explicit:
            missing = [column for column in self.feature_candidates if column not in normalized_df.columns]
            if missing:
                raise ValueError(f"입력 DataFrame에 필요한 컬럼이 없습니다: {missing}")
            self.features = list(self.feature_candidates)
            return list(self.features)

        selected = [column for column in self.feature_candidates if column in normalized_df.columns]
        selected.extend(
            column
            for column in OPTIONAL_CONTEXT_FEATURES
            if column in normalized_df.columns and column not in selected
        )
        selected.extend(
            column
            for column in sorted(normalized_df.columns)
            if any(column.startswith(prefix) for prefix in DYNAMIC_FEATURE_PREFIXES) and column not in selected
        )

        if not selected:
            raise ValueError("iTransformer가 사용할 거시/상관관계 피처를 찾지 못했습니다.")

        self.features = selected
        return selected

    def _resolve_dataframe_metadata_ids(
        self,
        df: pd.DataFrame,
        ticker_id: Optional[int] = None,
        sector_id: Optional[int] = None,
    ) -> tuple[int, int]:
        prepared = self._coerce_dataframe(df)

        resolved_ticker_id = int(ticker_id) if ticker_id is not None else None
        resolved_sector_id = int(sector_id) if sector_id is not None else None

        ticker_value = None
        if "ticker" in prepared.columns:
            ticker_values = prepared["ticker"].dropna().astype(str).unique()
            if len(ticker_values) == 1:
                ticker_value = ticker_values[0]

        if resolved_ticker_id is None:
            if ticker_value is None:
                raise ValueError(
                    "DataFrame 추론에서 ticker_id를 명시하지 않았다면 "
                    "입력 DataFrame에 단일 ticker 컬럼이 필요합니다."
                )
            if not self.ticker_to_id:
                raise ValueError(
                    "DataFrame 추론에서 ticker_id를 자동 복원하려면 "
                    "metadata.json에 ticker_to_id 매핑이 필요합니다. "
                    "매핑이 없다면 ticker_id를 명시적으로 넘겨주세요."
                )
            if ticker_value not in self.ticker_to_id:
                raise ValueError(
                    f"ticker '{ticker_value}'에 대한 ID 매핑이 metadata에 없습니다. "
                    "새 metadata로 재학습하거나 ticker_id를 명시적으로 넘겨주세요."
                )
            resolved_ticker_id = int(self.ticker_to_id[ticker_value])

        if resolved_sector_id is None:
            if ticker_value is not None and ticker_value in self.ticker_to_sector_id:
                resolved_sector_id = int(self.ticker_to_sector_id[ticker_value])
            elif "sector" in prepared.columns:
                sector_values = prepared["sector"].dropna().astype(str).unique()
                if len(sector_values) == 1:
                    sector_value = sector_values[0]
                    if not self.sector_to_id:
                        raise ValueError(
                            "DataFrame 추론에서 sector_id를 자동 복원하려면 "
                            "metadata.json에 sector_to_id 매핑이 필요합니다. "
                            "매핑이 없다면 sector_id를 명시적으로 넘겨주세요."
                        )
                    if sector_value not in self.sector_to_id:
                        raise ValueError(
                            f"sector '{sector_value}'에 대한 ID 매핑이 metadata에 없습니다. "
                            "새 metadata로 재학습하거나 sector_id를 명시적으로 넘겨주세요."
                        )
                    resolved_sector_id = int(self.sector_to_id[sector_value])

        if resolved_sector_id is None:
            raise ValueError(
                "DataFrame 추론에서 sector_id를 자동 복원하지 못했습니다. "
                "metadata의 ticker_to_sector_id/sector_to_id 매핑을 사용하거나 "
                "sector_id를 명시적으로 넘겨주세요."
            )

        return int(resolved_ticker_id), int(resolved_sector_id)

    def _prepare_feature_window(self, df: pd.DataFrame) -> np.ndarray:
        # 최종 목표:
        # DataFrame -> 필요한 피처만 선택 -> 결측치 보정 -> 최근 seq_len만 절단
        # -> scaler 적용 -> 모델 입력용 [seq_len, n_features] 배열 생성
        prepared = self._coerce_dataframe(df)
        try:
            feature_columns = self._resolve_feature_columns(prepared)
        except ValueError:
            if not self.auto_prepare:
                raise
            try:
                prepared = self._engineer_features(prepared)
                feature_columns = self._resolve_feature_columns(prepared)
            except Exception as exc:
                raise ValueError(
                    "iTransformer용 피처 자동 생성에 실패했습니다. "
                    "원본 df에 OHLCV와 거시 컬럼이 충분한지 확인하세요."
                ) from exc

        feature_frame = prepared[feature_columns].replace([np.inf, -np.inf], np.nan).ffill().fillna(0)

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

    def _resolve_signal_weights(self, prob_count: int) -> np.ndarray:
        weights = np.asarray(self.signal_horizon_weights, dtype=np.float32).reshape(-1)
        if len(weights) != prob_count or np.any(weights < 0) or float(weights.sum()) <= 0.0:
            weights = np.ones(prob_count, dtype=np.float32)
        return weights / weights.sum()

    def _aggregate_signal(self, probs: np.ndarray) -> float:
        weights = self._resolve_signal_weights(len(probs))
        return float(np.dot(probs.astype(np.float32), weights))

    def _format_signal_outputs(self, probs: np.ndarray) -> Dict[str, float]:
        resolved_horizons = (
            list(self.horizons[: len(probs)])
            if len(self.horizons) >= len(probs)
            else list(range(1, len(probs) + 1))
        )
        outputs = {
            f"{self.model_name}_{horizon}d": float(prob)
            for horizon, prob in zip(resolved_horizons, probs)
        }
        outputs[self.signal_name] = self._aggregate_signal(probs)
        return outputs

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
        target_width = int(train_targets.shape[1])

        if len(self.horizons) != target_width:
            if "horizons" in self.config or self.metadata.get("horizons"):
                raise ValueError(
                    f"y_train 열 수({target_width})와 horizons 길이({len(self.horizons)})가 다릅니다."
                )
            self.horizons = list(range(1, target_width + 1))
            self.signal_horizon_weights = resolve_signal_horizon_weights(
                self.horizons,
                self.config.get("signal_horizon_weights"),
            )

        self.config["n_outputs"] = target_width

        if self.model is None:
            self.build(train_inputs[0].shape[1:])
        elif int(self.model.output_shape[-1]) != target_width:
            raise ValueError(
                f"현재 모델 출력 수({int(self.model.output_shape[-1])})와 "
                f"y_train 열 수({target_width})가 다릅니다."
            )

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
        ticker_id: Optional[int] = None,
        sector_id: Optional[int] = None,
        **kwargs,
    ) -> np.ndarray:
        """
        BaseSignalModel 계약을 유지하기 위해 항상 raw probability array를 반환합니다.
        """
        if isinstance(X_input, pd.DataFrame):
            self._load_artifacts(require_scaler=True, require_metadata=True)
            window = self._prepare_feature_window(X_input)
            resolved_ticker_id, resolved_sector_id = self._resolve_dataframe_metadata_ids(
                X_input,
                ticker_id=ticker_id,
                sector_id=sector_id,
            )
            return self._predict_array(
                window,
                ticker_id=resolved_ticker_id,
                sector_id=resolved_sector_id,
                **kwargs,
            )

        return self._predict_array(
            X_input,
            ticker_id=0 if ticker_id is None else ticker_id,
            sector_id=0 if sector_id is None else sector_id,
            **kwargs,
        )

    def get_signals(
        self,
        df: pd.DataFrame,
        ticker_id: Optional[int] = None,
        sector_id: Optional[int] = None,
    ) -> Dict[str, float]:
        pred_array = self.predict(df, ticker_id=ticker_id, sector_id=sector_id, verbose=0)
        probs = np.asarray(pred_array[0], dtype=np.float32).reshape(-1)
        return self._format_signal_outputs(probs)

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
<<<<<<< HEAD
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
=======
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
>>>>>>> e47fa9e ([AI] [FEAT] 볼륨 마운트를 통한 가중치 저장)
