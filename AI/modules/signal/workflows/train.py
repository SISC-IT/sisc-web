# AI/modules/signal/workflows/train.py
"""
[모델 학습 스크립트]
- 특정 종목(Ticker)에 대한 데이터를 로드하고 AI 모델을 학습시킵니다.
- 학습된 모델 가중치(.keras)와 데이터 스케일러(.pkl)를 함께 저장합니다.
"""

import sys
import os
import argparse
import joblib  # 스케일러 저장을 위해 추가
import numpy as np
from datetime import datetime, timedelta

# 프로젝트 루트 경로 추가 (절대 경로 import 위함)
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model

def run_training(ticker: str, model_type: str = "transformer", epochs: int = 50):
    print(f"=== [{ticker}] {model_type} 모델 학습 시작 ===")
    
    # 1. 데이터 로드 설정
    # 학습 기간: 최근 2년 데이터 사용
    end_date = datetime.now().strftime("%Y-%m-%d")
    start_date = (datetime.now() - timedelta(days=730)).strftime("%Y-%m-%d")
    
    # 2. 데이터 로더 초기화
    loader = SignalDataLoader(sequence_length=60)
    
    print(f"1. 데이터 로드 중... ({start_date} ~ {end_date})")
    df = loader.load_data(ticker, start_date, end_date)
    
    if df.empty or len(df) < 100:
        print(f"[Error] 데이터가 부족하여 학습을 중단합니다. (행 수: {len(df)})")
        return

    # 3. 데이터셋 생성 (Sequence)
    # 내부적으로 loader.scaler가 데이터에 맞게 학습(fit)됩니다.
    print("2. 데이터 전처리 및 시퀀스 생성 중...")
    X, y = loader.create_sequences(df, target_col='close', prediction_horizon=1)
    
    # 학습/검증 데이터 분리 (80:20)
    split_idx = int(len(X) * 0.8)
    X_train, y_train = X[:split_idx], y[:split_idx]
    X_val, y_val = X[split_idx:], y[split_idx:]
    
    print(f"   - 학습 데이터: {X_train.shape}, 검증 데이터: {X_val.shape}")

    # 4. 모델 설정 및 빌드
    config = {
        "input_shape": (X_train.shape[1], X_train.shape[2]),
        "epochs": epochs,
        "batch_size": 32,
        "head_size": 256,
        "num_heads": 4,
        "dropout": 0.4
    }
    
    model = get_model(model_type, config)
    model.build(input_shape=config["input_shape"])
    
    # 5. 모델 학습
    print("3. 모델 학습 진행 중...")
    history = model.train(X_train, y_train, X_val, y_val)
    
    # 6. 결과 저장 (모델 + 스케일러)
    # 저장 경로: AI/data/weights/{model_type}/
    save_dir = os.path.join(project_root, "AI", "data", "weights", model_type)
    os.makedirs(save_dir, exist_ok=True)
    
    # (1) 모델 가중치 저장
    model_path = os.path.join(save_dir, f"{ticker}_{model_type}.keras")
    model.save(model_path)
    
    # (2) 스케일러 저장 (매우 중요! 추론 때 필요함)
    scaler_path = os.path.join(save_dir, f"{ticker}_{model_type}_scaler.pkl")
    joblib.dump(loader.scaler, scaler_path)
    
    print(f"\n=== 학습 완료 및 저장 성공 ===")
    print(f"- 모델 파일: {model_path}")
    print(f"- 스케일러 : {scaler_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI 모델 학습 실행기")
    parser.add_argument("--ticker", type=str, required=True, help="학습할 종목 코드 (예: AAPL)")
    parser.add_argument("--model", type=str, default="transformer", help="사용할 모델 종류")
    parser.add_argument("--epochs", type=int, default=30, help="학습 에폭 수")
    
    args = parser.parse_args()
    
    run_training(args.ticker, args.model, args.epochs)