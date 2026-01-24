import pandas as pd
from sqlalchemy import text
from .connection import get_engine

def fetch_price_data(
    ticker: str,
    start_date: str,
    db_name: str = "db"
) -> pd.DataFrame:
    """
    [기본] 종목별 시세 데이터 조회 (Price Data)
    """
    engine = get_engine(db_name)
    query = text("""
        SELECT date, ticker, open, high, low, close, adjusted_close, volume, amount
        FROM public.price_data
        WHERE ticker = :ticker
          AND date >= :start_date
        ORDER BY date ASC;
    """)

    with engine.connect() as conn:
        df = pd.read_sql(query, conn, params={"ticker": ticker, "start_date": start_date})

    if not df.empty:
        df["date"] = pd.to_datetime(df["date"])
        # 수정주가 처리
        if "adjusted_close" in df.columns:
            df["close"] = df["adjusted_close"].fillna(df["close"])
            
    return df

def fetch_macro_indicators(
    start_date: str,
    db_name: str = "db"
) -> pd.DataFrame:
    """
    [공통] 거시경제 지표 조회 (모든 종목 공통 적용)
    """
    engine = get_engine(db_name)
    query = text("""
        SELECT date, 
               us10y, us2y, yield_spread, 
               vix_close, dxy_close, wti_price, gold_price, 
               credit_spread_hy
        FROM public.macroeconomic_indicators
        WHERE date >= :start_date
        ORDER BY date ASC;
    """)

    with engine.connect() as conn:
        df = pd.read_sql(query, conn, params={"start_date": start_date})
    
    if not df.empty:
        df["date"] = pd.to_datetime(df["date"])
        
    return df

def fetch_market_breadth(
    start_date: str,
    db_name: str = "db"
) -> pd.DataFrame:
    """
    [공통] 시장 건전성 지표 (Market Breadth)
    """
    engine = get_engine(db_name)
    query = text("""
        SELECT date, 
               advance_decline_ratio, fear_greed_index, 
               new_highs, new_lows, above_ma200_pct
        FROM public.market_breadth
        WHERE date >= :start_date
        ORDER BY date ASC;
    """)

    with engine.connect() as conn:
        df = pd.read_sql(query, conn, params={"start_date": start_date})
    
    if not df.empty:
        df["date"] = pd.to_datetime(df["date"])

    return df

def fetch_news_sentiment(
    ticker: str,
    start_date: str,
    db_name: str = "db"
) -> pd.DataFrame:
    """
    [개별] 뉴스 심리 지수 조회
    """
    engine = get_engine(db_name)
    query = text("""
        SELECT date, sentiment_score, risk_keyword_cnt, article_count
        FROM public.news_sentiment
        WHERE ticker = :ticker
          AND date >= :start_date
        ORDER BY date ASC;
    """)

    with engine.connect() as conn:
        df = pd.read_sql(query, conn, params={"ticker": ticker, "start_date": start_date})
    
    if not df.empty:
        df["date"] = pd.to_datetime(df["date"])
        
    return df

def fetch_fundamentals(
    ticker: str,
    db_name: str = "db"
) -> pd.DataFrame:
    """
    [개별] 기업 펀더멘털 데이터 (재무제표)
    """
    engine = get_engine(db_name)
    # 재무제표는 start_date 제한 없이 가져와서 ffill 하는 것이 안전함
    query = text("""
        SELECT date, per, pbr, roe, debt_ratio, operating_cash_flow
        FROM public.company_fundamentals
        WHERE ticker = :ticker
        ORDER BY date ASC;
    """)

    with engine.connect() as conn:
        df = pd.read_sql(query, conn, params={"ticker": ticker})
        
    if not df.empty:
        df["date"] = pd.to_datetime(df["date"])
        
    return df