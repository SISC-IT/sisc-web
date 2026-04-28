# AI/modules/signal/models/TCN/tcn_wandb_sweep.py
"""
TCN 하이퍼파라미터 탐색 - W&B Sweep
--------------------------------------
Kaggle 노트북에서 실행

사용법:
    1. 이 파일을 Kaggle 노트북 셀에 붙여넣기
    2. wandb.agent()가 자동으로 파라미터 조합 탐색
    3. wandb.ai 에서 결과 시각화 확인

탐색 파라미터:
    - learning_rate: 학습률
    - dropout: 드롭아웃 비율
    - channels: 채널 구성
    - batch_size: 배치 크기
    - val_split: Train/Val 분리 비율
"""
import os
import sys
import copy
import pickle
import json
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from sklearn.preprocessing import StandardScaler
from torch.utils.data import DataLoader as TorchDataLoader, TensorDataset
from tqdm import tqdm

import wandb

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
sys.path.insert(0, '/kaggle/working/sisc-web')

from AI.modules.signal.models.TCN.architecture import TCNClassifier
from AI.modules.features.legacy.technical_features import add_technical_indicators

# ─────────────────────────────────────────────────────────────────────────────
# 고정 설정
# ─────────────────────────────────────────────────────────────────────────────
FIXED = {
    'parquet_dir' : 'AI/data/kaggle_data',
    'start_date'  : '2015-01-01',
    'end_date'    : '2023-12-31',
    'seq_len'     : 60,
    'kernel_size' : 3,
}

FEATURE_COLUMNS = [
    "log_return", "open_ratio", "high_ratio", "low_ratio", "vol_change",
    "ma5_ratio", "ma20_ratio", "ma60_ratio", "rsi", "macd_ratio", "bb_position",
]

HORIZONS = [1, 3, 5, 7]

# ─────────────────────────────────────────────────────────────────────────────
# Sweep 설정 (탐색할 파라미터 범위)
# ─────────────────────────────────────────────────────────────────────────────
SWEEP_CONFIG = {
    'method': 'bayes',
    'metric': {
        'name': 'val_loss',
        'goal': 'minimize'
    },
    'parameters': {
        'learning_rate': {
            'distribution': 'log_uniform_values',
            'min': 1e-5,
            'max': 1e-3,
        },
        'dropout': {
            'values': [0.2, 0.3, 0.4, 0.5]
        },
        'channels': {
            'values': [
                [32, 64, 64],
                [64, 64, 64],
                [32, 64, 128],
                [32, 32, 64],
            ]
        },
        'batch_size': {
            'values': [64, 128, 256]
        },
        # val_split이 높을수록 val 시작점이 뒤로 밀림
        # 0.75 → val 2020년~ (코로나 구간 포함)
        # 0.80 → val 2021년~
        # 0.85 → val 2022년~ (금리인상 포함)
        # 0.90 → val 2022년 하반기~
        'val_split': {
            'values': [0.75, 0.80, 0.85, 0.90]
        },
        # 에폭 수도 탐색 (과적합 vs 과소적합)
        'epochs': {
            'values': [20, 30, 50]
        },
    },
    'early_terminate': {
        'type': 'hyperband',
        'min_iter': 5,
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# 데이터 로드 (전역 캐시 - 매 run마다 다시 로드 안 하도록)
# ─────────────────────────────────────────────────────────────────────────────
_cached_df = None

def get_data():
    global _cached_df
    if _cached_df is not None:
        return _cached_df

    parquet_path = os.path.join(FIXED['parquet_dir'], 'price_data.parquet')
    print(f">> parquet 로드: {parquet_path}")
    raw_df = pd.read_parquet(parquet_path)
    raw_df['date'] = pd.to_datetime(raw_df['date'])
    raw_df = raw_df[
        (raw_df['date'] >= FIXED['start_date']) &
        (raw_df['date'] <= FIXED['end_date'])
    ].copy()

    processed = []
    for ticker in tqdm(raw_df['ticker'].unique(), desc="피처 계산"):
        df_t = raw_df[raw_df['ticker'] == ticker].copy()
        try:
            df_t = add_technical_indicators(df_t)
            processed.append(df_t)
        except Exception:
            pass

    _cached_df = pd.concat(processed).reset_index(drop=True)
    print(f">> 데이터 준비 완료: {len(_cached_df):,}행")
    return _cached_df


def build_sequences(df, seq_len, feature_cols, horizons):
    features, labels = [], []
    max_h = max(horizons)
    for _, sub in df.groupby("ticker"):
        sub = sub.sort_values("date").dropna(subset=["close"])
        if len(sub) < seq_len + max_h:
            continue
        feat = sub[feature_cols].to_numpy(dtype=np.float32)
        cls  = sub["close"].to_numpy(dtype=np.float32)
        for s in range(len(sub) - seq_len - max_h + 1):
            e = s + seq_len
            features.append(feat[s:e])
            labels.append([1.0 if cls[e+h-1] > cls[e-1] else 0.0 for h in horizons])
    if not features:
        return np.empty((0, seq_len, len(feature_cols)), np.float32), \
               np.empty((0, len(horizons)), np.float32)
    return np.array(features, np.float32), np.array(labels, np.float32)


# ─────────────────────────────────────────────────────────────────────────────
# 학습 함수 (W&B에서 호출)
# ─────────────────────────────────────────────────────────────────────────────
def train_sweep():
    run = wandb.init()
    cfg = run.config

    learning_rate = cfg.learning_rate
    dropout       = cfg.dropout
    channels      = cfg.channels
    batch_size    = cfg.batch_size
    val_split     = cfg.val_split
    epochs        = cfg.epochs

    # 데이터 준비
    full_df = get_data()
    dates   = full_df['date'].sort_values().unique()
    split_i = int(len(dates) * val_split)
    split_d = dates[split_i]

    train_df = full_df[full_df['date'] <  split_d].copy()
    val_df   = full_df[full_df['date'] >= split_d].copy()

    scaler = StandardScaler()
    scaler.fit(train_df[FEATURE_COLUMNS])
    train_df[FEATURE_COLUMNS] = scaler.transform(train_df[FEATURE_COLUMNS])
    val_df[FEATURE_COLUMNS]   = scaler.transform(val_df[FEATURE_COLUMNS])

    X_tr, y_tr = build_sequences(train_df, FIXED['seq_len'], FEATURE_COLUMNS, HORIZONS)
    X_vl, y_vl = build_sequences(val_df,   FIXED['seq_len'], FEATURE_COLUMNS, HORIZONS)

    if len(X_tr) == 0 or len(X_vl) == 0:
        wandb.log({"val_loss": 9.99})
        return

    train_loader = TorchDataLoader(
        TensorDataset(torch.from_numpy(X_tr), torch.from_numpy(y_tr)),
        batch_size=batch_size, shuffle=True
    )
    val_loader = TorchDataLoader(
        TensorDataset(torch.from_numpy(X_vl), torch.from_numpy(y_vl)),
        batch_size=batch_size, shuffle=False
    )

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model  = TCNClassifier(
        input_size   = len(FEATURE_COLUMNS),
        output_size  = len(HORIZONS),
        num_channels = channels,
        kernel_size  = FIXED['kernel_size'],
        dropout      = dropout,
    ).to(device)

    criterion = nn.BCEWithLogitsLoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=learning_rate, weight_decay=1e-4)
    
    best_val_loss = float('inf')

    for epoch in range(epochs):
        # Train
        model.train()
        tr_loss = 0.0
        for bx, by in train_loader:
            bx, by = bx.to(device), by.to(device)
            optimizer.zero_grad()
            loss = criterion(model(bx), by)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
            tr_loss += loss.item() * bx.size(0)
        tr_loss /= len(X_tr)

        # Val
        model.eval()
        vl_loss = 0.0
        correct = 0
        total   = 0
        with torch.no_grad():
            for bx, by in val_loader:
                bx, by = bx.to(device), by.to(device)
                out     = model(bx)
                vl_loss += criterion(out, by).item() * bx.size(0)
                preds    = (torch.sigmoid(out) > 0.5).float()
                correct += (preds == by).float().mean(dim=1).sum().item()
                total   += bx.size(0)

        vl_loss  /= len(X_vl)
        vl_acc    = correct / total if total > 0 else 0.0

        if vl_loss < best_val_loss:
            best_val_loss = vl_loss

        wandb.log({
            "epoch"         : epoch + 1,
            "train_loss"    : tr_loss,
            "val_loss"      : vl_loss,
            "val_accuracy"  : vl_acc,
            "best_val_loss" : best_val_loss,
            "learning_rate" : learning_rate,
            "dropout"       : dropout,
            "val_split"     : val_split,
        })

        print(f"Epoch [{epoch+1:2d}/{epochs}] "
              f"train={tr_loss:.4f} val={vl_loss:.4f} acc={vl_acc:.3f}")

    wandb.log({"final_best_val_loss": best_val_loss})


# ─────────────────────────────────────────────────────────────────────────────
# 실행
# ─────────────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    # W&B 로그인
    wandb.login(key=os.environ.get("WANDB_API_KEY", ""))

    # Sweep 생성 & 실행
    sweep_id = wandb.sweep(
        sweep=SWEEP_CONFIG,
        project="sisc-tcn-sweep"
    )
    print(f">> Sweep ID: {sweep_id}")
    print(f">> 확인: https://wandb.ai/vmfhdirn2014/sisc-tcn-sweep/sweeps/{sweep_id}")

    # count: 몇 번 탐색할지 (GPU 시간 고려해서 조절)
    wandb.agent(sweep_id, function=train_sweep, count=20)
