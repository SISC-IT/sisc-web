# AI/pipelines/components/data_processor.py

from typing import Any, Dict

import pandas as pd

from AI.modules.features.legacy.technical_features import (
    add_multi_timeframe_features,
    add_technical_indicators,
)
from AI.modules.signal.core.data_loader import DataLoader


def _collect_required_features(model_wrappers: Dict[str, Any]) -> set:
    required_features = set()
    for wrapper in model_wrappers.values():
        if hasattr(wrapper, "get_required_features"):
            required_features.update(wrapper.get_required_features())
    return required_features


def _merge_common_features(loader: DataLoader, df: pd.DataFrame) -> pd.DataFrame:
    merged = df.copy()

    if not loader.macro_df.empty:
        merged = pd.merge(merged, loader.macro_df, on="date", how="left")
    if not loader.breadth_df.empty:
        merged = pd.merge(merged, loader.breadth_df, on="date", how="left")

    return merged.ffill().bfill()


def load_and_preprocess_data(
    loader: DataLoader,
    target_tickers: list,
    exec_date_str: str,
    strategy_config: dict,
    model_wrappers: Dict[str, Any],
) -> dict:
    """
    Load price data once, then enrich each ticker only with the features required
    by the currently loaded model wrappers.
    """
    print(f"3. 데이터 로딩 및 전처리 중 ({len(target_tickers)}종목)...")
    data_map = {}
    required_features = _collect_required_features(model_wrappers)

    bulk_df = loader.load_data_from_db(
        start_date="2023-01-01",
        end_date=exec_date_str,
        tickers=target_tickers,
    )
    target_timestamp = pd.to_datetime(exec_date_str)

    if bulk_df.empty:
        return data_map

    for ticker in target_tickers:
        df = bulk_df[bulk_df["ticker"] == ticker].copy()
        if df.empty:
            continue

        try:
            df = _merge_common_features(loader, df)
            df = add_technical_indicators(df)
            df = add_multi_timeframe_features(df)
            df.set_index("date", inplace=True)
            df = df.loc[:target_timestamp]

            if df.empty or df.index[-1] != target_timestamp:
                continue

            missing_required = [col for col in required_features if col not in df.columns]
            if missing_required:
                print(f"   [Skip] {ticker} missing features: {missing_required}")
                continue

            if len(df) >= strategy_config["seq_len"]:
                data_map[ticker] = df
        except Exception as e:
            print(f"   [Error] {ticker} preprocessing failed: {e}")

    return data_map
