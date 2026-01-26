#AI/modules/data_collector/market_breadth_stats.py
import sys
import os
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

class MarketBreadthStatsCollector:
    """
    [시장 통계 계산기]
    - 외부 수집 없이 DB 내부 데이터(price_data)를 집계하여 파생 지표 생성
    - 1. NH-NL Index (52주 신고가 - 신저가)
    - 2. Stocks > MA200 (200일선 상회 비율)
    """

    def __init__(self, db_name: str = "db"):
        self.db_name = db_name

    def load_price_data(self, lookback_days: int = 400) -> pd.DataFrame:
        """
        통계 계산을 위해 최근 주가 데이터를 로드합니다.
        (MA200, 52주(252일) 신고가 계산을 위해 최소 1년 이상의 데이터 필요)
        """
        print(f"[Stats] 최근 {lookback_days}일치 주가 데이터 로딩 중 (메모리 최적화)...")
        conn = get_db_conn(self.db_name)
        
        # 필요한 컬럼만 로드하여 메모리 절약
        query = f"""
            SELECT date, ticker, close
            FROM public.price_data
            WHERE date >= CURRENT_DATE - INTERVAL '{lookback_days} days'
            ORDER BY ticker, date
        """
        
        try:
            df = pd.read_sql(query, conn)
            df['date'] = pd.to_datetime(df['date'])
            return df
        except Exception as e:
            print(f"   [Error] 데이터 로드 실패: {e}")
            return pd.DataFrame()
        finally:
            conn.close()

    def calculate_stats(self, df: pd.DataFrame) -> pd.DataFrame:
        """Pandas를 이용해 종목별 지표 계산 후 날짜별로 집계(Groupby)"""
        print("[Stats] 시장 지표(NH-NL, MA200%) 계산 중...")
        
        if df.empty:
            return pd.DataFrame()

        # 1. 종목별 보조지표 계산 (Rolling)
        # 52주 = 약 252 거래일, MA200 = 200 거래일
        df = df.sort_values(['ticker', 'date'])
        
        # GroupBy 객체 생성
        grouped = df.groupby('ticker')['close']
        
        # (1) 52주 고가/저가 (현재가 포함)
        df['high_52w'] = grouped.rolling(window=252, min_periods=200).max().reset_index(0, drop=True)
        df['low_52w'] = grouped.rolling(window=252, min_periods=200).min().reset_index(0, drop=True)
        
        # (2) 200일 이동평균
        df['ma_200'] = grouped.rolling(window=200, min_periods=150).mean().reset_index(0, drop=True)
        
        # 2. 상태 판별 (Boolean)
        # 신고가: 현재가가 52주 최고가와 같음 (또는 근접 99% 등 조정 가능하나 여기선 엄격하게 적용)
        # 신저가: 현재가가 52주 최저가와 같음
        # 데이터 정밀도 문제로 약간의 오차 허용 (np.isclose) 또는 단순 비교
        
        # 단순 비교 (Close >= High_52w)
        # 주의: 당일 종가가 갱신되면 당일 High_52w도 당일 종가로 변함. 따라서 == 비교가 맞음.
        df['is_nh'] = df['close'] >= df['high_52w']
        df['is_nl'] = df['close'] <= df['low_52w']
        df['is_above_ma200'] = df['close'] > df['ma_200']
        
        # 3. 날짜별 집계 (Cross-sectional Aggregation)
        stats = df.groupby('date').agg(
            total_count=('ticker', 'count'),
            nh_count=('is_nh', 'sum'),
            nl_count=('is_nl', 'sum'),
            above_ma200_count=('is_above_ma200', 'sum')
        ).reset_index()
        
        # 4. 최종 메트릭 계산
        stats['nh_nl_index'] = stats['nh_count'] - stats['nl_count']
        stats['ma200_pct'] = (stats['above_ma200_count'] / stats['total_count']) * 100
        
        # NaN 처리 (종목 수가 너무 적거나 초기 데이터 구간)
        stats = stats.dropna()
        
        # 유효한 통계만 필터링 (최소 50종목 이상 거래된 날만)
        stats = stats[stats['total_count'] > 50]
        
        return stats[['date', 'nh_nl_index', 'ma200_pct']]

    def save_to_db(self, stats_df: pd.DataFrame):
        """계산된 통계를 market_breadth 테이블에 저장"""
        if stats_df.empty:
            print("   >> 계산된 통계 데이터가 없습니다.")
            return

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()

        query = """
            INSERT INTO public.market_breadth (date, nh_nl_index, ma200_pct)
            VALUES %s
            ON CONFLICT (date) 
            DO UPDATE SET 
                nh_nl_index = EXCLUDED.nh_nl_index,
                ma200_pct = EXCLUDED.ma200_pct;
        """
        
        # DataFrame -> Tuples
        data_values = []
        for _, row in stats_df.iterrows():
            data_values.append((
                row['date'].date(),
                int(row['nh_nl_index']),
                float(row['ma200_pct'])
            ))
            
        try:
            execute_values(cursor, query, data_values)
            conn.commit()
            print(f"   >> 시장 통계(NH-NL, MA200%) {len(data_values)}일치 저장 완료.")
        except Exception as e:
            conn.rollback()
            print(f"   [Error] DB 저장 실패: {e}")
        finally:
            cursor.close()
            conn.close()

    def run(self, repair_mode: bool = False):
        # Repair 모드면 전체 기간(예: 10년), 아니면 최근 400일(MA200 계산 여유분 포함) 로드
        lookback = 365 * 10 if repair_mode else 400
        
        # 1. 데이터 로드
        df = self.load_price_data(lookback_days=lookback)
        
        # 2. 계산
        stats_df = self.calculate_stats(df)
        
        # 3. 저장
        self.save_to_db(stats_df)

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--repair", action="store_true")
    parser.add_argument("--db", default="db")
    args = parser.parse_args()

    collector = MarketBreadthStatsCollector(db_name=args.db)
    collector.run(repair_mode=args.repair)