# AI/modules/data_collector/macro_data.py
import sys
import os
import pandas as pd
import numpy as np
import yfinance as yf
from fredapi import Fred
from datetime import datetime, timedelta
from psycopg2.extras import execute_values

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn

# 환경 변수 로드
FRED_API_KEY = os.getenv("FRED_API_KEY")

class MacroDataCollector:
    """
    거시경제 지표 및 시장 위험 지표를 수집하여 DB에 적재하는 클래스
    """
    def __init__(self, db_name="db"):
        self.db_name = db_name
        self.fred = Fred(api_key=FRED_API_KEY)

    def fetch_fred_data(self, start_date):
        """
        FRED에서 거시경제 데이터를 수집합니다.
        """
        print("[Macro] FRED 데이터 수집 시작...")
        
        # 스키마 컬럼명 : FRED Series ID 매핑
        fred_map = {
            # --- 물가 및 경제성장 ---
            'cpi': 'CPIAUCSL',                  # 소비자물가지수 (Monthly)
            'core_cpi': 'CPILFESL',             # 근원 소비자물가지수 (Monthly)
            'ppi': 'PPIACO',                    # 생산자물가지수 (Monthly)
            'gdp': 'GDP',                       # 명목 GDP (Quarterly)
            'real_gdp': 'GDPC1',                # 실질 GDP (Quarterly)
            'pce': 'PCEPI',                     # 개인소비지출 물가지수 (Monthly)
            'core_pce': 'PCEPILFE',             # 근원 PCE (Monthly)

            # --- 고용 및 소비 심리 ---
            'unemployment_rate': 'UNRATE',      # 실업률 (Monthly)
            'jolt': 'JTSJOL',                   # 구인 건수 (Monthly)
            'consumer_sentiment': 'UMCSENT',    # 미시간대 소비자심리지수 (Monthly)
            'cci': 'CSCICP03USM665S',           # OECD 기준 소비자신뢰지수 (Monthly)

            # --- 금리 및 통화 정책 ---
            'interest_rate': 'DFF',             # 연방기금 실효금리 (Daily, FEDFUNDS는 월간)
            'ff_targetrate_upper': 'DFEDTARU',  # 연방기금금리 목표 상단 (Daily)
            'ff_targetrate_lower': 'DFEDTARL',  # 연방기금금리 목표 하단 (Daily)
            'us10y': 'DGS10',                   # 미국채 10년물 금리 (Daily)
            'us2y': 'DGS2',                     # 미국채 2년물 금리 (Daily)
            'credit_spread_hy': 'BAMLH0A0HYM2', # 하이일드 채권 스프레드 (Daily)

            # --- 무역 ---
            'trade_balance': 'BOPGSTB',         # 상품 및 서비스 무역수지 (Monthly)
            'tradebalance_goods': 'BOPGTB',     # 상품 무역수지 (Monthly)
            'trade_import': 'IMPGS',            # 수입액 (Monthly)
            'trade_export': 'EXPGS'             # 수출액 (Monthly)
        }

        dfs = []
        for col, series_id in fred_map.items():
            try:
                series = self.fred.get_series(series_id, observation_start=start_date)
                df = pd.DataFrame(series, columns=[col])
                dfs.append(df)
            except Exception as e:
                print(f"  [Warning] FRED '{col}' ({series_id}) 수집 실패: {e}")

        if not dfs:
            return pd.DataFrame()

        # 인덱스(날짜) 기준으로 병합 (Outer Join)
        fred_df = pd.concat(dfs, axis=1)
        return fred_df

    def fetch_yahoo_data(self, start_date):
        """
        Yahoo Finance에서 시장 지표(VIX, 달러, 유가, 금)를 수집합니다.
        """
        print("[Macro] Yahoo Finance 데이터 수집 시작...")
        
        # 스키마 컬럼명 : Yahoo Ticker 매핑
        yahoo_map = {
            'vix_close': '^VIX',       # 변동성 지수
            'dxy_close': 'DX-Y.NYB',   # 달러 인덱스
            'wti_price': 'CL=F',       # WTI 원유 선물
            'gold_price': 'GC=F'       # 금 선물
        }

        dfs = []
        for col, ticker in yahoo_map.items():
            try:
                # yfinance는 데이터프레임으로 반환
                data = yf.download(ticker, start=start_date, progress=False)
                if not data.empty:
                    # 'Close' 컬럼만 추출하여 이름 변경
                    if isinstance(data.columns, pd.MultiIndex):
                        price_series = data['Close'][ticker]
                    else:
                        price_series = data['Close']
                    
                    df = pd.DataFrame(price_series)
                    df.columns = [col]
                    dfs.append(df)
            except Exception as e:
                print(f"  [Warning] Yahoo '{col}' ({ticker}) 수집 실패: {e}")

        if not dfs:
            return pd.DataFrame()

        yahoo_df = pd.concat(dfs, axis=1)
        # Timezone 정보 제거 (FRED 데이터와 인덱스 타입을 맞추기 위함)
        if yahoo_df.index.tz is not None:
            yahoo_df.index = yahoo_df.index.tz_localize(None)
            
        return yahoo_df

    def save_to_db(self, combined_df):
        """
        통합된 데이터를 DB에 저장합니다.
        """
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()

        insert_query = """
            INSERT INTO public.macroeconomic_indicators (
                date, cpi, gdp, ppi, jolt, cci, interest_rate, trade_balance,
                core_cpi, real_gdp, unemployment_rate, consumer_sentiment,
                ff_targetrate_upper, ff_targetrate_lower, pce, core_pce,
                tradebalance_goods, trade_import, trade_export,
                us10y, us2y, yield_spread, vix_close, dxy_close,
                wti_price, gold_price, credit_spread_hy
            )
            VALUES %s
            ON CONFLICT (date) DO UPDATE SET
                cpi = EXCLUDED.cpi,
                gdp = EXCLUDED.gdp,
                ppi = EXCLUDED.ppi,
                jolt = EXCLUDED.jolt,
                cci = EXCLUDED.cci,
                interest_rate = EXCLUDED.interest_rate,
                trade_balance = EXCLUDED.trade_balance,
                core_cpi = EXCLUDED.core_cpi,
                real_gdp = EXCLUDED.real_gdp,
                unemployment_rate = EXCLUDED.unemployment_rate,
                consumer_sentiment = EXCLUDED.consumer_sentiment,
                ff_targetrate_upper = EXCLUDED.ff_targetrate_upper,
                ff_targetrate_lower = EXCLUDED.ff_targetrate_lower,
                pce = EXCLUDED.pce,
                core_pce = EXCLUDED.core_pce,
                tradebalance_goods = EXCLUDED.tradebalance_goods,
                trade_import = EXCLUDED.trade_import,
                trade_export = EXCLUDED.trade_export,
                us10y = EXCLUDED.us10y,
                us2y = EXCLUDED.us2y,
                yield_spread = EXCLUDED.yield_spread,
                vix_close = EXCLUDED.vix_close,
                dxy_close = EXCLUDED.dxy_close,
                wti_price = EXCLUDED.wti_price,
                gold_price = EXCLUDED.gold_price,
                credit_spread_hy = EXCLUDED.credit_spread_hy;
        """

        try:
            data_to_insert = []
            
            for date_idx, row in combined_df.iterrows():
                # [수정] Numpy 타입을 Python Native Type으로 변환하는 헬퍼 함수
                def to_py(val):
                    # NaN 또는 None 체크
                    if pd.isna(val) or val is None:
                        return None
                    # Numpy 숫자 타입이면 .item()으로 변환
                    if hasattr(val, 'item'):
                        return val.item()
                    return float(val)

                data_to_insert.append((
                    date_idx.date(),
                    to_py(row.get('cpi')), to_py(row.get('gdp')), to_py(row.get('ppi')), 
                    to_py(row.get('jolt')), to_py(row.get('cci')),
                    to_py(row.get('interest_rate')), to_py(row.get('trade_balance')),
                    to_py(row.get('core_cpi')), to_py(row.get('real_gdp')), 
                    to_py(row.get('unemployment_rate')), to_py(row.get('consumer_sentiment')),
                    to_py(row.get('ff_targetrate_upper')), to_py(row.get('ff_targetrate_lower')),
                    to_py(row.get('pce')), to_py(row.get('core_pce')),
                    to_py(row.get('tradebalance_goods')), to_py(row.get('trade_import')), 
                    to_py(row.get('trade_export')),
                    to_py(row.get('us10y')), to_py(row.get('us2y')), to_py(row.get('yield_spread')),
                    to_py(row.get('vix_close')), to_py(row.get('dxy_close')),
                    to_py(row.get('wti_price')), to_py(row.get('gold_price')), 
                    to_py(row.get('credit_spread_hy'))
                ))

            if data_to_insert:
                execute_values(cursor, insert_query, data_to_insert)
                conn.commit()
                print(f"[Macro] {len(data_to_insert)}일치 데이터 저장/업데이트 완료.")
            else:
                print("[Macro] 저장할 데이터가 없습니다.")

        except Exception as e:
            conn.rollback()
            print(f"[Macro][Error] DB 저장 실패: {e}")
        finally:
            cursor.close()
            conn.close()

    def run(self, lookback_days=365*2):
        """
        데이터 수집 및 저장 실행 메인 메소드
        """
        # 시작 날짜 계산
        start_date = (datetime.now() - timedelta(days=lookback_days)).strftime('%Y-%m-%d')
        
        # 1. 데이터 수집
        df_fred = self.fetch_fred_data(start_date)
        df_yahoo = self.fetch_yahoo_data(start_date)

        # 2. 데이터 병합 (Outer Join)
        if df_fred.empty and df_yahoo.empty:
            print("[Macro] 수집된 데이터가 없습니다.")
            return

        combined_df = pd.concat([df_fred, df_yahoo], axis=1)

        # 3. 파생 변수 계산: 장단기 금리차 (10Y - 2Y)
        # 두 데이터가 모두 존재할 때만 계산
        if 'us10y' in combined_df.columns and 'us2y' in combined_df.columns:
            combined_df['yield_spread'] = combined_df['us10y'] - combined_df['us2y']
        else:
            combined_df['yield_spread'] = np.nan

        # 날짜 내림차순 정렬 (필수는 아니지만 확인용)
        combined_df.sort_index(inplace=True)

        # 4. DB 저장
        self.save_to_db(combined_df)

if __name__ == "__main__":
    # 최근 5년치 데이터 업데이트
    collector = MacroDataCollector()
    collector.run(lookback_days=365*5)