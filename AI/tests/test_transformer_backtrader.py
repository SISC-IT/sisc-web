# AI/tests/test_transformer_backtrader.py
# 사용불가. 추후 수정 필요
import os
import sys
from typing import List, Dict, Optional
from datetime import datetime, timedelta
import pandas as pd
import pathlib

# --- 프로젝트/레포 경로 설정 ---------------------------------------------------
_this_file = os.path.abspath(__file__)
project_root = os.path.dirname(os.path.dirname(_this_file))      # .../transformer
repo_root    = os.path.dirname(project_root)                     # .../
libs_root    = os.path.join(repo_root, "libs")                   # .../libs

# sys.path에 중복 없이 추가
for p in (project_root, repo_root, libs_root):
    if p not in sys.path:
        sys.path.append(p)
# ------------------------------------------------------------------------------

# ----------------------------------------------------------------------
# Transformer 실행
# ----------------------------------------------------------------------
from transformer.main import run_transformer

from typing import Optional
import pandas as pd
from sqlalchemy import text

# DB용 유틸: SQLAlchemy Engine 생성 함수 사용 (get_engine)
from libs.utils.get_db_conn import get_engine

def fetch_ohlcv(
    ticker: List[str],
    start: str,
    end: str,
    interval: str = "1d",
    db_name: str = "db",
) -> pd.DataFrame:
    """
    특정 티커, 날짜 범위의 OHLCV 데이터를 DB에서 불러오기 (SQLAlchemy 엔진 사용)

    Args:
        ticker (List[str]): 종목 코드 리스트 (예: ["AAPL", "MSFT", "GOOGL"])
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

    # 2) 쿼리: named parameter(:tickers 등) 사용 -> 안전하고 가독성 좋음
    query = text("""
        SELECT ticker, date, open, high, low, close, adjusted_close, volume
        FROM public.price_data
        WHERE ticker IN :tickers  -- 수정된 부분
          AND date BETWEEN :start AND :end
        ORDER BY date;
    """)

    # 3) DB에서 읽기 (with 문으로 커넥션 자동 정리)
    with engine.connect() as conn:
        df = pd.read_sql(
            query,
            con=conn,  # 꼭 키워드 인자로 con=conn
            params={"tickers": tuple(ticker), "start": start, "end": end},  # 수정된 부분
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



def run_transformer_for_test(finder_df: pd.DataFrame, raw_data: pd.DataFrame, start_date: str, end_date: str) -> pd.DataFrame:
    """
    Transformer 모델을 실행하여 매매 신호를 예측하는 함수
    :param finder_df: 종목 리스트
    :param raw_data: OHLCV 시계열 데이터
    :param start_date: 시작 날짜
    :param end_date: 종료 날짜
    :return: 매매 신호를 포함한 DataFrame
    """
    transformer_result = run_transformer(
        finder_df=finder_df,
        seq_len=64,
        pred_h=5,
        raw_data=raw_data,
        run_date=end_date,  # 예측 날짜
        weights_path=None,  # 가중치 경로. transformer/main.py 내부에서 기본 경로 사용
        interval="1d"
    )
    
    logs_df = transformer_result.get("logs", pd.DataFrame())
    return logs_df

# ----------------------------------------------------------------------
# Backtrader 실행
# ----------------------------------------------------------------------
from backtrader import Cerebro, Strategy
from backtrader.feeds import PandasData

class SimpleStrategy(Strategy):
    """
    Backtrader 전략
    """
    def __init__(self, logs_df: pd.DataFrame):
        self.order = None
        self.buy_price = None
        self.logs_df = logs_df  # 매매 신호를 담은 logs_df를 클래스에 저장

    def next(self):
        # 매수/매도 신호에 따라 거래 진행
        if self.order:
            return  # 이미 주문이 있으면 아무것도 하지 않음

        for _, row in self.logs_df.iterrows():
            if row['action'] == 'BUY' and self.data.datetime.date(0) == pd.to_datetime(row['date']).date():
                self.buy_price = row['predicted_price']
                self.order = self.buy(size=1)  # 예시: 1주 매수

            elif row['action'] == 'SELL' and self.data.datetime.date(0) == pd.to_datetime(row['date']).date():
                if self.buy_price:
                    sell_price = row['predicted_price']
                    profit = (sell_price - self.buy_price) / self.buy_price * 100  # 수익률 계산
                    print(f"Profit from {row['ticker']}: {profit:.2f}%")
                    self.order = self.sell(size=1)  # 예시: 1주 매도
                    self.buy_price = None

# ----------------------------------------------------------------------
# 테스트 실행 (2024년 1월 1일부터 12월 31일까지의 데이터로 테스트)
# ----------------------------------------------------------------------
def test_transformer_backtrader():
    """
    1년 동안 Transformer 모델을 통해 매매 신호를 예측하고, 
    Backtrader를 사용하여 수익률을 계산하는 테스트 함수
    """
    start_date = "2024-01-01"
    end_date = "2024-12-31"
    db_name = "db"  # DB 이름

    # 1. Transformer 모델을 통한 예측 신호 생성
    finder_df = pd.DataFrame({"ticker": ["AAPL", "MSFT", "GOOGL"]})
    
    # DB에서 OHLCV 데이터 가져오기
    raw_data = fetch_ohlcv(finder_df['ticker'].tolist(), start_date, end_date, db_name=db_name)

    # Transformer 모델 실행
    logs_df = run_transformer_for_test(finder_df, raw_data, start_date, end_date)
    
    if logs_df.empty:
        print("Transformer 모델에서 예측된 신호가 없습니다.")
        return

    # 2. Backtrader 전략 실행 (매매 시뮬레이션)
    ohlcv_data_feed = PandasData(dataname=raw_data)

    # Cerebro 엔진 설정
    cerebro = Cerebro()
    cerebro.addstrategy(SimpleStrategy, logs_df=logs_df)  # logs_df를 전략에 전달
    cerebro.adddata(ohlcv_data_feed)

    # 초기 자본금 및 수수료 설정
    cerebro.broker.set_cash(100000)  # 초기 자본금 설정
    cerebro.broker.set_commission(commission=0.001)  # 거래 수수료 설정

    # 3. 백테스트 실행
    cerebro.run()

    # 최종 자본금 출력
    print(f"Final Portfolio Value: {cerebro.broker.getvalue():.2f}")

if __name__ == "__main__":
    test_transformer_backtrader()
