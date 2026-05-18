import argparse
import copy
import json
import os
import pickle
import sys
from typing import Dict, List, Tuple

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from sklearn.preprocessing import StandardScaler
from torch.utils.data import DataLoader as TorchDataLoader
from torch.utils.data import TensorDataset

current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import DataLoader
from AI.config import load_trading_config
from AI.modules.signal.core.artifact_paths import resolve_model_artifacts
from AI.modules.signal.models.TCN.architecture import TCNClassifier
from AI.modules.signal.models.TCN.preprocessing import (
    SUPPORTED_TCN_FEATURE_SET_VERS,
    TECHNICAL_DAILY_V1,
    get_tcn_feature_columns,
    normalize_tcn_feature_set_ver,
    prepare_tcn_standard_data,
)


FEATURE_COLUMNS = get_tcn_feature_columns(TECHNICAL_DAILY_V1)

HORIZONS = [1, 3, 5, 7]


def build_sequences(
    df: pd.DataFrame,
    seq_len: int,
    feature_cols: List[str],
    horizons: List[int],
) -> Tuple[np.ndarray, np.ndarray]:
    """
    미리 스케일링이 완료된 DataFrame을 받아 [Batch, Seq, Features] 형태의 윈도우와 라벨을 생성합니다.
    """
    features = []
    labels = []
    max_horizon = max(horizons)

    for _, sub_df in df.groupby("ticker"):
        sub_df = sub_df.sort_values("date").copy()
        sub_df = sub_df.dropna(subset=["close"])

        if len(sub_df) < seq_len + max_horizon:
            continue

        feature_values = sub_df[feature_cols].to_numpy(dtype=np.float32)
        closes = sub_df["close"].to_numpy(dtype=np.float32)

        for start in range(len(sub_df) - seq_len - max_horizon + 1):
            end = start + seq_len
            current_close = closes[end - 1]
            
            target = []
            for horizon in horizons:
                future_close = closes[end + horizon - 1]
                # 미래 종가가 현재 종가보다 크면 상승(1), 아니면 하락(0)
                target.append(1.0 if future_close > current_close else 0.0)

            features.append(feature_values[start:end])
            labels.append(target)

    # 빈 배열 처리
    if not features:
        return np.empty((0, seq_len, len(feature_cols)), dtype=np.float32), np.empty((0, len(horizons)), dtype=np.float32)

    return np.array(features, dtype=np.float32), np.array(labels, dtype=np.float32)


def train_model(args: argparse.Namespace):
    _validate_train_args(args)
    output_dir = os.path.abspath(args.output_dir)
    feature_set_ver = normalize_tcn_feature_set_ver(args.feature_set_ver)
    feature_columns = get_tcn_feature_columns(feature_set_ver)

    # 1. DB에서 원시 데이터를 로드한 뒤 ticker별로 표준 전처리를 적용한다.
    # 여러 ticker를 한 번에 FeatureProcessor에 넣으면 주봉/월봉 join에서 row가 증폭될 수 있다.
    loader = DataLoader()
    raw_price_df = loader.load_data_from_db(
        args.start_date,
        args.end_date,
        tickers=[str(ticker) for ticker in args.tickers] if args.tickers else None,
    )
    raw_df = prepare_tcn_standard_data(
        raw_price_df,
        stage_name="TCN train",
        feature_set_ver=feature_set_ver,
    )

    if raw_df.empty:
        raise ValueError("No raw price data loaded from DB.")

    if args.tickers:
        if "ticker" not in raw_df.columns:
            raise ValueError("--tickers 필터를 적용하려면 raw_df에 ticker 컬럼이 필요합니다.")
        requested_tickers = {str(ticker) for ticker in args.tickers}
        ticker_mask = raw_df["ticker"].astype(str).isin(requested_tickers)
        raw_df = raw_df[ticker_mask].copy()
        if raw_df.empty:
            raise ValueError(f"--tickers 필터 결과 데이터가 비었습니다: {sorted(requested_tickers)}")
        print(
            "티커 필터 - "
            f"요청={sorted(requested_tickers)}, 선택={raw_df['ticker'].nunique()}"
        )

    # 예: 전체 기간의 80% 시점을 기준으로 날짜를 분할합니다.
    dates = raw_df['date'].sort_values().unique()
    if len(dates) < 2:
        raise ValueError(f"train/validation 분할에 필요한 날짜가 부족합니다. unique dates={len(dates)}")
    split_date_idx = int(len(dates) * 0.8) # 전체 날짜의 80% 지점에서 분할 날짜 인덱스 계산
    split_date_idx = min(max(0, split_date_idx), len(dates) - 2)
    split_date = dates[split_date_idx]

    train_df = raw_df[raw_df['date'] <= split_date].copy()
    val_df = raw_df[raw_df['date'] > split_date].copy()
    if train_df.empty or val_df.empty:
        raise ValueError(
            f"train/validation 분할 결과가 비었습니다. train={len(train_df)}, val={len(val_df)}"
        )

    print(f"Data Split - Train: ~{split_date}, Validation: {split_date}~")
    print(f"Feature Set - {feature_set_ver}, feature_count={len(feature_columns)}")

    # 2D 형태에서 스케일링 수행 (안전하고 직관적)
    scaler = StandardScaler()
    # Train 데이터로만 스케일러 학습 (Validation 데이터 유출 방지)
    scaler.fit(train_df[feature_columns])

    train_df[feature_columns] = scaler.transform(train_df[feature_columns])
    val_df[feature_columns] = scaler.transform(val_df[feature_columns])

    # 4. 스케일링된 DataFrame으로 3D 시퀀스(윈도우) 텐서 구축
    X_train, y_train = build_sequences(train_df, args.seq_len, feature_columns, HORIZONS)
    X_val, y_val = build_sequences(val_df, args.seq_len, feature_columns, HORIZONS)

    if len(X_train) == 0 or len(X_val) == 0:
        raise ValueError("Insufficient data to create sequences for either train or validation set.")

    train_dataset = TensorDataset(torch.from_numpy(X_train), torch.from_numpy(y_train))
    val_dataset = TensorDataset(torch.from_numpy(X_val), torch.from_numpy(y_val))

    train_loader = TorchDataLoader(train_dataset, batch_size=args.batch_size, shuffle=True)
    val_loader = TorchDataLoader(val_dataset, batch_size=args.batch_size, shuffle=False)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")
    
    model = TCNClassifier(
        input_size=len(feature_columns),
        output_size=len(HORIZONS),
        num_channels=args.channels,
        kernel_size=args.kernel_size,
        dropout=args.dropout,
    ).to(device)

    criterion = nn.BCEWithLogitsLoss()
    optimizer = torch.optim.Adam(
        model.parameters(),
        lr=args.learning_rate,
        weight_decay=args.weight_decay,
    )

    best_val_loss = float("inf")
    best_train_loss = None
    best_state = None
    patience = int(args.patience)
    early_stopping_enabled = patience > 0
    patience_counter = 0

    # 5. 모델 학습 루프
    for epoch in range(args.epochs):
        model.train()
        train_loss = 0.0
        for batch_x, batch_y in train_loader:
            batch_x, batch_y = batch_x.to(device), batch_y.to(device)

            optimizer.zero_grad()
            logits = model(batch_x)
            loss = criterion(logits, batch_y)
            loss.backward()
            optimizer.step()

            train_loss += loss.item() * batch_x.size(0)

        model.eval()
        val_loss = 0.0
        with torch.no_grad():
            for batch_x, batch_y in val_loader:
                batch_x, batch_y = batch_x.to(device), batch_y.to(device)
                logits = model(batch_x)
                loss = criterion(logits, batch_y)
                val_loss += loss.item() * batch_x.size(0)

        train_loss /= len(train_dataset)
        val_loss /= len(val_dataset)
        print(
            f"Epoch {epoch + 1}/{args.epochs} - "
            f"train_loss: {train_loss:.4f} val_loss: {val_loss:.4f}",
            end="",
        )

        # 검증 손실이 최저일 때의 train/val loss와 checkpoint를 sweep 비교 기준으로 남긴다.
        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_train_loss = train_loss
            best_state = copy.deepcopy(model.state_dict())
            patience_counter = 0
            print(" 저장")
        else:
            if early_stopping_enabled:
                patience_counter += 1
                print(f" (조기 종료 대기: {patience_counter}/{patience})")
                if patience_counter >= patience:
                    print(f"조기 종료 - 검증 손실이 {patience} epoch 동안 개선되지 않았습니다.")
                    break
            else:
                print()

    if best_state is None:
        best_train_loss = train_loss
        best_val_loss = val_loss
        best_state = copy.deepcopy(model.state_dict())

    train_val_loss_gap = float(best_val_loss - best_train_loss)

    # 6. 아티팩트(가중치, 스케일러, 메타데이터) 파일 시스템 저장
    os.makedirs(output_dir, exist_ok=True)
    model_path = os.path.join(output_dir, "model.pt")
    scaler_path = os.path.join(output_dir, "scaler.pkl")
    metadata_path = os.path.join(output_dir, "metadata.json")

    torch.save(best_state, model_path)
    with open(scaler_path, "wb") as f:
        pickle.dump(scaler, f)

    metadata: Dict[str, object] = {
        "feature_set_ver": feature_set_ver,
        "feature_columns": feature_columns,
        "feature_count": len(feature_columns),
        "horizons": HORIZONS,
        "seq_len": args.seq_len,
        "kernel_size": args.kernel_size,
        "dropout": args.dropout,
        "channels": args.channels,
        "weight_decay": args.weight_decay,
        "patience": args.patience,
        "learning_rate": args.learning_rate,
        "batch_size": args.batch_size,
        "train_loss_best": float(best_train_loss),
        "val_loss_best": float(best_val_loss),
        "train_val_loss_gap": train_val_loss_gap,
        "tickers": list(args.tickers) if args.tickers else None,
        "model_path": model_path,
        "scaler_path": scaler_path,
    }
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print("\n학습 완료")
    print(f"모델 저장: {model_path}")
    print(f"스케일러 저장: {scaler_path}")
    print(f"메타데이터 저장: {metadata_path}")


def parse_args() -> argparse.Namespace:
    default_output_dir = resolve_model_artifacts(
        model_name="tcn",
        config_weights_dir=load_trading_config().model.weights_dir,
    ).model_dir

    parser = argparse.ArgumentParser(description="TCN 시그널 모델 학습.")
    parser.add_argument("--start-date", default="2018-01-01")
    parser.add_argument("--end-date", default="2024-01-01")
    parser.add_argument("--tickers", nargs="*", default=None)
    parser.add_argument("--seq-len", type=int, default=60)
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    parser.add_argument("--weight-decay", type=float, default=0.0)
    parser.add_argument("--kernel-size", type=int, default=3)
    parser.add_argument("--dropout", type=float, default=0.2)
    parser.add_argument("--channels", type=int, nargs="+", default=[32, 64, 64])
    parser.add_argument("--patience", type=int, default=0)
    parser.add_argument(
        "--feature-set-ver",
        default=TECHNICAL_DAILY_V1,
        choices=SUPPORTED_TCN_FEATURE_SET_VERS,
        help="TCN 입력 피처셋 버전",
    )
    parser.add_argument("--output-dir", default=default_output_dir)
    return parser.parse_args()


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


if __name__ == "__main__":
    train_model(parse_args())
