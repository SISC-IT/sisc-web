# AI/modules/signal/models/TCN/train_kaggle.py
"""
TCN 학습 스크립트 - Kaggle 크론잡 버전
-----------------------------------------------
[train.py와의 차이점]
- DB 연결 없음 (get_standard_training_data 사용 안 함)
- parquet 파일에서 직접 로드 후 피처 계산
- 서버 크론잡이 Kaggle 커널을 push할 때 사용

[train.py는 그대로 유지]
- 로컬/서버에서 DB 연결로 학습할 때 사용

[과적합 방지 기본값]
- 채널은 [32, 64, 64] 이하의 작은 모델부터 사용한다.
- weight_decay, dropout, early stopping을 기본 적용한다.
-----------------------------------------------
"""
import argparse
import copy
import json
import os
import warnings
warnings.filterwarnings('ignore')
import pickle
import sys
from datetime import date
from typing import Dict, List, Tuple

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from sklearn.preprocessing import StandardScaler
from torch.utils.data import DataLoader as TorchDataLoader
from torch.utils.data import TensorDataset
from tqdm import tqdm

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.models.TCN.architecture import TCNClassifier
from AI.modules.features.legacy.technical_features import (
    add_technical_indicators,
    add_multi_timeframe_features
)

# ─────────────────────────────────────────────────────────────────────────────
# 피처 정의 (train.py와 동일)
# ─────────────────────────────────────────────────────────────────────────────
FEATURE_COLUMNS = [
    "log_return",
    "open_ratio",
    "high_ratio",
    "low_ratio",
    "vol_change",
    "ma5_ratio",
    "ma20_ratio",
    "ma60_ratio",
    "rsi",
    "macd_ratio",
    "bb_position",
]

HORIZONS = [1, 3, 5, 7]


# ─────────────────────────────────────────────────────────────────────────────
# 시퀀스 생성 (train.py와 동일)
# ─────────────────────────────────────────────────────────────────────────────
def build_sequences(
    df: pd.DataFrame,
    seq_len: int,
    feature_cols: List[str],
    horizons: List[int],
) -> Tuple[np.ndarray, np.ndarray]:
    features   = []
    labels     = []
    max_horizon = max(horizons)

    for _, sub_df in df.groupby("ticker"):
        sub_df = sub_df.sort_values("date").copy()
        sub_df = sub_df.dropna(subset=["close"])

        if len(sub_df) < seq_len + max_horizon:
            continue

        feature_values = sub_df[feature_cols].to_numpy(dtype=np.float32)
        closes         = sub_df["close"].to_numpy(dtype=np.float32)

        for start in range(len(sub_df) - seq_len - max_horizon + 1):
            end           = start + seq_len
            current_close = closes[end - 1]
            target        = [
                1.0 if closes[end + h - 1] > current_close else 0.0
                for h in horizons
            ]
            features.append(feature_values[start:end])
            labels.append(target)

    if not features:
        return (
            np.empty((0, seq_len, len(feature_cols)), dtype=np.float32),
            np.empty((0, len(horizons)), dtype=np.float32)
        )

    return np.array(features, dtype=np.float32), np.array(labels, dtype=np.float32)


# ─────────────────────────────────────────────────────────────────────────────
# [핵심 변경] 데이터 로드
# train.py: get_standard_training_data() → DB 연결 필요
# train_kaggle.py: pd.read_parquet() → DB 연결 불필요
# ─────────────────────────────────────────────────────────────────────────────
def load_and_preprocess(parquet_dir: str, start_date: str, end_date: str) -> pd.DataFrame:
    parquet_path = os.path.join(parquet_dir, 'price_data.parquet')
    print(f">> parquet 로드 중: {parquet_path}")

    raw_df = pd.read_parquet(parquet_path)
    raw_df['date'] = pd.to_datetime(raw_df['date'])

    raw_df = raw_df[
        (raw_df['date'] >= start_date) &
        (raw_df['date'] <= end_date)
    ].copy()

    print(f">> 로드 완료: {len(raw_df):,}행, {raw_df['ticker'].nunique()}개 종목")

    # 피처 계산
    print(">> 피처 계산 중...")
    processed  = []
    fail_count = 0

    for ticker in tqdm(raw_df['ticker'].unique(), desc="피처 계산"):
        df_t = raw_df[raw_df['ticker'] == ticker].copy()
        try:
            df_t = add_technical_indicators(df_t)
            processed.append(df_t)
        except Exception as e:
            fail_count += 1
            print(f"\n[경고] {ticker} 피처 계산 실패 ({fail_count}/20): {e}")
            if fail_count >= 20:
                raise RuntimeError("피처 계산 실패가 20개를 초과했습니다.")

    if not processed:
        raise ValueError("전처리된 데이터가 없습니다. 날짜 범위나 parquet 파일을 확인하세요.")

    full_df = pd.concat(processed).reset_index(drop=True)
    print(f">> 피처 계산 완료: {len(full_df):,}행 (실패: {fail_count}개)")
    return full_df


def train_model(args: argparse.Namespace):
    print("=" * 50)
    print(" TCN 학습 시작 (Kaggle 크론잡 버전)")
    print("=" * 50)

    # 1. 데이터 로드
    raw_df = load_and_preprocess(args.parquet_dir, args.start_date, args.end_date)

    # 2. Train/Val 날짜 기준 분리
    dates          = raw_df['date'].sort_values().unique()
    if len(dates) < 2:
        raise ValueError(f"날짜 분할을 위한 데이터가 부족합니다. unique dates={len(dates)}")
    split_date_idx = min(max(1, int(len(dates) * 0.9)), len(dates) - 1)
    split_date     = dates[split_date_idx]

    # split_date 미만을 train으로 → val이 비어지는 경계 케이스 방지
    train_df = raw_df[raw_df['date'] <  split_date].copy()
    val_df   = raw_df[raw_df['date'] >= split_date].copy()
    if train_df.empty or val_df.empty:
        raise ValueError(
            f"train/val 분할 결과가 비었습니다. train={len(train_df)}, val={len(val_df)}"
        )
    print(f">> Train: ~{split_date} 미만, Val: {split_date}~")

    # 3. 스케일링 (train만 fit)
    scaler = StandardScaler()
    scaler.fit(train_df[FEATURE_COLUMNS])
    train_df[FEATURE_COLUMNS] = scaler.transform(train_df[FEATURE_COLUMNS])
    val_df[FEATURE_COLUMNS]   = scaler.transform(val_df[FEATURE_COLUMNS])

    # 4. 시퀀스 생성
    X_train, y_train = build_sequences(train_df, args.seq_len, FEATURE_COLUMNS, HORIZONS)
    X_val,   y_val   = build_sequences(val_df,   args.seq_len, FEATURE_COLUMNS, HORIZONS)

    if len(X_train) == 0 or len(X_val) == 0:
        raise ValueError("시퀀스 생성 결과가 비어있습니다.")

    print(f">> Train: {X_train.shape}, Val: {X_val.shape}")

    train_loader = TorchDataLoader(
        TensorDataset(torch.from_numpy(X_train), torch.from_numpy(y_train)),
        batch_size=args.batch_size, shuffle=True
    )
    val_loader = TorchDataLoader(
        TensorDataset(torch.from_numpy(X_val), torch.from_numpy(y_val)),
        batch_size=args.batch_size, shuffle=False
    )

    # 5. 모델
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f">> Device: {device}")

    model = TCNClassifier(
        input_size  = len(FEATURE_COLUMNS),
        output_size = len(HORIZONS),
        num_channels = args.channels,
        kernel_size  = args.kernel_size,
        dropout      = args.dropout,
    ).to(device)

    criterion = nn.BCEWithLogitsLoss()
    optimizer = torch.optim.Adam(
        model.parameters(),
        lr=args.learning_rate,
        weight_decay=args.weight_decay,
    )

    best_val_loss = float("inf")
    best_state    = None

    patience = args.patience
    counter = 0
    # 6. 학습 루프
    print(f">> 학습 시작 (epochs={args.epochs})\n")
    for epoch in range(args.epochs):
        model.train()
        train_loss = 0.0
        for batch_x, batch_y in train_loader:
            batch_x, batch_y = batch_x.to(device), batch_y.to(device)
            optimizer.zero_grad()
            loss = criterion(model(batch_x), batch_y)
            loss.backward()
            optimizer.step()
            train_loss += loss.item() * batch_x.size(0)

        model.eval()
        val_loss = 0.0
        with torch.no_grad():
            for batch_x, batch_y in val_loader:
                batch_x, batch_y = batch_x.to(device), batch_y.to(device)
                val_loss += criterion(model(batch_x), batch_y).item() * batch_x.size(0)

        train_loss /= len(X_train)
        val_loss   /= len(X_val)
        print(f"Epoch [{epoch+1:3d}/{args.epochs}] Train: {train_loss:.4f} | Val: {val_loss:.4f}", end="")

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_state    = copy.deepcopy(model.state_dict())
            counter = 0  # 성능이 개선되었으므로 카운터 초기화
            print("  saved")
        else:
            counter += 1  # 성능 개선이 없으므로 카운터 증가
            print(f"  (Patience: {counter}/{patience})")
        
            if counter >= patience:
                print(f"\n>> [Early Stopping] {patience}번의 에포크 동안 개선이 없어 학습을 중단합니다.")
                break

    # 7. 저장
    os.makedirs(args.output_dir, exist_ok=True)
    model_path    = os.path.join(args.output_dir, "model.pt")
    scaler_path   = os.path.join(args.output_dir, "scaler.pkl")
    metadata_path = os.path.join(args.output_dir, "metadata.json")

    torch.save(best_state or model.state_dict(), model_path)

    with open(scaler_path, "wb") as f:
        pickle.dump(scaler, f)

    metadata = {
        "feature_columns": FEATURE_COLUMNS,
        "horizons"       : HORIZONS,
        "seq_len"        : args.seq_len,
        "kernel_size"    : args.kernel_size,
        "dropout"        : args.dropout,
        "channels"       : args.channels,
        "model_path"     : model_path,
        "scaler_path"    : scaler_path,
    }
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print("\n>> 완료")
    print(f"   모델    : {model_path}")
    print(f"   스케일러: {scaler_path}")
    print(f"   메타데이터: {metadata_path}")


def _find_kaggle_dataset_path() -> str:
    """Kaggle 입력 데이터셋 경로를 자동으로 탐색"""
    base = "/kaggle/input"
    if os.path.exists(base):
        for entry in sorted(os.listdir(base)):
            full = os.path.join(base, entry)
            if os.path.isdir(full) and os.path.exists(os.path.join(full, "price_data.parquet")):
                return full
    return os.environ.get("PARQUET_DIR", base)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train TCN signal model (Kaggle 크론잡 버전)")
    parser.add_argument("--parquet-dir",   default=os.environ.get("PARQUET_DIR", _find_kaggle_dataset_path()))
    parser.add_argument("--start-date",    default="2015-01-01")
    parser.add_argument("--end-date",      default=os.environ.get("END_DATE", date.today().isoformat()))
    parser.add_argument("--seq-len",       type=int,   default=60)
    parser.add_argument("--epochs",        type=int,   default=20)
    parser.add_argument("--batch-size",    type=int,   default=64)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    parser.add_argument("--weight-decay",  type=float, default=1e-4)
    parser.add_argument("--kernel-size",   type=int,   default=3)
    parser.add_argument("--dropout",       type=float, default=0.2)
    parser.add_argument("--channels",      type=int, nargs="+", default=[32, 64, 64])
    parser.add_argument("--patience",      type=int,   default=7)
    parser.add_argument("--output-dir",    default=os.environ.get('WEIGHTS_DIR', '/kaggle/working/tcn'))
    return parser.parse_args()


if __name__ == "__main__":
    train_model(parse_args())


def train():
    """노트북에서 module.train()으로 호출하기 위한 래퍼"""
    import argparse
    args = argparse.Namespace(
        parquet_dir    = '/kaggle/input/sisc-ai-trading-dataset',
        start_date     = "2015-01-01",
        end_date       = os.environ.get("END_DATE", date.today().isoformat()),
        seq_len        = 60,
        epochs         = 20,
        batch_size     = 64,
        learning_rate  = 1e-3,
        weight_decay   = 1e-4,
        kernel_size    = 3,
        dropout        = 0.2,
        channels       = [32, 64, 64],
        patience       = 7,
        output_dir     = "/kaggle/working",
    )
    train_model(args)
