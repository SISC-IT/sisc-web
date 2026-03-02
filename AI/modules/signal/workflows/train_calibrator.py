#ㄴ AI/modules/signal/workflows/train_calibrator.py
"""
[Meta-Labeling / Calibration Model Training]
- Stage 1: Transformer 모델이 예측한 점수(Probability)를 가져옵니다.
- Stage 2: [점수 + 섹터정보 + 시장변동성]을 입력으로 받아,
           '진짜로 수익이 날 확률'을 보정하는 모델(Gradient Boosting)을 학습합니다.
- 결과물: calibrator_model.pkl (보정 모델)
"""

import os
import sys
import pickle
import numpy as np
import pandas as pd
from tqdm import tqdm
from sklearn.model_selection import train_test_split
from sklearn.ensemble import HistGradientBoostingClassifier # 가볍고 강력한 모델 (LightGBM과 유사)
from sklearn.metrics import accuracy_score, precision_score, roc_auc_score

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.signal.models.PatchTST.architecture import build_transformer_model
from AI.modules.features.legacy.technical_features import add_technical_indicators, add_multi_timeframe_features

def train_calibrator():
    print("==================================================")
    print(" 🚀 [Stage 2] Meta-Calibrator 학습 (보정 모델)")
    print("==================================================")

    # 1. 경로 설정
    current_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
    if project_root not in sys.path:
        sys.path.append(project_root)
    weights_dir = os.path.join(project_root, "AI/data/weights/transformer")
    model_path = os.path.join(weights_dir, "multi_horizon_model.keras")
    scaler_path = os.path.join(weights_dir, "multi_horizon_scaler.pkl")
    calibrator_path = os.path.join(weights_dir, "meta_calibrator.pkl")

    if not os.path.exists(model_path):
        print("❌ Transformer 모델이 없습니다. train.py 먼저 실행하세요.")
        return

    # 2. Stage 1 모델(Transformer) 로드
    print(">> Stage 1 모델(Transformer) 로드 중...")
    stage1_model = build_transformer_model(
        input_shape=(60, 17), n_tickers=503, n_sectors=30, n_outputs=4
    )
    stage1_model.load_weights(model_path)

    with open(scaler_path, "rb") as f:
        scaler = pickle.load(f)

    # 3. 데이터 로드 (최근 3년치 - 보정 모델은 최신 트렌드를 반영하는게 좋음)
    print(">> 데이터 생성 중 (Meta-Feature 추출)...")
    loader = DataLoader(lookback=60)
    raw_df = loader.load_data_from_db(start_date="2021-01-01")

    # ----------------------------------------------------------------
    # [핵심] 메타 데이터셋 만들기
    # X_meta = [Transformer점수, 섹터ID, 변동성지표]
    # y_meta = [실제 상승 여부]
    # ----------------------------------------------------------------
    X_meta_list = []
    y_meta_list = []
    
    # 빠른 처리를 위해 종목별 루프
    tickers = raw_df['ticker'].unique()
    
    # Feature Engineering (전체 한 번에)
    # (메모리 절약을 위해 필요한 컬럼만 추출해서 처리 가능하지만, 일단 로직 유지)
    # 실제 구현 시에는 DataLoader의 기능을 활용하거나 최적화 필요
    # 여기서는 이해를 돕기 위해 직관적으로 작성
    
    # *주의: 이 과정은 Transformer Inference가 포함되어 시간이 좀 걸립니다.*
    print(f">> {len(tickers)}개 종목에 대해 1차 예측 수행 중...")
    
    # 사용할 피처 정의
    feature_cols = [
        'log_return', 'open_ratio', 'high_ratio', 'low_ratio', 'vol_change',
        'ma5_ratio', 'ma20_ratio', 'ma60_ratio', 'rsi', 'macd_ratio', 'bb_position',
        'week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change',
        'month_ma12_ratio', 'month_rsi'
    ]

    for ticker in tqdm(tickers):
        try:
            sub_df = raw_df[raw_df['ticker'] == ticker].copy().sort_values('date')
            if len(sub_df) < 100: continue
            
            # 지표 생성
            sub_df = add_technical_indicators(sub_df)
            try:
                sub_df = add_multi_timeframe_features(sub_df)
            except:
                continue
            
            sub_df.dropna(subset=feature_cols, inplace=True)
            if len(sub_df) < 60: continue

            # 전처리
            vals = sub_df[feature_cols].values
            scaled_vals = scaler.transform(vals)
            closes = sub_df['close'].values
            
            # 섹터 ID 찾기
            sector_id = loader.ticker_sector_map.get(ticker, 0)
            
            # 배치 생성을 위한 리스트
            batch_X = []
            batch_meta_feats = [] # [Sector, Volatility]
            batch_y = []

            for i in range(len(scaled_vals) - 60 - 5): # 5일 뒤 미래 확인
                # 1. Transformer 입력
                window = scaled_vals[i : i+60]
                
                # 2. 메타 피처 (보정용 힌트)
                # - 섹터 정보
                # - 최근 변동성 (표준편차) -> 변동성이 크면 AI 점수를 깎으려고
                volatility = np.std(closes[i : i+60]) / np.mean(closes[i : i+60])
                
                # 3. 정답 (3일 뒤 상승 여부)
                curr_price = closes[i+60-1]
                future_price = closes[i+60+3-1] # 3일 뒤
                label = 1 if future_price > curr_price * 1.005 else 0 # 0.5% 이상 상승
                
                batch_X.append(window)
                batch_meta_feats.append([sector_id, volatility])
                batch_y.append(label)
            
            if not batch_X: continue
            
            # Transformer 예측 (한 방에)
            batch_X = np.array(batch_X)
            t_input = np.zeros((len(batch_X), 1)) # 임베딩은 일단 0으로 (모델 내부용이라)
            s_input = np.zeros((len(batch_X), 1))
            
            preds = stage1_model.predict([batch_X, t_input, s_input], verbose=0)
            # preds shape: (N, 4) -> [1일, 3일, 5일, 7일]
            
            # [Stage 2 입력 데이터 조립]
            # 입력: [1일점수, 3일점수, 5일점수, 7일점수, 섹터ID, 변동성]
            # Transformer 점수와 메타 피처를 합침
            for j in range(len(preds)):
                transformer_scores = preds[j] # [p1, p3, p5, p7]
                meta_info = batch_meta_feats[j] # [sector, vol]
                
                # 합체!
                combined_features = np.concatenate([transformer_scores, meta_info])
                
                X_meta_list.append(combined_features)
                y_meta_list.append(batch_y[j])

        except Exception as e:
            continue

    # 4. Stage 2 모델 학습
    print("\n>> 메타 데이터셋 준비 완료.")
    X_meta = np.array(X_meta_list)
    y_meta = np.array(y_meta_list)
    
    print(f"   - 데이터 수: {len(X_meta)}")
    print(f"   - 입력 Feature: [1일확률, 3일확률, 5일확률, 7일확률, 섹터ID, 변동성]")

    X_train, X_val, y_train, y_val = train_test_split(X_meta, y_meta, test_size=0.2, random_state=42)

    print(">> 보정 모델(Gradient Boosting) 학습 시작...")
    # HistGradientBoostingClassifier: Scikit-learn의 LightGBM 버전 (빠름)
    calibrator = HistGradientBoostingClassifier(
        learning_rate=0.05, 
        max_iter=100, 
        max_depth=5,
        categorical_features=[4] # 4번째 컬럼(섹터ID)은 카테고리다! 라고 알려줌 (중요)
    )
    
    calibrator.fit(X_train, y_train)

    # 5. 평가 및 저장
    y_pred = calibrator.predict(X_val)
    acc = accuracy_score(y_val, y_pred)
    auc = roc_auc_score(y_val, calibrator.predict_proba(X_val)[:, 1])
    
    print(f"\n📊 [Result] 보정 모델 성능")
    print(f"   - Accuracy: {acc:.4f}")
    print(f"   - AUC Score: {auc:.4f}")
    print(f"   -> AUC가 0.5보다 높으면 보정 효과가 있다는 뜻입니다.")

    with open(calibrator_path, "wb") as f:
        pickle.dump(calibrator, f)
        
    print(f"\n✅ 보정 모델 저장 완료: {calibrator_path}")

if __name__ == "__main__":
    train_calibrator()