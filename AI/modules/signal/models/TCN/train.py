import argparse
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

from AI.modules.signal.core.dataset_builder import get_standard_training_data
from AI.config import load_trading_config
from AI.modules.signal.core.artifact_paths import resolve_model_artifacts
from AI.modules.signal.models.TCN.architecture import TCNClassifier


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
    output_dir = os.path.abspath(args.output_dir)

    # 1. DB에서 원시 데이터 로드 및 파이프라인 표준 전처리 적용 (단일 데이터프레임 반환)
    # [주의] get_standard_training_data가 원본 DB 로드 기능까지 수행하므로 DataLoader 별도 호출 불필요
    raw_df = get_standard_training_data(args.start_date, args.end_date)

    if raw_df.empty:
        raise ValueError("No raw price data loaded from DB.")

    # 예: 전체 기간의 80% 시점을 기준으로 날짜를 분할합니다.
    dates = raw_df['date'].sort_values().unique()
    split_date_idx = int(len(dates) * 0.8) # 전체 날짜의 80% 지점에서 분할 날짜 인덱스 계산
    split_date = dates[split_date_idx]

    train_df = raw_df[raw_df['date'] <= split_date].copy()
    val_df = raw_df[raw_df['date'] > split_date].copy()

    print(f"Data Split - Train: ~{split_date}, Validation: {split_date}~")

    # 2D 형태에서 스케일링 수행 (안전하고 직관적)
    scaler = StandardScaler()
    # Train 데이터로만 스케일러 학습 (Validation 데이터 유출 방지)
    scaler.fit(train_df[FEATURE_COLUMNS])

    train_df[FEATURE_COLUMNS] = scaler.transform(train_df[FEATURE_COLUMNS])
    val_df[FEATURE_COLUMNS] = scaler.transform(val_df[FEATURE_COLUMNS])

    # 4. 스케일링된 DataFrame으로 3D 시퀀스(윈도우) 텐서 구축
    X_train, y_train = build_sequences(train_df, args.seq_len, FEATURE_COLUMNS, HORIZONS)
    X_val, y_val = build_sequences(val_df, args.seq_len, FEATURE_COLUMNS, HORIZONS)

    if len(X_train) == 0 or len(X_val) == 0:
        raise ValueError("Insufficient data to create sequences for either train or validation set.")

    train_dataset = TensorDataset(torch.from_numpy(X_train), torch.from_numpy(y_train))
    val_dataset = TensorDataset(torch.from_numpy(X_val), torch.from_numpy(y_val))

    train_loader = TorchDataLoader(train_dataset, batch_size=args.batch_size, shuffle=True)
    val_loader = TorchDataLoader(val_dataset, batch_size=args.batch_size, shuffle=False)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")
    
    model = TCNClassifier(
        input_size=len(FEATURE_COLUMNS),
        output_size=len(HORIZONS),
        num_channels=args.channels,
        kernel_size=args.kernel_size,
        dropout=args.dropout,
    ).to(device)

    criterion = nn.BCEWithLogitsLoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=args.learning_rate)

    best_val_loss = float("inf")
    best_state = None

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
        print(f"Epoch {epoch + 1}/{args.epochs} - train_loss: {train_loss:.4f} val_loss: {val_loss:.4f}")

        # 검증 손실이 최저일 때 모델 가중치 저장 (Early Stopping 준비)
        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_state = model.state_dict()

    if best_state is None:
        best_state = model.state_dict()

    # 6. 아티팩트(가중치, 스케일러, 메타데이터) 파일 시스템 저장
    os.makedirs(output_dir, exist_ok=True)
    model_path = os.path.join(output_dir, "model.pt")
    scaler_path = os.path.join(output_dir, "scaler.pkl")
    metadata_path = os.path.join(output_dir, "metadata.json")

    torch.save(best_state, model_path)
    with open(scaler_path, "wb") as f:
        pickle.dump(scaler, f)

    metadata: Dict[str, object] = {
        "feature_columns": FEATURE_COLUMNS,
        "horizons": HORIZONS,
        "seq_len": args.seq_len,
        "kernel_size": args.kernel_size,
        "dropout": args.dropout,
        "channels": args.channels,
        "model_path": model_path,
        "scaler_path": scaler_path,
    }
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print("\n✅ Training Complete!")
    print(f"Saved model to: {model_path}")
    print(f"Saved scaler to: {scaler_path}")
    print(f"Saved metadata to: {metadata_path}")


def parse_args() -> argparse.Namespace:
    default_output_dir = resolve_model_artifacts(
        model_name="tcn",
        config_weights_dir=load_trading_config().model.weights_dir,
    ).model_dir

    parser = argparse.ArgumentParser(description="Train TCN signal model.")
    parser.add_argument("--start-date", default="2018-01-01")
    parser.add_argument("--end-date", default="2024-01-01")
    parser.add_argument("--tickers", nargs="*", default=None)
    parser.add_argument("--seq-len", type=int, default=60)
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    parser.add_argument("--kernel-size", type=int, default=3)
    parser.add_argument("--dropout", type=float, default=0.2)
    parser.add_argument("--channels", type=int, nargs="+", default=[32, 64, 64])
    parser.add_argument("--output-dir", default=default_output_dir)
    return parser.parse_args()


if __name__ == "__main__":
    train_model(parse_args())
