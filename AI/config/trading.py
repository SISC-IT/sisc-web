from __future__ import annotations

import json
import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[2]
CONFIG_DIR = Path(__file__).resolve().parent
CONFIG_ENV_VAR = "AI_TRADING_CONFIG_PATH"
MODEL_WEIGHTS_DIR_ENV_VAR = "AI_MODEL_WEIGHTS_DIR"
DEFAULT_CONFIG_PATH = CONFIG_DIR / "trading.default.json"
DEFAULT_LOCAL_CONFIG_PATH = CONFIG_DIR / "trading.local.json"


@dataclass(frozen=True, slots=True)
class MacroFallbackConfig:
    vix_z_score: float
    mkt_breadth_nh_nl: float
    ma_trend_score: float


@dataclass(frozen=True, slots=True)
class PipelineConfig:
    db_name: str
    default_mode: str
    enable_xai: bool
    data_start_date: str
    initial_capital: float
    active_models: tuple[str, ...]
    macro_fallback: MacroFallbackConfig


@dataclass(frozen=True, slots=True)
class ScreenerConfig:
    top_n: int
    lookback_days: int
    min_market_cap: float
    watchlist_path: str


@dataclass(frozen=True, slots=True)
class DataConfig:
    seq_len: int
    minimum_history_length: int
    feature_columns: tuple[str, ...]
    prediction_horizons: tuple[int, ...]


@dataclass(frozen=True, slots=True)
class ModelConfig:
    weights_dir: str
    weights_file: str
    scaler_file: str


@dataclass(frozen=True, slots=True)
class RiskOverlayConfig:
    vix_reduce_exposure_threshold: float
    vix_exit_threshold: float
    reduced_exposure_ratio: float
    full_exit_ratio: float


@dataclass(frozen=True, slots=True)
class PortfolioConfig:
    top_k: int
    buy_threshold: float
    default_score: float
    risk_overlay: RiskOverlayConfig


@dataclass(frozen=True, slots=True)
class ExecutionConfig:
    strong_buy_score: float
    buy_score_floor: float
    sell_score: float
    stop_loss_ratio: float
    min_conviction_weight: float
    max_conviction_weight: float
    commission: float


@dataclass(frozen=True, slots=True)
class TradingConfig:
    pipeline: PipelineConfig
    screener: ScreenerConfig
    data: DataConfig
    model: ModelConfig
    portfolio: PortfolioConfig
    execution: ExecutionConfig


def _resolve_path(raw_path: str) -> str:
    path = Path(raw_path)
    if path.is_absolute():
        return str(path)
    return str((PROJECT_ROOT / path).resolve())


def _deep_merge(base: dict[str, Any], override: dict[str, Any]) -> dict[str, Any]:
    merged = dict(base)
    for key, value in override.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = _deep_merge(merged[key], value)
        else:
            merged[key] = value
    return merged


def _read_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def _build_config(raw: dict[str, Any]) -> TradingConfig:
    env_model_weights_dir = os.getenv(MODEL_WEIGHTS_DIR_ENV_VAR)
    if env_model_weights_dir and env_model_weights_dir.strip():
        model_weights_dir = env_model_weights_dir.strip()
    else:
        model_weights_dir = raw["model"]["weights_dir"]
    risk_overlay = RiskOverlayConfig(**raw["portfolio"]["risk_overlay"])
    macro_fallback = MacroFallbackConfig(**raw["pipeline"]["macro_fallback"])
    config = TradingConfig(
        pipeline=PipelineConfig(
            db_name=raw["pipeline"]["db_name"],
            default_mode=raw["pipeline"]["default_mode"],
            enable_xai=raw["pipeline"]["enable_xai"],
            data_start_date=raw["pipeline"]["data_start_date"],
            initial_capital=raw["pipeline"]["initial_capital"],
            active_models=tuple(raw["pipeline"]["active_models"]),
            macro_fallback=macro_fallback,
        ),
        screener=ScreenerConfig(
            top_n=raw["screener"]["top_n"],
            lookback_days=raw["screener"]["lookback_days"],
            min_market_cap=raw["screener"]["min_market_cap"],
            watchlist_path=_resolve_path(raw["screener"]["watchlist_path"]),
        ),
        data=DataConfig(
            seq_len=raw["data"]["seq_len"],
            minimum_history_length=raw["data"]["minimum_history_length"],
            feature_columns=tuple(raw["data"]["feature_columns"]),
            prediction_horizons=tuple(raw["data"]["prediction_horizons"]),
        ),
        model=ModelConfig(
            weights_dir=_resolve_path(model_weights_dir),
            weights_file=raw["model"]["weights_file"],
            scaler_file=raw["model"]["scaler_file"],
        ),
        portfolio=PortfolioConfig(
            top_k=raw["portfolio"]["top_k"],
            buy_threshold=raw["portfolio"]["buy_threshold"],
            default_score=raw["portfolio"]["default_score"],
            risk_overlay=risk_overlay,
        ),
        execution=ExecutionConfig(**raw["execution"]),
    )
    if config.pipeline.initial_capital <= 0:
        raise ValueError("pipeline.initial_capital must be greater than 0")
    return config


@lru_cache(maxsize=None)
def load_trading_config(config_path: str | None = None) -> TradingConfig:
    raw = _read_json(DEFAULT_CONFIG_PATH)

    env_path = os.getenv(CONFIG_ENV_VAR)
    candidate_paths: list[Path] = []
    if DEFAULT_LOCAL_CONFIG_PATH.exists():
        candidate_paths.append(DEFAULT_LOCAL_CONFIG_PATH)
    if env_path:
        candidate_paths.append(Path(env_path))
    if config_path:
        candidate_paths.append(Path(config_path))

    for candidate in candidate_paths:
        if candidate.exists():
            raw = _deep_merge(raw, _read_json(candidate))

    return _build_config(raw)
