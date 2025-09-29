import psycopg2
import pandas as pd

# DB 접속 커넥션 생성
def get_db_conn(config: dict):
    """config에서 DB 접속 정보 가져와 psycopg2 Connection 생성"""
    conn = psycopg2.connect(
        host=config["db"]["host"],
        user=config["db"]["user"],
        password=config["db"]["password"],
        dbname=config["db"]["dbname"],
        port=config["db"].get("port", 5432),
    )
    return conn

# OHLCV 데이터 불러오기
def fetch_ohlcv(
    ticker: str,
    start: str,
    end: str,
    interval: str = "1d",
    config: dict = None,  # type: ignore
) -> pd.DataFrame:
    """
    특정 티커, 날짜 범위의 OHLCV 데이터를 DB에서 불러오기

    Args:
        ticker (str): 종목 코드
        start (str): 시작일자 'YYYY-MM-DD'
        end (str): 종료일자 'YYYY-MM-DD'
        interval (str): 데이터 간격 ('1d' 등)
        config (dict): DB 접속 정보 포함한 설정

    Returns:
        DataFrame: 컬럼 = [date, open, high, low, close, volume]
    """
    conn = get_db_conn(config)

    query = """
        SELECT date, open, high, low, close, volume
        FROM stock_prices
        WHERE ticker = %s
          AND interval = %s
          AND date BETWEEN %s AND %s
        ORDER BY date;
    """

    # 파라미터 바인딩 (%s) 사용 → SQL injection 방지
    df = pd.read_sql(query, conn, params=(ticker, interval, start, end))

    conn.close()
    return df
