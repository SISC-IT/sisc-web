# libs/database/fetcher.py
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
    특정 티커, 날짜 범위의 OHLCV 데이터를 DB에서 불러오기 (SQLAlchemy 엔진 사용)

    Args:
        ticker (str): 종목 코드 (예: "AAPL")
        start (str): 시작일자 'YYYY-MM-DD' (inclusive)
        end (str): 종료일자 'YYYY-MM-DD' (inclusive)
        interval (str): 데이터 간격 ('1d' 등) - 현재 테이블이 일봉만 제공하면 무시됨
        db_name (str): get_engine()가 참조할 설정 블록 이름 (예: "db", "report_DB")

    Returns:
        pd.DataFrame: 컬럼 = [ticker, date, open, high, low, close, adjusted_close, volume]
                      (date 컬럼은 pandas datetime으로 변환됨)
    """

    # 1) SQLAlchemy engine 얻기 (configs/config.json 기준)
    engine = get_engine(db_name)

    # 2) 쿼리: named parameter(:ticker 등) 사용 -> 안전하고 가독성 좋음
    #    - interval 분기가 필요하면 테이블/파티션 구조에 따라 쿼리를 분기하도록 확장 가능
    query = text("""
        SELECT ticker, date, open, high, low, close, adjusted_close, volume
        FROM public.price_data
        WHERE ticker = :ticker
          AND date BETWEEN :start AND :end
        ORDER BY date;
    """)

    # 3) DB에서 읽기 (with 문으로 커넥션 자동 정리)
    with engine.connect() as conn:
        df = pd.read_sql(
            query,
            con=conn,  # 꼭 키워드 인자로 con=conn
            params={"ticker": ticker, "start": start, "end": end},  # 튜플 X, 딕셔너리 O
            )

    # 4) 후처리: 컬럼 정렬 및 date 타입 통일
    if df is None or df.empty:
        # 빈 DataFrame이면 일관된 컬럼 스키마로 반환
        return pd.DataFrame(columns=["ticker", "date", "open", "high", "low", "close", "adjusted_close", "volume"])

    # date 컬럼을 datetime으로 변경 (UTC로 맞추고 싶으면 pd.to_datetime(..., utc=True) 사용)
    if "date" in df.columns:
        df["date"] = pd.to_datetime(df["date"])

    # 선택: 컬럼 순서 고정 (일관성 유지)
    desired_cols = ["ticker", "date", "open", "high", "low", "close", "adjusted_close", "volume"]
    # 존재하는 컬럼만 가져오기
    cols_present = [c for c in desired_cols if c in df.columns]
    df = df.loc[:, cols_present]

    return df

