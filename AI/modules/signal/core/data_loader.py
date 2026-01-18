# AI/modules/signal/core/data_loader.py
"""
[데이터 로더]
- DB에서 시세 데이터(price_data)와 종목 정보(stock_info)를 로드합니다.
- features.py의 기술적 지표 생성 함수를 활용하여 데이터 풍부화(Feature Engineering)를 수행합니다.
- Transformer 모델 학습을 위해 데이터를 시퀀스로 변환하고, Ticker와 Sector를 ID로 매핑합니다.
"""

import numpy as np
import pandas as pd
from typing import Tuple, Dict
from sklearn.preprocessing import MinMaxScaler
from tqdm import tqdm

# 경로 설정 및 features 모듈 임포트
import sys
import os
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn
from AI.modules.signal.core.features import add_technical_indicators

class DataLoader:
    def __init__(self, db_name="db", lookback=60):
        self.db_name = db_name
        self.lookback = lookback  # 시계열 윈도우 크기 (예: 60일)
        self.scaler = MinMaxScaler()
        
        # ID 매핑을 위한 딕셔너리
        self.ticker_to_id: Dict[str, int] = {}
        self.sector_to_id: Dict[str, int] = {}
        self.ticker_sector_map: Dict[str, int] = {}
        
        # 메타데이터 로드 (초기화 시 실행)
        self._load_metadata()

    def _load_metadata(self):
        """
        DB의 stock_info 테이블을 읽어 Ticker와 Sector를 ID로 변환하는 맵을 생성합니다.
        """
        conn = get_db_conn(self.db_name)
        cursor = conn.cursor()
        
        try:
            # 1. 모든 종목의 티커와 섹터 정보 조회
            query = "SELECT ticker, COALESCE(sector, 'Unknown') FROM public.stock_info"
            cursor.execute(query)
            rows = cursor.fetchall()
            
            # 2. 고유한 Sector 추출 및 정렬
            unique_sectors = sorted(list(set([row[1] for row in rows])))
            
            # 3. Sector ID 매핑 생성 (Unknown=0 부터 시작)
            self.sector_to_id = {sec: i for i, sec in enumerate(unique_sectors)}
            
            # 4. Ticker 별 Sector ID 캐싱
            self.ticker_sector_map = {row[0]: self.sector_to_id[row[1]] for row in rows}
            
            # 5. Ticker ID 매핑 생성
            self.ticker_to_id = {row[0]: i for i, row in enumerate(rows)}
            
            print(f"[DataLoader] 메타데이터 로드 완료: {len(self.ticker_to_id)}개 종목, {len(self.sector_to_id)}개 섹터")
            
        except Exception as e:
            print(f"[DataLoader] 메타데이터 로드 실패: {e}")
        finally:
            if cursor: cursor.close()
            if conn: conn.close()

    def load_data_from_db(self, start_date="2020-01-01") -> pd.DataFrame:
        """
        DB에서 전체 시세 데이터를 가져옵니다.
        """
        conn = get_db_conn(self.db_name)
        query = f"""
            SELECT date, ticker, open, high, low, close, volume, adjusted_close
            FROM public.price_data
            WHERE date >= '{start_date}'
            ORDER BY ticker, date ASC
        """
        df = pd.read_sql(query, conn)
        conn.close()
        
        # 날짜 변환
        df['date'] = pd.to_datetime(df['date'])
        return df

    def create_dataset(self, df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, Dict]:
        """
        DataFrame을 모델 입력용 Numpy 배열로 변환합니다.
        (기술적 지표 추가 -> 스케일링 -> 시퀀싱)
        """
        X_ts_list = []      # 시계열
        X_ticker_list = []  # 티커 ID
        X_sector_list = []  # 섹터 ID
        y_list = []         # 라벨
        
        tickers = df['ticker'].unique()
        print(f"[DataLoader] {len(tickers)}개 종목에 대해 Feature Engineering 및 시퀀스 생성 시작...")

        # ------------------------------------------------------------------
        # [Step 1] 기술적 지표 추가 (Feature Engineering)
        # 중요: 지표 계산(Rolling 등)은 반드시 '종목별'로 수행해야 데이터가 섞이지 않음
        # ------------------------------------------------------------------
        processed_dfs = []
        for ticker in tqdm(tickers, desc="Adding Indicators"):
            sub_df = df[df['ticker'] == ticker].copy().sort_values('date')
            
            # 데이터가 너무 적으면 지표 계산 불가하므로 스킵
            if len(sub_df) < self.lookback + 60: # 60일(MA60) 여유분 포함
                continue
                
            # features.py의 함수 호출
            sub_df = add_technical_indicators(sub_df)
            processed_dfs.append(sub_df)
            
        if not processed_dfs:
            raise ValueError("[Error] 유효한 데이터가 없습니다.")
            
        # 다시 하나의 큰 데이터프레임으로 합침
        full_df = pd.concat(processed_dfs)
        
        # ------------------------------------------------------------------
        # [Step 2] 데이터 정규화 (Scaling)
        # 모델이 학습할 feature 컬럼 정의 (features.py에서 생성된 컬럼 포함)
        # ------------------------------------------------------------------
        feature_cols = [
            'open', 'high', 'low', 'close', 'volume',
            'ma5', 'ma20', 'ma60', 
            'rsi', 'macd', 'signal_line', 
            'upper_band', 'lower_band', 'vol_change'
        ]
        
        # NaN 제거 (지표 계산 초반부)
        full_df = full_df.dropna(subset=feature_cols)
        
        # 전체 데이터 스케일링 (MinMaxScaler)
        print(">> 데이터 스케일링 중...")
        full_df[feature_cols] = self.scaler.fit_transform(full_df[feature_cols])

        # ------------------------------------------------------------------
        # [Step 3] 시퀀스 데이터 생성 (Sliding Window)
        # ------------------------------------------------------------------
        print(">> 시퀀스 데이터 생성 중...")
        # 다시 종목별로 그룹화하여 시퀀싱 (합쳐진 상태에서 그냥 슬라이딩하면 종목 간 경계에서 오류 발생)
        
        # 성능 최적화를 위해 numpy 변환 후 루프
        # (Pandas groupby 후 apply는 느리므로, 종목별 루프 사용)
        for ticker in tqdm(full_df['ticker'].unique(), desc="Sequencing"):
            sub_df = full_df[full_df['ticker'] == ticker]
            
            if len(sub_df) <= self.lookback:
                continue

            # ID 조회
            t_id = self.ticker_to_id.get(ticker, 0)
            s_id = self.ticker_sector_map.get(ticker, 0)

            values = sub_df[feature_cols].values
            closes = sub_df['close'].values # 스케일링 된 값 (예측 대상)
            # 만약 스케일링 안 된 원래 가격으로 등락률을 계산하고 싶다면 별도로 저장해뒀어야 함
            # 여기서는 스케일링 된 close 값끼리 비교해도 대소 관계는 같으므로 무방함
            
            num_samples = len(sub_df) - self.lookback
            if num_samples <= 0:
                continue

            # Vectorization 대신 명시적 루프로 이해하기 쉽게 구현 (성능 민감하면 stride_tricks 사용 가능)
            for i in range(num_samples):
                # 입력: i ~ i+lookback
                window = values[i : i + self.lookback]
                
                # 정답 라벨 생성 (내일 종가 > 오늘 종가)
                # target_idx = i + self.lookback
                current_close = closes[i + self.lookback - 1]
                next_close = closes[i + self.lookback]
                
                # [상승 예측] 다음날 오르면 1, 내리거나 같으면 0
                label = 1 if next_close > current_close else 0
                
                X_ts_list.append(window)
                X_ticker_list.append(t_id)
                X_sector_list.append(s_id)
                y_list.append(label)

        # Numpy 배열 변환
        X_ts = np.array(X_ts_list)
        X_ticker = np.array(X_ticker_list)
        X_sector = np.array(X_sector_list)
        y = np.array(y_list)
        
        info = {
            "n_tickers": len(self.ticker_to_id),
            "n_sectors": len(self.sector_to_id),
            "scaler": self.scaler,
            "n_features": len(feature_cols) # 모델 입력 차원 설정을 위해 필요
        }
        
        return X_ts, X_ticker, X_sector, y, info