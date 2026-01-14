# AI/tests/optimization.py
"""
[하이퍼파라미터 최적화 (AutoML) - Stable & Strict Version]
- Optuna를 사용하여 Transformer 모델 최적화
- [수정 내역]:
  1. 피처 NaN 결측치 엄격한 필터링
  2. Class Weight 계산 시 int 타입 보장
  3. Validation 데이터 시퀀스 생성 시 Train 후반부 Context 활용 (평가 데이터 보존)
  4. 점수 산정 시 Cooldown(중복 진입 방지) 적용으로 현실성 확보
  5. 예외 발생 시 안전한 자원 해제 로직
"""

import sys
import os
import gc
import optuna
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.utils.class_weight import compute_class_weight
from tensorflow.keras.callbacks import EarlyStopping
from tensorflow.keras import backend as K
from sklearn.preprocessing import MinMaxScaler

# ─────────────────────────────────────────────────────────────────────────────
#  1. 경로 및 모듈 로드
# ─────────────────────────────────────────────────────────────────────────────
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
sys.path.append(project_root)

from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model

# ─────────────────────────────────────────────────────────────────────────────
#  2. 전역 데이터 로드
# ─────────────────────────────────────────────────────────────────────────────
print("[AutoML] 데이터 메모리 로딩 중...")

target_tickers = ["AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "META"] 
start_date = "2023-01-01"
end_date = "2024-12-31"

loader = SignalDataLoader(sequence_length=60)
grouped_data = []

for ticker in target_tickers:
    try:
        df = loader.load_data(ticker, start_date, end_date)
        if not df.empty and len(df) > 300:
            grouped_data.append(df)
    except Exception as e:
        print(f"[Warning] {ticker} 데이터 로드 실패: {e}")

print(f"[AutoML] {len(grouped_data)}개 종목 데이터 준비 완료.")


# ─────────────────────────────────────────────────────────────────────────────
#  3. Helper Functions
# ─────────────────────────────────────────────────────────────────────────────
def _label_by_future_return(close_prices: pd.Series, horizon: int, threshold: float) -> tuple[pd.Series, pd.Series]:
    """
    [수정 6] 라벨 타입 명시 (int32)
    """
    future_ret = (close_prices.shift(-horizon) / close_prices) - 1.0
    
    # 1=BUY, 0=HOLD
    labels = np.where(future_ret > threshold, 1, 0).astype(np.int32)
    
    # 마지막 horizon 기간 -1 처리
    labels[-horizon:] = -1 
    
    return pd.Series(labels, index=close_prices.index), future_ret

def _build_sequences(data: np.ndarray, seq_len: int) -> np.ndarray:
    num_samples = len(data) - seq_len + 1
    if num_samples <= 0:
        return np.array([])
    
    X = []
    for i in range(num_samples):
        X.append(data[i : i+seq_len])
        
    return np.array(X)

# ─────────────────────────────────────────────────────────────────────────────
#  4. Objective Function
# ─────────────────────────────────────────────────────────────────────────────
def objective(trial):
    # (0) 메모리 정리
    K.clear_session()
    gc.collect()
    model_wrapper = None # [수정 3] 초기화

    # (1) 하이퍼파라미터 정의
    SEQ_LEN = trial.suggest_categorical('seq_len', [30, 60, 90])
    PRED_H = 5
    HOLD_THR = trial.suggest_float('hold_thr', 0.01, 0.04) 
    
    LEARNING_RATE = trial.suggest_float('learning_rate', 1e-4, 5e-3, log=True)
    DROPOUT = trial.suggest_float('dropout', 0.1, 0.4)
    NUM_BLOCKS = trial.suggest_int('num_blocks', 1, 3)
    HEAD_SIZE = trial.suggest_categorical('head_size', [64, 128])
    NUM_HEADS = trial.suggest_categorical('num_heads', [2, 4])
    FF_DIM = trial.suggest_categorical('ff_dim', [64, 128, 256])
    
    PRED_THR = trial.suggest_float('pred_thr', 0.4, 0.7)

    # (2) 데이터셋 구성
    if not grouped_data:
        return -999.0
    
    X_train_list, y_train_list = [], []
    X_val_list, y_val_list, r_val_list = [], [], []

    sample_df = grouped_data[0]
    feature_cols = sample_df.select_dtypes(include=[np.number]).columns.tolist()
    
    for df in grouped_data:
        labels, future_ret = _label_by_future_return(df["close"], PRED_H, HOLD_THR)
        
        # [수정 1] 피처 NaN 포함 여부까지 엄격하게 검사
        valid_mask = (
            (labels != -1) & 
            future_ret.notna() & 
            df[feature_cols].notna().all(axis=1)
        )
        
        df_valid = df.loc[valid_mask]
        labels_valid = labels[valid_mask]
        ret_valid = future_ret[valid_mask]
        
        if len(df_valid) < SEQ_LEN + 20: # 여유분 포함
            continue
            
        # Time Split (8:2)
        split_idx = int(len(df_valid) * 0.8)
        
        train_df = df_valid.iloc[:split_idx]
        val_df = df_valid.iloc[split_idx:]
        
        train_labels = labels_valid.iloc[:split_idx]
        val_labels = labels_valid.iloc[split_idx:]
        val_rets = ret_valid.iloc[split_idx:]
        
        # 스케일링
        scaler = MinMaxScaler()
        train_feat = train_df[feature_cols].values.astype(np.float32)
        scaler.fit(train_feat) # Train Fit
        
        # ─── Train Data 생성 ───
        train_scaled = scaler.transform(train_feat)
        X_train_seq = _build_sequences(train_scaled, SEQ_LEN)
        y_train_seq = train_labels.values[SEQ_LEN-1:] # 시퀀스 끝나는 시점의 라벨
        
        min_len_train = min(len(X_train_seq), len(y_train_seq))
        if min_len_train > 0:
            X_train_list.append(X_train_seq[:min_len_train])
            y_train_list.append(y_train_seq[:min_len_train])
            
        # ─── Val Data 생성 (Context Prefix 적용) [수정 4] ───
        # 검증 데이터의 첫 날부터 예측하기 위해 Train의 마지막 부분을 가져옴
        lookback = SEQ_LEN - 1
        if len(train_df) >= lookback:
            # Train 뒷부분 + Val 전체
            val_input_df = pd.concat([train_df.iloc[-lookback:], val_df], axis=0)
            val_input_feat = val_input_df[feature_cols].values.astype(np.float32)
            val_scaled = scaler.transform(val_input_feat)
            
            X_val_seq = _build_sequences(val_scaled, SEQ_LEN)
            # Context를 붙였으므로, 생성된 시퀀스 개수는 정확히 val_df 길이와 같음
            # 따라서 slicing 불필요 (단, 길이가 맞는지 min으로 안전장치)
            
            y_val_seq = val_labels.values
            r_val_seq = val_rets.values
        else:
            # Train이 너무 짧은 경우 (예외적) -> 기존 방식 fallback
            val_feat = val_df[feature_cols].values.astype(np.float32)
            val_scaled = scaler.transform(val_feat)
            X_val_seq = _build_sequences(val_scaled, SEQ_LEN)
            y_val_seq = val_labels.values[SEQ_LEN-1:]
            r_val_seq = val_rets.values[SEQ_LEN-1:]
        
        min_len_val = min(len(X_val_seq), len(y_val_seq), len(r_val_seq))
        if min_len_val > 0:
            X_val_list.append(X_val_seq[:min_len_val])
            y_val_list.append(y_val_seq[:min_len_val])
            r_val_list.append(r_val_seq[:min_len_val])

    if not X_train_list or not X_val_list:
        return -999.0

    # (3) 데이터 병합 및 가중치 계산
    X_train = np.concatenate(X_train_list, axis=0)
    # [수정 2] int32로 명시적 변환
    y_train = np.concatenate(y_train_list, axis=0).astype(np.int32)
    
    X_val = np.concatenate(X_val_list, axis=0)
    y_val = np.concatenate(y_val_list, axis=0).astype(np.int32)
    r_val = np.concatenate(r_val_list, axis=0)
    
    classes = np.unique(y_train)
    class_weight_dict = None
    if len(classes) >= 2:
        weights = compute_class_weight(class_weight='balanced', classes=classes, y=y_train)
        # [수정 2] Dict key: int, value: float 보장
        class_weight_dict = {int(c): float(w) for c, w in zip(classes, weights)}
    
    # (4) 모델 학습
    config = {
        "input_shape": (SEQ_LEN, len(feature_cols)),
        "head_size": HEAD_SIZE,
        "num_heads": NUM_HEADS,
        "ff_dim": FF_DIM,
        "num_blocks": NUM_BLOCKS,
        "mlp_units": [64], 
        "dropout": DROPOUT,
        "mlp_dropout": DROPOUT,
        "learning_rate": LEARNING_RATE
    }
    
    try:
        model_wrapper = get_model("transformer", config)
        model_wrapper.build(config["input_shape"])
        
        # model.train 내부에서 fit 호출 시 y_train이 int여도 binary_crossentropy(from_logits=False)면 OK
        # 단, TransformerSignalModel 구조상 sigmoid 출력이면 y는 0/1 (int or float) 호환됨
        model_wrapper.train(
            X_train, y_train,
            X_val=X_val, y_val=y_val,
            epochs=5,
            batch_size=1024,
            class_weight=class_weight_dict,
            callbacks=[EarlyStopping(patience=2, monitor='val_loss', restore_best_weights=True)],
            verbose=2
        )
        
        # (5) 평가 및 점수 산정
        y_pred_probs = model_wrapper.predict(X_val).flatten()
        buy_signals = (y_pred_probs > PRED_THR) # boolean mask
        
        if np.sum(buy_signals) < 5: # 최소 거래 횟수 미달
            return -10.0
            
        # [수정 5] Cooldown(중복 진입 방지) 적용 Score 계산
        # "BUY 신호가 뜨면 PRED_H(=5일) 동안은 추가 매수 불가(이미 포지션 보유)" 가정
        
        total_profit = 0.0
        trade_count = 0
        last_exit_idx = -1  # 마지막 매도(보유 종료) 시점 인덱스
        
        # buy_signals가 True인 인덱스만 추출
        signal_indices = np.where(buy_signals)[0]
        
        for idx in signal_indices:
            # 이전 거래가 끝난 이후에만 진입 가능
            if idx > last_exit_idx:
                # 수익 실현 (r_val[idx]는 idx 시점 매수 후 5일 뒤 수익률)
                total_profit += r_val[idx]
                trade_count += 1
                last_exit_idx = idx + PRED_H - 1 # 보유 기간 설정
        
        # 거래가 너무 적게 걸러졌을 경우 재확인
        if trade_count == 0:
            return -5.0

        # 최종 점수: 누적 수익률
        # (옵션) 승률이나 거래 횟수에 따른 가중치를 더 줄 수도 있음
        score = total_profit
        
        # 승률 계산 (실제 진입한 거래 기준)
        # 루프를 다시 돌 필요 없이, 위에서 더할 때 승/패 카운팅 가능하나 간략화
        # 여기서는 단순 누적 수익을 최우선 지표로 삼음
        
        return score

    except Exception as e:
        print(f"[Trial Fail] Error: {e}")
        # traceback을 보고 싶으면 import traceback; traceback.print_exc() 사용
        return -999.0
        
    finally:
        # [수정 3] 안전한 자원 해제
        if model_wrapper is not None:
            del model_wrapper
        K.clear_session()
        gc.collect()

# ─────────────────────────────────────────────────────────────────────────────
#  5. 메인 실행
# ─────────────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    gpus = tf.config.list_physical_devices('GPU')
    if gpus:
        try:
            for gpu in gpus:
                tf.config.experimental.set_memory_growth(gpu, True)
        except RuntimeError as e:
            print(e)
            
    study = optuna.create_study(direction="maximize")
    
    print("🚀 하이퍼파라미터 최적화 시작 (Stable & Strict)")
    # 예외로 죽지 않도록 catch_catch=True 옵션을 고려할 수 있으나,
    # 여기서는 objective 내부 try-except로 처리함.
    study.optimize(objective, n_trials=30, n_jobs=1)
    
    print("\n" + "="*50)
    print("🏆 Best Trial Result")
    print(f"  Score (Cooldown Total Return): {study.best_value:.4f}")
    print("  Best Params:")
    for key, value in study.best_params.items():
        print(f"    {key}: {value}")
    print("="*50)