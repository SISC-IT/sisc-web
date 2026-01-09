import os
import sys
import numpy as np
import pandas as pd
import tensorflow as tf
import optuna
from sklearn.metrics import accuracy_score
from tensorflow.keras.callbacks import EarlyStopping

# ─────────────────────────────────────────────────────────────────────────────
#  경로 및 모듈 로드
# ─────────────────────────────────────────────────────────────────────────────
_this_file = os.path.abspath(__file__)
project_root = os.path.dirname(os.path.dirname(_this_file)) 
if project_root not in sys.path: sys.path.append(project_root)

from modules.models import build_transformer_classifier
from modules.features import FEATURES, build_features
from training.train_transformer import (
    _fetch_db_ohlcv_for_tickers, 
    load_all_tickers_from_db,
    _label_by_future_return,
    _build_sequences,
    _align_labels
)

# ─────────────────────────────────────────────────────────────────────────────
#  데이터 로드 (전역 변수로 한 번만 로드하여 속도 향상)
# ─────────────────────────────────────────────────────────────────────────────
print("[AutoML] 데이터 메모리 로딩 중...")
tickers = load_all_tickers_from_db(verbose=False)
raw_df = _fetch_db_ohlcv_for_tickers(tickers, "2023-01-01", "2024-12-31") # 기간 설정

# 전처리 미리 수행 (피처 생성까지)
grouped_data = []
for t, g in raw_df.groupby("ticker"):
    g = g.sort_values('ts_local').set_index('ts_local')
    feats = build_features(g)
    # NaN 제거는 나중에 시퀀스 만들 때 처리
    grouped_data.append(feats)

print(f"[AutoML] {len(grouped_data)}개 종목 데이터 준비 완료.")

# ─────────────────────────────────────────────────────────────────────────────
#  Objective Function (Optuna가 최적화할 대상)
# ─────────────────────────────────────────────────────────────────────────────
def objective(trial):
    # 1. 탐색할 하이퍼파라미터 정의 (Search Space)
    SEQ_LEN = trial.suggest_categorical('seq_len', [30, 60, 128]) # 윈도우 크기
    PRED_H = 7  # 예측 기간은 고정 (필요 시 변경 가능)
    
    # ★ 핵심: 매수/매도 기준 임계값도 최적화 대상에 포함
    HOLD_THR = trial.suggest_float('hold_thr', 0.002, 0.02) # 0.2% ~ 2.0% 사이 탐색
    
    # 모델 관련 파라미터
    LEARNING_RATE = trial.suggest_float('learning_rate', 1e-5, 1e-3, log=True)
    DROPOUT = trial.suggest_float('dropout', 0.1, 0.5)
    NUM_LAYERS = trial.suggest_int('num_layers', 1, 4)
    D_MODEL = trial.suggest_categorical('d_model', [64, 128, 256])
    
    # 2. 데이터셋 구성 (파라미터에 따라 라벨이 바뀌므로 매번 생성해야 함)
    X_all, y_all, r_all = [], [], []
    model_feats = [f for f in FEATURES if f != "CLOSE_RAW"]
    
    for feats in grouped_data:
        # 라벨링 다시 수행 (HOLD_THR가 바뀌므로)
        labels = _label_by_future_return(feats["CLOSE_RAW"], PRED_H, HOLD_THR)
        future_ret = (feats["CLOSE_RAW"].shift(-PRED_H) / feats["CLOSE_RAW"]) - 1.0
        
        valid = feats.notna().all(axis=1) & labels.notna() & future_ret.notna()
        f_valid = feats[valid]
        l_valid = labels[valid]
        r_valid = future_ret[valid]
        
        if len(f_valid) < SEQ_LEN: continue
            
        X_seq = _build_sequences(f_valid, model_feats, SEQ_LEN)
        y_seq = _align_labels(f_valid, l_valid, SEQ_LEN)
        r_seq = _align_labels(f_valid, r_valid, SEQ_LEN) # 수익률 시퀀스
        
        # 길이 맞춤
        min_len = min(len(X_seq), len(y_seq), len(r_seq))
        X_all.append(X_seq[:min_len])
        y_all.append(y_seq[:min_len])
        r_all.append(r_seq[:min_len])

    if not X_all:
        return -999.0 # 실패 시 매우 낮은 점수

    X = np.concatenate(X_all, axis=0)
    y = np.concatenate(y_all, axis=0).astype(int)
    r = np.concatenate(r_all, axis=0)
    
    # Train/Val 분리 (최근 20%를 검증용으로 사용)
    split_idx = int(len(X) * 0.8)
    X_train, X_val = X[:split_idx], X[split_idx:]
    y_train, y_val = y[:split_idx], y[split_idx:]
    r_val = r[split_idx:] # 검증 데이터의 실제 수익률
    
    # 클래스 가중치 계산 (HOLD 편향 방지)
    from sklearn.utils.class_weight import compute_class_weight
    classes = np.unique(y_train)
    weights = compute_class_weight(class_weight='balanced', classes=classes, y=y_train)
    class_weight_dict = {i: w for i, w in zip(classes, weights)}

    # 3. 모델 빌드 및 학습
    # (build_transformer_classifier 함수 파라미터를 수정하여 dropout 등을 받을 수 있게 해야 함. 
    #  여기서는 예시로 optimizer 설정만 보여줌)
    model = build_transformer_classifier(SEQ_LEN, X.shape[2]) 
    
    optimizer = tf.keras.optimizers.Adam(learning_rate=LEARNING_RATE)
    model.compile(optimizer=optimizer, loss='sparse_categorical_crossentropy', metrics=['accuracy'])
    
    # 속도를 위해 Epoch는 짧게 설정 (Pruning 활용 가능)
    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=5,  # 최적화 단계에서는 epoch를 줄임
        batch_size=512,
        class_weight=class_weight_dict,
        verbose=0, # 로그 숨김
        callbacks=[EarlyStopping(patience=2)]
    )
    
    # 4. 백테스팅 시뮬레이션 (검증 데이터셋 대상)
    y_pred_probs = model.predict(X_val, batch_size=512, verbose=0)
    y_pred = np.argmax(y_pred_probs, axis=1)
    
    # BUY 신호(0)에 대한 수익률 계산
    buy_signals = (y_pred == 0)
    
    if np.sum(buy_signals) == 0:
        return 0.0 # 매수 신호가 하나도 없으면 0점 처리
        
    # 평가지표: 총 수익률 (Total Return) 또는 샤프 지수
    # 여기서는 'BUY 신호 시 평균 수익률 * 적중률'을 점수로 사용해 봅니다.
    avg_return = np.mean(r_val[buy_signals])
    win_rate = np.mean(r_val[buy_signals] > 0)
    
    # 점수 산정 공식 (사용자 정의 가능)
    # 예: 평균 수익률이 높으면서도, 너무 적게 거래하지 않는 균형점 찾기
    score = avg_return * 100 
    
    # 너무 위험한 매매를 막기 위해 승률 페널티 추가 가능
    if win_rate < 0.5: 
        score *= 0.5 
        
    return score

# ─────────────────────────────────────────────────────────────────────────────
#  실행 (Main)
# ─────────────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    # 방향: maximize (수익률 최대화)
    study = optuna.create_study(direction="maximize")
    
    print("🚀 하이퍼파라미터 최적화 시작 (총 20회 시도)")
    study.optimize(objective, n_trials=20)
    
    print("\n" + "="*50)
    print("🏆 Best Trial 결과:")
    print(f"  Value (Score): {study.best_value}")
    print("  Params:")
    for key, value in study.best_params.items():
        print(f"    {key}: {value}")
    print("="*50)
    
    # 최적 파라미터 저장 (파일 등)
    # ...