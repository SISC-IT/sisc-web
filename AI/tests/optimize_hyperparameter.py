# AI/tests/optimize_hyperparameter.py
"""
[하이퍼파라미터 최적화 (AutoML) - Stable & Strict Version]
- Optuna를 사용하여 Transformer 모델 최적화
- [수정 내역]:
  1. 단 한 번의 DB 조회(Bulk Load)로 모든 데이터 병합 (속도 극대화)
  2. 모델 학습을 위한 기술적 지표(Feature) 생성 로직 추가
  3. Class Weight 계산 시 int 타입 보장
  4. Validation 데이터 시퀀스 생성 시 Train 후반부 Context 활용 (평가 데이터 보존)
  5. 점수 산정 시 Cooldown(중복 진입 방지) 적용으로 현실성 확보
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

from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.signal.models import get_model
from AI.modules.features.legacy.technical_features import add_technical_indicators  # [수정 2] 지표 생성 모듈 추가

# ─────────────────────────────────────────────────────────────────────────────
#  2. 전역 데이터 로드 (Bulk Load)
# ─────────────────────────────────────────────────────────────────────────────
print("[AutoML] 데이터 메모리 로딩 중...")

target_tickers = ["AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "META"] 
start_date = "2023-01-01"
end_date = "2024-12-31"

loader = DataLoader(lookback=60)
grouped_data = []

try:
    # [수정 1] for문 밖에서 단 한 번의 쿼리로 전체 종목을 통째로 가져옵니다 (속도 극대화)
    bulk_df = loader.load_data_from_db(start_date=start_date, end_date=end_date, tickers=target_tickers)
    
    if bulk_df is not None and not bulk_df.empty:
        for ticker in target_tickers:
            # 가져온 뭉치 데이터(bulk)에서 해당 종목만 잘라냅니다
            df = bulk_df[bulk_df['ticker'] == ticker].copy()
            
            if not df.empty and len(df) > 300:
                # [수정 2] 모델 입력에 반드시 필요한 17개 기술적 지표를 생성합니다
                df = add_technical_indicators(df)
                # 시계열 순서 보장을 위해 인덱스를 날짜로 세팅합니다
                df.set_index('date', inplace=True)
                grouped_data.append(df)
                
except Exception as e:
    print(f"[Warning] 데이터 로드 및 전처리 실패: {e}")

print(f"[AutoML] {len(grouped_data)}개 종목 데이터 준비 완료.")

# ─────────────────────────────────────────────────────────────────────────────
#  3. Helper Functions
# ─────────────────────────────────────────────────────────────────────────────
def _label_by_future_return(close_prices: pd.Series, horizon: int, threshold: float) -> tuple[pd.Series, pd.Series]:
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
    model_wrapper = None 

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
    # np.number 타입 컬럼을 선택하므로 방금 생성한 기술적 지표들이 포함됩니다.
    feature_cols = sample_df.select_dtypes(include=[np.number]).columns.tolist()
    
    for df in grouped_data:
        labels, future_ret = _label_by_future_return(df["close"], PRED_H, HOLD_THR)
        
        # 피처 NaN 포함 여부까지 엄격하게 검사
        valid_mask = (
            (labels != -1) & 
            future_ret.notna() & 
            df[feature_cols].notna().all(axis=1)
        )
        
        df_valid = df.loc[valid_mask]
        labels_valid = labels[valid_mask]
        ret_valid = future_ret[valid_mask]
        
        if len(df_valid) < SEQ_LEN + 20: 
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
        scaler.fit(train_feat) 
        
        # ─── Train Data 생성 ───
        train_scaled = scaler.transform(train_feat)
        X_train_seq = _build_sequences(train_scaled, SEQ_LEN)
        y_train_seq = train_labels.values[SEQ_LEN-1:] 
        
        min_len_train = min(len(X_train_seq), len(y_train_seq))
        if min_len_train > 0:
            X_train_list.append(X_train_seq[:min_len_train])
            y_train_list.append(y_train_seq[:min_len_train])
            
        # ─── Val Data 생성 (Context Prefix 적용) ───
        lookback = SEQ_LEN - 1
        if len(train_df) >= lookback:
            val_input_df = pd.concat([train_df.iloc[-lookback:], val_df], axis=0)
            val_input_feat = val_input_df[feature_cols].values.astype(np.float32)
            val_scaled = scaler.transform(val_input_feat)
            
            X_val_seq = _build_sequences(val_scaled, SEQ_LEN)
            y_val_seq = val_labels.values
            r_val_seq = val_rets.values
        else:
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
    y_train = np.concatenate(y_train_list, axis=0).astype(np.int32)
    
    X_val = np.concatenate(X_val_list, axis=0)
    y_val = np.concatenate(y_val_list, axis=0).astype(np.int32)
    r_val = np.concatenate(r_val_list, axis=0)
    
    classes = np.unique(y_train)
    class_weight_dict = None
    if len(classes) >= 2:
        weights = compute_class_weight(class_weight='balanced', classes=classes, y=y_train)
        class_weight_dict = {int(c): float(w) for c, w in zip(classes, weights)}
    
    # (4) 모델 학습
    # [수정] 임베딩 처리를 위해 껍데기 변수 추가 (optuna 최적화용이므로 임시 할당)
    config = {
        "input_shape": (SEQ_LEN, len(feature_cols)),
        "n_tickers": len(target_tickers),
        "n_sectors": 1,
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
        buy_signals = (y_pred_probs > PRED_THR)
        
        if np.sum(buy_signals) < 5: 
            return -10.0
            
        total_profit = 0.0
        trade_count = 0
        last_exit_idx = -1  
        
        signal_indices = np.where(buy_signals)[0]
        
        for idx in signal_indices:
            if idx > last_exit_idx:
                total_profit += r_val[idx]
                trade_count += 1
                last_exit_idx = idx + PRED_H - 1 
        
        if trade_count == 0:
            return -5.0

        return total_profit

    except Exception as e:
        print(f"[Trial Fail] Error: {e}")
        return -999.0
        
    finally:
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
    
    print("🚀 하이퍼파라미터 최적화 시작 (Stable & Strict - Bulk Load)")
    study.optimize(objective, n_trials=30, n_jobs=1)
    
    print("\n" + "="*50)
    print("🏆 Best Trial Result")
    print(f"  Score (Cooldown Total Return): {study.best_value:.4f}")
    print("  Best Params:")
    for key, value in study.best_params.items():
        print(f"    {key}: {value}")
    print("="*50)