# AI/modules/signal/workflows/inference.py
"""
[모델 추론 스크립트]
- 'train.py'를 통해 저장된 모델(.keras)과 스케일러(.pkl)를 로드합니다.
- 최신 데이터를 불러와 로드된 스케일러로 전처리(transform)한 뒤, 추론을 수행합니다.
- 파이프라인(Daily Routine)에서 이 모듈을 호출하여 매매 신호를 생성합니다.
"""

import sys
import os
import argparse
import joblib
import numpy as np
import pandas as pd
from datetime import datetime, timedelta

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model

def run_inference(ticker: str, model_type: str = "transformer") -> float:
    # 1. 저장된 파일 경로 설정
    save_dir = os.path.join(project_root, "AI", "data", "weights", model_type)
    weights_path = os.path.join(save_dir, f"{ticker}_{model_type}.keras")
    scaler_path = os.path.join(save_dir, f"{ticker}_{model_type}_scaler.pkl")
    
    # 파일 존재 여부 확인
    if not os.path.exists(weights_path) or not os.path.exists(scaler_path):
        print(f"[Inference][Error] 모델 또는 스케일러 파일이 없습니다.")
        print(f" - Weights: {weights_path}")
        print(f" - Scaler: {scaler_path}")
        print(" -> 먼저 train.py를 실행하여 모델을 학습시켜주세요.")
        return -1.0

    # 2. 최신 데이터 로드
    # 시퀀스 길이(60일) + 여유분 확보를 위해 150일 전 데이터부터 조회
    end_date = datetime.now().strftime("%Y-%m-%d")
    start_date = (datetime.now() - timedelta(days=150)).strftime("%Y-%m-%d")
    
    loader = SignalDataLoader(sequence_length=60)
    df = loader.load_data(ticker, start_date, end_date)
    
    if len(df) < 60:
        print(f"[Inference][Error] 추론을 위한 데이터가 부족합니다. (현재: {len(df)}개, 필요: 60개)")
        return -1.0

    try:
        # 3. 데이터 전처리 (저장된 스케일러 사용)
        print(f"1. 저장된 스케일러 로드 중... ({scaler_path})")
        loaded_scaler = joblib.load(scaler_path)
        
        # 데이터 로더의 스케일러를 로드한 것으로 교체
        loader.scaler = loaded_scaler
        
        # 피처 컬럼 선택 (학습 때와 동일한 컬럼 순서여야 함)
        features = df.select_dtypes(include=[np.number]).columns.tolist()
        
        # ★ 중요: fit_transform이 아니라 transform을 사용해야 함!
        # 학습 때의 기준(Min/Max)을 그대로 적용하기 위함
        scaled_data = loader.scaler.transform(df[features])
        
        # 마지막 60일치 데이터 추출 (오늘 기준 과거 60일)
        last_sequence = scaled_data[-60:] # (60, features)
        last_sequence = np.expand_dims(last_sequence, axis=0) # (1, 60, features) 모델 입력 형태

        # 4. 모델 로드 및 예측
        print(f"2. 저장된 모델 로드 중... ({weights_path})")
        # 설정값은 load_model에서 자동으로 복원되므로 빈 config 전달
        model = get_model(model_type, {})
        model.load(weights_path)
        
        print("3. 추론 수행 중...")
        prediction = model.predict(last_sequence)
        score = float(prediction[0][0])
        
        print(f"=== [{ticker}] 추론 결과 ===")
        print(f"▷ 상승 확률: {score*100:.2f}%")
        
        return score
        
    except Exception as e:
        print(f"[Inference][Error] 추론 중 치명적인 오류 발생: {e}")
        import traceback
        traceback.print_exc()
        return -1.0

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI 모델 추론 실행기")
    parser.add_argument("--ticker", type=str, required=True, help="추론할 종목 코드")
    parser.add_argument("--model", type=str, default="transformer", help="사용할 모델 종류")
    
    args = parser.parse_args()
    run_inference(args.ticker, args.model)