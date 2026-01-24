# AI/modules/signal/core/data_loader.py
"""
[Data Loader - Integrated & Dynamic Version]
- 주가(Price), 거시경제(Macro), 시장지표(Breadth), 뉴스심리(Sentiment), 펀더멘털(Fundamental) 데이터를 통합 로드합니다.
- 테이블별로 데이터를 조회한 뒤, Pandas Merge를 통해 시계열을 정렬합니다.
- Multi-Horizon (예: 1, 3, 5, 7일) 예측을 동적으로 설정하여 라벨링을 수행합니다.
"""

import sys
import os
import numpy as np
import pandas as pd
from typing import Tuple, Dict, List, Optional
from sklearn.preprocessing import MinMaxScaler
from sqlalchemy import text
from tqdm import tqdm

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# DB 연결 및 Fetcher 모듈 import
from AI.libs.database.connection import get_engine
from AI.libs.database.fetcher import (
    fetch_macro_indicators, 
    fetch_market_breadth, 
    fetch_news_sentiment, 
    fetch_fundamentals
)
from AI.modules.signal.core.features import add_technical_indicators

class DataLoader:
    def __init__(self, db_name="db", lookback=60, horizons: List[int] = None):
        """
        :param db_name: DB 연결 설정 이름
        :param lookback: 시퀀스 길이 (과거 며칠을 볼 것인가)
        :param horizons: 예측할 미래 시점 리스트 (예: [1, 3, 5] -> 1일뒤, 3일뒤, 5일뒤 예측)
                         None일 경우 기본값 [1, 3, 5, 7] 사용
        """
        self.db_name = db_name
        self.lookback = lookback
        self.horizons = horizons if horizons else [1, 3, 5, 7]
        self.scaler = MinMaxScaler()
        
        # 메타데이터 ID 매핑
        self.ticker_to_id: Dict[str, int] = {}
        self.sector_to_id: Dict[str, int] = {}
        self.ticker_sector_map: Dict[str, int] = {}
        
        # 공통 데이터 캐싱 (Macro, Market Breadth)
        self.macro_df: pd.DataFrame = pd.DataFrame()
        self.breadth_df: pd.DataFrame = pd.DataFrame()
        
        # 초기화 시 메타데이터 로드
        self._load_metadata()

    def _load_metadata(self):
        """종목 및 섹터 정보를 로드하여 ID 매핑 생성"""
        engine = get_engine(self.db_name)
        try:
            query = text("SELECT ticker, COALESCE(sector, 'Unknown') as sector FROM public.stock_info")
            with engine.connect() as conn:
                df_meta = pd.read_sql(query, conn)
            
            if df_meta.empty:
                print("[DataLoader] Warning: stock_info 테이블이 비어있습니다.")
                return

            unique_sectors = sorted(df_meta['sector'].unique().tolist())
            self.sector_to_id = {sec: i for i, sec in enumerate(unique_sectors)}
            
            for _, row in df_meta.iterrows():
                self.ticker_sector_map[row['ticker']] = self.sector_to_id[row['sector']]
                
            self.ticker_to_id = {t: i for i, t in enumerate(df_meta['ticker'])}
            print(f"[DataLoader] 메타데이터 로드 완료: {len(self.ticker_to_id)}개 종목")
            
        except Exception as e:
            print(f"[DataLoader] 메타데이터 로드 실패: {e}")

    def _prepare_common_data(self, start_date: str):
        """
        [최적화] 모든 종목에 공통으로 적용되는 거시경제/시장지표를 미리 한 번만 로드합니다.
        """
        try:
            print("[DataLoader] 공통 데이터(Macro, Breadth) 로드 중...")
            self.macro_df = fetch_macro_indicators(start_date, self.db_name)
            self.breadth_df = fetch_market_breadth(start_date, self.db_name)
        except Exception as e:
            print(f"[DataLoader] 공통 데이터 로드 중 오류 발생 (무시하고 진행): {e}")

    def load_data_from_db(self, start_date="2018-01-01") -> pd.DataFrame:
        """
        1. 공통 데이터를 먼저 로드합니다.
        2. 전체 종목의 주가 데이터(Price Data)를 대량으로 조회합니다.
        """
        # 1. 공통 데이터 준비
        self._prepare_common_data(start_date)
        
        # 2. 주가 데이터 Bulk Load
        print(f"[DataLoader] {start_date} 부터 전체 주가 데이터를 조회합니다...")
        engine = get_engine(self.db_name)
        
        # 거래대금(amount) 포함
        query = text("""
            SELECT date, ticker, open, high, low, close, volume, adjusted_close, amount
            FROM public.price_data
            WHERE date >= :start_date
            ORDER BY ticker, date ASC
        """)
        
        with engine.connect() as conn:
            df_price = pd.read_sql(query, conn, params={"start_date": start_date})
            
        if not df_price.empty:
            df_price['date'] = pd.to_datetime(df_price['date'])
            # 수정주가(adjusted_close) 우선 사용
            if 'adjusted_close' in df_price.columns:
                df_price['close'] = df_price['adjusted_close'].fillna(df_price['close'])
        
        print(f"[DataLoader] 주가 데이터 로드 완료: {len(df_price)} rows")
        return df_price

    def create_dataset(self, df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, np.ndarray, Dict]:
        """
        로드된 주가 데이터(df)를 순회하며:
        1. 공통 데이터(Macro, Breadth) 병합
        2. 개별 데이터(News, Fundamental) 조회 및 병합
        3. 기술적 지표 생성 및 스케일링
        4. 시퀀스 데이터셋 생성 (X, y)
        """
        X_ts_list, X_ticker_list, X_sector_list = [], [], []
        y_class_list, y_reg_list = [], []
        
        # 동적으로 설정된 호라이즌 사용
        max_horizon = max(self.horizons)
        
        tickers = df['ticker'].unique()
        print(f"🎯 예측 목표: {self.horizons}일 뒤 등락 동시 예측 (Max Horizon: {max_horizon}일)")
        
        processed_dfs = []
        
        # -------------------------------------------------------------------------
        # Step 1: 종목별 데이터 병합 및 전처리 (Merging & Feature Engineering)
        # -------------------------------------------------------------------------
        for ticker in tqdm(tickers, desc="Processing Tickers"):
            # 해당 종목 데이터 추출
            sub_df = df[df['ticker'] == ticker].copy().sort_values('date')
            
            # 데이터 최소 길이 확인 (lookback + max_horizon 보다 작으면 시퀀스 생성 불가)
            if len(sub_df) <= self.lookback + max_horizon: continue
            if sub_df['close'].std() == 0: continue # 변동성 없는 데이터 제외

            # [Merge 1] 공통 데이터 병합 (Left Join)
            if not self.macro_df.empty:
                sub_df = pd.merge(sub_df, self.macro_df, on='date', how='left')
            if not self.breadth_df.empty:
                sub_df = pd.merge(sub_df, self.breadth_df, on='date', how='left')
            
            # [Merge 2] 개별 데이터 조회 및 병합 (News, Fundamentals)
            try:
                # 2-1. 뉴스 심리 (종목별)
                df_news = fetch_news_sentiment(ticker, sub_df['date'].min().strftime('%Y-%m-%d'), self.db_name)
                if not df_news.empty:
                    sub_df = pd.merge(sub_df, df_news, on='date', how='left')
                    sub_df[['sentiment_score', 'risk_keyword_cnt']] = sub_df[['sentiment_score', 'risk_keyword_cnt']].fillna(0)
                
                # 2-2. 펀더멘털 (종목별) - ffill 사용
                df_fund = fetch_fundamentals(ticker, self.db_name)
                if not df_fund.empty:
                    sub_df = pd.merge(sub_df, df_fund, on='date', how='left')
                    fund_cols = ['per', 'pbr', 'roe', 'debt_ratio']
                    cols_to_fill = [c for c in fund_cols if c in sub_df.columns]
                    sub_df[cols_to_fill] = sub_df[cols_to_fill].ffill().fillna(0)
            except Exception:
                pass # 부가 데이터 로드 실패 시 무시하고 진행

            # [Preprocessing] 결측치 보간 (Macro 주말 데이터 등)
            sub_df = sub_df.ffill().bfill()

            # [Feature Engineering] 기술적 지표 생성
            try:
                sub_df = add_technical_indicators(sub_df)
            except Exception:
                continue
            
            processed_dfs.append(sub_df)
            
        if not processed_dfs: 
            raise ValueError("[Error] 전처리된 유효 데이터가 없습니다.")
            
        full_df = pd.concat(processed_dfs)
        full_df['raw_close'] = full_df['close'] # 스케일링 전 원본 가격 보존
        
        # 사용 가능한 Feature 자동 감지
        potential_features = [
            # 1. Technical
            'log_return', 'open_ratio', 'high_ratio', 'low_ratio', 'vol_change',
            'ma_5_ratio', 'ma_20_ratio', 'ma_60_ratio', 
            'rsi', 'macd_ratio', 'bb_position',
            # 2. Macro (New)
            'us10y', 'yield_spread', 'vix_close', 'dxy_close', 'credit_spread_hy',
            # 3. Breadth (New)
            'advance_decline_ratio', 'fear_greed_index',
            # 4. Sentiment (New)
            'sentiment_score', 'risk_keyword_cnt',
            # 5. Fundamental (New)
            'per', 'pbr', 'roe'
        ]
        
        available_cols = [c for c in potential_features if c in full_df.columns]
        full_df = full_df.dropna(subset=available_cols)
        
        print(f">> Scaling Features: {len(available_cols)} columns selected")
        print(f"   (Included: {available_cols})")
        
        # Scaling (전체 데이터 기준 fitting)
        full_df[available_cols] = self.scaler.fit_transform(full_df[available_cols])

        # -------------------------------------------------------------------------
        # Step 2: 시퀀스 생성 (Sequencing)
        # -------------------------------------------------------------------------
        print(">> Generating Sequences & Labels...")
        
        # 속도 최적화를 위해 numpy 변환 후 루프 수행
        # (종목별로 group하여 처리)
        for ticker in tqdm(full_df['ticker'].unique(), desc="Sequencing"):
            sub_df = full_df[full_df['ticker'] == ticker]
            
            # 시퀀스 생성 가능 길이 확인 (위에서 했지만 dropna 등으로 줄어들었을 수 있으므로 재확인)
            if len(sub_df) <= self.lookback + max_horizon: continue

            # 메타데이터 매핑
            t_id = self.ticker_to_id.get(ticker, 0)
            s_id = self.ticker_sector_map.get(ticker, 0)

            # Numpy 변환 (속도 최적화)
            feature_vals = sub_df[available_cols].values
            raw_closes = sub_df['raw_close'].values
            
            # 루프 범위 계산: 마지막 데이터에서 max_horizon 만큼은 정답을 알 수 없음
            num_samples = len(sub_df) - self.lookback - max_horizon + 1
            if num_samples <= 0: continue

            for i in range(num_samples):
                # X: 과거 데이터 Window (Sequence)
                window = feature_vals[i : i + self.lookback]
                
                # y: 미래 예측 (Multi-Horizon)
                curr_price = raw_closes[i + self.lookback - 1]
                
                multi_labels = []
                # 동적 horizons 변수를 사용하여 라벨 생성
                for h in self.horizons:
                    future_price = raw_closes[i + self.lookback + h - 1]
                    # 등락 라벨 (1: 상승, 0: 하락/보합)
                    label = 1 if future_price > curr_price else 0
                    multi_labels.append(label)
                
                # Regression Target (예시: 가장 먼 미래 수익률)
                label_reg = 0.0
                if curr_price != 0:
                    label_reg = (raw_closes[i + self.lookback + max_horizon - 1] - curr_price) / curr_price

                X_ts_list.append(window)
                X_ticker_list.append(t_id)
                X_sector_list.append(s_id)
                y_class_list.append(multi_labels)
                y_reg_list.append(label_reg)

        # 결과 변환
        X_ts = np.array(X_ts_list)
        X_ticker = np.array(X_ticker_list)
        X_sector = np.array(X_sector_list)
        y_class = np.array(y_class_list) # Shape: (N, len(horizons))
        y_reg = np.array(y_reg_list)
        
        info = {
            "n_tickers": len(self.ticker_to_id),
            "n_sectors": len(self.sector_to_id),
            "feature_names": available_cols,
            "n_features": len(available_cols),
            "horizons": self.horizons, # 메타데이터에 사용된 horizons 기록
            "scaler": self.scaler
        }
        
        print(f"[Dataset Ready] Samples: {len(y_class)}, Features: {len(available_cols)}")
        return X_ts, X_ticker, X_sector, y_class, y_reg, info