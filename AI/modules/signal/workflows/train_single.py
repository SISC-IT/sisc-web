# AI/modules/signal/workflows/train_single.py
"""
[단일 모델 학습 파이프라인]
- Dual Model이 부담스럽거나, 분류(상승/하락) 모델만 빠르게 실험하고 싶을 때 사용합니다.
- 변경된 DataLoader(6개 반환)에 맞춰 코드를 수정했습니다.
"""

import os
import sys
import pickle
import numpy as np
from sklearn.model_selection import train_test_split

# 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.signal.models.transformer.architecture import build_transformer_model

def train_pipeline():
    print("==================================================")
    print(" [Training] Single Transformer Model (Class Only)")
    print("==================================================")

    # 1. 데이터 로드
    loader = DataLoader(lookback=60)
    print(">> 데이터 로딩 및 지표 생성 중...")
    raw_df = loader.load_data_from_db(start_date="2020-01-01")
    
    print(">> 데이터셋 생성 중...")
    # [수정 포인트] 반환값이 6개로 늘어났으므로, y_reg는 여기서 받아서 무시(_)하거나 안 씁니다.
    X_ts, X_ticker, X_sector, y_class, _, info = loader.create_dataset(raw_df)
    
    print(f"   - 총 샘플 수: {len(y_class)}")

    # 2. 데이터 분할
    print(">> 데이터 분할 (Train 80% / Val 20%)...")
    
    # y_class(분류 라벨)만 사용하여 분할
    X_ts_train, X_ts_val, \
    X_tick_train, X_tick_val, \
    X_sec_train, X_sec_val, \
    y_train, y_val = train_test_split(
        X_ts, X_ticker, X_sector, y_class,
        test_size=0.2,
        shuffle=True,
        random_state=42
    )
    
    # 3. 모델 빌드 (분류 모델)
    print(">> 모델 빌드 중...")
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
    
    # 5. 저장
    save_dir = os.path.join(project_root, "AI/data/weights/transformer")
    os.makedirs(save_dir, exist_ok=True)
    
    # 파일명은 train_dual.py와 겹치지 않게 'single'이라고 하거나 그대로 둬도 됨
    # 여기서는 호환성을 위해 기존 이름 유지 (덮어씌워짐 주의)
    model_path = os.path.join(save_dir, "universal_transformer.keras")
    scaler_path = os.path.join(save_dir, "universal_scaler.pkl")
    
    model.save(model_path)
    with open(scaler_path, "wb") as f:
        pickle.dump(info['scaler'], f)
        
    print(f"\n[완료] 단일 모델 저장됨: {model_path}")

if __name__ == "__main__":
    train_pipeline()