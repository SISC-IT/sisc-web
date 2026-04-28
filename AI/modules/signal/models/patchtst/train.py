# AI/modules/signal/models/patchtst/train.py
"""
PatchTST 학습 스크립트
-----------------------------------------------
- create_dataset() 사용 안 함 (피처 리스트가 코어에서 고정되어 있어서)
- 시퀀스 생성을 여기서 직접 수행
- 주봉/월봉 피처 포함 (개선안)

[코드래빗 리뷰 반영]
- 스케일러 누수 수정: train/val 분리 후 train만으로 scaler fit
- 경계 조건 버그 수정: <= → <
- smoke_test_mode CONFIG 추가 (50개 종목 테스트용 플래그)
- 에러 로깅 개선: 실패 종목 출력 및 임계값 초과 시 중단
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

# 경로 설정 (다른 import보다 먼저)
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.models.patchtst.architecture import PatchTST_Model
from AI.modules.signal.core.data_loader import DataLoader as SISCDataLoader
from AI.modules.features.legacy.technical_features import (
    add_technical_indicators,
    add_multi_timeframe_features
)

# ─────────────────────────────────────────────────────────────────────────────
# CONFIG
# ─────────────────────────────────────────────────────────────────────────────
CONFIG = {
    'start_date'     : '2015-01-01',
    'end_date'       : '2023-12-31',  # 미래 데이터 차단 (Look-ahead bias 방지)
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
    'batch_size'     : 32,
    'learning_rate'  : 0.0001,
    'epochs'         : 50,
    'patience'       : 10,

    # 테스트 모드 (True: 50개 종목만, False: 전체)
    'smoke_test_mode': False,
    'smoke_test_n'   : 50,

    # 저장 경로
    'weights_dir'    : 'AI/data/weights/patchtst',
    'model_name'     : 'patchtst_model.pt',
    'scaler_name'    : 'patchtst_scaler.pkl',
}

# ─────────────────────────────────────────────────────────────────────────────
# 피처 정의 (train.py와 wrapper.py가 동일한 순서를 공유)
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

HORIZONS = [1, 3, 5, 7]  # wrapper.py와 공유


# ─────────────────────────────────────────────────────────────────────────────
# 시퀀스 생성
# fit_scaler=True  → scaler.fit_transform (학습용)
# fit_scaler=False → scaler.transform만  (검증/추론용)
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

    # 스케일링 (fit은 train partition에서만)
    if fit_scaler:
        full_df[available] = scaler.fit_transform(full_df[available])
    else:
        full_df[available] = scaler.transform(full_df[available])

    X_list, y_list = [], []

    for ticker in tqdm(full_df['ticker'].unique(), desc="시퀀스 생성"):
        sub = full_df[full_df['ticker'] == ticker].sort_values('date')

        # [수정] <= → < (경계 케이스 허용)
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
# 데이터 로드 + 피처 계산
# ─────────────────────────────────────────────────────────────────────────────
def load_and_preprocess():
    print(">> DB에서 데이터 로드 중...")
    loader = SISCDataLoader(lookback=CONFIG['seq_len'], horizons=CONFIG['horizons'])

    raw_df = loader.load_data_from_db(
        start_date=CONFIG['start_date'],
        end_date=CONFIG['end_date']
    )
    print(f">> 로드 완료: {len(raw_df)} rows")

    # [수정] smoke_test_mode: CONFIG 플래그로 제어
    if CONFIG['smoke_test_mode']:
        n = CONFIG['smoke_test_n']
        sample_tickers = raw_df['ticker'].unique()[:n]
        raw_df = raw_df[raw_df['ticker'].isin(sample_tickers)]
        print(f">> [Smoke Test] {n}개 종목으로 제한")

    # 피처 계산 (에러 로깅 개선)
    print(">> 피처 계산 중 (일봉 + 주봉/월봉)...")
    processed    = []
    fail_count   = 0
    fail_limit   = 20  # 실패 종목이 20개 넘으면 중단

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
                raise RuntimeError(f"피처 계산 실패가 {fail_limit}개를 초과했습니다. 데이터를 확인하세요.")

    if not processed:
        raise ValueError("전처리된 데이터가 없습니다.")

    full_df = pd.concat(processed).reset_index(drop=True)
    print(f">> 피처 계산 완료: {len(full_df)} rows (실패: {fail_count}개)")
    return full_df


# ─────────────────────────────────────────────────────────────────────────────
# 학습 메인
# ─────────────────────────────────────────────────────────────────────────────
def train():
    print("=" * 50)
    print(" PatchTST 학습 시작")
    print(f" 피처: {len(FEATURE_COLUMNS)}개")
    print(f" horizon: {CONFIG['horizons']}일")
    if CONFIG['smoke_test_mode']:
        print(f" [Smoke Test 모드] {CONFIG['smoke_test_n']}개 종목")
    print("=" * 50)

    # 1. 데이터 로드
    full_df = load_and_preprocess()

    # ── [수정] Train/Val 분리 먼저 → 그 다음 스케일링 ──────────────────────
    # 기존: 전체 스케일링 → 분리 (데이터 누수 발생)
    # 수정: 티커 기준으로 분리 → train만 fit → val은 transform
    tickers = full_df['ticker'].unique()

    # 최소 2개 이상 있어야 train/val 분리 가능
    if len(tickers) < 2:
        raise ValueError(f"학습에 필요한 ticker가 부족합니다. (현재: {len(tickers)}개, 최소 2개 필요)")

    # val 비율 20%, 단 train이 최소 1개는 남도록 상한 보정
    n_val         = max(1, min(int(len(tickers) * 0.2), len(tickers) - 1))
    val_tickers   = tickers[-n_val:]   # 마지막 20% 티커를 val로 (시간 순서 보존)
    train_tickers = tickers[:-n_val]

    train_df = full_df[full_df['ticker'].isin(train_tickers)].copy()
    val_df   = full_df[full_df['ticker'].isin(val_tickers)].copy()

    print(f"\n>> Train 티커: {len(train_tickers)}개, Val 티커: {len(val_tickers)}개")

    # 2. 시퀀스 생성
    # train: fit_scaler=True (scaler.fit_transform)
    # val  : fit_scaler=False (scaler.transform만, 누수 방지)
    scaler  = MinMaxScaler()
    X_train, y_train = build_sequences(train_df, scaler, fit_scaler=True)
    X_val,   y_val   = build_sequences(val_df,   scaler, fit_scaler=False)

    print(f"\n>> Train: {X_train.shape}, Val: {X_val.shape}")

    # 3. DataLoader
    train_loader = DataLoader(
        TensorDataset(torch.FloatTensor(X_train), torch.FloatTensor(y_train)),
        batch_size=CONFIG['batch_size'], shuffle=True
    )
    val_loader = DataLoader(
        TensorDataset(torch.FloatTensor(X_val), torch.FloatTensor(y_val)),
        batch_size=CONFIG['batch_size'], shuffle=False
    )

    # 4. 모델
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

    # 5. 손실함수 & 옵티마이저
    criterion = nn.BCEWithLogitsLoss()
    optimizer = optim.AdamW(model.parameters(), lr=CONFIG['learning_rate'])

    # 6. 저장 경로
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

        # Early Stopping & 저장
        if avg_val < best_val_loss:
            best_val_loss    = avg_val
            patience_counter = 0
            # config + state_dict 같이 저장 (load 시 구조 재현 가능)
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

    # 7. 스케일러 저장
    with open(scaler_path, 'wb') as f:
        pickle.dump(scaler, f)

    print(f"\n>> 완료")
    print(f"   모델    : {model_path}")
    print(f"   스케일러: {scaler_path}")


if __name__ == '__main__':
    train()
