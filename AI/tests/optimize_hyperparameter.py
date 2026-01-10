# AI/tests/optimization.py
"""
[하이퍼파라미터 최적화 (AutoML)]
- Optuna를 사용하여 Transformer 모델의 하이퍼파라미터 및 매매 임계값(Threshold)을 최적화합니다.
- 'test' 성격의 실험용 스크립트이므로, 파이프라인에는 포함되지 않고 독립적으로 실행됩니다.
- 메모리 효율성을 위해 데이터 로딩은 전역 변수로 처리하여 중복 로드를 방지합니다.
"""

import sys
import os
import argparse
import optuna
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.utils.class_weight import compute_class_weight
from tensorflow.keras.callbacks import EarlyStopping

# ─────────────────────────────────────────────────────────────────────────────
#  경로 및 모듈 로드
# ─────────────────────────────────────────────────────────────────────────────
# 프로젝트 루트 경로 추가 (절대 경로 import 위함)
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model
# (필요 시) 티커 리스트 로드 함수 임포트
# from AI.modules.finder.selector import load_all_tickers 

# ─────────────────────────────────────────────────────────────────────────────
#  전역 데이터 로드 (Global Data Loading)
#  - Optuna Trial마다 DB 접속을 반복하지 않기 위해 한 번만 로드합니다.
# ─────────────────────────────────────────────────────────────────────────────
print("[AutoML] 데이터 메모리 로딩 중...")

# 1. 대상 티커 선정 (예시: S&P 500 상위 종목 등)
# 실제로는 DB나 파일에서 가져오는 로직 사용
target_tickers = ["AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "META"] 

# 2. 데이터 로드 및 전처리 (피처 생성)
# 학습 기간 설정
start_date = "2023-01-01"
end_date = "2024-12-31"

loader = SignalDataLoader(sequence_length=60) # 기본 시퀀스 길이는 나중에 변경됨
grouped_data = []

for ticker in target_tickers:
    try:
        # load_data 내부에서 fetch_ohlcv + add_technical_indicators 수행
        df = loader.load_data(ticker, start_date, end_date)
        if not df.empty and len(df) > 100:
            grouped_data.append(df)
    except Exception as e:
        print(f"[Warning] {ticker} 데이터 로드 실패: {e}")

print(f"[AutoML] {len(grouped_data)}개 종목 데이터 준비 완료.")


# ─────────────────────────────────────────────────────────────────────────────
#  Helper Functions (라벨링 및 시퀀스 생성)
#  - Trial 내부에서 동적으로 호출됩니다.
# ─────────────────────────────────────────────────────────────────────────────
def _label_by_future_return(close_prices: pd.Series, horizon: int, threshold: float) -> pd.Series:
    """미래 수익률 기반 라벨링 (0: BUY, 1: HOLD)"""
    # future_return = (미래 가격 / 현재 가격) - 1
    future_ret = (close_prices.shift(-horizon) / close_prices) - 1.0
    
    # 임계값 이상이면 BUY(0), 아니면 HOLD(1)
    # (참고: 일반적으로 1이 Positive Class지만, 기존 코드 유지)
    labels = np.where(future_ret > threshold, 0, 1)
    
    # 마지막 horizon 기간은 NaN 처리 (미래 데이터 없음)
    labels[-horizon:] = -1 
    return pd.Series(labels, index=close_prices.index), future_ret

def _build_sequences(df: pd.DataFrame, feature_cols: list, seq_len: int) -> np.ndarray:
    """시퀀스 데이터 생성"""
    data = df[feature_cols].values
    num_samples = len(data) - seq_len + 1
    
    X = []
    for i in range(num_samples):
        X.append(data[i : i+seq_len])
        
    return np.array(X)

def _align_labels(labels: pd.Series, seq_len: int) -> np.ndarray:
    """시퀀스에 맞춰 라벨 정렬"""
    # 시퀀스의 마지막 시점에 해당하는 라벨을 가져옴
    # 입력 시퀀스: t ~ t+seq_len-1
    # 예측 대상: t+seq_len-1 시점에서의 판단
    return labels.iloc[seq_len-1:].values


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
    NUM_LAYERS = trial.suggest_int('num_blocks', 1, 4) # layer -> blocks 용어 통일
    HEAD_SIZE = trial.suggest_categorical('head_size', [64, 128, 256]) # d_model 대신 head_size 사용
    NUM_HEADS = trial.suggest_int('num_heads', 2, 8)
    FF_DIM = trial.suggest_int('ff_dim', 2, 8)
    
    # 2. 데이터셋 구성 (파라미터에 따라 라벨이 바뀌므로 매번 생성해야 함)
    X_all, y_all, r_all = [], [], []
    
    # 피처 컬럼 선택 (Date, Ticker 등 제외하고 수치형만)
    if not grouped_data:
        return -999.0
        
    sample_df = grouped_data[0]
    feature_cols = sample_df.select_dtypes(include=[np.number]).columns.tolist()
    
    # 데이터셋 생성 루프
    for df in grouped_data:
        # (1) 라벨링 다시 수행 (HOLD_THR가 바뀌므로)
        labels, future_ret = _label_by_future_return(df["close"], PRED_H, HOLD_THR)
        
        # (2) 유효 데이터 필터링
        # 피처, 라벨, 미래수익률 모두 NaN이 아닌 구간만 사용
        valid_mask = df[feature_cols].notna().all(axis=1) & (labels != -1) & future_ret.notna()
        
        df_valid = df[valid_mask]
        labels_valid = labels[valid_mask]
        ret_valid = future_ret[valid_mask]
        
        if len(df_valid) < SEQ_LEN:
            continue
            
        # (3) 스케일링 (간소화를 위해 각 종목별 MinMax 적용)
        # 주의: 엄밀한 검증을 위해서는 Train/Val 분리 후 스케일링해야 함
        from sklearn.preprocessing import MinMaxScaler
        scaler = MinMaxScaler()
        scaled_features = scaler.fit_transform(df_valid[feature_cols])
        df_scaled = pd.DataFrame(scaled_features, columns=feature_cols, index=df_valid.index)
        
        # (4) 시퀀스 생성
        X_seq = _build_sequences(df_scaled, feature_cols, SEQ_LEN)
        y_seq = _align_labels(labels_valid, SEQ_LEN)
        r_seq = _align_labels(ret_valid, SEQ_LEN) # 수익률 시퀀스
        
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
    
    # Train/Val 분리 (최근 20%를 검증용으로 사용 - 시계열 고려)
    # 여기서는 단순 Shuffle 없이 뒤쪽 데이터를 Val로 사용
    split_idx = int(len(X) * 0.8)
    X_train, X_val = X[:split_idx], X[split_idx:]
    y_train, y_val = y[:split_idx], y[split_idx:]
    r_val = r[split_idx:] # 검증 데이터의 실제 수익률
    
    # 클래스 가중치 계산 (HOLD 편향 방지)
    classes = np.unique(y_train)
    if len(classes) < 2:
        return -999.0 # 클래스가 하나뿐이면 학습 불가
        
    weights = compute_class_weight(class_weight='balanced', classes=classes, y=y_train)
    class_weight_dict = {i: w for i, w in zip(classes, weights)}

    # 3. 모델 빌드 및 학습
    # get_model 팩토리 함수 사용
    config = {
        "input_shape": (SEQ_LEN, X.shape[2]),
        "head_size": HEAD_SIZE,
        "num_heads": NUM_HEADS,
        "ff_dim": FF_DIM,
        "num_blocks": NUM_LAYERS,
        "mlp_units": [128], # 고정값 또는 탐색 가능
        "dropout": DROPOUT,
        "mlp_dropout": DROPOUT,
        "learning_rate": LEARNING_RATE
    }
    
    model_wrapper = get_model("transformer", config)
    model_wrapper.build(config["input_shape"])
    
    # 속도를 위해 Epoch는 짧게 설정 (Pruning 활용 가능)
    # fit() 메서드 직접 호출 대신 wrapper의 train 사용
    history = model_wrapper.train(
        X_train, y_train,
        X_val=X_val, y_val=y_val,
        epochs=5,          # 최적화 단계에서는 epoch를 줄임
        batch_size=512,
        class_weight=class_weight_dict,
        callbacks=[EarlyStopping(patience=2, monitor='val_loss')]
    )
    
    # 4. 백테스팅 시뮬레이션 (검증 데이터셋 대상)
    # wrapper의 predict 메서드 사용 (확률값 반환)
    # TransformerSignalModel은 기본적으로 이진 분류(sigmoid) -> 0.5 기준
    # 하지만 위 로직은 Sparse Categorical (다중분류) 로직을 따르고 있으므로,
    # 모델 아키텍처가 이진분류(sigmoid)인지 다중분류(softmax)인지에 따라 처리가 다릅니다.
    # 현재 architecture.py는 sigmoid(이진분류)로 되어 있으므로 이에 맞게 조정합니다.
    
    y_pred_probs = model_wrapper.predict(X_val) # (N, 1) 형태
    
    # 0: BUY, 1: HOLD 라고 가정했으나, 
    # Sigmoid 출력 1에 가까울수록 "상승(Positive)" -> BUY
    # Sigmoid 출력 0에 가까울수록 "하락/보합(Negative)" -> HOLD
    # 따라서, score > 0.5 이면 BUY로 간주
    
    buy_signals = (y_pred_probs.flatten() > 0.5)
    
    if np.sum(buy_signals) == 0:
        return 0.0 # 매수 신호가 하나도 없으면 0점 처리
        
    # 평가지표: 'BUY 신호 시 평균 수익률 * 적중률'
    # r_val은 실제 수익률
    
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
    # 방향: maximize (수익률 점수 최대화)
    study = optuna.create_study(direction="maximize")
    
    print("🚀 하이퍼파라미터 최적화 시작 (총 20회 시도)")
    # n_jobs=1 (TensorFlow 충돌 방지)
    study.optimize(objective, n_trials=20, n_jobs=1)
    
    print("\n" + "="*50)
    print("🏆 Best Trial 결과:")
    print(f"  Value (Score): {study.best_value}")
    print("  Params:")
    for key, value in study.best_params.items():
        print(f"    {key}: {value}")
    print("="*50)
    
    # 최적 파라미터 저장 (선택 사항)
    # import json
    # with open('best_params.json', 'w') as f:
    #     json.dump(study.best_params, f)