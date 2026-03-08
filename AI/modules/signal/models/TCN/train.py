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

from AI.modules.features.legacy.technical_features import add_technical_indicators
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.signal.models.TCN.architecture import TCNClassifier


# TCN은 명세에 맞춰 개별 기술적 지표만 사용합니다.
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

# 서비스 추론 결과와 맞추기 위해 1/3/5/7일 방향성을 동시에 학습합니다.
HORIZONS = [1, 3, 5, 7]


def build_sequences(
    df: pd.DataFrame,
    seq_len: int,
    feature_cols: List[str],
    horizons: List[int],
) -> Tuple[np.ndarray, np.ndarray]:
    # 종목별 시계열을 순회하면서 [seq_len, features] 윈도우와 멀티 호라이즌 라벨을 만듭니다.
    features = []
    labels = []
    max_horizon = max(horizons)

    for _, sub_df in df.groupby("ticker"):
        sub_df = sub_df.sort_values("date").copy()
        sub_df = add_technical_indicators(sub_df)
        sub_df = sub_df.dropna(subset=["close"])
        sub_df = sub_df.replace([np.inf, -np.inf], np.nan).fillna(0)

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
                target.append(1.0 if future_close > current_close else 0.0)

            features.append(feature_values[start:end])
            labels.append(target)

    if not features:
        raise ValueError("No training sequences were created. Check DB data coverage.")

    return np.array(features, dtype=np.float32), np.array(labels, dtype=np.float32)


def fit_scaler(X_train: np.ndarray) -> StandardScaler:
    # 시퀀스 축을 펼쳐 feature 단위로 표준화 스케일러를 학습합니다.
    scaler = StandardScaler()
    scaler.fit(X_train.reshape(-1, X_train.shape[-1]))
    return scaler


def transform_sequences(X: np.ndarray, scaler: StandardScaler) -> np.ndarray:
    # 학습 시 저장한 스케일러를 시퀀스 전체에 동일하게 적용합니다.
    shape = X.shape
    flat = X.reshape(-1, shape[-1])
    scaled = scaler.transform(flat)
    return scaled.reshape(shape).astype(np.float32)


def train_model(args: argparse.Namespace):
    # 공통 DataLoader로 DB에서 가격 데이터를 읽고 TCN 전용 입력만 추립니다.
    loader = DataLoader(lookback=args.seq_len, horizons=HORIZONS)
    raw_df = loader.load_data_from_db(
        start_date=args.start_date,
        end_date=args.end_date,
        tickers=args.tickers,
    )

    if raw_df.empty:
        raise ValueError("No raw price data loaded from DB.")

    X, y = build_sequences(raw_df, args.seq_len, FEATURE_COLUMNS, HORIZONS)

    # 시계열 순서를 유지한 채 뒤쪽 구간을 검증셋으로 둡니다.
    split_idx = max(int(len(X) * 0.8), 1)
    if split_idx >= len(X):
        split_idx = len(X) - 1

    X_train, X_val = X[:split_idx], X[split_idx:]
    y_train, y_val = y[:split_idx], y[split_idx:]

    scaler = fit_scaler(X_train)
    X_train = transform_sequences(X_train, scaler)
    X_val = transform_sequences(X_val, scaler)

    train_dataset = TensorDataset(torch.from_numpy(X_train), torch.from_numpy(y_train))
    val_dataset = TensorDataset(torch.from_numpy(X_val), torch.from_numpy(y_val))

    train_loader = TorchDataLoader(train_dataset, batch_size=args.batch_size, shuffle=True)
    val_loader = TorchDataLoader(val_dataset, batch_size=args.batch_size, shuffle=False)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    # output_size는 horizon 개수와 동일합니다.
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

    for epoch in range(args.epochs):
        model.train()
        train_loss = 0.0
        for batch_x, batch_y in train_loader:
            batch_x = batch_x.to(device)
            batch_y = batch_y.to(device)

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
                batch_x = batch_x.to(device)
                batch_y = batch_y.to(device)
                logits = model(batch_x)
                loss = criterion(logits, batch_y)
                val_loss += loss.item() * batch_x.size(0)

        train_loss /= len(train_dataset)
        val_loss /= len(val_dataset)
        print(
            f"Epoch {epoch + 1}/{args.epochs} "
            f"- train_loss: {train_loss:.4f} val_loss: {val_loss:.4f}"
        )

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_state = model.state_dict()

    if best_state is None:
        best_state = model.state_dict()

    # wrapper가 바로 사용할 수 있도록 가중치, scaler, 메타데이터를 함께 저장합니다.
    os.makedirs(args.output_dir, exist_ok=True)
    model_path = os.path.join(args.output_dir, "model.pt")
    scaler_path = os.path.join(args.output_dir, "scaler.pkl")
    metadata_path = os.path.join(args.output_dir, "metadata.json")

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
        json.dump(metadata, f, ensure_ascii=True, indent=2)

    print(f"Saved model to: {model_path}")
    print(f"Saved scaler to: {scaler_path}")
    print(f"Saved metadata to: {metadata_path}")


def parse_args() -> argparse.Namespace:
    # 실험 시 종목, 기간, 모델 폭을 CLI에서 바로 바꿀 수 있게 둡니다.
    parser = argparse.ArgumentParser(description="Train TCN signal model.")
    parser.add_argument("--start-date", default="2018-01-01")
    parser.add_argument("--end-date", default=None)
    parser.add_argument("--tickers", nargs="*", default=None)
    parser.add_argument("--seq-len", type=int, default=60)
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    parser.add_argument("--kernel-size", type=int, default=3)
    parser.add_argument("--dropout", type=float, default=0.2)
    parser.add_argument(
        "--channels",
        type=int,
        nargs="+",
        default=[32, 64, 64],
    )
    parser.add_argument(
        "--output-dir",
        default=os.path.join(project_root, "AI", "data", "weights", "tcn"),
    )
    return parser.parse_args()


if __name__ == "__main__":
    train_model(parse_args())
