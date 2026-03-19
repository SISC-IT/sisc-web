# AI/modules/data_collector/market_data.py
import sys
import os
from tqdm import tqdm
import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
from typing import List
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

class MarketDataCollector:
    """
    주식 종목의 시세(OHLCV) 데이터를 수집하여 DB에 적재하는 클래스
    - yfinance를 사용하여 데이터 수집
    - 수리(Repair) 모드 지원 (누락 데이터 복구)
    - 거래대금(Amount) 자동 계산
    - [업데이트] Pandas merge_asof를 활용한 과거 시점(Trailing) 기준 PER/PBR 고속 계산
    """
    
    def __init__(self, db_name: str = "db"):
        self.db_name = db_name
        # 수집 시작 기준일 (데이터가 아예 없을 경우)
        self.FIXED_START_DATE = "2015-01-01"

    def get_start_date(self, ticker: str, repair_mode: bool) -> str:
        """
        DB를 조회하여 수집 시작 날짜를 결정합니다.
        """
        if repair_mode:
            return self.FIXED_START_DATE

        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT MAX(date) FROM public.price_data WHERE ticker = %s", (ticker,))
            last_date = cursor.fetchone()[0]
            
            if last_date:
                # 마지막 데이터 다음 날부터 수집
                return (last_date + timedelta(days=1)).strftime("%Y-%m-%d")
            else:
                return self.FIXED_START_DATE
        except Exception as e:
            print(f"   [Warning] 시작 날짜 조회 실패 ({ticker}): {e}")
            return self.FIXED_START_DATE
        finally:
            cursor.close()
            conn.close()

    def fetch_ohlcv(self, ticker: str, start_date: str) -> pd.DataFrame:
        """
        yfinance에서 OHLCV 데이터를 다운로드하고 전처리합니다.
        (과거 재무 데이터를 매핑하여 정확한 시점의 PER/PBR 계산)
        """
        try:
            # 1. 데이터 다운로드
            df = yf.download(ticker, start=start_date, progress=False, auto_adjust=False, threads=False)
            
            if df.empty:
                return pd.DataFrame()

            # 2. MultiIndex 컬럼 평탄화
            if isinstance(df.columns, pd.MultiIndex):
                df.columns = df.columns.get_level_values(0)
            
            # 3. 거래대금(Amount) 계산: 종가 * 거래량 (근사치)
            if 'Close' in df.columns and 'Volume' in df.columns:
                df['Amount'] = df['Close'] * df['Volume']
            else:
                df['Amount'] = 0

            # 4. 전체 재무 데이터(Fundamentals) 조회
            #    특정 시점의 주가에 그보다 '가장 최근에 발표된 과거 실적'을 매핑하기 위해 전체를 가져옵니다.
            conn = get_db_conn(self.db_name)
            cursor = conn.cursor()
            
            fund_records = []
            try:
                # 해당 종목의 모든 재무 데이터를 과거순으로 조회
                query = """
                    SELECT date, equity, shares_issued, eps 
                    FROM public.company_fundamentals
                    WHERE ticker = %s 
                    ORDER BY date ASC
                """
                cursor.execute(query, (ticker,))
                fund_records = cursor.fetchall()
            except Exception as e:
                print(f"   [Warning] 재무 데이터 조회 실패 ({ticker}): {e}")
            finally:
                cursor.close()
                conn.close()

            # 5. 과거 시점 기준(Trailing) PER/PBR 계산 (Vectorized + As-of Merge)
            if fund_records:
                # 재무 데이터를 DataFrame으로 변환
                fund_df = pd.DataFrame(fund_records, columns=['date', 'equity', 'shares_issued', 'eps'])
                fund_df['date'] = pd.to_datetime(fund_df['date'])
                
                # 계산을 위해 자료형을 float으로 강제 변환 (Decimal 등 처리)
                for col in ['equity', 'shares_issued', 'eps']:
                    fund_df[col] = pd.to_numeric(fund_df[col], errors='coerce')
                
                fund_df = fund_df.sort_values('date')
                
                # 주가 데이터에 기준 날짜 컬럼 추가
                df_temp = df.copy()
                df_temp['price_date'] = pd.to_datetime(df_temp.index)
                
                # [핵심 로직] merge_asof를 활용해 주가 날짜(price_date) 기준으로 
                # 같거나 그 이전(direction='backward')의 가장 가까운 재무 데이터(date)를 매핑합니다.
                merged = pd.merge_asof(
                    df_temp.sort_values('price_date'),
                    fund_df,
                    left_on='price_date',
                    right_on='date',
                    direction='backward'
                )
                
                # 인덱스 복구
                merged.index = df_temp.index
                
                # PER 계산 (주가 / EPS)
                merged['per'] = merged['Close'] / merged['eps']
                # EPS가 0 이하이거나 결측치면 PER 계산 불가 처리
                merged.loc[merged['eps'] <= 0, 'per'] = None
                
                # PBR 계산 (주가 / (자본/주식수))
                bps = merged['equity'] / merged['shares_issued']
                merged['pbr'] = merged['Close'] / bps
                # 주식수 또는 자본이 0 이하인 경우 PBR 계산 불가 처리
                merged.loc[(merged['shares_issued'] <= 0) | (bps <= 0), 'pbr'] = None
                
                # 원본 df에 결합된 계산값 적용
                df['per'] = merged['per']
                df['pbr'] = merged['pbr']
            else:
                # DB에 해당 종목의 재무 데이터가 전혀 없는 경우
                df['per'] = None
                df['pbr'] = None

            # 6. 무한대(inf)나 NaN 값 정리 (DB 저장 에러 방지)
            df.replace([np.inf, -np.inf], None, inplace=True)
            df = df.where(pd.notnull(df), None)

            return df

        except Exception as e:
            print(f"   [Error] {ticker} 다운로드 중 에러: {e}")
            import traceback
            traceback.print_exc()
            return pd.DataFrame()

    def save_to_db(self, ticker: str, df: pd.DataFrame):
        """
        데이터프레임을 DB에 저장(Upsert)합니다.
        """
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()

        insert_query = """
            INSERT INTO public.price_data (
                date, ticker, open, high, low, close, volume, adjusted_close, amount, per, pbr
            )
            VALUES %s
            ON CONFLICT (date, ticker) DO UPDATE SET
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                volume = EXCLUDED.volume,
                adjusted_close = EXCLUDED.adjusted_close,
                amount = EXCLUDED.amount,
                per = EXCLUDED.per,
                pbr = EXCLUDED.pbr;
        """

        try:
            data_to_insert = []
            has_adj = 'Adj Close' in df.columns
            has_per = 'per' in df.columns
            has_pbr = 'pbr' in df.columns

            for index, row in df.iterrows():
                date_val = index.date()
                
                # 안전한 값 추출 헬퍼 함수
                def get_val(col, default=0):
                    val = row.get(col, default)
                    if hasattr(val, 'iloc'):
                        val = val.iloc[0]
                    if val is None or pd.isna(val):
                        return None
                    return float(val)

                open_val = get_val('Open') or 0
                high_val = get_val('High') or 0
                low_val = get_val('Low') or 0
                close_val = get_val('Close') or 0
                vol_val = int(get_val('Volume') or 0)
                amount_val = get_val('Amount') or 0
                adj_close_val = get_val('Adj Close') if has_adj else close_val
                
                per_val = get_val('per', None) if has_per else None
                pbr_val = get_val('pbr', None) if has_pbr else None

                data_to_insert.append((
                    date_val, ticker, open_val, high_val, low_val, close_val, 
                    vol_val, adj_close_val, amount_val, per_val, pbr_val
                ))

            if data_to_insert:
                execute_values(cursor, insert_query, data_to_insert)
                conn.commit()
                # ★ 수정됨: 너무 많은 성공 로그를 숨기고 싶다면 이 줄을 주석 처리하셔도 됩니다.
                # tqdm.write(f"   [{ticker}] {len(data_to_insert)}건 저장 완료.") 
            else:
                tqdm.write(f"   [{ticker}] 저장할 데이터가 없습니다.")

        except Exception as e:
            conn.rollback()
            tqdm.write(f"   [{ticker}][Error] DB 저장 실패: {e}")
        finally:
            cursor.close()
            conn.close()

    def update_tickers(self, tickers: List[str], repair_mode: bool = False):
        """
        여러 종목에 대해 수집 작업을 수행하는 메인 로직
        """
        mode_msg = "[Repair Mode]" if repair_mode else "[Update Mode]"
        print(f"[MarketData] {mode_msg} {len(tickers)}개 종목 업데이트 시작...")

        today = datetime.now().strftime("%Y-%m-%d")

        for ticker in tqdm(tickers, desc="OHLCV 수집 진행률", unit="종목"):
            start_date = self.get_start_date(ticker, repair_mode)

            if not repair_mode and start_date > today:
                # 이미 최신이면 조용히 넘어감 (로그 축소)
                continue
            
            df = self.fetch_ohlcv(ticker, start_date)
            
            if not df.empty:
                self.save_to_db(ticker, df)
            # 수집된 데이터가 없거나 에러난 경우만 콘솔에 남김
            else:
                tqdm.write(f"   [{ticker}] 수집된 데이터가 없습니다.")

# ----------------------------------------------------------------------
# [실행 모드]
# ----------------------------------------------------------------------
if __name__ == "__main__":
    import argparse
    from AI.libs.database.ticker_loader import load_all_tickers_from_db

    parser = argparse.ArgumentParser(description="[수동] 주식 데이터 수집기")
    parser.add_argument("tickers", nargs='*', help="수집할 종목 티커 리스트")
    parser.add_argument("--all", action="store_true", help="DB에 등록된 모든 종목 업데이트")
    parser.add_argument("--repair", action="store_true", help="[복구모드] 전체 기간 재수집")
    parser.add_argument("--db", default="db", help="DB 이름")
    
    args = parser.parse_args()
    target_tickers = args.tickers
    
    if args.all:
        try:
            print(">> DB에서 전체 종목 리스트를 조회합니다...")
            target_tickers = load_all_tickers_from_db(verbose=True)
        except Exception as e:
            print(f"[Error] 종목 로드 실패: {e}")
            sys.exit(1)

    if not target_tickers:
        print("\n========================================================")
        print(" [Manual Mode] 주식 시장 데이터 수집기")
        print("========================================================")
        print(" 사용 예시:")
        print("  1) 특정 종목 수집  : python market_data.py AAPL TSLA")
        print("  2) 전체 종목 업데이트: python market_data.py --all")
        print("  3) 누락 데이터 복구 : python market_data.py --all --repair")
        print("========================================================")
        print(">> 수집할 종목 코드를 공백으로 구분하여 입력하세요 (종료: Enter)")
        
        try:
            input_str = sys.stdin.readline().strip()
            if input_str:
                target_tickers = input_str.split()
            else:
                sys.exit(0)
        except KeyboardInterrupt:
            sys.exit(0)

    if target_tickers:
        collector = MarketDataCollector(db_name=args.db)
        collector.update_tickers(target_tickers, repair_mode=args.repair)
        print("\n[완료] 모든 작업이 끝났습니다.")