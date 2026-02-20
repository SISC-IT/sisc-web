# AI/modules/signal/workflows/inference.py
"""
[모델 추론 스크립트]
- 학습된 'Universal Transformer' 모델을 로드합니다.
- 특정 종목의 최신 데이터를 DB에서 조회하고, 학습과 동일한 전처리를 수행합니다.
- (시계열 데이터 + 티커 ID + 섹터 ID) 3가지 입력을 모델에 넣어 상승 확률을 예측합니다.
"""

import sys
import os
import argparse
import pickle
import numpy as np
import pandas as pd
import tensorflow as tf
from datetime import datetime, timedelta

# ─────────────────────────────────────────────────────────────────────────────
#  경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.features.legacy.technical_features import add_technical_indicators

# ─────────────────────────────────────────────────────────────────────────────
#  추론 함수
# ─────────────────────────────────────────────────────────────────────────────
def run_inference(ticker: str, model_type: str = "transformer") -> float:
    print(f"=== [Inference] {ticker} 예측 시작 ===")

    # 1. 경로 설정 및 파일 확인
    base_dir = os.path.join(project_root, "AI", "data", "weights", "trasformer" ,"prod" , model_type)
    model_path = os.path.join(base_dir, "multi_horizon_model_prod.keras")
    scaler_path = os.path.join(base_dir, "multi_horizon_scaler_prod.pkl")
    
    if not os.path.exists(model_path) or not os.path.exists(scaler_path):
        print(f"[Err] 학습된 모델이나 스케일러가 없습니다.")
        print(f"      - Model: {model_path}")
        print(f"      - Scaler: {scaler_path}")
        return -1.0

    # 2. 메타데이터(ID 매핑) 준비
    # DataLoader를 초기화하면 DB에서 종목/섹터 정보를 읽어와 ID 매핑을 만듭니다.
    print(">> 메타데이터 로드 중...")
    loader = DataLoader(lookback=60)
    
    # 해당 종목의 ID 찾기 (없으면 0: Unknown)
    ticker_id = loader.ticker_to_id.get(ticker, 0)
    sector_id = loader.ticker_sector_map.get(ticker, 0)
    
    print(f"   - Ticker ID: {ticker_id}")
    print(f"   - Sector ID: {sector_id}")

    # 3. DB에서 최신 데이터 조회
    # 기술적 지표(MA60, RSI 등) 계산을 위해 최소 200일치 정도의 여유 데이터를 가져옵니다.
    print(f">> [{ticker}] 시세 데이터 조회 중...")
    start_date = (datetime.now() - timedelta(days=300)).strftime("%Y-%m-%d")
    
    conn = get_db_conn()
    query = """
        SELECT date, ticker, open, high, low, close, volume, adjusted_close
        FROM public.price_data
        WHERE ticker = %s AND date >= %s
        ORDER BY date ASC
    """
    df = pd.read_sql(query, conn, params=(ticker, start_date))
    conn.close()
    
    # 데이터 유효성 체크
    if df.empty or len(df) < 100:
        print(f"[Err] 데이터가 너무 적습니다. (Rows: {len(df)})")
        return -1.0
        
    df['date'] = pd.to_datetime(df['date'])

    # 4. 전처리 (Feature Engineering + Scaling)
    try:
        # (1) 기술적 지표 추가
        df = add_technical_indicators(df)
        
        # (2) 최근 60일 데이터만 잘라내기 전에, 스케일링을 위해 필요한 컬럼 선택
        # 학습 때 사용한 feature_cols와 순서가 정확히 일치해야 함!
        feature_cols = [
            'open', 'high', 'low', 'close', 'volume',
            'ma5', 'ma20', 'ma60', 
            'rsi', 'macd', 'signal_line', 
            'upper_band', 'lower_band', 'vol_change'
        ]
        
        # NaN 제거 (지표 계산 초반부)
        df_clean = df.dropna(subset=feature_cols)
        
        if len(df_clean) < 60:
            print("[Err] 지표 계산 후 유효 데이터가 60일 미만입니다.")
            return -1.0

        # (3) 저장된 스케일러 로드 및 적용
        with open(scaler_path, "rb") as f:
            loaded_scaler = pickle.load(f)
            
        # 전체를 변환하고 마지막 60개만 씀
        scaled_data = loaded_scaler.transform(df_clean[feature_cols])
        
        # (4) 입력 텐서 생성
        # Shape: (1, 60, 14)
        last_sequence = scaled_data[-60:]
        input_ts = np.expand_dims(last_sequence, axis=0)
        
        # Shape: (1, 1)
        input_ticker = np.array([[ticker_id]])
        input_sector = np.array([[sector_id]])

    except Exception as e:
        print(f"[Err] 전처리 중 오류 발생: {e}")
        return -1.0

    # 5. 모델 로드 및 추론
    try:
        print(">> 모델 로드 및 추론...")
        # 커스텀 객체나 설정 없이 저장된 Keras 모델 통째로 로드
        model = tf.keras.models.load_model(model_path)
        
        # predict는 리스트 형태로 입력을 받습니다: [시계열, 티커, 섹터]
        prediction = model.predict([input_ts, input_ticker, input_sector], verbose=0)
        
        score = float(prediction[0][0])
        print(f"\n✅ Result ▷ [{ticker}] 상승 확률: {score*100:.2f}%")
        
        return score

    except Exception as e:
        print(f"[Err] 추론 실패: {e}")
        return -1.0

# ─────────────────────────────────────────────────────────────────────────────
#  실행부
# ─────────────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI 모델 추론기")
    parser.add_argument("ticker", type=str, help="종목 코드 (예: AAPL)")
    
    args = parser.parse_args()
    
    # 실행
    run_inference(args.ticker)