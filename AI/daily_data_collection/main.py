# ======================================================================
# SECTION 1 — Imports + 경로 설정 + 공용 유틸 + DB 전체 티커 로딩
# ======================================================================

from __future__ import annotations

import os
import sys
from datetime import datetime, timedelta, timezone
from typing import List, Dict, Any, Optional

import numpy as np
import pandas as pd
import yfinance as yf
from psycopg2.extras import execute_values
from fredapi import Fred

# ----------------------------------------------------------------------
# 프로젝트 루트 경로를 sys.path에 추가 (절대경로 import 문제 해결)
# ----------------------------------------------------------------------
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if project_root not in sys.path:
    sys.path.append(project_root)

# DB 연결 함수 가져오기
from libs.utils.get_db_conn import get_db_conn, get_engine


# ----------------------------------------------------------------------
# 한국 표준시 (KST)
# ----------------------------------------------------------------------
KST = timezone(timedelta(hours=9))


# ======================================================================
# 공용 유틸 함수
# ======================================================================

def today_kst() -> datetime.date:
    """한국(KST) 기준 today's date 반환."""
    return datetime.now(KST).date()


def get_last_date_in_table(db_name: str, table: str, date_col: str) -> Optional[datetime.date]:
    """
    테이블의 날짜 컬럼(date_col)에서 MAX(date)를 얻는 함수
    """
    from sqlalchemy import text
    engine = get_engine(db_name)

    with engine.connect() as conn:
        res = conn.execute(text(f"SELECT MAX({date_col}) FROM {table};")).scalar()

    return res if res is not None else None


# ======================================================================
# ✨ DB 전체 티커 로딩 함수 (핵심 기능 추가)
# ======================================================================

def get_all_tickers_from_db(db_name: str) -> List[str]:
    """
    public.price_data 에서 존재하는 모든 ticker를 DISTINCT 로 가져오는 함수.
    manual_backfill_all() 에서 전체 티커를 자동으로 사용하기 위해 도입.
    """
    from sqlalchemy import text
    engine = get_engine(db_name)

    with engine.connect() as conn:
        rows = conn.execute(text("""
            SELECT DISTINCT ticker
            FROM public.price_data
            ORDER BY ticker;
        """)).fetchall()

    return [r[0] for r in rows]


# ======================================================================
# ✨ Series, numpy 자료형 등 → 스칼라 float 변환 함수 (중요)
# ======================================================================

def to_scalar(v):
    """
    yfinance 또는 pandas Series에서 발생하는
    numpy.float64 / ndarray / Series 형태를 모두 float로 정규화.
    """
    if isinstance(v, (np.generic, np.float64, np.int64)):
        return float(v)

    if isinstance(v, (list, tuple, np.ndarray)):
        return float(v[0]) if len(v) > 0 else None

    if isinstance(v, pd.Series):
        # Series인 경우 iloc으로 첫 값만 사용
        return float(v.iloc[0]) if len(v) > 0 else None

    # 이미 파이썬 float인 경우
    try:
        return float(v)
    except Exception:
        return None
# ======================================================================
# SECTION 2 — PRICE_DATA FETCH / UPSERT / PIPELINE
# ======================================================================

# --------------------------------------------------------------
# DataFrame → 파이썬 records (numpy / Series 모두 스칼라로 변환)
# --------------------------------------------------------------

def df_to_python_records_price(df: pd.DataFrame):
    """
    price_data DataFrame 을 Python tuple 리스트로 변환.
    - 스키마 기준 8컬럼만 사용한다.
    - MultiIndex / 중복 컬럼 이름이 있어도 첫 번째 컬럼만 사용하도록 정규화한다.
    """
    # MultiIndex -> 1단계 이름으로 평탄화
    if isinstance(df.columns, pd.MultiIndex):
        df.columns = df.columns.get_level_values(0)

    target_cols = ["ticker", "date", "open", "high", "low", "close", "volume", "adjusted_close"]

    # 중복 컬럼 / DataFrame 반환 케이스 안전하게 처리
    safe_dict = {}
    for c in target_cols:
        if c not in df.columns:
            # 아예 없으면 전부 None
            safe_dict[c] = pd.Series([None] * len(df), index=df.index)
            continue

        col = df[c]
        # 같은 이름의 컬럼이 여러 개인 경우 df[c] 가 DataFrame 이라서
        # 첫 번째 컬럼만 사용
        if isinstance(col, pd.DataFrame):
            safe_dict[c] = col.iloc[:, 0]
        else:
            safe_dict[c] = col

    df = pd.DataFrame(safe_dict)

    records = []

    def scalar_or_none(v):
        if v is None or pd.isna(v):
            return None
        if isinstance(v, (int, float)):
            return v
        try:
            return float(v)
        except Exception:
            return None

    # 여기서는 무조건 8개 컬럼만 존재
    for ticker, date, open_, high_, low_, close_, volume, adjusted_close in df.itertuples(index=False, name=None):
        records.append(
            (
                str(ticker),
                date,
                scalar_or_none(open_),
                scalar_or_none(high_),
                scalar_or_none(low_),
                scalar_or_none(close_),
                int(volume) if (volume is not None and not pd.isna(volume)) else None,
                scalar_or_none(adjusted_close),
            )
        )

    return records


# --------------------------------------------------------------
# yfinance 일봉 수집
# --------------------------------------------------------------
def fetch_price_data_from_yf(tickers: List[str], start: str, end: str) -> pd.DataFrame:
    """
    yfinance 를 '티커 1개씩' 호출해서
    항상 세로(long) 구조의 8컬럼 DF를 반환한다.
    """
    frames = []

    for t in tickers:
        print(f"[PRICE] Fetch {t} {start}~{end}")
        df = yf.download(t, start=start, end=end, auto_adjust=False)

        # MultiIndex 반환되는 경우 강제 단일화
        if isinstance(df.columns, pd.MultiIndex):
            df.columns = df.columns.get_level_values(0)


        if df.empty:
            print(f"[PRICE] No data for {t}")
            continue

        # index = DatetimeIndex
        df.index.name = "date"
        df = df.reset_index()

        # 표준 컬럼명으로 통일
        df = df.rename(
            columns={
                "Date": "date",
                "Open": "open",
                "High": "high",
                "Low": "low",
                "Close": "close",
                "Adj Close": "adjusted_close",
                "Volume": "volume",
            }
        )

        df["ticker"] = t
        df["date"] = pd.to_datetime(df["date"]).dt.date

        df = df[
            ["ticker", "date", "open",
             "high", "low", "close",
             "volume", "adjusted_close"]
        ]

        frames.append(df)

    if not frames:
        return pd.DataFrame(
            columns=[
                "ticker", "date", "open", "high", "low",
                "close", "volume", "adjusted_close"
            ]
        )

    return pd.concat(frames, ignore_index=True)



# --------------------------------------------------------------
# price_data UPSERT
# --------------------------------------------------------------
def upsert_price_data(db_name: str, df: pd.DataFrame, batch_size: int = 10_000) -> None:
    """
    public.price_data 테이블에 (ticker, date) 기준으로 UPSERT 수행.

    - df 전체를 한 번에 records 로 바꾸지 않고,
      batch_size 단위로 잘라서 DB에 넣음 → 메모리 폭발 방지.
    """
    if df.empty:
        print("[PRICE] No new data to upsert.")
        return

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

    conn = get_db_conn(db_name)
    try:
        with conn.cursor() as cur:
            # df 를 행 기준으로 잘라서 차례대로 INSERT
            total_rows = len(df)
            for start in range(0, total_rows, batch_size):
                end = min(start + batch_size, total_rows)
                batch_df = df.iloc[start:end]
                records = df_to_python_records_price(batch_df)

                if not records:
                    continue

                execute_values(cur, sql, records)

                print(f"[PRICE] Upserted rows {start} ~ {end-1} / {total_rows}")

        conn.commit()
        print(f"[PRICE] Upserted TOTAL {len(df)} rows into public.price_data")

    finally:
        conn.close()



# --------------------------------------------------------------
# (자동용) price pipeline: 증분 업데이트
# --------------------------------------------------------------
def run_price_pipeline(config: Dict[str, Any]) -> None:
    db_name = config["db_name"]
    tickers = config["tickers"]

    last = get_last_date_in_table(db_name, "public.price_data", "date")

    if last is None:
        start_date = config.get("price_start", "2017-01-01")
        print(f"[PRICE] 기존 없음 → {start_date} 부터 시작")
    else:
        start_date = (last + timedelta(days=1)).strftime("%Y-%m-%d")
        print(f"[PRICE] 마지막 날짜 {last} 이후 → {start_date} 부터 수집")

    end_date = today_kst().strftime("%Y-%m-%d")

    if start_date > end_date:
        print("[PRICE] Already up to date.")
        return

    df = fetch_price_data_from_yf(tickers, start_date, end_date)
    upsert_price_data(db_name, df)
# ======================================================================
# SECTION 3 — TECHNICAL INDICATORS (계산 + UPSERT + PIPELINE)
# ======================================================================

# --------------------------------------------------------------
# 기술지표 계산 (티커 1개 단위)
# --------------------------------------------------------------
def compute_technical_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """
    입력 df: ticker, date, open, high, low, close, volume
    출력 df: ticker, date, RSI, MACD, Bollinger_Bands_upper, ... MA_200
    """

    df = df.sort_values("date").reset_index(drop=True)


    close = df["close"]
    high = df["high"]
    low = df["low"]
    volume = df["volume"]
    # 결측치 보정 (앞뒤로 채우기)
    df["close"] = df["close"].ffill().bfill()
    df["high"]  = df["high"].ffill().bfill()
    df["low"]   = df["low"].ffill().bfill()
    df["volume"] = df["volume"].fillna(0)


    # ------------------------- RSI -------------------------
    delta = close.diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)

    window_rsi = 14
    avg_gain = gain.rolling(window_rsi).mean()
    avg_loss = loss.rolling(window_rsi).mean()
    rs = avg_gain / (avg_loss + 1e-9)
    rsi = 100 - (100 / (1 + rs))

    # ------------------------- MACD -------------------------
    ema12 = close.ewm(span=12, adjust=False).mean()
    ema26 = close.ewm(span=26, adjust=False).mean()
    macd = ema12 - ema26

    # ------------------------- Bollinger Bands -------------------------
    ma20 = close.rolling(20).mean()
    std20 = close.rolling(20).std()
    bb_upper = ma20 + 2 * std20
    bb_lower = ma20 - 2 * std20

    # ------------------------- ATR -------------------------
    prev_close = close.shift(1)
    tr1 = high - low
    tr2 = (high - prev_close).abs()
    tr3 = (low - prev_close).abs()
    tr = pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)
    atr = tr.rolling(14).mean()

    # ------------------------- OBV -------------------------
    obv = [0]
    for i in range(1, len(close)):
        if close.iloc[i] > close.iloc[i-1]:
            obv.append(obv[-1] + volume.iloc[i])
        elif close.iloc[i] < close.iloc[i-1]:
            obv.append(obv[-1] - volume.iloc[i])
        else:
            obv.append(obv[-1])
    obv = pd.Series(obv, index=df.index)

    # ------------------------- Stochastic -------------------------
    lowest_low = low.rolling(14).min()
    highest_high = high.rolling(14).max()
    stochastic = (close - lowest_low) / (highest_high - lowest_low + 1e-9) * 100

    # ------------------------- MFI -------------------------
    typical_price = (high + low + close) / 3
    raw_mf = typical_price * volume
    tp_diff = typical_price.diff()
    pos_mf = raw_mf.where(tp_diff > 0, 0)
    neg_mf = raw_mf.where(tp_diff < 0, 0)
    pos_14 = pos_mf.rolling(14).sum()
    neg_14 = neg_mf.abs().rolling(14).sum() + 1e-9
    mfr = pos_14 / neg_14
    mfi = 100 - (100 / (1 + mfr))

    # ------------------------- MA -------------------------
    ma_5 = close.rolling(5).mean()
    ma_20_roll = close.rolling(20).mean()
    ma_50 = close.rolling(50).mean()
    ma_200 = close.rolling(200).mean()

    out = pd.DataFrame({
        "ticker": df["ticker"],
        "date": df["date"],
        "RSI": rsi,
        "MACD": macd,
        "Bollinger_Bands_upper": bb_upper,
        "Bollinger_Bands_lower": bb_lower,
        "ATR": atr,
        "OBV": obv,
        "Stochastic": stochastic,
        "MFI": mfi,
        "MA_5": ma_5,
        "MA_20": ma_20_roll,
        "MA_50": ma_50,
        "MA_200": ma_200,
    })

    return out


# --------------------------------------------------------------
# UPSERT for technical_indicators 
# --------------------------------------------------------------
def upsert_technical_indicators(db_name: str, df: pd.DataFrame, batch_size: int = 50_000):
    if df.empty:
        print("[TECH] No technical indicators to upsert.")
        return

    sql = """
    INSERT INTO public.technical_indicators (
        ticker, date,
        RSI, MACD,
        Bollinger_Bands_upper, Bollinger_Bands_lower,
        ATR, OBV, Stochastic, MFI,
        MA_5, MA_20, MA_50, MA_200
    )
    VALUES %s
    ON CONFLICT (ticker, date) DO UPDATE SET
        RSI                    = EXCLUDED.RSI,
        MACD                   = EXCLUDED.MACD,
        Bollinger_Bands_upper  = EXCLUDED.Bollinger_Bands_upper,
        Bollinger_Bands_lower  = EXCLUDED.Bollinger_Bands_lower,
        ATR                    = EXCLUDED.ATR,
        OBV                    = EXCLUDED.OBV,
        Stochastic             = EXCLUDED.Stochastic,
        MFI                    = EXCLUDED.MFI,
        MA_5                   = EXCLUDED.MA_5,
        MA_20                  = EXCLUDED.MA_20,
        MA_50                  = EXCLUDED.MA_50,
        MA_200                 = EXCLUDED.MA_200;
    """

    conn = get_db_conn(db_name)
    try:
        with conn.cursor() as cur:
            total = len(df)
            for start in range(0, total, batch_size):
                end = min(start + batch_size, total)
                batch = df.iloc[start:end]

                records = []
                for _, r in batch.iterrows():
                    records.append((
                        r["ticker"], r["date"],
                        to_scalar(r["RSI"]), to_scalar(r["MACD"]),
                        to_scalar(r["Bollinger_Bands_upper"]), to_scalar(r["Bollinger_Bands_lower"]),
                        to_scalar(r["ATR"]), to_scalar(r["OBV"]),
                        to_scalar(r["Stochastic"]), to_scalar(r["MFI"]),
                        to_scalar(r["MA_5"]), to_scalar(r["MA_20"]),
                        to_scalar(r["MA_50"]), to_scalar(r["MA_200"]),
                    ))

                if not records:
                    continue

                execute_values(cur, sql, records)
                print(f"[TECH] Upserted rows {start} ~ {end-1} / {total}")

        conn.commit()
        print(f"[TECH] Upserted TOTAL {len(df)} rows into public.technical_indicators")

    finally:
        conn.close()



# --------------------------------------------------------------
# 기술지표 파이프라인: DB price_data 전체 기반
# --------------------------------------------------------------
def run_technical_indicators_full(config: Dict[str, Any]) -> None:
    """
    기술지표 전체 FULL 재계산 (manual_backfill_all 전용)
    """
    db_name = config["db_name"]

    print("[TECH-FULL] 전체 기간 price_data 로딩 중…")

    from sqlalchemy import text
    engine = get_engine(db_name)

    query = text("""
        SELECT ticker, date, open, high, low, close, volume, adjusted_close
        FROM public.price_data
        ORDER BY ticker, date;
    """)

    with engine.connect() as conn:
        df_price = pd.read_sql(query, conn)

    if df_price.empty:
        print("[TECH-FULL] price_data 없음 → 스킵")
        return

    tickers = sorted(df_price["ticker"].unique())
    print(f"[TECH-FULL] 전체 티커 수: {len(tickers)}")

    frames = []

    with engine.connect() as conn:
        conn.execute(text("DELETE FROM public.technical_indicators;"))
        print("[TECH-FULL] 기존 technical_indicators 테이블 초기화 완료.")
        conn.commit()


    for idx, t in enumerate(tickers, start=1):
        print(f"[TECH-FULL] ({idx}/{len(tickers)}) {t} 계산 중…")
        df_t = df_price[df_price["ticker"] == t]
        tech_df = compute_technical_indicators(df_t)
        frames.append(tech_df)

    full_df = pd.concat(frames, ignore_index=True)

    print(f"[TECH-FULL] 기술지표 전체 계산 완료: {len(full_df)} rows")

    # 전체 덮어쓰기 (UPSERT)
    upsert_technical_indicators(db_name, full_df)

    print("[TECH-FULL] 기술지표 FULL 업서트 완료.")

# --------------------------------------------------------------
# 기술지표 파이프라인: 증분용 (최근 250일치만 계산)
# --------------------------------------------------------------

def run_technical_indicators_incremental(config: Dict[str, Any]) -> None:
    """
    기존: price_data 전체 기간을 읽고 모든 기술지표 전체 재계산 (매우 무거움)
    변경: 최근 250일만 계산 → 5~10배 이상 빠름
    """
    db_name = config["db_name"]
    window_days = 250   # rolling 기간 + 여유 buffer

    # ---------------------------------------------------------
    # 최신 날짜 구하고, 최근 250일 구간만 조회
    # ---------------------------------------------------------
    from sqlalchemy import text
    engine = get_engine(db_name)

    # price_data 에 최신 날짜 확인
    with engine.connect() as conn:
        last_date = conn.execute(
            text("SELECT MAX(date) FROM public.price_data")
        ).scalar()

    if last_date is None:
        print("[TECH] price_data empty → 기술지표 계산 불가")
        return

    start_date = last_date - timedelta(days=window_days)

    print(f"[TECH] 최근 {window_days}일 ({start_date} ~ {last_date}) 데이터만 사용")

    # 최근 250일 price data만 로딩
    query = text("""
        SELECT ticker, date, open, high, low, close, volume
        FROM public.price_data
        WHERE date >= :start_date
        ORDER BY ticker, date
    """)

    with engine.connect() as conn:
        df_price = pd.read_sql(query, conn, params={"start_date": start_date})

    if df_price.empty:
        print("[TECH] 최근 데이터 없음 → 기술지표 계산 스킵")
        return

    # ---------------------------------------------------------
    # 티커별 기술지표 계산
    # ---------------------------------------------------------
    tickers = sorted(df_price["ticker"].unique())
    print(f"[TECH] 대상 티커 수: {len(tickers)}")

    frames = []
    for idx, t in enumerate(tickers, start=1):
        print(f"[TECH] ({idx}/{len(tickers)}) {t} 지표 계산")
        df_t = df_price[df_price["ticker"] == t]
        tech_df = compute_technical_indicators(df_t)
        frames.append(tech_df)

    tech_recent = pd.concat(frames, ignore_index=True)

    print(f"[TECH] 기술지표 계산 완료: {len(tech_recent)} rows")

    # ---------------------------------------------------------
    # 최근 250일 데이터만 UPSERT
    # ---------------------------------------------------------
    upsert_technical_indicators(db_name, tech_recent)

    print("[TECH] 최근 250일 기술지표 업데이트 완료")


# ======================================================================
# SECTION 4 — MACROECONOMIC INDICATORS (FETCH + UPSERT + PIPELINE)
# ======================================================================

# --------------------------------------------------------------
# Macro Data Fetch from FRED
# --------------------------------------------------------------

# FRED API 읽어오기
FRED_API_KEY = os.getenv("FRED_API_KEY")
def get_fred_client() -> Fred | None:
    """
    한국어 주석:
    - FRED API 클라이언트를 생성하는 헬퍼 함수.
    - FRED_API_KEY가 없거나 잘못 설정된 경우 None을 반환하고,
      매크로 데이터 수집을 스킵할 수 있도록 한다.
    """
    if not FRED_API_KEY:
        print("[WARN] FRED_API_KEY가 설정되어 있지 않아 매크로 데이터 수집을 건너뜁니다.")
        return None

    try:
        fred = Fred(api_key=FRED_API_KEY)
        return fred
    except ValueError as e:
        # fredapi 내부에서 키가 잘못되었을 때 발생하는 에러를 안전하게 처리
        print(f"[WARN] FRED API 초기화 실패: {e}")
        return None

def fetch_macro_from_fred(series_map: Dict[str, str], start: str, end: str) -> pd.DataFrame:
    dates = pd.date_range(start, end, freq="D")
    out = pd.DataFrame({"date": dates})
    out["date"] = out["date"].dt.date
    
    fred = get_fred_client()
    if fred is None:
        print("[MACRO] FRED 클라이언트 초기화 실패 → 매크로 데이터 수집 건너뜀")
        return pd.DataFrame(columns=["date"] + list(series_map.keys()))

    for col_name, fred_symbol in series_map.items():
        print(f"[MACRO] Fetch {col_name} ({fred_symbol}) {start}~{end}")
        s = fred.get_series(fred_symbol, observation_start=start, observation_end=end)
        s = s.reset_index().rename(columns={"index": "date", 0: col_name})
        s["date"] = pd.to_datetime(s["date"]).dt.date
        out = out.merge(s, on="date", how="left")

    return out

# --------------------------------------------------------------
# Macro UPSERT
# --------------------------------------------------------------
def upsert_macro(db_name: str, df: pd.DataFrame):
    if df.empty:
        print("[MACRO] No macro data to upsert.")
        return

    # 누락된 컬럼 채우기
    required_cols = [
        "cpi", "gdp", "ppi", "jolt",
        "cci", "interest_rate", "trade_balance"
    ]
    for c in required_cols:
        if c not in df.columns:
            df[c] = None

    # pandas/Numpy → Python scalar
    records = []
    for _, r in df.iterrows():
        records.append((
            r["date"],
            to_scalar(r["cpi"]),
            to_scalar(r["gdp"]),
            to_scalar(r["ppi"]),
            to_scalar(r["jolt"]),
            to_scalar(r["cci"]),
            to_scalar(r["interest_rate"]),
            to_scalar(r["trade_balance"]),
        ))

    sql = """
    INSERT INTO public.macroeconomic_indicators (
        date, cpi, gdp, ppi, jolt,
        cci, interest_rate, trade_balance
    )
    VALUES %s
    ON CONFLICT (date) DO UPDATE SET
        cpi           = EXCLUDED.cpi,
        gdp           = EXCLUDED.gdp,
        ppi           = EXCLUDED.ppi,
        jolt          = EXCLUDED.jolt,
        cci           = EXCLUDED.cci,
        interest_rate = EXCLUDED.interest_rate,
        trade_balance = EXCLUDED.trade_balance;
    """

    conn = get_db_conn(db_name)
    try:
        with conn.cursor() as cur:
            execute_values(cur, sql, records)
        conn.commit()
        print(f"[MACRO] Upserted {len(df)} rows into public.macroeconomic_indicators")

    finally:
        conn.close()


# --------------------------------------------------------------
# Macro Pipeline (증분)
# --------------------------------------------------------------
def run_macro_pipeline(config: Dict[str, Any]):
    db_name = config["db_name"]
    series_map = config["macro_series"]

    if not series_map:
        print("[MACRO] macro_series is empty → skip macroeconomic_indicators")
        return

    # 테이블 마지막 날짜
    last = get_last_date_in_table(
        db_name,
        "public.macroeconomic_indicators",
        "date"
    )

    if last is None:
        start_date = config.get("macro_start", "2017-01-01")
        print(f"[MACRO] 기존 없음 → {start_date} 부터 시작")
    else:
        start_date = (last + timedelta(days=1)).strftime("%Y-%m-%d")
        print(f"[MACRO] 마지막 날짜 {last} 이후 → {start_date} 부터 수집")

    end_date = today_kst().strftime("%Y-%m-%d")

    if start_date > end_date:
        print("[MACRO] Already up to date.")
        return

    df_macro = fetch_macro_from_fred(series_map, start_date, end_date)
    upsert_macro(db_name, df_macro)
# ======================================================================
# SECTION 5 — COMPANY FUNDAMENTALS (FETCH + UPSERT + PIPELINE)
# ======================================================================

# --------------------------------------------------------------
# yfinance 재무제표 Fetch
# --------------------------------------------------------------
def fetch_company_fundamentals_from_yf(tickers: List[str]) -> pd.DataFrame:
    """
    각 티커에 대해 다음 데이터를 가져와 company_fundamentals 형태로 변환:
    - annual financials (Income Statement)
    - annual balance sheet
    - EPS, PE_ratio (yfinance.info 에서)
    """
    rows = []

    for t in tickers:
        print(f"[FUND] Fetch fundamentals for {t}")
        yf_t = yf.Ticker(t)

        fs = yf_t.financials            # 손익계산서
        bs = yf_t.balance_sheet         # 재무상태표

        if fs is None or fs.empty:
            print(f"[FUND] No financials for {t}")
            continue
        if bs is None or bs.empty:
            print(f"[FUND] No balance_sheet for {t}")
            continue

        fs = fs.copy()
        bs = bs.copy()

        # --- 여기 부분만 수정 ---
        def normalize_cols_to_date(idx):
            # 이미 DatetimeIndex면 .date (ndarray of date) 사용
            if isinstance(idx, pd.DatetimeIndex):
                return idx.date
            # 그 외에는 to_datetime 후 .date
            return pd.to_datetime(idx).date

        fs.columns = normalize_cols_to_date(fs.columns)
        bs.columns = normalize_cols_to_date(bs.columns)
        # ------------------------

        # 계정명 매칭 함수
        def find_row(df, names):
            for n in names:
                if n in df.index:
                    return df.loc[n]
            df_map = {idx.lower().replace(" ", ""): idx for idx in df.index}
            for n in names:
                key = n.lower().replace(" ", "")
                if key in df_map:
                    return df.loc[df_map[key]]
            return None

        revenue_row = find_row(fs, ["Total Revenue", "Revenue"])
        net_income_row = find_row(fs, ["Net Income", "NetIncome"])
        assets_row = find_row(bs, ["Total Assets"])
        liab_row = find_row(bs, ["Total Liab", "Total Liabilities"])
        equity_row = find_row(bs, ["Total Stockholder Equity", "Total Equity"])

        info = {}
        try:
            info = yf_t.info or {}
        except:
            pass

        eps_value = info.get("trailingEps")
        pe_value = info.get("trailingPE")

        report_dates = sorted(set(fs.columns) | set(bs.columns))

        for d in report_dates:
            rows.append({
                "ticker": t,
                "date": d,
                "revenue": float(revenue_row[d]) if (revenue_row is not None and d in revenue_row and pd.notna(revenue_row[d])) else None,
                "net_income": float(net_income_row[d]) if (net_income_row is not None and d in net_income_row and pd.notna(net_income_row[d])) else None,
                "total_assets": float(assets_row[d]) if (assets_row is not None and d in assets_row and pd.notna(assets_row[d])) else None,
                "total_liabilities": float(liab_row[d]) if (liab_row is not None and d in liab_row and pd.notna(liab_row[d])) else None,
                "equity": float(equity_row[d]) if (equity_row is not None and d in equity_row and pd.notna(equity_row[d])) else None,
                "EPS": float(eps_value) if eps_value is not None else None,
                "PE_ratio": float(pe_value) if pe_value is not None else None,
            })

    if not rows:
        return pd.DataFrame(
            columns=[
                "ticker", "date",
                "revenue", "net_income",
                "total_assets", "total_liabilities",
                "equity", "EPS", "PE_ratio"
            ]
        )

    return pd.DataFrame(rows)



# --------------------------------------------------------------
# UPSERT Company Fundamentals
# --------------------------------------------------------------
def upsert_company_fundamentals(db_name: str, df: pd.DataFrame):
    if df.empty:
        print("[FUND] No fundamentals to upsert.")
        return

    # pandas → python scalar
    records = []
    for _, r in df.iterrows():
        records.append((
            r["ticker"], r["date"],
            to_scalar(r["revenue"]),
            to_scalar(r["net_income"]),
            to_scalar(r["total_assets"]),
            to_scalar(r["total_liabilities"]),
            to_scalar(r["equity"]),
            to_scalar(r["EPS"]),
            to_scalar(r["PE_ratio"]),
        ))

    sql = """
    INSERT INTO public.company_fundamentals (
        ticker, date,
        revenue, net_income,
        total_assets, total_liabilities,
        equity, EPS, PE_ratio
    )
    VALUES %s
    ON CONFLICT (ticker, date) DO UPDATE SET
        revenue           = EXCLUDED.revenue,
        net_income        = EXCLUDED.net_income,
        total_assets      = EXCLUDED.total_assets,
        total_liabilities = EXCLUDED.total_liabilities,
        equity            = EXCLUDED.equity,
        EPS               = EXCLUDED.EPS,
        PE_ratio          = EXCLUDED.PE_ratio;
    """

    conn = get_db_conn(db_name)
    try:
        with conn.cursor() as cur:
            execute_values(cur, sql, records)
        conn.commit()
        print(f"[FUND] Upserted {len(df)} rows into public.company_fundamentals")

    finally:
        conn.close()


# --------------------------------------------------------------
# Fundamentals Pipeline (전체 업데이트)
# --------------------------------------------------------------
def run_company_fundamentals_pipeline(config: Dict[str, Any]):
    db_name = config["db_name"]
    tickers = config["tickers_for_fund"]

    df = fetch_company_fundamentals_from_yf(tickers)
    upsert_company_fundamentals(db_name, df)
# ======================================================================
# SECTION 6 — DAILY AUTO DATA COLLECTION (증분 업데이트)
# ======================================================================

def run_data_collection() -> None:
    """
    pipeline/run_pipeline.py 에서 "STEP 0" 으로 호출되는 자동 데이터 수집 함수.

    ▣ 동작
      1) price_data: MAX(date) 이후 구간만 yfinance 로 추가 수집 후 UPSERT
      2) technical_indicators: price_data 전체 기반으로 다시 계산 후 UPSERT
      3) macroeconomic_indicators: MAX(date)+1 이후 구간만 수집
      4) company_fundamentals: 전체 재수집 (annual/quarterly 로 증분 개념이 애매해서)
    """

    print("\n=== [STEP 0] DAILY DATA COLLECTION (AUTO, incremental) ===")

    db_name = "db"
    today_str = today_kst().strftime("%Y-%m-%d")

    # ------------------------------------------------------------------
    # 1) 가격/기술지표 대상 티커 (환경변수 or 기본값)
    # ------------------------------------------------------------------
    # 1) DB에서 모든 티커 가져오기
    tickers = get_all_tickers_from_db(db_name)

    # DB가 비어있으면 기본값 사용
    if not tickers:
        print("[INFO] DB에 티커가 없어서 기본 유니버스 사용: AAPL, MSFT, TSLA")
        tickers = ["AAPL", "MSFT", "TSLA"]


    # ------------------------------------------------------------------
    # 2) 펀더멘털 대상 티커
    # ------------------------------------------------------------------
    tickers_for_fund = get_all_tickers_from_db(db_name)

    # DB가 비어있으면 기본값 사용
    if not tickers_for_fund:
        print("[INFO] DB에 티커가 없어서 기본 유니버스 사용: AAPL, MSFT, TSLA")
        tickers_for_fund = ["AAPL", "MSFT", "TSLA"]


    # ------------------------------------------------------------------
    # 3) 거시지표 시리즈 매핑
    # ------------------------------------------------------------------
    macro_series = {
        # 예시. 실제 쓰는 yfinance symbol 로 교체 가능
        "cpi": "CPIAUCSL",
        "gdp": "GDP",
        "ppi": "PPIACO",
        "jolt": "JTSJOL",
        "cci": "CONCCONF",
        "interest_rate": "^TNX",
    }

    config = {
        "db_name": db_name,
        "tickers": tickers,
        "price_start": "2017-01-01",
        "tickers_for_fund": tickers_for_fund,
        "macro_series": macro_series,
        "macro_start": "2017-01-01",
    }

    # ------------------------------------------------------------------
    # [1] price_data 증분 업데이트
    # ------------------------------------------------------------------
    print("\n[1] price_data incremental update…")
    run_price_pipeline(config)

    # ------------------------------------------------------------------
    # [2] technical_indicators 전체 재계산
    # ------------------------------------------------------------------
    print("\n[2] technical_indicators incremental recompute…")
    run_technical_indicators_incremental(config)

    # ------------------------------------------------------------------
    # [3] macroeconomic_indicators 증분 업데이트
    # ------------------------------------------------------------------
    print("\n[3] macroeconomic_indicators incremental update…")
    run_macro_pipeline(config)

    # ------------------------------------------------------------------
    # [4] company_fundamentals 전체 업데이트
    # ------------------------------------------------------------------
    print("\n[4] company_fundamentals full update…")
    run_company_fundamentals_pipeline(config)

    print("=== [STEP 0] DAILY DATA COLLECTION DONE ===\n")
# ======================================================================
# SECTION 7 — MANUAL BACKFILL ALL (FULL REFILL)
# ======================================================================

def manual_backfill_all() -> None:
    """
    ⚠ 전체 백필(Backfill) 모드
    -----------------------------------------
    이 함수는 "모든 티커 × 모든 스키마 × 전체 기간"을 다시 채운다.
    즉, 다음 순서로 강제 재수집한다:

      1) price_data               (OHLCV 전체 재수집)
      2) technical_indicators    (price_data 전체 기반 재계산)
      3) macroeconomic_indicators (전체 재수집)
      4) company_fundamentals    (전체 재수집)

    ※ 이 작업은 매우 오래 걸릴 수 있음.
       실행 전 반드시 티커 개수와 시작일 범위를 확인해야 함.
    """

    db_name = "db"
    today_str = today_kst().strftime("%Y-%m-%d")

    # ------------------------------------------------------------
    # 1) 가격/기술지표 대상 티커
    # ------------------------------------------------------------
    tickers = get_all_tickers_from_db(db_name)
    if not tickers:
        print("[INFO] DB에 티커가 없어서 기본 유니버스 사용: AAPL, MSFT, TSLA")
        tickers = ["AAPL", "MSFT", "TSLA"]

    # ------------------------------------------------------------
    # 2) 펀더멘털 대상 티커
    # ------------------------------------------------------------
    tickers_for_fund = get_all_tickers_from_db(db_name)
    if not tickers_for_fund:
        print("[INFO] DB에 티커가 없어서 기본 유니버스 사용: AAPL, MSFT, TSLA")
        tickers_for_fund = ["AAPL", "MSFT", "TSLA"]

    # ------------------------------------------------------------
    # 3) 거시지표 시리즈
    # ------------------------------------------------------------
    macro_series = {
        # 필요 시 실제 프로젝트에서 수정
        "cpi": "CPIAUCSL",
        "gdp": "GDP",
        "ppi": "PPIACO",
        "jolt": "JTSJOL",
    }

    # ------------------------------------------------------------
    # 4) 백필 시작일 설정
    # ------------------------------------------------------------
    price_backfill_start = os.getenv("PRICE_BACKFILL_START", "2017-01-01")
    macro_backfill_start = os.getenv("MACRO_BACKFILL_START", "2017-01-01")

    # ------------------------------------------------------------
    # ⚠️ 진짜 위험한 작업이므로 경고 출력
    # ------------------------------------------------------------
    print(
        "\n⚠⚠⚠ [전체 백필 모드 경고] ⚠⚠⚠\n"
        "이 명령은 다음 작업을 '모두 강제로' 다시 수행합니다.\n"
        "  1) 모든 티커의 전체 OHLCV → price_data 를 다시 채움\n"
        "  2) price_data 전체 기반으로 technical_indicators 전체 재계산\n"
        "  3) 거시지표 전체 재수집 → macroeconomic_indicators 다시 기록\n"
        "  4) 모든 펀더멘털 티커에 대해 재무제표 전체 재수집\n\n"
        "■ 작업량이 매우 큽니다. (티커 수 × 기간 × 스키마)\n"
        "■ 네트워크 상황에 따라 몇십 분~몇 시간 걸릴 수 있습니다.\n"
        "■ 지금 설정된 값은 다음과 같습니다:\n"
        "---------------------------------------------------------------\n"
        f"  오늘(KST):                 {today_str}\n"
        f"  price_data 티커:           {tickers}\n"
        f"  fundamentals 티커:         {tickers_for_fund}\n"
        f"  macro 시리즈:              {macro_series}\n"
        f"  가격 백필 시작일:          {price_backfill_start}\n"
        f"  거시 백필 시작일:          {macro_backfill_start}\n"
        "---------------------------------------------------------------\n"
        "※ 의도한 값이 맞는지 반드시 확인하세요.\n"
        "※ 중간에 CTRL+C 로 중단해도 그 시점까지는 DB에 반영됨.\n"
        "---------------------------------------------------------------\n"
    )

    # ==================================================================
    # STEP 1 — PRICE_DATA 전체 백필 (티커도 나눠서 처리)
    # ==================================================================
    print("\n[STEP 1/4] price_data FULL backfill 시작…")

    tickers_per_batch = 20  # 티커도 20개 단위로 끊어서 처리 (원하면 조정 가능)

    for i in range(0, len(tickers), tickers_per_batch):
        batch_tickers = tickers[i : i + tickers_per_batch]
        print(f"[STEP 1/4] price_data batch {i} ~ {i + len(batch_tickers) - 1} 티커 처리 중...")

        # 이 배치의 모든 티커에 대해 OHLCV 수집
        df_price_batch = fetch_price_data_from_yf(
            batch_tickers,
            price_backfill_start,
            today_str,
        )

        # 이 배치만 DB에 UPSERT (행 단위 batch_size 는 upsert_price_data 에서 또 나눔)
        upsert_price_data(db_name, df_price_batch, batch_size=10_000)

    print("[STEP 1/4] price_data FULL backfill 완료.\n")


    # ==================================================================
    # STEP 2 — TECHNICAL_INDICATORS 전체 재계산/백필
    # ==================================================================
    print("[STEP 2/4] technical_indicators FULL 재계산…")
    config_for_tech = {
        "db_name": db_name,
        "tickers": tickers
    }
    run_technical_indicators_full(config_for_tech)
    print("[STEP 2/4] technical_indicators FULL backfill 완료.\n")

    # ==================================================================
    # STEP 3 — MACROECONOMIC_INDICATORS 전체 백필
    # ==================================================================
    if macro_series:
        print("[STEP 3/4] macroeconomic_indicators FULL backfill 시작…")
        df_macro = fetch_macro_from_fred(macro_series, macro_backfill_start, today_str)
        upsert_macro(db_name, df_macro)
        print("[STEP 3/4] macroeconomic_indicators FULL backfill 완료.\n")
    else:
        print("[STEP 3/4] macroeconomic_indicators: macro_series 비어 있어서 skip.\n")

    # ==================================================================
    # STEP 4 — COMPANY FUNDAMENTALS 전체 백필
    # ==================================================================
    print("[STEP 4/4] company_fundamentals FULL backfill 시작…")
    config_for_fund = {
        "db_name": db_name,
        "tickers_for_fund": tickers_for_fund
    }
    run_company_fundamentals_pipeline(config_for_fund)
    print("[STEP 4/4] company_fundamentals FULL backfill 완료.\n")

    print("✅ 전체 백필 완료. (manual_backfill_all)")
# ======================================================================
# SECTION 8 — MAIN ENTRYPOINT
# ======================================================================

if __name__ == "__main__":
    """
    이 파일을 직접 실행하면 “전체 백필 모드”가 실행된다.

    사용 예:
        python AI/daily_data_collection/main.py

    주의:
    - manual_backfill_all() 은 매우 무거운 작업이다.
    - 다음 환경변수를 통해 대상 티커/기간을 조절할 수 있다:

        PRICE_INGEST_TICKERS      : price_data + technical 대상
        FUND_INGEST_TICKERS       : company_fundamentals 대상
        PRICE_BACKFILL_START      : OHLCV 백필 시작일
        MACRO_BACKFILL_START      : macro 백필 시작일

    - pipeline/run_pipeline.py 에서 자동 실행되는 함수는
          run_data_collection()
      이고, manual_backfill_all() 은 직접 실행할 때만 사용해야 한다.
    """
    manual_backfill_all()
