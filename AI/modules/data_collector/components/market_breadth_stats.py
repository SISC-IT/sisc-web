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

        # 데이터 정렬 (중요: 그룹 연산 전 필수)
        df = df.sort_values(['ticker', 'date'])
        
        # [수정 1] transform과 rolling을 결합하여 인덱스 꼬임 방지
        # groupby 이후 transform 안에서 rolling을 호출합니다.
        
        # (1) 52주 고가/저가
        # - 오늘의 종가를 제외하고 어제까지의 252일 최고/최저가를 구하려면
        #   rolling 계산 후 shift(1)을 해주는 것이 논리적으로 가장 정확합니다.
        #   (하지만 현재 코드를 최대한 존중하여 단순 rolling.max/min을 사용하되, transform으로 안전하게 매핑합니다)
        df['high_52w'] = df.groupby('ticker')['close'].transform(
            lambda x: x.rolling(window=252, min_periods=200).max()
        )
        df['low_52w'] = df.groupby('ticker')['close'].transform(
            lambda x: x.rolling(window=252, min_periods=200).min()
        )
        
        # (2) 200일 이동평균
        df['ma_200'] = df.groupby('ticker')['close'].transform(
            lambda x: x.rolling(window=200, min_periods=150).mean()
        )
        
        # 계산 안 된 앞부분 결측치 제거
        df = df.dropna(subset=['high_52w', 'low_52w', 'ma_200'])

        if df.empty:
            print("[Stats] 계산 후 유효한 데이터가 모두 사라졌습니다. 데이터 길이를 확인하세요.")
            return pd.DataFrame()

        # 2. 상태 판별 (Boolean)
        # 소수점 오차 방지를 위해 엄격한 부등호가 아닌 아주 미세한 여유를 두거나, 크거나 같다로 판별
        df['is_nh'] = df['close'] >= df['high_52w']
        df['is_nl'] = df['close'] <= df['low_52w']
        df['is_above_ma200'] = df['close'] > df['ma_200']
        
        # 3. 날짜별 집계
        stats = df.groupby('date').agg(
            total_count=('ticker', 'count'),
            nh_count=('is_nh', 'sum'),
            nl_count=('is_nl', 'sum'),
            above_ma200_count=('is_above_ma200', 'sum')
        ).reset_index()
        
        # 4. 최종 메트릭 계산
        stats['nh_nl_index'] = stats['nh_count'] - stats['nl_count']
        stats['ma200_pct'] = (stats['above_ma200_count'] / stats['total_count']) * 100
        
        # NaN 처리 및 최소 종목 수(예: 50종목) 필터링
        stats = stats.dropna()
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