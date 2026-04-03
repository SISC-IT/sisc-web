# AI/modules/signal/models/PatchTST/train_kaggle.py
"""
PatchTST 학습 스크립트 - Kaggle/GitHub Actions 버전
-----------------------------------------------
[train.py와의 차이점]
- DB 연결 없음 (SISCDataLoader 사용 안 함)
- parquet 파일에서 직접 로드
- GitHub Actions 자동화 파이프라인에서 사용

[사용 환경]
- Kaggle 노트북 (GPU 학습)
- GitHub Actions (자동화)

[train.py는 그대로 유지]
- 로컬/서버에서 DB 연결로 학습할 때 사용
- 팀원 파트 영향 없음
-----------------------------------------------
"""
import os
import sys
import pickle
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, TensorDataset
from sklearn.preprocessing import MinMaxScaler
from tqdm import tqdm

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# Kaggle: /kaggle/input/datasets/jihyeongkimm/sisc-ai-trading-dataset
# GitHub Actions: ./kaggle_data
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.models.PatchTST.architecture import PatchTST_Model
from AI.modules.features.legacy.technical_features import (
    add_technical_indicators,
    add_multi_timeframe_features
)

# ─────────────────────────────────────────────────────────────────────────────
# CONFIG
# ─────────────────────────────────────────────────────────────────────────────
CONFIG = {
    # parquet 파일 경로
    # Kaggle 환경이면 /kaggle/input/datasets/... 로 바꿔서 쓰면 됨
    'parquet_dir'    : os.environ.get(
        'PARQUET_DIR',
        '/kaggle/input/datasets/jihyeongkimm/sisc-ai-trading-dataset'
    ),

    'start_date'     : '2015-01-01',
    'end_date'       : '2023-12-31',
    'seq_len'        : 120,
    'horizons'       : [1, 3, 5, 7],

    # 모델 구조
    'patch_len'      : 16,
    'stride'         : 8,
    'd_model'        : 128,
    'n_heads'        : 4,
    'e_layers'       : 3,
    'd_ff'           : 256,
    'dropout'        : 0.1,

    # 학습
    'batch_size'     : 256,
    'learning_rate'  : 0.0001,
    'epochs'         : 50,
    'patience'       : 10,

    # 저장 경로
    # Kaggle: /kaggle/working/
    # GitHub Actions: AI/data/weights/PatchTST/
    'weights_dir'    : os.environ.get('WEIGHTS_DIR', '/kaggle/working'),
    'model_name'     : 'patchtst_model.pt',
    'scaler_name'    : 'patchtst_scaler.pkl',
}

# ─────────────────────────────────────────────────────────────────────────────
# 피처 정의 (train.py, wrapper.py와 동일한 순서 유지)
# ─────────────────────────────────────────────────────────────────────────────
FEATURE_COLUMNS = [
    # 일봉 (11개)
    'log_return',
    'ma5_ratio', 'ma20_ratio', 'ma60_ratio',
    'rsi', 'bb_position', 'macd_ratio',
    'open_ratio', 'high_ratio', 'low_ratio',
    'vol_change',
    # 주봉 (4개)
    'week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change',
    # 월봉 (2개)
    'month_ma12_ratio', 'month_rsi',
]

HORIZONS = [1, 3, 5, 7]


# ─────────────────────────────────────────────────────────────────────────────
# 시퀀스 생성 (train.py와 동일)
# ─────────────────────────────────────────────────────────────────────────────
def build_sequences(full_df: pd.DataFrame, scaler: MinMaxScaler, fit_scaler: bool = True):
    seq_len     = CONFIG['seq_len']
    horizons    = CONFIG['horizons']
    max_horizon = max(horizons)

    available = [c for c in FEATURE_COLUMNS if c in full_df.columns]
    missing   = set(FEATURE_COLUMNS) - set(available)
    if missing:
        print(f"[경고] 누락된 피처: {missing}")

    full_df = full_df.dropna(subset=available).copy()

    if fit_scaler:
        full_df[available] = scaler.fit_transform(full_df[available])
    else:
        full_df[available] = scaler.transform(full_df[available])

    X_list, y_list = [], []

    for ticker in tqdm(full_df['ticker'].unique(), desc="시퀀스 생성"):
        sub = full_df[full_df['ticker'] == ticker].sort_values('date')

        if len(sub) < seq_len + max_horizon:
            continue

        feat_vals  = sub[available].values
        raw_closes = sub['close'].values

        num_samples = len(sub) - seq_len - max_horizon + 1
        if num_samples <= 0:
            continue

        for i in range(num_samples):
            window     = feat_vals[i : i + seq_len]
            curr_price = raw_closes[i + seq_len - 1]
            labels     = []
            for h in horizons:
                future_price = raw_closes[i + seq_len + h - 1]
                labels.append(1 if future_price > curr_price else 0)

            X_list.append(window)
            y_list.append(labels)

    X = np.array(X_list, dtype=np.float32)
    y = np.array(y_list,  dtype=np.float32)
    print(f">> 시퀀스 완료: X={X.shape}, y={y.shape}")
    return X, y


# ─────────────────────────────────────────────────────────────────────────────
# [핵심 변경] 데이터 로드
# train.py: SISCDataLoader → DB 연결 필요
# train_kaggle.py: parquet 직접 읽기 → DB 연결 불필요
# ─────────────────────────────────────────────────────────────────────────────
def load_and_preprocess():
    parquet_path = os.path.join(CONFIG['parquet_dir'], 'price_data.parquet')
    print(f">> parquet 로드 중: {parquet_path}")

    raw_df = pd.read_parquet(parquet_path)
    raw_df['date'] = pd.to_datetime(raw_df['date'])

    # 날짜 필터링
    raw_df = raw_df[
        (raw_df['date'] >= CONFIG['start_date']) &
        (raw_df['date'] <= CONFIG['end_date'])
    ].copy()

    print(f">> 로드 완료: {len(raw_df):,}행, {raw_df['ticker'].nunique()}개 종목")

    # 피처 계산
    print(">> 피처 계산 중 (일봉 + 주봉/월봉)...")
    processed  = []
    fail_count = 0
    fail_limit = 20

    for ticker in tqdm(raw_df['ticker'].unique(), desc="피처 계산"):
        df_t = raw_df[raw_df['ticker'] == ticker].copy()
        try:
            df_t = add_technical_indicators(df_t)
            df_t = add_multi_timeframe_features(df_t)
            processed.append(df_t)
        except Exception as e:
            fail_count += 1
            print(f"\n[경고] {ticker} 피처 계산 실패 ({fail_count}/{fail_limit}): {e}")
            if fail_count >= fail_limit:
                raise RuntimeError(f"피처 계산 실패가 {fail_limit}개를 초과했습니다.")

    if not processed:
        raise ValueError("전처리된 데이터가 없습니다.")

    full_df = pd.concat(processed).reset_index(drop=True)
    print(f">> 피처 계산 완료: {len(full_df):,}행 (실패: {fail_count}개)")
    return full_df


# ─────────────────────────────────────────────────────────────────────────────
# 학습 메인 (train.py와 동일)
# ─────────────────────────────────────────────────────────────────────────────
def train():
    print("=" * 50)
    print(" PatchTST 학습 시작 (Kaggle/Actions 버전)")
    print(f" 데이터: {CONFIG['parquet_dir']}")
    print(f" 피처: {len(FEATURE_COLUMNS)}개")
    print(f" horizon: {CONFIG['horizons']}일")
    print("=" * 50)

    # 1. 데이터 로드
    full_df = load_and_preprocess()

    # 2. Train/Val 분리
    tickers       = full_df['ticker'].unique()
    n_val         = max(1, int(len(tickers) * 0.2))
    val_tickers   = tickers[-n_val:]
    train_tickers = tickers[:-n_val]

    train_df = full_df[full_df['ticker'].isin(train_tickers)].copy()
    val_df   = full_df[full_df['ticker'].isin(val_tickers)].copy()
    print(f"\n>> Train 티커: {len(train_tickers)}개, Val 티커: {len(val_tickers)}개")

    # 3. 시퀀스 생성
    scaler  = MinMaxScaler()
    X_train, y_train = build_sequences(train_df, scaler, fit_scaler=True)
    X_val,   y_val   = build_sequences(val_df,   scaler, fit_scaler=False)
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
        enc_in    = len(FEATURE_COLUMNS),
        patch_len = CONFIG['patch_len'],
        stride    = CONFIG['stride'],
        d_model   = CONFIG['d_model'],
        n_heads   = CONFIG['n_heads'],
        e_layers  = CONFIG['e_layers'],
        d_ff      = CONFIG['d_ff'],
        dropout   = CONFIG['dropout'],
        n_outputs = len(CONFIG['horizons'])
    ).to(device)

    # 6. 손실함수 & 옵티마이저
    criterion = nn.BCEWithLogitsLoss()
    optimizer = optim.AdamW(model.parameters(), lr=CONFIG['learning_rate'])

    # 7. 저장 경로
    save_dir    = CONFIG['weights_dir']
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

        # Early Stopping & 저장
        if avg_val < best_val_loss:
            best_val_loss    = avg_val
            patience_counter = 0
            torch.save({
                'config'    : CONFIG,
                'state_dict': model.state_dict()
            }, model_path)
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
