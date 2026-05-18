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


def log_gpu_status() -> None:
    if torch.cuda.is_available():
        devices = [torch.cuda.get_device_name(i) for i in range(torch.cuda.device_count())]
        print(f"[INFO] GPU devices: {devices}")
        print("[INFO] Using GPU")
    else:
        print("[INFO] GPU devices: []")
        print("[INFO] Using CPU")

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.models.TCN.architecture import TCNClassifier
from AI.modules.signal.models.TCN.preprocessing import (
    SUPPORTED_TCN_FEATURE_SET_VERS,
    TECHNICAL_DAILY_V1,
    get_tcn_feature_columns,
    log_ticker_date_counts,
    normalize_tcn_feature_set_ver,
    prepare_tcn_feature_set,
    validate_processed_row_count,
    validate_unique_ticker_date,
)
from AI.modules.features.legacy.technical_features import add_technical_indicators

# ─────────────────────────────────────────────────────────────────────────────
# 피처 정의 (train.py와 동일)
# ─────────────────────────────────────────────────────────────────────────────
FEATURE_COLUMNS = get_tcn_feature_columns(TECHNICAL_DAILY_V1)

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
def load_and_preprocess(
    parquet_dir: str,
    start_date: str,
    end_date: str,
    tickers: List[str] | None = None,
    feature_set_ver: str = TECHNICAL_DAILY_V1,
) -> pd.DataFrame:
    feature_set_ver = normalize_tcn_feature_set_ver(feature_set_ver)
    parquet_path = os.path.join(parquet_dir, 'price_data.parquet')
    print(f">> parquet 로드 중: {parquet_path}")

    raw_df = pd.read_parquet(parquet_path)
    raw_df['date'] = pd.to_datetime(raw_df['date'])

    raw_df = raw_df[
        (raw_df['date'] >= start_date) &
        (raw_df['date'] <= end_date)
    ].copy()

    if tickers:
        requested_tickers = {str(ticker) for ticker in tickers}
        raw_df = raw_df[raw_df["ticker"].astype(str).isin(requested_tickers)].copy()
        if raw_df.empty:
            raise ValueError(f"--tickers 필터 결과 데이터가 비었습니다: {sorted(requested_tickers)}")
        print(
            ">> 티커 필터 적용: "
            f"요청={sorted(requested_tickers)}, 선택={raw_df['ticker'].nunique()}개"
        )

    raw_df = validate_unique_ticker_date(raw_df, stage_name="TCN kaggle raw")
    log_ticker_date_counts(raw_df, stage_name="TCN kaggle raw")
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
    full_df = prepare_tcn_feature_set(
        full_df,
        feature_set_ver=feature_set_ver,
        stage_name=f"TCN kaggle feature_set={feature_set_ver}",
    )
    full_df = validate_unique_ticker_date(full_df, stage_name="TCN kaggle processed")
    validate_processed_row_count(raw_df, full_df, stage_name="TCN kaggle processed")
    log_ticker_date_counts(full_df, stage_name="TCN kaggle processed")
    print(f">> 피처 계산 완료: {len(full_df):,}행 (실패: {fail_count}개)")
    return full_df


def train_model(args: argparse.Namespace):
    _validate_train_args(args)
    feature_set_ver = normalize_tcn_feature_set_ver(args.feature_set_ver)
    feature_columns = get_tcn_feature_columns(feature_set_ver)

    print("=" * 50)
    print(" TCN 학습 시작 (Kaggle 크론잡 버전)")
    print("=" * 50)

    # 1. 데이터 로드
    raw_df = load_and_preprocess(
        args.parquet_dir,
        args.start_date,
        args.end_date,
        args.tickers,
        feature_set_ver,
    )

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
    print(f">> Feature Set: {feature_set_ver}, feature_count={len(feature_columns)}")

    # 3. 스케일링 (train만 fit)
    train_df[feature_columns] = (
        train_df[feature_columns]
        .replace([np.inf, -np.inf], np.nan)
        .fillna(0)
        .clip(-1e6, 1e6)
    )
    val_df[feature_columns] = (
        val_df[feature_columns]
        .replace([np.inf, -np.inf], np.nan)
        .fillna(0)
        .clip(-1e6, 1e6)
    )
    scaler = StandardScaler()
    scaler.fit(train_df[feature_columns])
    train_df[feature_columns] = scaler.transform(train_df[feature_columns])
    val_df[feature_columns]   = scaler.transform(val_df[feature_columns])

    # 4. 시퀀스 생성
    X_train, y_train = build_sequences(train_df, args.seq_len, feature_columns, HORIZONS)
    X_val,   y_val   = build_sequences(val_df,   args.seq_len, feature_columns, HORIZONS)

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
    log_gpu_status()
    print(f">> Device: {device}")

    model = TCNClassifier(
        input_size  = len(feature_columns),
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
    best_train_loss = None
    best_state    = None

    patience = int(args.patience)
    early_stopping_enabled = patience > 0
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
            best_train_loss = train_loss
            best_state    = copy.deepcopy(model.state_dict())
            counter = 0  # 성능이 개선되었으므로 카운터 초기화
            print("  saved")
        else:
            if not early_stopping_enabled:
                print()
                continue
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

    if best_train_loss is None:
        best_train_loss = train_loss
        best_val_loss = val_loss
    train_val_loss_gap = float(best_val_loss - best_train_loss)

    torch.save(best_state or model.state_dict(), model_path)

    with open(scaler_path, "wb") as f:
        pickle.dump(scaler, f)

    metadata = {
        "feature_set_ver": feature_set_ver,
        "feature_columns": feature_columns,
        "feature_count": len(feature_columns),
        "horizons"       : HORIZONS,
        "seq_len"        : args.seq_len,
        "kernel_size"    : args.kernel_size,
        "dropout"        : args.dropout,
        "channels"       : args.channels,
        "weight_decay"   : args.weight_decay,
        "patience"       : args.patience,
        "learning_rate"  : args.learning_rate,
        "batch_size"     : args.batch_size,
        "train_loss_best": float(best_train_loss),
        "val_loss_best"  : float(best_val_loss),
        "train_val_loss_gap": train_val_loss_gap,
        "tickers"         : list(args.tickers) if args.tickers else None,
        "model_path"     : model_path,
        "scaler_path"    : scaler_path,
    }
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print("\n>> 완료")
    print(f"   모델    : {model_path}")
    print(f"   스케일러: {scaler_path}")
    print(f"   메타데이터: {metadata_path}")


def _validate_train_args(args: argparse.Namespace) -> None:
    for option_name, value in [
        ("--weight-decay", args.weight_decay),
        ("--learning-rate", args.learning_rate),
        ("--dropout", args.dropout),
    ]:
        if not np.isfinite(float(value)):
            raise ValueError(f"{option_name}는 유한한 숫자여야 합니다.")
    if args.weight_decay < 0:
        raise ValueError("--weight-decay는 0 이상이어야 합니다.")
    if not 0 <= args.dropout < 1:
        raise ValueError("--dropout은 0 이상 1 미만이어야 합니다.")
    if args.patience < 0:
        raise ValueError("--patience는 0 이상이어야 합니다. 0이면 early stopping을 끕니다.")
    if args.epochs <= 0:
        raise ValueError("--epochs는 1 이상이어야 합니다.")
    if args.batch_size <= 0:
        raise ValueError("--batch-size는 1 이상이어야 합니다.")
    if args.seq_len <= 0:
        raise ValueError("--seq-len은 1 이상이어야 합니다.")
    if args.learning_rate <= 0:
        raise ValueError("--learning-rate는 0보다 커야 합니다.")
    if args.kernel_size <= 0:
        raise ValueError("--kernel-size는 1 이상이어야 합니다.")
    if not args.channels or any(channel <= 0 for channel in args.channels):
        raise ValueError("--channels는 1 이상의 정수 목록이어야 합니다.")
    normalize_tcn_feature_set_ver(args.feature_set_ver)


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
    parser.add_argument("--tickers",       nargs="*", default=None)
    parser.add_argument("--seq-len",       type=int,   default=60)
    parser.add_argument("--epochs",        type=int,   default=20)
    parser.add_argument("--batch-size",    type=int,   default=64)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    parser.add_argument("--weight-decay",  type=float, default=1e-4)
    parser.add_argument("--kernel-size",   type=int,   default=3)
    parser.add_argument("--dropout",       type=float, default=0.2)
    parser.add_argument("--channels",      type=int, nargs="+", default=[32, 64, 64])
    parser.add_argument("--patience",      type=int,   default=7)
    parser.add_argument(
        "--feature-set-ver",
        default=TECHNICAL_DAILY_V1,
        choices=SUPPORTED_TCN_FEATURE_SET_VERS,
        help="TCN 입력 피처셋 버전",
    )
    parser.add_argument("--output-dir",    default=os.environ.get('WEIGHTS_DIR', '/kaggle/working/tcn'))
    return parser.parse_args()


if __name__ == "__main__":
    train_model(parse_args())


def train():
    """노트북에서 module.train()으로 호출하기 위한 래퍼"""
    import argparse
    args = argparse.Namespace(
        parquet_dir    = os.environ.get("PARQUET_DIR", _find_kaggle_dataset_path()),
        start_date     = "2015-01-01",
        end_date       = os.environ.get("END_DATE", date.today().isoformat()),
        tickers        = None,
        seq_len        = 60,
        epochs         = 20,
        batch_size     = 64,
        learning_rate  = 1e-3,
        weight_decay   = 1e-4,
        kernel_size    = 3,
        dropout        = 0.2,
        channels       = [32, 64, 64],
        patience       = 7,
        feature_set_ver= TECHNICAL_DAILY_V1,
        output_dir     = "/kaggle/working",
    )
    train_model(args)
