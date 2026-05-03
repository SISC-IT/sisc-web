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
    account_code: str
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
    min_avg_dollar_vol: float
    min_avg_volume: float
    min_price: float
    max_price: float | None
    include_tickers: tuple[str, ...]
    exclude_tickers: tuple[str, ...]
    include_sectors: tuple[str, ...]
    exclude_sectors: tuple[str, ...]
    dollar_vol_weight: float
    volume_weight: float
    market_cap_weight: float
    sticky_slots: int


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


def _as_clean_tuple(values: Any, *, upper: bool = False) -> tuple[str, ...]:
    if values is None:
        return tuple()
    if not isinstance(values, list):
        values = [values]

    seen: set[str] = set()
    result: list[str] = []
    for item in values:
        text = str(item).strip()
        if not text:
            continue
        if upper:
            text = text.upper()
        key = text.upper()
        if key in seen:
            continue
        seen.add(key)
        result.append(text)
    return tuple(result)


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
    screener_raw = raw["screener"]
    config = TradingConfig(
        pipeline=PipelineConfig(
            db_name=raw["pipeline"]["db_name"],
            account_code=str(raw["pipeline"].get("account_code", "chart_based_signal_model")).strip(),
            default_mode=raw["pipeline"]["default_mode"],
            enable_xai=raw["pipeline"]["enable_xai"],
            data_start_date=raw["pipeline"]["data_start_date"],
            initial_capital=raw["pipeline"]["initial_capital"],
            active_models=tuple(raw["pipeline"]["active_models"]),
            macro_fallback=macro_fallback,
        ),
        screener=ScreenerConfig(
            top_n=screener_raw["top_n"],
            lookback_days=screener_raw["lookback_days"],
            min_market_cap=screener_raw["min_market_cap"],
            watchlist_path=_resolve_path(screener_raw["watchlist_path"]),
            min_avg_dollar_vol=float(screener_raw.get("min_avg_dollar_vol", 0.0)),
            min_avg_volume=float(screener_raw.get("min_avg_volume", 0.0)),
            min_price=float(screener_raw.get("min_price", 0.0)),
            max_price=(
                float(screener_raw["max_price"])
                if screener_raw.get("max_price") is not None
                else None
            ),
            include_tickers=_as_clean_tuple(screener_raw.get("include_tickers"), upper=True),
            exclude_tickers=_as_clean_tuple(screener_raw.get("exclude_tickers"), upper=True),
            include_sectors=_as_clean_tuple(screener_raw.get("include_sectors")),
            exclude_sectors=_as_clean_tuple(screener_raw.get("exclude_sectors")),
            dollar_vol_weight=float(screener_raw.get("dollar_vol_weight", 1.0)),
            volume_weight=float(screener_raw.get("volume_weight", 0.0)),
            market_cap_weight=float(screener_raw.get("market_cap_weight", 0.0)),
            sticky_slots=int(screener_raw.get("sticky_slots", 0)),
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
    if not config.pipeline.account_code:
        raise ValueError("pipeline.account_code must be a non-empty string")
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
