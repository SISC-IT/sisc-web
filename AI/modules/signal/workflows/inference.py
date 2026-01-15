"""
[모델 추론 스크립트]
- 학습된 '글로벌 범용 모델(Universal Model)'을 로드합니다.
- 특정 종목의 최신 데이터를 DB에서 조회합니다.
- 데이터의 최근 패턴을 0~1로 정규화(Local Scaling)하여 모델에 입력합니다.
- 최종적으로 익일 주가 상승 확률을 예측하여 반환합니다.
"""

import sys
import os
import argparse
import joblib
import numpy as np
import pandas as pd
from datetime import datetime, timedelta

# ─────────────────────────────────────────────────────────────────────────────
#  경로 설정 (프로젝트 루트 추가)
# ─────────────────────────────────────────────────────────────────────────────
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model

# ─────────────────────────────────────────────────────────────────────────────
#  추론 함수
# ─────────────────────────────────────────────────────────────────────────────
def run_inference(ticker: str, model_type: str = "transformer") -> float:
    print(f"=== [Inference] {ticker} ({model_type}) 추론 시작 ===")

    # 1. 모델 파일 경로 설정 (Universal Model)
    # train.py에서 저장한 'universal_transformer.keras'를 바라봅니다.
    save_dir = os.path.join(project_root, "AI", "data", "weights", model_type)
    weights_path = os.path.join(save_dir, "universal_transformer.keras")
    
    # 모델 파일 존재 확인
    if not os.path.exists(weights_path):
        print(f"[Err] 모델 파일이 존재하지 않습니다.")
        print(f"      Path: {weights_path}")
        print("      -> 먼저 'train.py'를 실행하여 범용 모델을 학습시켜 주세요.")
        return -1.0

    # 2. 최신 데이터 로드
    # 시퀀스 길이(60일) + 보조지표 계산용 여유분(SMA200 등 고려) 확보를 위해 300일 전부터 조회
    end_date = datetime.now().strftime("%Y-%m-%d")
    start_date = (datetime.now() - timedelta(days=300)).strftime("%Y-%m-%d")
    
    loader = SignalDataLoader(sequence_length=60)
    
    try:
        df = loader.load_data(ticker, start_date, end_date)
    except Exception as e:
        print(f"[Err] 데이터 로드 실패: {e}")
        return -1.0
    
    # 데이터 부족 시 중단 (최소 60일 + 지표 계산분 필요)
    if df.empty or len(df) < 60:
        print(f"[Err] 추론을 위한 데이터가 부족합니다. (현재: {len(df)} row)")
        return -1.0

    try:
        # 3. 데이터 전처리 (Local Scaling)
        # ★ 중요: 범용 모델은 '패턴'을 보므로, 현재 주가 범위에 맞춰 새로 스케일링합니다.
        # 저장된 스케일러를 쓰지 않고, 현재 데이터로 fit_transform 합니다.
        
        # (1) 학습에 사용되는 수치형 컬럼만 선택
        features = df.select_dtypes(include=[np.number]).columns.tolist()
        
        # (2) 스케일링 (0~1 범위로 정규화)
        # 전체 기간 데이터를 기준으로 fit하되, 입력은 마지막 60일치만 사용됨
        scaled_data = loader.scaler.fit_transform(df[features])
        
        # (3) 모델 입력 형태 생성 (Samples, TimeSteps, Features)
        # 마지막 60일치 데이터 추출
        last_sequence = scaled_data[-60:] 
        
        # 차원 확장: (60, features) -> (1, 60, features)
        input_tensor = np.expand_dims(last_sequence, axis=0)

        # 4. 모델 로드 및 구조 빌드
        # Config는 load_weights 시 자동 적용되거나, 저장된 포맷에 따라 다르나
        # 여기서는 get_model로 껍데기를 만들고 가중치를 입히는 방식을 사용
        model = get_model(model_type, {
            # 입력 형태를 명시하여 모델 구조를 확정합니다.
            "input_shape": (input_tensor.shape[1], input_tensor.shape[2]),
            # 아래 파라미터는 로드 시 덮어써지거나, build용으로 사용됨
            "head_size": 256, "num_heads": 4, "ff_dim": 4,
            "num_blocks": 4, "mlp_units": [128], "dropout": 0.4
        })
        
        # 모델 빌드 (가중치 로드 전 필수)
        model.build(input_shape=(None, input_tensor.shape[1], input_tensor.shape[2]))
        
        # 가중치 로드
        model.load(weights_path)
        
        # 5. 예측 수행
        # predict 반환값: [[0.732]] 형태의 확률값 (Sigmoid 출력)
        prediction = model.predict(input_tensor)
        score = float(prediction[0][0])
        
        print(f"Result ▷ [{ticker}] 상승 확률: {score*100:.2f}%")
        
        return score
        
    except Exception as e:
        print(f"[Err] 추론 중 치명적인 오류 발생: {e}")
        import traceback
        traceback.print_exc()
        return -1.0

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI 모델 추론 실행기")
    parser.add_argument("--ticker", type=str, required=True, help="추론할 종목 코드 (예: AAPL)")
    parser.add_argument("--model", type=str, default="transformer", help="사용할 모델 종류")
    
    args = parser.parse_args()
    
    run_inference(args.ticker, args.model)