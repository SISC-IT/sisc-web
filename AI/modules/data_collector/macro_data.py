import sys
import os
import pandas as pd
import yfinance as yf
from fredapi import Fred
from datetime import datetime
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

# FRED API 키 설정 (발급받은 키를 입력하거나 환경변수 사용)
# 테스트용 예시 키입니다. 실제 사용 시 본인 키로 교체하세요.
FRED_API_KEY = os.getenv("FRED_API_KEY", "your_fred_api_key_here")

def update_macro_data(db_name: str = "db"):
    """
    거시경제 지표(금리, CPI, 환율 등)를 수집하여 DB에 저장합니다.
    """
    print(f"[Macro Collector] 거시경제 데이터 업데이트 시작...")
    
    conn = get_db_conn(db_name)
    cursor = conn.cursor()
    
    try:
        fred = Fred(api_key=FRED_API_KEY)
        
        # 1. FRED 데이터 매핑 (DB컬럼명 : FRED Series ID)
        fred_map = {
            'interest_rate': 'FEDFUNDS',      # 기준금리
            'cpi': 'CPIAUCSL',                # 소비자물가지수
            'unemployment_rate': 'UNRATE',    # 실업률
            'gdp': 'GDP',                     # GDP
            'consumer_sentiment': 'UMCSENT',  # 소비자심리지수
            'ppi': 'PPIACO'                   # 생산자물가지수
        }

        # 데이터프레임 병합을 위한 리스트
        dfs = []
        
        # FRED 데이터 가져오기
        for col, series_id in fred_map.items():
            try:
                # 최근 5년치 데이터 (필요 시 수정)
                series = fred.get_series(series_id, observation_start='2020-01-01')
                df = pd.DataFrame(series, columns=[col])
                dfs.append(df)
            except Exception as e:
                print(f"   [FRED] {col} 수집 실패: {e}")

        # 2. Yahoo Finance 데이터 (환율, 유가 등)
        # 스키마에 컬럼이 없다면 필요한 것만 추가하거나, 기존 컬럼에 매핑
        # 여기서는 예시로 추가적인 데이터를 수집한다고 가정
        yf_map = {
            # 'col_name': 'ticker'
            # 현재 schema.sql에는 환율 컬럼이 명시되어 있지 않으나, 
            # 필요하다면 ALTER TABLE로 추가 후 사용 권장
        }
        
        # 3. 데이터 병합 (날짜 기준 Outer Join)
        if dfs:
            macro_df = pd.concat(dfs, axis=1)
        else:
            macro_df = pd.DataFrame()

        # FRED 데이터는 월/분기 단위이므로 빈 날짜가 많음 -> 필요시 ffill()로 채우거나 그대로 저장
        # 여기서는 원본 날짜 그대로 저장 (발표일 기준)
        macro_df.dropna(how='all', inplace=True)
        
        # 4. DB 저장
        insert_query = """
            INSERT INTO public.macroeconomic_indicators (
                date, interest_rate, cpi, unemployment_rate, gdp, 
                consumer_sentiment, ppi
            )
            VALUES %s
            ON CONFLICT (date) DO UPDATE SET
                interest_rate = EXCLUDED.interest_rate,
                cpi = EXCLUDED.cpi,
                unemployment_rate = EXCLUDED.unemployment_rate,
                gdp = EXCLUDED.gdp,
                consumer_sentiment = EXCLUDED.consumer_sentiment,
                ppi = EXCLUDED.ppi;
        """
        
        data_to_insert = []
        for date_idx, row in macro_df.iterrows():
            data_to_insert.append((
                date_idx.date(),
                row.get('interest_rate'),
                row.get('cpi'),
                row.get('unemployment_rate'),
                row.get('gdp'),
                row.get('consumer_sentiment'),
                row.get('ppi')
            ))
            
        if data_to_insert:
            execute_values(cursor, insert_query, data_to_insert)
            conn.commit()
            print(f"[Macro Collector] {len(data_to_insert)}건 저장 완료.")
        else:
            print("[Macro Collector] 저장할 데이터가 없습니다.")

    except Exception as e:
        conn.rollback()
        print(f"[Macro Collector][Error] 실패: {e}")
    finally:
        cursor.close()
        conn.close()

if __name__ == "__main__":
    update_macro_data()