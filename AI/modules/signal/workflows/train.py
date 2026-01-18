# AI/modules/signal/workflows/train.py

import os
import sys
import pickle
import numpy as np
from datetime import datetime
from sklearn.model_selection import train_test_split  # [추가] 필수 라이브러리

# 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.signal.models.transformer.architecture import build_transformer_model

def train_pipeline():
    print("==================================================")
    print(" [Training] Universal Transformer Model 학습 시작")
    print("==================================================")

    # 1. 데이터 로드 및 전처리
    loader = DataLoader(lookback=60)
    
    print(">> 데이터 로딩 중 (DB)...")
    raw_df = loader.load_data_from_db(start_date="2020-01-01")
    
    print(">> 데이터셋 생성 중 (Sequencing)...")
    X_ts, X_ticker, X_sector, y, info = loader.create_dataset(raw_df)
    
    print(f"   - 총 샘플 수: {len(y)}")
    
    # ------------------------------------------------------------------
    # 2. Train / Validation 분리 (Random Shuffle Split)
    # 기존의 단순 슬라이싱(Slicing)은 종목별로 데이터가 쏠리는 문제가 있어 수정함.
    # ------------------------------------------------------------------
    print(">> 데이터 분할 중 (Train 80% / Val 20%)...")
    
    # 여러 배열을 동일한 인덱스 기준으로 섞어서 나눔
    X_ts_train, X_ts_val, \
    X_tick_train, X_tick_val, \
    X_sec_train, X_sec_val, \
    y_train, y_val = train_test_split(
        X_ts, X_ticker, X_sector, y,
        test_size=0.2,
        shuffle=True,       # 데이터를 무작위로 섞음 (종목 쏠림 방지)
        random_state=42     # 재현성을 위한 시드 고정
    )
    
    print(f"   - Train Set: {len(y_train)}건")
    print(f"   - Val Set  : {len(y_val)}건")

    # 3. 모델 빌드
    print(">> 모델 빌드 중...")
    # 시계열 데이터의 shape: (Lookback, Features)
    input_shape = (X_ts.shape[1], X_ts.shape[2]) 
    
    model = build_transformer_model(
        input_shape=input_shape,
        n_tickers=info['n_tickers'],
        n_sectors=info['n_sectors']
    )
    
    model.compile(
        optimizer="adam",
        loss="binary_crossentropy",
        metrics=["accuracy"]
    )
    model.summary()

    # 4. 학습 시작
    print(">> 학습 시작 (Epochs=10)...")
    history = model.fit(
        x=[X_ts_train, X_tick_train, X_sec_train],
        y=y_train,
        validation_data=([X_ts_val, X_tick_val, X_sec_val], y_val),
        epochs=10,
        batch_size=32,
        shuffle=True
    )
    
    # 5. 모델 및 스칼라 저장
    save_dir = os.path.join(project_root, "AI/data/weights/transformer")
    os.makedirs(save_dir, exist_ok=True)
    
    model_path = os.path.join(save_dir, "universal_transformer.keras")
    scaler_path = os.path.join(save_dir, "universal_scaler.pkl")
    
    model.save(model_path)
    with open(scaler_path, "wb") as f:
        pickle.dump(info['scaler'], f)
        
    print(f"\n[완료] 모델 저장됨: {model_path}")

if __name__ == "__main__":
    train_pipeline()