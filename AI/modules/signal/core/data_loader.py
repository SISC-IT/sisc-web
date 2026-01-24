# AI/modules/signal/core/data_loader.py
"""
[데이터 로더 - Multi-Horizon Version]
- 1, 3, 5, 7일 뒤의 등락을 한 번에 모두 라벨링합니다.
- 정답(y)의 모양이 (N,)에서 (N, 4)로 바뀝니다.
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
        return df

    def create_dataset(self, df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, np.ndarray, Dict]:
        X_ts_list = []
        X_ticker_list = []
        X_sector_list = []
        y_class_list = [] # 이제 여기가 2차원 리스트가 됨
        y_reg_list = []   
        
        # -----------------------------------------------------------
        # [설정] Multi-Horizon (1, 3, 5, 7일 뒤 예측)
        # -----------------------------------------------------------
        HORIZONS = [1, 3, 5, 7]
        max_horizon = max(HORIZONS)
        print(f"🎯 예측 목표: {HORIZONS}일 뒤의 등락을 동시 예측")

        tickers = df['ticker'].unique()
        print(f"[DataLoader] {len(tickers)}개 종목에 대해 Feature Engineering 시작...")

        processed_dfs = []
        for ticker in tqdm(tickers, desc="Adding Indicators"):
            sub_df = df[df['ticker'] == ticker].copy().sort_values('date')
            
            if len(sub_df) < 200: continue
            if sub_df['close'].std() == 0: continue # 좀비 데이터 제거

            sub_df = add_technical_indicators(sub_df)
            try:
                sub_df = add_multi_timeframe_features(sub_df)
            except Exception:
                continue
            
            processed_dfs.append(sub_df)
            
        if not processed_dfs: raise ValueError("[Error] 유효한 데이터가 없습니다!")
        full_df = pd.concat(processed_dfs)
        
        full_df['raw_close'] = full_df['close']
        
        feature_cols = [
            'log_return', 
            'open_ratio', 'high_ratio', 'low_ratio', 
            'vol_change',
            'ma5_ratio', 'ma20_ratio', 'ma60_ratio', 
            'rsi', 
            'macd_ratio', 
            'bb_position',
            'week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change',
            'month_ma12_ratio', 'month_rsi'
        ]
        
        available_cols = [c for c in feature_cols if c in full_df.columns]
        full_df = full_df.dropna(subset=available_cols)
        
        print(f">> 데이터 스케일링 중... (Features: {len(available_cols)}개)")
        full_df[available_cols] = self.scaler.fit_transform(full_df[available_cols])

        print(">> 시퀀스 및 라벨 생성 중...")
        
        debug_printed = False
        
        for ticker in tqdm(full_df['ticker'].unique(), desc="Sequencing"):
            sub_df = full_df[full_df['ticker'] == ticker]
            # 데이터가 (lookback + 가장 먼 미래) 보다 많아야 함
            if len(sub_df) <= self.lookback + max_horizon: continue

            t_id = self.ticker_to_id.get(ticker, 0)
            s_id = self.ticker_sector_map.get(ticker, 0)

            values = sub_df[available_cols].values
            raw_closes = sub_df['raw_close'].values 
            
            if not debug_printed:
                print(f"\n[DEBUG Sample] Ticker: {ticker}")
                print(f"   - Raw Closes (First 5): {raw_closes[:5]}")
                debug_printed = True

            # 루프 범위: 끝에서 max_horizon 만큼은 정답을 알 수 없으므로 제외
            num_samples = len(sub_df) - self.lookback - max_horizon + 1
            if num_samples <= 0: continue

            for i in range(num_samples):
                window = values[i : i + self.lookback]
                curr_raw = raw_closes[i + self.lookback - 1]
                
                # [핵심] 1, 3, 5, 7일 뒤 정답을 모두 구해서 리스트로 만듦
                multi_labels = []
                
                for h in HORIZONS:
                    next_raw = raw_closes[i + self.lookback + h - 1]
                    threshold = 0.000
                    
                    if curr_raw == 0:
                        label = 0
                    else:
                        label = 1 if next_raw > curr_raw * (1 + threshold) else 0
                    multi_labels.append(label)
                
                # 회귀 라벨은 대표값(가장 먼 7일) 하나만 씀 (여기선 분류가 메인이므로)
                label_reg = 0.0 
                
                X_ts_list.append(window)
                X_ticker_list.append(t_id)
                X_sector_list.append(s_id)
                y_class_list.append(multi_labels) # [0, 1, 1, 0] 형태 저장
                y_reg_list.append(label_reg)

        X_ts = np.array(X_ts_list)
        X_ticker = np.array(X_ticker_list)
        X_sector = np.array(X_sector_list)
        y_class = np.array(y_class_list) # Shape: (N, 4)
        y_reg = np.array(y_reg_list)
        
        info = {
            "n_tickers": len(self.ticker_to_id),
            "n_sectors": len(self.sector_to_id),
            "scaler": self.scaler,
            "n_features": len(available_cols),
            "horizons": HORIZONS # 메타데이터에 추가
        }
        
        return X_ts, X_ticker, X_sector, y_class, y_reg, info