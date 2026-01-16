# AI/modules/signal/workflows/train.py
"""
[글로벌 모델 학습 스크립트]
- DB에 있는 여러 종목(S&P500 등)의 데이터를 모두 가져와 하나의 'Universal Model'을 학습시킵니다.
- 개별 종목 모델이 아닌, 시장 전체의 패턴을 학습한 범용 모델을 생성합니다.
"""

import sys
import os
import argparse
import joblib
import numpy as np
import pandas as pd
from datetime import datetime, timedelta
from tqdm import tqdm

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model
from AI.modules.finder.selector import load_all_tickers_from_db

def run_global_training(tickers: list = None, model_type: str = "transformer", epochs: int = 30):
    print(f"=== [Global Training] {model_type} 범용 모델 학습 시작 ===")
    
    # 1. 대상 종목 설정
    if not tickers:
        print("전체 종목 리스트 조회 중...")
        tickers = load_all_tickers_from_db(verbose=False) 
    
    print(f"학습 대상 종목 수: {len(tickers)}개")

    # 2. 데이터 로드 및 병합
    end_date = datetime.now().strftime("%Y-%m-%d")
    start_date = (datetime.now() - timedelta(days=730)).strftime("%Y-%m-%d") # 최근 2년
    
    loader = SignalDataLoader(sequence_length=60)
    
    X_all = []
    y_all = []
    
    # 데이터 수집 루프
    print("1. 데이터 수집 및 전처리 중...")
    valid_ticker_count = 0
    
    for ticker in tqdm(tickers):
        try:
            # 개별 종목 데이터 로드
            df = loader.load_data(ticker, start_date, end_date)
            if df.empty or len(df) < 100:
                continue
            
            # 시퀀스 생성 (fit은 나중에 전체 데이터로 하거나, 여기서 개별 fit_transform 사용)
            # 글로벌 모델 학습 시, 각 종목별로 스케일링을 따로 해서 합치는 것(Local Scaling)이 
            # 가격대가 다른 종목들을 섞어 쓰기에 유리합니다.
            # SignalDataLoader.create_sequences는 내부적으로 fit_transform을 수행합니다.
            X, y = loader.create_sequences(df, target_col='close', prediction_horizon=1)
            
            X_all.append(X)
            y_all.append(y)
            valid_ticker_count += 1
            
        except Exception as e:
            print(f"   [Error] {ticker}: {e}")
            pass

    if valid_ticker_count == 0:
        print("[Critical] 학습할 유효 데이터가 없습니다.")
        return

    # 데이터 병합
    X_train_concat = np.concatenate(X_all, axis=0)
    y_train_concat = np.concatenate(y_all, axis=0)
    
    print(f"   - 총 학습 샘플 수: {len(X_train_concat)}개")

    # 학습/검증 데이터 분리 (Shuffle)
    indices = np.arange(len(X_train_concat))
    np.random.shuffle(indices)
    
    split_idx = int(len(indices) * 0.8)
    train_idx, val_idx = indices[:split_idx], indices[split_idx:]
    
    X_train = X_train_concat[train_idx]
    y_train = y_train_concat[train_idx]
    X_val = X_train_concat[val_idx]
    y_val = y_train_concat[val_idx]

    # 3. 모델 설정 및 빌드
    config = {
        "input_shape": (X_train.shape[1], X_train.shape[2]),
        "epochs": epochs,
        "batch_size": 1024, # 데이터가 많으므로 배치 키움
        "head_size": 256,
        "num_heads": 4,
        "dropout": 0.4,
        "learning_rate": 1e-4
    }
    
    model = get_model(model_type, config)
    model.build(input_shape=config["input_shape"])
    
    # 4. 모델 학습
    print("2. 모델 학습 진행 중...")
    history = model.train(X_train, y_train, X_val, y_val)
    
    # 5. 결과 저장 (Universal Model)
    save_dir = os.path.join(project_root, "AI", "data", "weights", model_type)
    os.makedirs(save_dir, exist_ok=True)
    
    # 파일명 고정: universal_transformer.keras
    model_path = os.path.join(save_dir, "universal_transformer.keras")
    model.save(model_path)
    
    # ★ 중요: 스케일러 저장
    # 여기서 주의! 우리는 각 종목별로 'Local Scaling'을 해서 합쳤습니다.
    # 즉, 하나의 전역 스케일러(Global Scaler)가 존재하는 게 아닙니다.
    # 추론 시에도 "해당 종목의 데이터"를 그 종목 기준으로 스케일링해서 넣어야 합니다.
    # 따라서, 사실상 저장할 '학습된 스케일러'는 없지만, 
    # 평가 코드 등에서 형식상 필요할 수 있으므로 대표 스케일러(빈 껍데기나 마지막 것)를 저장하거나
    # 추론 시 매번 새로 fit하도록 가이드해야 합니다.
    # -> 편의상 마지막 종목의 스케일러라도 저장해둡니다.
    scaler_path = os.path.join(save_dir, "universal_scaler.pkl")
    joblib.dump(loader.scaler, scaler_path)
    
    print(f"\n=== 글로벌 학습 완료 ===")
    print(f"- 모델 저장됨: {model_path}")
    print(f"- 스케일러(참고용) 저장됨: {scaler_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=10)
    args = parser.parse_args()
    
    # 기본적으로 DB의 모든 티커를 대상으로 함
    run_global_training(epochs=args.epochs)