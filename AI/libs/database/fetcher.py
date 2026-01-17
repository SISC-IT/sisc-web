# AI/libs/database/fetcher.py
from __future__ import annotations
from typing import Optional
import pandas as pd
from sqlalchemy import text

# DB용 유틸: SQLAlchemy Engine 생성 함수 사용 (get_engine)
from .connection import get_engine

def fetch_ohlcv(
    ticker: str,
    start: str,
    end: str,
    interval: str = "1d",
    db_name: str = "db",
) -> pd.DataFrame:
    """
    특정 티커, 날짜 범위의 OHLCV 데이터를 DB에서 불러오기

    Args:
        ticker (str): 종목 코드 (예: "AAPL")
        start (str): 시작일자 'YYYY-MM-DD'
        end (str): 종료일자 'YYYY-MM-DD'
        interval (str): 데이터 간격 (현재 일봉만 지원)
        db_name (str): DB 설정 이름

    Returns:
        pd.DataFrame: [ticker, date, open, high, low, close, adjusted_close, volume]
    """

    engine = get_engine(db_name)

    # adjusted_close가 중요하다면 쿼리 단계에서 확실히 가져옵니다.
    query = text("""
        SELECT ticker, date, open, high, low, close, adjusted_close, volume
        FROM public.price_data
        WHERE ticker = :ticker
          AND date BETWEEN :start AND :end
        ORDER BY date;
    """)

    with engine.connect() as conn:
        df = pd.read_sql(
            query,
            con=conn,
            params={"ticker": ticker, "start": start, "end": end},
        )

    # 빈 데이터 처리
    if df is None or df.empty:
        return pd.DataFrame(columns=["ticker", "date", "open", "high", "low", "close", "adjusted_close", "volume"])

    # 날짜 변환
    if "date" in df.columns:
        df["date"] = pd.to_datetime(df["date"])

    # 데이터 보정 로직 추가
    # 1. adjusted_close가 없는 경우(NaN) -> close 값으로 대체 (결측치 방지)
    if "adjusted_close" in df.columns and "close" in df.columns:
        df["adjusted_close"] = df["adjusted_close"].fillna(df["close"])
    elif "adjusted_close" not in df.columns and "close" in df.columns:
        # 컬럼 자체가 없으면 close를 복사해서 생성
        df["adjusted_close"] = df["close"]

    # 컬럼 순서 정리
    desired_cols = ["ticker", "date", "open", "high", "low", "close", "adjusted_close", "volume"]
    cols_present = [c for c in desired_cols if c in df.columns]
    df = df.loc[:, cols_present]

    return df