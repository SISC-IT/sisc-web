# AI/modules/signal/core/data_loader.py
"""
[데이터 로더 - 멀티 타임프레임 적용 버전]
- features.py의 add_technical_indicators 및 add_multi_timeframe_features를 모두 사용합니다.
- 일봉뿐만 아니라 주봉/월봉 지표까지 학습하여 장기 추세를 반영합니다.
"""

import numpy as np
import pandas as pd
from typing import Tuple, Dict
from sklearn.preprocessing import MinMaxScaler
from tqdm import tqdm

import sys
import os
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn
# [수정] 두 함수 모두 임포트
from AI.modules.signal.core.features import add_technical_indicators, add_multi_timeframe_features

class DataLoader:
    def __init__(self, db_name="db", lookback=60):
        self.db_name = db_name
        self.lookback = lookback
        self.scaler = MinMaxScaler()
        
        self.ticker_to_id: Dict[str, int] = {}
        self.sector_to_id: Dict[str, int] = {}
        self.ticker_sector_map: Dict[str, int] = {}
        
        self._load_metadata()

    def _load_metadata(self):
        """ (기존과 동일) 메타데이터 로드 """
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        try:
            query = "SELECT ticker, COALESCE(sector, 'Unknown') FROM public.stock_info"
            cursor.execute(query)
            rows = cursor.fetchall()
            unique_sectors = sorted(list(set([row[1] for row in rows])))
            self.sector_to_id = {sec: i for i, sec in enumerate(unique_sectors)}
            self.ticker_sector_map = {row[0]: self.sector_to_id[row[1]] for row in rows}
            self.ticker_to_id = {row[0]: i for i, row in enumerate(rows)}
            print(f"[DataLoader] 메타데이터 로드 완료: {len(self.ticker_to_id)}개 종목")
        except Exception as e:
            print(f"[DataLoader] 메타데이터 로드 실패: {e}")
        finally:
            if cursor: cursor.close()
            if conn: conn.close()

    def load_data_from_db(self, start_date="2018-01-01") -> pd.DataFrame:
        """ (기존과 동일) DB 로드 """
        conn = get_db_conn(self.db_name)
        query = f"""
            SELECT date, ticker, open, high, low, close, volume, adjusted_close
            FROM public.price_data
            WHERE date >= '{start_date}'
            ORDER BY ticker, date ASC
        """
        df = pd.read_sql(query, conn)
        conn.close()
        df['date'] = pd.to_datetime(df['date'])
        
        # [중요] 주봉/월봉 계산을 위해 인덱스를 날짜로 설정했다가 나중에 풀기 위해 sort
        return df

    def create_dataset(self, df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, np.ndarray, Dict]:
        X_ts_list = []
        X_ticker_list = []
        X_sector_list = []
        y_class_list = [] 
        y_reg_list = []   
        
        tickers = df['ticker'].unique()
        print(f"[DataLoader] {len(tickers)}개 종목에 대해 Feature Engineering 시작...")

        # [Step 1] 지표 추가 (일봉 + 멀티 타임프레임)
        processed_dfs = []
        for ticker in tqdm(tickers, desc="Adding Indicators"):
            # 날짜순 정렬 및 인덱스 설정 (resample을 위해 필수)
            sub_df = df[df['ticker'] == ticker].copy().sort_values('date')
            sub_df = sub_df.set_index('date') 
            
            # 데이터 길이가 너무 짧으면(예: 1년 미만) 월봉 계산이 부정확하므로 스킵 가능
            # 여기서는 최소 200일 정도로 잡음
            if len(sub_df) < 200: continue
            
            # 1) 기본 일봉 지표 추가
            sub_df = add_technical_indicators(sub_df)
            
            # 2) [NEW] 멀티 타임프레임 지표 추가 (주봉/월봉)
            try:
                sub_df = add_multi_timeframe_features(sub_df)
            except Exception as e:
                # 데이터 부족 등으로 실패 시 해당 종목 건너뜀
                continue
            
            # 인덱스 리셋 (다시 컬럼으로)
            sub_df = sub_df.reset_index()
            processed_dfs.append(sub_df)
            
        if not processed_dfs: raise ValueError("[Error] 데이터 없음")
        full_df = pd.concat(processed_dfs)
        
        # [Step 1.5] 원본 주가 백업
        full_df['raw_close'] = full_df['close']

        # [Step 2] 스케일링 대상 컬럼 확장 (주봉/월봉 포함)
        feature_cols = [
            # 일봉 (Daily)
            'open', 'high', 'low', 'close', 'volume',
            'ma5', 'ma20', 'ma60', 
            'rsi', 'macd', 'signal_line', 
            'upper_band', 'lower_band', 'vol_change',
            
            # 주봉 (Weekly)
            'week_ma20', 'week_rsi', 
            'week_bollinger_upper', 'week_bollinger_lower', 
            'week_volume_change', 'week_macd', 'week_macd_signal',
            'dist_week_ma20',
            
            # 월봉 (Monthly)
            'month_ma12', 
            'month_bollinger_upper', 'month_bollinger_lower', 
            'month_volume_change', 'month_macd', 'month_macd_signal'
        ]
        
        # 없는 컬럼이 있으면 에러 나므로 교집합만 사용 (안전장치)
        available_cols = [c for c in feature_cols if c in full_df.columns]
        
        # NaN 제거 (지표 계산 초반부 + ffill로 인한 앞쪽 공백)
        full_df = full_df.dropna(subset=available_cols)
        
        print(f">> 데이터 스케일링 중... (Features: {len(available_cols)}개)")
        full_df[available_cols] = self.scaler.fit_transform(full_df[available_cols])

        # [Step 3] 시퀀스 생성
        print(">> 시퀀스 및 라벨 생성 중...")
        for ticker in tqdm(full_df['ticker'].unique(), desc="Sequencing"):
            sub_df = full_df[full_df['ticker'] == ticker]
            if len(sub_df) <= self.lookback: continue

            t_id = self.ticker_to_id.get(ticker, 0)
            s_id = self.ticker_sector_map.get(ticker, 0)

            values = sub_df[available_cols].values
            raw_closes = sub_df['raw_close'].values 
            
            num_samples = len(sub_df) - self.lookback
            if num_samples <= 0: continue

            for i in range(num_samples):
                window = values[i : i + self.lookback]
                curr_raw = raw_closes[i + self.lookback - 1]
                next_raw = raw_closes[i + self.lookback]
                
                label_cls = 1 if next_raw > curr_raw else 0
                epsilon = 1e-9
                label_reg = (next_raw - curr_raw) / (curr_raw + epsilon)
                
                X_ts_list.append(window)
                X_ticker_list.append(t_id)
                X_sector_list.append(s_id)
                y_class_list.append(label_cls)
                y_reg_list.append(label_reg)

        X_ts = np.array(X_ts_list)
        X_ticker = np.array(X_ticker_list)
        X_sector = np.array(X_sector_list)
        y_class = np.array(y_class_list)
        y_reg = np.array(y_reg_list)
        
        info = {
            "n_tickers": len(self.ticker_to_id),
            "n_sectors": len(self.sector_to_id),
            "scaler": self.scaler,
            "n_features": len(available_cols)
        }
        
        return X_ts, X_ticker, X_sector, y_class, y_reg, info