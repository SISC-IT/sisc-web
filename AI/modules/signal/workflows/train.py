# AI/modules/signal/workflows/train.py
"""
[2단 모델 학습 파이프라인]
- Stage 1: Classification Model (상승 여부 0/1 예측) -> 필터링용
- Stage 2: Regression Model (수익률 예측) -> 랭킹/비중조절용
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
from AI.modules.signal.models.transformer.architecture import build_transformer_model, build_regression_model

def train_dual_pipeline():
    print("==================================================")
    print(" [Training] Dual-Stage Strategy Model")
    print("==================================================")

    # ------------------------------------------------------------------
    # 1. 데이터 로드 및 전처리
    # ------------------------------------------------------------------
    loader = DataLoader(lookback=60)
    
    print(">> 데이터 로딩 및 지표 생성 중...")
    raw_df = loader.load_data_from_db(start_date="2015-01-01") # 데이터 기간을 좀 늘렸습니다
    
    print(">> 데이터셋 생성 중 (Sequencing)...")
    # 수정된 data_loader는 6개의 값을 반환합니다
    X_ts, X_ticker, X_sector, y_class, y_reg, info = loader.create_dataset(raw_df)
    
    print(f"   - 총 샘플 수: {len(y_class)}")
    print(f"   - 입력 Shape: {X_ts.shape}")

    # ------------------------------------------------------------------
    # 2. 데이터 분할 (Train / Val)
    # y_class와 y_reg를 동시에 섞어서 나눕니다.
    # ------------------------------------------------------------------
    print(">> 데이터 분할 (Shuffle & Split)...")
    
    X_ts_train, X_ts_val, \
    X_tick_train, X_tick_val, \
    X_sec_train, X_sec_val, \
    y_cls_train, y_cls_val, \
    y_reg_train, y_reg_val = train_test_split(
        X_ts, X_ticker, X_sector, y_class, y_reg,
        test_size=0.2,
        shuffle=True,
        random_state=42
    )

    input_shape = (X_ts.shape[1], X_ts.shape[2])
    save_dir = os.path.join(project_root, "AI/data/weights/transformer")
    os.makedirs(save_dir, exist_ok=True)

    # ------------------------------------------------------------------
    # [Stage 1] Classification Model 학습 (Screening)
    # ------------------------------------------------------------------
    print("\n========== [Stage 1] 분류 모델 학습 시작 ==========")
    model_cls = build_transformer_model(
        input_shape=input_shape,
        n_tickers=info['n_tickers'],
        n_sectors=info['n_sectors']
    )
    
    model_cls.compile(
        optimizer="adam",
        loss="binary_crossentropy",
        metrics=["accuracy"]
    )
    
    model_cls.fit(
        x=[X_ts_train, X_tick_train, X_sec_train],
        y=y_cls_train,
        validation_data=([X_ts_val, X_tick_val, X_sec_val], y_cls_val),
        epochs=10,
        batch_size=32,
        shuffle=True
    )
    
    # 저장
    path_cls = os.path.join(save_dir, "universal_transformer_cls.keras")
    model_cls.save(path_cls)
    print(f">> Stage 1 모델 저장 완료: {path_cls}")

    # ------------------------------------------------------------------
    # [Stage 2] Regression Model 학습 (Ranking)
    # ------------------------------------------------------------------
    print("\n========== [Stage 2] 회귀 모델 학습 시작 ==========")
    model_reg = build_regression_model(
        input_shape=input_shape,
        n_tickers=info['n_tickers'],
        n_sectors=info['n_sectors']
    )
    
    # 회귀는 MSE(Mean Squared Error)로 학습
    model_reg.compile(
        optimizer="adam",
        loss="mse",
        metrics=["mae"] # Mean Absolute Error (평균 절대 오차)
    )
    
    model_reg.fit(
        x=[X_ts_train, X_tick_train, X_sec_train],
        y=y_reg_train,
        validation_data=([X_ts_val, X_tick_val, X_sec_val], y_reg_val),
        epochs=10,
        batch_size=32,
        shuffle=True
    )
    
    # 저장
    path_reg = os.path.join(save_dir, "universal_transformer_reg.keras")
    model_reg.save(path_reg)
    print(f">> Stage 2 모델 저장 완료: {path_reg}")

    # ------------------------------------------------------------------
    # 스칼라 저장 (공통)
    # ------------------------------------------------------------------
    path_scaler = os.path.join(save_dir, "universal_scaler.pkl")
    with open(path_scaler, "wb") as f:
        pickle.dump(info['scaler'], f)
    
    print("\n[All Done] 2단 모델 학습 및 저장이 완료되었습니다.")

if __name__ == "__main__":
    train_dual_pipeline()