from __future__ import annotations

import json
import os
import sys
from pathlib import Path

import pandas as pd

current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.config import TradingConfig, load_trading_config
from AI.libs.database.connection import get_db_conn


class DynamicScreener:
    """
    Dynamic ticker screener.

    Base behavior remains backward-compatible:
    - Rank by average dollar volume over lookback window.
    - Keep top N tickers.

    Extended options are controlled via `config.screener`:
    - Additional filters (price/volume/sector/include/exclude ticker)
    - Weighted ranking blend (dollar volume, volume, market cap)
    - Sticky slots to reduce daily churn
    """

    def __init__(self, db_name: str | None = None, config: TradingConfig | None = None):
        self.config = config or load_trading_config()
        self.db_name = db_name or self.config.pipeline.db_name
        self.watchlist_path = self.config.screener.watchlist_path

    def _load_previous_watchlist(self) -> list[str]:
        path = Path(self.watchlist_path)
        if not path.exists():
            return []

        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            return []

        tickers = payload.get("tickers", [])
        if not isinstance(tickers, list):
            return []

        seen: set[str] = set()
        ordered: list[str] = []
        for ticker in tickers:
            text = str(ticker).strip().upper()
            if not text or text in seen:
                continue
            seen.add(text)
            ordered.append(text)
        return ordered

    def _fetch_candidates(
        self,
        conn,
        target_date: str,
        lookback_days: int,
        min_market_cap: float,
    ) -> pd.DataFrame:
        query = """
            SELECT
                p.ticker AS ticker,
                AVG(p.close * p.volume) AS avg_dollar_vol,
                AVG(p.volume) AS avg_volume,
                AVG(p.close) AS avg_close,
                MAX(COALESCE(s.market_cap, 0)) AS market_cap,
                MAX(COALESCE(s.sector, '')) AS sector
            FROM public.price_data p
            JOIN public.stock_info s ON p.ticker = s.ticker
            WHERE p.date <= %s::date
              AND p.date >= %s::date - (%s * INTERVAL '1 day')
              AND COALESCE(s.market_cap, 0) >= %s
            GROUP BY p.ticker
            HAVING AVG(p.close * p.volume) > 0
        """
        return pd.read_sql(
            query,
            conn,
            params=(target_date, target_date, lookback_days, min_market_cap),
        )

    @staticmethod
    def _dedupe_keep_order(tickers: list[str], limit: int) -> list[str]:
        seen: set[str] = set()
        ordered: list[str] = []
        for ticker in tickers:
            text = str(ticker).strip().upper()
            if not text or text in seen:
                continue
            seen.add(text)
            ordered.append(text)
            if len(ordered) >= limit:
                break
        return ordered

    @staticmethod
    def _normalize_set(values: tuple[str, ...]) -> set[str]:
        return {str(value).strip().upper() for value in values if str(value).strip()}

    def update_watchlist(self, target_date: str, top_n: int | None = None) -> list[str]:
        cfg = self.config.screener
        limit = int(top_n if top_n is not None else cfg.top_n)
        lookback_days = int(cfg.lookback_days)
        min_market_cap = float(cfg.min_market_cap)
        sticky_slots = max(0, int(cfg.sticky_slots))

        print(f"[Screener] Building watchlist for {target_date} (top {limit})...")

        try:
            conn = get_db_conn(self.db_name)
        except Exception as error:
            print(f"[Screener] DB connection failed: {error}")
            return []
        if not conn:
            return []

        try:
            candidates = self._fetch_candidates(
                conn=conn,
                target_date=target_date,
                lookback_days=lookback_days,
                min_market_cap=min_market_cap,
            )
            if candidates.empty:
                print("[Screener] No candidate rows fetched.")
                return []

            df = candidates.copy()
            df["ticker"] = df["ticker"].astype(str).str.upper()
            df["sector"] = df["sector"].astype(str).str.upper()
            for col in ["avg_dollar_vol", "avg_volume", "avg_close", "market_cap"]:
                df[col] = pd.to_numeric(df[col], errors="coerce")
            df = df.dropna(subset=["avg_dollar_vol", "avg_volume", "avg_close", "market_cap"])

            if cfg.min_avg_dollar_vol > 0:
                df = df[df["avg_dollar_vol"] >= float(cfg.min_avg_dollar_vol)]
            if cfg.min_avg_volume > 0:
                df = df[df["avg_volume"] >= float(cfg.min_avg_volume)]
            if cfg.min_price > 0:
                df = df[df["avg_close"] >= float(cfg.min_price)]
            if cfg.max_price is not None and float(cfg.max_price) > 0:
                df = df[df["avg_close"] <= float(cfg.max_price)]

            include_tickers = self._normalize_set(cfg.include_tickers)
            exclude_tickers = self._normalize_set(cfg.exclude_tickers)
            include_sectors = self._normalize_set(cfg.include_sectors)
            exclude_sectors = self._normalize_set(cfg.exclude_sectors)

            if exclude_tickers:
                df = df[~df["ticker"].isin(exclude_tickers)]
            if include_sectors:
                df = df[df["sector"].isin(include_sectors)]
            if exclude_sectors:
                df = df[~df["sector"].isin(exclude_sectors)]

            if df.empty:
                print("[Screener] No tickers left after filters.")
                return []

            weights = {
                "avg_dollar_vol": float(cfg.dollar_vol_weight),
                "avg_volume": float(cfg.volume_weight),
                "market_cap": float(cfg.market_cap_weight),
            }
            active_weights = {k: v for k, v in weights.items() if v > 0}
            if not active_weights:
                active_weights = {"avg_dollar_vol": 1.0}

            score = pd.Series(0.0, index=df.index)
            for col, weight in active_weights.items():
                score = score + (df[col].rank(method="average", pct=True, ascending=True) * weight)
            df["score"] = score
            ranked = df.sort_values(
                by=["score", "avg_dollar_vol", "market_cap", "ticker"],
                ascending=[False, False, False, True],
            )

            base_selection = ranked["ticker"].tolist()
            final = self._dedupe_keep_order(base_selection, limit=limit)

            # Force include configured tickers that pass all filters.
            if include_tickers:
                available = set(ranked["ticker"].tolist())
                forced = [ticker for ticker in cfg.include_tickers if ticker in available]
                forced = self._dedupe_keep_order(forced, limit=limit)
                if forced:
                    remain_slots = max(0, limit - len(forced))
                    non_forced = [ticker for ticker in final if ticker not in set(forced)]
                    final = forced + non_forced[:remain_slots]

            # Sticky slots: keep a subset of previous watchlist to reduce churn.
            if sticky_slots > 0 and limit > 0:
                previous = self._load_previous_watchlist()
                available = set(ranked["ticker"].tolist())
                sticky_candidates = [ticker for ticker in previous if ticker in available and ticker not in set(final)]
                sticky = sticky_candidates[:sticky_slots]
                if sticky:
                    keep_slots = max(0, limit - len(sticky))
                    final = final[:keep_slots] + sticky
                    final = self._dedupe_keep_order(final, limit=limit)

            if not final:
                print("[Screener] Final selection is empty.")
                return []

            os.makedirs(os.path.dirname(self.watchlist_path), exist_ok=True)
            payload = {
                "target_date": target_date,
                "tickers": final,
                "count": len(final),
            }
            with open(self.watchlist_path, "w", encoding="utf-8") as handle:
                json.dump(payload, handle, indent=4, ensure_ascii=False)

            print(
                f"[Screener] Watchlist updated: {len(final)} tickers "
                f"(candidates={len(candidates)}, filtered={len(df)}, sticky_slots={sticky_slots})"
            )
            return final
        except Exception as error:
            print(f"[Screener] Failed to update watchlist: {error}")
            return []
        finally:
            conn.close()
