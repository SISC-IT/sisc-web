# AI/modules/data_collector/fundamentals_data.py
"""
[기업 펀더멘털 데이터 수집기]
- yfinance를 통해 기업의 재무제표(손익계산서, 대차대조표) 데이터를 수집합니다.
- 수집된 데이터는 'company_fundamentals' 테이블에 저장됩니다.
- 주로 분기(Quarterly) 데이터를 기준으로 수집합니다.
"""

import sys
import os
import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime
from typing import List, Optional
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 추가 (기존 스타일 유지)
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn


def update_company_fundamentals(tickers: List[str], db_name: str = "db"):
    """
    지정된 종목들의 재무제표(펀더멘털) 데이터를 수집하여 DB에 저장합니다.
    """
    print(f"[Fundamentals] {len(tickers)}개 종목 재무 데이터 업데이트 시작...")
    
    conn = get_db_conn(db_name)
    cursor = conn.cursor()
    
    try:
        for ticker in tickers:
            print(f"   [{ticker}] 재무 정보 수집 중...")
            
            try:
                stock = yf.Ticker(ticker)
                
                # 1. 재무 데이터 가져오기 (분기 데이터 우선)
                # yfinance API: quarterly_financials(손익), quarterly_balance_sheet(대차대조표)
                # 데이터는 컬럼이 '날짜(Date)'로 되어 있으므로 전치(T)하여 행을 날짜로 만듦
                fin_df = stock.quarterly_financials.T
                bal_df = stock.quarterly_balance_sheet.T
                
                if fin_df.empty or bal_df.empty:
                    print(f"   [{ticker}] 재무 데이터 없음 (Skip).")
                    continue

                # 2. 인덱스(날짜) 통일 및 병합
                # 인덱스 이름이 다를 수 있으므로 날짜 포맷 통일
                fin_df.index = pd.to_datetime(fin_df.index)
                bal_df.index = pd.to_datetime(bal_df.index)
                
                # 날짜(index) 기준으로 병합 (Inner Join: 손익/대차대조표 모두 있는 날짜만)
                merged_df = fin_df.join(bal_df, lsuffix='_fin', rsuffix='_bal', how='inner')
                
                # 3. 데이터 매핑 및 전처리
                # yfinance 필드명 -> DB 컬럼명 매핑
                # (존재하지 않는 필드는 NaN 처리됨)
                
                data_to_insert = []
                
                for date_idx, row in merged_df.iterrows():
                    date_val = date_idx.date()
                    
                    # 안전하게 값 가져오기 헬퍼 함수
                    def get_val(keys):
                        for k in keys:
                            if k in row and pd.notna(row[k]):
                                return float(row[k])
                        return None

                    # 매핑 로직 (yfinance 필드명이 종종 변경되므로 여러 후보군 확인)
                    revenue = get_val(['Total Revenue', 'Operating Revenue'])
                    net_income = get_val(['Net Income', 'Net Income Common Stockholders'])
                    total_assets = get_val(['Total Assets'])
                    
                    # 부채총계: Total Liabilities Net Minority Interest 또는 Total Liabilities
                    total_liabilities = get_val(['Total Liabilities Net Minority Interest', 'Total Liabilities'])
                    
                    # 자본총계: Stockholders Equity
                    equity = get_val(['Stockholders Equity', 'Total Equity Gross Minority Interest'])
                    
                    # EPS
                    eps = get_val(['Basic EPS', 'Diluted EPS'])
                    
                    # P/E Ratio
                    # 재무제표 발표 시점의 P/E는 과거 주가가 필요하므로,
                    # 여기서는 간단히 NULL로 두거나, 필요시 price_data 테이블 조인해서 계산해야 함.
                    # 일단은 NULL로 저장.
                    pe_ratio = None 

                    data_to_insert.append((
                        str(ticker),
                        date_val,
                        revenue,
                        net_income,
                        total_assets,
                        total_liabilities,
                        equity,
                        eps,
                        pe_ratio
                    ))
                
                # 4. DB 저장 (Upsert)
                # company_fundamentals_pkey: (ticker, date)
                insert_query = """
                    INSERT INTO public.company_fundamentals (
                        ticker, date, revenue, net_income, total_assets, 
                        total_liabilities, equity, eps, pe_ratio
                    )
                    VALUES %s
                    ON CONFLICT (ticker, date) 
                    DO UPDATE SET
                        revenue = EXCLUDED.revenue,
                        net_income = EXCLUDED.net_income,
                        total_assets = EXCLUDED.total_assets,
                        total_liabilities = EXCLUDED.total_liabilities,
                        equity = EXCLUDED.equity,
                        eps = EXCLUDED.eps,
                        pe_ratio = EXCLUDED.pe_ratio
                """
                
                if data_to_insert:
                    execute_values(cursor, insert_query, data_to_insert)
                    conn.commit()
                    print(f"   [{ticker}] {len(data_to_insert)}건 재무 데이터 저장 완료.")
                else:
                    print(f"   [{ticker}] 저장할 유효한 데이터가 없습니다.")

            except Exception as e:
                print(f"   [{ticker}] 처리 중 에러 발생: {e}")
                conn.rollback()
                continue

    except Exception as e:
        conn.rollback()
        print(f"[Fundamentals][Error] 치명적 오류: {e}")
    finally:
        cursor.close()
        conn.close()


# ----------------------------------------------------------------------
# [수동 실행 모드]
# ----------------------------------------------------------------------
if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="[수동] 기업 펀더멘털 데이터 수집기")
    parser.add_argument("tickers", nargs='*', help="수집할 종목 티커 리스트")
    parser.add_argument("--all", action="store_true", help="DB 전 종목 업데이트")
    parser.add_argument("--db", default="db", help="DB 이름")

    args = parser.parse_args()
    target_tickers = args.tickers

    # --all 옵션 처리
    if args.all:
        try:
            # ticker_loader가 없다면 직접 쿼리
            conn = get_db_conn(args.db)
            cur = conn.cursor()
            cur.execute("SELECT DISTINCT ticker FROM public.price_data") # 가격 데이터가 있는 종목 기준
            rows = cur.fetchall()
            target_tickers = [r[0] for r in rows]
            cur.close()
            conn.close()
            print(f">> DB에서 {len(target_tickers)}개 종목을 조회했습니다.")
        except Exception as e:
            print(f"[Error] 종목 로드 실패: {e}")
            sys.exit(1)

    if not target_tickers:
        print("\n>> 수집할 종목 코드를 입력하세요 (예: AAPL TSLA)")
        print("   (종료하려면 엔터)")
        try:
            input_str = sys.stdin.readline().strip()
            if input_str:
                target_tickers = input_str.split()
            else:
                sys.exit(0)
        except KeyboardInterrupt:
            sys.exit(0)

    if target_tickers:
        update_company_fundamentals(target_tickers, db_name=args.db)
        print("\n[완료] 작업이 끝났습니다.")