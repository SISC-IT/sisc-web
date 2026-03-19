# AI/modules/signal/models/PatchTST/train.py
"""
PatchTST 학습 스크립트
-----------------------------------------------
- create_dataset() 사용 안 함 (피처 리스트가 코어에서 고정되어 있어서)
- 시퀀스 생성을 여기서 직접 수행
- 주봉/월봉 피처 포함 (개선안)
-----------------------------------------------
"""
import os
import sys

current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

print("project_root:", project_root)

import pickle
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, TensorDataset
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import MinMaxScaler
from tqdm import tqdm

from AI.modules.signal.models.PatchTST.architecture import PatchTST_Model
from AI.modules.signal.core.data_loader import DataLoader as SISCDataLoader
from AI.modules.features.legacy.technical_features import (
    add_technical_indicators,
    add_multi_timeframe_features
)
# ─────────────────────────────────────────────────────────────────────────────
# CONFIG
# ─────────────────────────────────────────────────────────────────────────────
CONFIG = {
    'start_date'  : '2022-01-01',
    'end_date'    : '2023-12-31',  # 미래 데이터 차단 (Look-ahead bias 방지)
    'seq_len'     : 60,           # 과거 120일치를 보고 예측
    'horizons'    : [1, 3, 5, 7],  # 1일/3일/5일/7일 후 동시 예측

    # 모델 구조
    'patch_len'   : 16,
    'stride'      : 8,
    'd_model'     : 128,
    'n_heads'     : 4,
    'e_layers'    : 3,
    'd_ff'        : 256,
    'dropout'     : 0.1,

    # 학습
    'batch_size'      : 256,
    'learning_rate'   : 0.0001,
    'epochs'          : 3,
    'patience'        : 10,

    # 저장 경로
    'weights_dir' : 'AI/data/weights/PatchTST',
    'model_name'  : 'patchtst_model.pt',
    'scaler_name' : 'patchtst_scaler.pkl',
}

# ─────────────────────────────────────────────────────────────────────────────
# 피처 정의 (명세서 기준 + 주봉/월봉 개선안)
# architecture.py enc_in=17 과 반드시 일치
# ─────────────────────────────────────────────────────────────────────────────
FEATURE_COLUMNS = [
    # 일봉 (11개)
    'log_return',
    'ma5_ratio', 'ma20_ratio', 'ma60_ratio',
    'rsi', 'bb_position', 'macd_ratio',
    'open_ratio', 'high_ratio', 'low_ratio',
    'vol_change',

    # 주봉 (4개) ← 개선안
    'week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change',

    # 월봉 (2개) ← 개선안
    'month_ma12_ratio', 'month_rsi',
]


# ─────────────────────────────────────────────────────────────────────────────
# 시퀀스 직접 생성
# create_dataset() 대신 여기서 직접 만든다.
# 이유: create_dataset()은 피처 리스트가 내부에서 고정되어 있고
#       컬럼명도 달라서 (ma_5_ratio vs ma5_ratio) 주봉/월봉 피처가 안 들어간다.
# ─────────────────────────────────────────────────────────────────────────────
def build_sequences(full_df: pd.DataFrame, scaler: MinMaxScaler, fit_scaler: bool = True):
    """
    전처리된 데이터프레임에서 시퀀스(X)와 레이블(y)을 만든다.

    X shape: (샘플수, seq_len, 피처수)  예) (N, 120, 17)
    y shape: (샘플수, horizon수)        예) (N, 4)
    """
    seq_len    = CONFIG['seq_len']
    horizons   = CONFIG['horizons']
    max_horizon = max(horizons)

    # 사용 가능한 피처만 필터링 (안전장치)
    available = [c for c in FEATURE_COLUMNS if c in full_df.columns]
    missing   = set(FEATURE_COLUMNS) - set(available)
    if missing:
        print(f"[경고] 누락된 피처: {missing}")

    full_df = full_df.dropna(subset=available)

    # 스케일링
    # fit_scaler=True: 학습 시 scaler를 fit+transform
    # fit_scaler=False: 추론 시 transform만 (fit 금지)
    if fit_scaler:
        full_df[available] = scaler.fit_transform(full_df[available])
    else:
        full_df[available] = scaler.transform(full_df[available])

    X_list, y_list = [], []

    for ticker in tqdm(full_df['ticker'].unique(), desc="시퀀스 생성"):
        sub = full_df[full_df['ticker'] == ticker].sort_values('date')

        # 데이터 길이가 부족하면 건너뜀
        if len(sub) <= seq_len + max_horizon:
            continue

        feat_vals   = sub[available].values          # (T, F)
        raw_closes  = sub['close'].values             # 레이블 계산용 원본 종가

        num_samples = len(sub) - seq_len - max_horizon + 1
        if num_samples <= 0:
            continue

        for i in range(num_samples):
            # X: 과거 seq_len일치 피처
            window = feat_vals[i : i + seq_len]      # (120, 17)

            # y: 각 horizon별 상승 여부 (1: 상승, 0: 하락/보합)
            curr_price = raw_closes[i + seq_len - 1]
            labels = []
            for h in horizons:
                future_price = raw_closes[i + seq_len + h - 1]
                labels.append(1 if future_price > curr_price else 0)

            X_list.append(window)
            y_list.append(labels)

    X = np.array(X_list, dtype=np.float32)  # (N, 120, 17)
    y = np.array(y_list,  dtype=np.float32)  # (N, 4)

    print(f">> 시퀀스 완료: X={X.shape}, y={y.shape}")
    return X, y


# ─────────────────────────────────────────────────────────────────────────────
# 데이터 로드 + 피처 계산
# ─────────────────────────────────────────────────────────────────────────────
def load_and_preprocess():
    """DB에서 주가 로드 → 일봉/주봉/월봉 피처 계산"""
    print(">> DB에서 데이터 로드 중...")

    # DataLoader는 DB 연결 + 메타데이터 로드용으로만 사용
    # (create_dataset은 쓰지 않음)
    loader = SISCDataLoader(lookback=CONFIG['seq_len'], horizons=CONFIG['horizons'])

    raw_df = loader.load_data_from_db(
        start_date=CONFIG['start_date'],
        end_date=CONFIG['end_date']
    )
    tickers_sample = raw_df['ticker'].unique()[:50]
    raw_df = raw_df[raw_df['ticker'].isin(tickers_sample)]
    
    print(f">> 로드 완료: {len(raw_df)} rows")

    # 종목별로 피처 계산
    print(">> 피처 계산 중 (일봉 + 주봉/월봉)...")
    processed = []
    for ticker in tqdm(raw_df['ticker'].unique(), desc="피처 계산"):
        df_t = raw_df[raw_df['ticker'] == ticker].copy()
        try:
            df_t = add_technical_indicators(df_t)       # 일봉 11개
            df_t = add_multi_timeframe_features(df_t)   # 주봉/월봉 6개
            processed.append(df_t)
        except Exception:
            continue

    if not processed:
        raise ValueError("전처리된 데이터가 없습니다.")

    full_df = pd.concat(processed).reset_index(drop=True)
    print(f">> 피처 계산 완료: {len(full_df)} rows")
    return full_df


# ─────────────────────────────────────────────────────────────────────────────
# 학습 메인
# ─────────────────────────────────────────────────────────────────────────────
def train():
    print("=" * 50)
    print(" PatchTST 학습 시작")
    print(f" 피처: {len(FEATURE_COLUMNS)}개")
    print(f" horizon: {CONFIG['horizons']}일")
    print("=" * 50)

    # 1. 데이터 로드
    full_df = load_and_preprocess()

    # 2. 시퀀스 생성 (직접)
    scaler = MinMaxScaler()
    X, y   = build_sequences(full_df, scaler, fit_scaler=True)
    # X: (N, 120, 17) / y: (N, 4)

    # 3. Train / Val 분리
    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=0.2, shuffle=True, random_state=42
    )
    print(f"\n>> Train: {X_train.shape}, Val: {X_val.shape}")

    # 4. DataLoader
    train_loader = DataLoader(
        TensorDataset(torch.FloatTensor(X_train), torch.FloatTensor(y_train)),
        batch_size=CONFIG['batch_size'], shuffle=True
    )
    val_loader = DataLoader(
        TensorDataset(torch.FloatTensor(X_val), torch.FloatTensor(y_val)),
        batch_size=CONFIG['batch_size'], shuffle=False
    )

    # 5. 모델
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f">> Device: {device}\n")

    model = PatchTST_Model(
        seq_len   = CONFIG['seq_len'],
        enc_in    = len(FEATURE_COLUMNS),   # 17
        patch_len = CONFIG['patch_len'],
        stride    = CONFIG['stride'],
        d_model   = CONFIG['d_model'],
        n_heads   = CONFIG['n_heads'],
        e_layers  = CONFIG['e_layers'],
        d_ff      = CONFIG['d_ff'],
        dropout   = CONFIG['dropout'],
        n_outputs = len(CONFIG['horizons'])  # 4
    ).to(device)

    # 6. 손실함수 & 옵티마이저
    # BCEWithLogitsLoss: sigmoid + BCE를 한 번에 처리 (수치적으로 안정적)
    criterion = nn.BCEWithLogitsLoss()
    optimizer = optim.AdamW(model.parameters(), lr=CONFIG['learning_rate'])

    # 7. 저장 경로
    save_dir    = os.path.join(project_root, CONFIG['weights_dir'])
    os.makedirs(save_dir, exist_ok=True)
    model_path  = os.path.join(save_dir, CONFIG['model_name'])
    scaler_path = os.path.join(save_dir, CONFIG['scaler_name'])

    best_val_loss    = float('inf')
    patience_counter = 0

    print(f">> 학습 시작 (epochs={CONFIG['epochs']}, patience={CONFIG['patience']})\n")

    for epoch in range(CONFIG['epochs']):

        # Training
        model.train()
        train_loss = 0.0
        for X_b, y_b in train_loader:
            X_b, y_b = X_b.to(device), y_b.to(device)
            optimizer.zero_grad()
            loss = criterion(model(X_b), y_b)
            loss.backward()
            optimizer.step()
            train_loss += loss.item()

        avg_train = train_loss / len(train_loader)

        # Validation
        model.eval()
        val_loss = 0.0
        with torch.no_grad():
            for X_v, y_v in val_loader:
                X_v, y_v = X_v.to(device), y_v.to(device)
                val_loss += criterion(model(X_v), y_v).item()

        avg_val = val_loss / len(val_loader)

        print(f"Epoch [{epoch+1:3d}/{CONFIG['epochs']}] "
              f"Train: {avg_train:.4f} | Val: {avg_val:.4f}", end="")

        # Early Stopping
        if avg_val < best_val_loss:
            best_val_loss    = avg_val
            patience_counter = 0
            torch.save(model.state_dict(), model_path)
            print("  ✓ saved")
        else:
            patience_counter += 1
            print(f"  ({patience_counter}/{CONFIG['patience']})")
            if patience_counter >= CONFIG['patience']:
                print(f"\n>> Early Stopping at epoch {epoch+1}")
                break

    # 8. 스케일러 저장
    with open(scaler_path, 'wb') as f:
        pickle.dump(scaler, f)

    print(f"\n>> 완료")
    print(f"   모델    : {model_path}")
    print(f"   스케일러: {scaler_path}")


if __name__ == '__main__':
    train()
