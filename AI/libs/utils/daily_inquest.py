# AI/data_ingestion/daily_ingest.py

from __future__ import annotations
import os
from datetime import datetime, timedelta, timezone
from typing import List, Dict, Any

import pandas as pd
import yfinance as yf
from psycopg2.extras import execute_values

from AI.libs.utils.get_db_conn import get_db_conn, get_engine

KST = timezone(timedelta(hours=9))


# =============================
#  공통 유틸
# =============================
def today_kst() -> datetime.date:
    return datetime.now(KST).date()


def get_last_date_in_table(db_name: str, table: str, date_col: str) -> datetime.date | None:
    """
    해당 테이블에서 date_col의 최대 날짜를 가져옴.
    아무 데이터도 없으면 None 리턴.
    """
    from sqlalchemy import text

    engine = get_engine(db_name)
    with engine.connect() as conn:
        res = conn.execute(text(f"SELECT MAX({date_col}) FROM {table};")).scalar()
    if res is None:
        return None
    # res 가 date/datetime 타입이라 가정
    return res


# =============================
#  1) 주가 데이터 수집/업서트
# =============================
def fetch_price_data_from_yf(tickers: List[str], start: str, end: str) -> pd.DataFrame:
    """
    yfinance에서 일봉 데이터 가져와서
    transformer에서 쓰는 public.price_data 구조에 맞게 정리
    """
    frames = []
    for t in tickers:
        print(f"[PRICE] Fetch {t} {start}~{end}")
        df = yf.download(t, start=start, end=end, auto_adjust=False)
        if df.empty:
            continue
        df = df.rename(
            columns={
                "Open": "open",
                "High": "high",
                "Low": "low",
                "Close": "close",
                "Adj Close": "adjusted_close",
                "Volume": "volume",
            }
        )
        df["ticker"] = t
        df = df[["ticker", "open", "high", "low", "close", "volume", "adjusted_close"]]
        df.index.name = "date"
        df = df.reset_index()
        # date는 timezone 없는 date로
        df["date"] = pd.to_datetime(df["date"]).dt.date
        frames.append(df)

    if not frames:
        return pd.DataFrame(columns=["ticker", "date", "open", "high", "low", "close", "volume", "adjusted_close"])
    out = pd.concat(frames, ignore_index=True)
    return out


def upsert_price_data(db_name: str, df: pd.DataFrame):
    """
    public.price_data 에 (ticker, date) 기준으로 UPSERT
    """
    if df.empty:
        print("[PRICE] No new data to upsert.")
        return

    conn = get_db_conn(db_name)
    try:
        records = df[
            ["ticker", "date", "open", "high", "low", "close", "volume", "adjusted_close"]
        ].to_records(index=False)

        sql = """
        INSERT INTO public.price_data
            (ticker, date, open, high, low, close, volume, adjusted_close)
        VALUES %s
        ON CONFLICT (ticker, date) DO UPDATE SET
            open           = EXCLUDED.open,
            high           = EXCLUDED.high,
            low            = EXCLUDED.low,
            close          = EXCLUDED.close,
            volume         = EXCLUDED.volume,
            adjusted_close = EXCLUDED.adjusted_close;
        """
        with conn.cursor() as cur:
            execute_values(cur, sql, records)
        conn.commit()
        print(f"[PRICE] Upserted {len(df)} rows into public.price_data")
    finally:
        conn.close()


def run_price_pipeline(config: Dict[str, Any]):
    db_name = config["db_name"]
    tickers = config["tickers"]

    # 테이블에 아무것도 없으면 start_from_config, 있으면 max(date)+1 부터 오늘까지
    last = get_last_date_in_table(db_name, "public.price_data", "date")
    if last is None:
        start_date = config.get("price_start", "2018-01-01")
    else:
        start_date = (last + timedelta(days=1)).strftime("%Y-%m-%d")

    end_date = today_kst().strftime("%Y-%m-%d")

    if start_date > end_date:
        print("[PRICE] Already up to date.")
        return

    df = fetch_price_data_from_yf(tickers, start_date, end_date)
    upsert_price_data(db_name, df)


# =============================
#  2) 재무제표 수집/업서트
# =============================
def fetch_financials_from_yf(tickers: List[str]) -> pd.DataFrame:
    """
    yfinance 의 재무제표(fast 버전) → long 형태로 풀어서 저장
    - 손익계산서: IS
    - 재무상태표: BS
    - 현금흐름표: CF
    """
    rows = []
    for t in tickers:
        print(f"[FS] Fetch financials for {t}")
        yf_t = yf.Ticker(t)

        # annual / quarterly 예시 (필요에 따라 둘 다 or 하나만)
        fs_map = [
            ("IS", "annual", yf_t.financials),
            ("BS", "annual", yf_t.balance_sheet),
            ("CF", "annual", yf_t.cashflow),
            ("IS", "quarterly", yf_t.quarterly_financials),
            ("BS", "quarterly", yf_t.quarterly_balance_sheet),
            ("CF", "quarterly", yf_t.quarterly_cashflow),
        ]

        for fs_type, freq, df in fs_map:
            if df is None or df.empty:
                continue
            # columns: 보고일(date), index: 항목
            df = df.copy()
            df.columns = pd.to_datetime(df.columns).date
            for report_date in df.columns:
                for item, value in df[report_date].items():
                    if pd.isna(value):
                        continue
                    rows.append(
                        {
                            "ticker": t,
                            "report_date": report_date,
                            "fs_type": fs_type,
                            "item": str(item),
                            "value": float(value),
                            "currency": None,  # 필요하면 yfinance info에서 끌어와도 됨
                            "freq": freq,
                        }
                    )

    if not rows:
        return pd.DataFrame(
            columns=["ticker", "report_date", "fs_type", "item", "value", "currency", "freq"]
        )

    return pd.DataFrame(rows)


def upsert_financials(db_name: str, df: pd.DataFrame):
    if df.empty:
        print("[FS] No financials to upsert.")
        return

    conn = get_db_conn(db_name)
    try:
        records = df[
            ["ticker", "report_date", "fs_type", "item", "value", "currency", "freq"]
        ].to_records(index=False)

        sql = """
        INSERT INTO public.financials
            (ticker, report_date, fs_type, item, value, currency, freq)
        VALUES %s
        ON CONFLICT (ticker, report_date, fs_type, item) DO UPDATE SET
            value    = EXCLUDED.value,
            currency = COALESCE(EXCLUDED.currency, public.financials.currency),
            freq     = EXCLUDED.freq;
        """
        with conn.cursor() as cur:
            execute_values(cur, sql, records)
        conn.commit()
        print(f"[FS] Upserted {len(df)} rows into public.financials")
    finally:
        conn.close()


def run_financials_pipeline(config: Dict[str, Any]):
    db_name = config["db_name"]
    tickers = config["tickers_for_fs"]
    df = fetch_financials_from_yf(tickers)
    upsert_financials(db_name, df)


# =============================
#  3) 거시지표 수집/업서트
# =============================
def fetch_macro_from_yf(series_map: Dict[str, str], start: str, end: str) -> pd.DataFrame:
    """
    series_map: {내부코드: yfinance_티커} 형태
      예: {"US10Y": "^TNX", "KOSPI": "^KS11", "KRWUSD": "KRW=X"}
    """
    rows = []
    for code, yf_symbol in series_map.items():
        print(f"[MACRO] Fetch {code}({yf_symbol}) {start}~{end}")
        df = yf.download(yf_symbol, start=start, end=end, auto_adjust=False)
        if df.empty:
            continue
        df.index.name = "date"
        df = df.reset_index()
        df["date"] = pd.to_datetime(df["date"]).dt.date
        for _, r in df.iterrows():
            # 여기서는 종가만 value로 사용 (필요하면 다른 컬럼도 가능)
            value = r.get("Close")
            if pd.isna(value):
                continue
            rows.append(
                {
                    "series_code": code,
                    "date": r["date"],
                    "value": float(value),
                    "meta": None,
                }
            )

    if not rows:
        return pd.DataFrame(columns=["series_code", "date", "value", "meta"])
    return pd.DataFrame(rows)


def upsert_macro(db_name: str, df: pd.DataFrame):
    if df.empty:
        print("[MACRO] No macro data to upsert.")
        return

    conn = get_db_conn(db_name)
    try:
        records = df[["series_code", "date", "value", "meta"]].to_records(index=False)
        sql = """
        INSERT INTO public.macro_data
            (series_code, date, value, meta)
        VALUES %s
        ON CONFLICT (series_code, date) DO UPDATE SET
            value = EXCLUDED.value,
            meta  = COALESCE(EXCLUDED.meta, public.macro_data.meta);
        """
        with conn.cursor() as cur:
            execute_values(cur, sql, records)
        conn.commit()
        print(f"[MACRO] Upserted {len(df)} rows into public.macro_data")
    finally:
        conn.close()


def run_macro_pipeline(config: Dict[str, Any]):
    db_name = config["db_name"]
    series_map = config["macro_series"]

    last = get_last_date_in_table(db_name, "public.macro_data", "date")
    if last is None:
        start_date = config.get("macro_start", "2010-01-01")
    else:
        start_date = (last + timedelta(days=1)).strftime("%Y-%m-%d")
    end_date = today_kst().strftime("%Y-%m-%d")

    if start_date > end_date:
        print("[MACRO] Already up to date.")
        return

    df = fetch_macro_from_yf(series_map, start_date, end_date)
    upsert_macro(db_name, df)


# =============================
#  메인: 하루 한 번 돌릴 거
# =============================
def run_all():
    today = today_kst().strftime("%Y-%m-%d")
    print(f"=== DAILY INGEST ({today}) ===")

    config = {
        "db_name": "db",  # get_db_conn 에서 쓰는 이름 (config.json 의 키)

        # 1) 주가
        "tickers": ["AAPL", "MSFT", "TSLA", "^KS11"],  # 네가 원하는 티커들
        "price_start": "2018-01-01",

        # 2) 재무제표를 받을 티커 (보통은 개별 주식만)
        "tickers_for_fs": ["AAPL", "MSFT", "TSLA"],

        # 3) 거시지표: {내부코드: yfinance 심볼}
        "macro_series": {
            "US10Y": "^TNX",
            "KOSPI": "^KS11",
            "KRWUSD": "KRW=X",
        },
        "macro_start": "2010-01-01",
    }

    # 필요한 것만 골라서 돌리면 됨
    run_price_pipeline(config)
    run_financials_pipeline(config)
    run_macro_pipeline(config)

    print("=== DAILY INGEST DONE ===")


if __name__ == "__main__":
    run_all()
