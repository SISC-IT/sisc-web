# AI/modules/signal/models/transformer/train.py
import argparse
import os
import sys
import pickle
import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
import tensorflow as tf
from tensorflow.keras.callbacks import ModelCheckpoint, EarlyStopping, ReduceLROnPlateau

# --------------------------------------------------------------------------
# 1. GPU 및 환경 설정
# --------------------------------------------------------------------------
print("텐서플로우 버전:", tf.__version__)
print("GPU 목록:", tf.config.list_physical_devices('GPU'))
print("\n" + "="*50)

# OOM(메모리 부족) 방지를 위한 GPU 메모리 점진적 할당 설정
gpus = tf.config.list_physical_devices('GPU')
if gpus:
    try:
        for gpu in gpus:
            tf.config.experimental.set_memory_growth(gpu, True)
        print(f"🚀 GPU 발견됨! ({len(gpus)}대): {gpus}")
    except RuntimeError as e:
        print(e)
else:
    print("🐢 GPU 없음... CPU 사용합니다.")
print("="*50 + "\n")

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.signal.models.transformer.architecture import build_transformer_model
from AI.config import load_trading_config
from AI.modules.signal.core.artifact_paths import resolve_model_artifacts

TRANSFORMER_TRAIN_FEATURES = [
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
    "week_ma20_ratio",
    "week_rsi",
    "week_bb_pos",
    "week_vol_change",
    "month_ma12_ratio",
    "month_rsi",
]

def _resolve_transformer_artifacts(
    save_dir: str | None,
    artifact_mode: str,
) -> tuple[str, str, str]:
    if save_dir:
        artifacts = resolve_model_artifacts(
            model_name="transformer",
            mode=artifact_mode,
            model_dir=save_dir,
        )
    else:
        trading_config = load_trading_config()
        artifacts = resolve_model_artifacts(
            model_name="transformer",
            mode=artifact_mode,
            config_weights_dir=trading_config.model.weights_dir,
        )

    return artifacts.model_dir, artifacts.model_path, artifacts.scaler_path


def train_single_pipeline(save_dir: str | None = None, artifact_mode: str = "tests"):
    print("==================================================")
    print(" [Training] Multi-Horizon Model (1, 3, 5, 7 Days)")
    print("==================================================")

    # --------------------------------------------------------------------------
    # 2. 데이터 로드 및 기간 분리 (Data Leakage 방지)
    # --------------------------------------------------------------------------
    loader = DataLoader(lookback=60)
    print(">> 데이터 로딩 및 지표 생성 중...")
    
    # 전체 데이터를 가져온 뒤...
    full_df = loader.load_data_from_db(start_date="2015-01-01")
    
    # [핵심] 2023년 12월 31일 이전 데이터만 학습에 사용! (미래 데이터 차단)
    raw_df = full_df[full_df['date'] <= '2023-12-31'].copy()
    
    print(f">> 학습 데이터 기간: {raw_df['date'].min()} ~ {raw_df['date'].max()}")
    print(f">> 총 데이터 행 수: {len(raw_df)} rows")

    # --------------------------------------------------------------------------
    # 3. 데이터셋 생성 (Sequencing)
    # --------------------------------------------------------------------------
    # y_class는 (N, 4) 형태: [1일뒤, 3일뒤, 5일뒤, 7일뒤]
    X_ts, X_ticker, X_sector, y_class, _, info = loader.create_dataset(
        raw_df,
        feature_columns=TRANSFORMER_TRAIN_FEATURES,
    )
    
    # [디버그] 정답 분포 확인
    horizons = info.get("horizons", [1])
    n_outputs = len(horizons) # 보통 4개
    
    print("\n" + "="*50)
    print(f" 🚨 [DEBUG] Multi-Horizon 데이터 점검 ({horizons}일)")
    print("="*50)
    for i, h in enumerate(horizons):
        col_data = y_class[:, i]
        unique, counts = np.unique(col_data, return_counts=True)
        dist = dict(zip(unique, counts))
        ratio = counts[1] / sum(counts) * 100 if 1 in dist else 0
        print(f" - [{h}일 뒤] 상승 비율: {ratio:.2f}% (분포: {dist})")
    print("="*50 + "\n")

    # --------------------------------------------------------------------------
    # 4. 데이터 분할 (Train / Validation)
    # --------------------------------------------------------------------------
    X_ts_train, X_ts_val, \
    X_tick_train, X_tick_val, \
    X_sec_train, X_sec_val, \
    y_train, y_val = train_test_split(
        X_ts, X_ticker, X_sector, y_class,
        test_size=0.2, shuffle=True, random_state=42
    )
    
    # --------------------------------------------------------------------------
    # 5. 모델 빌드
    # --------------------------------------------------------------------------
    print(f">> 모델 빌드 중 (Outputs: {n_outputs})...")
    
    model = build_transformer_model(
        input_shape=(X_ts.shape[1], X_ts.shape[2]),
        n_tickers=info['n_tickers'],
        n_sectors=info['n_sectors'],
        n_outputs=n_outputs # 4개 출력
    )
    
    optimizer = tf.keras.optimizers.Adam(learning_rate=0.0001)
    
    model.compile(
        optimizer=optimizer,
        loss="binary_crossentropy", 
        metrics=["accuracy"]
    )

    # --------------------------------------------------------------------------
    # 6. 콜백 설정 (학습 전략의 핵심)
    # --------------------------------------------------------------------------
    save_dir, model_save_path, scaler_save_path = _resolve_transformer_artifacts(
        save_dir=save_dir,
        artifact_mode=artifact_mode,
    )
    os.makedirs(save_dir, exist_ok=True)

    # (1) 최고 성능 모델 저장 (전성기 캡처)
    chk_point = ModelCheckpoint(
        filepath=model_save_path,
        monitor='val_loss',
        save_best_only=True, 
        verbose=1
    )
    
    # (2) 조기 종료 (10번 참았는데 안 좋아지면 멈춤)
    early_stop = EarlyStopping(
        monitor='val_loss',
        patience=10,
        restore_best_weights=True
    )

    # (3) 학습률 조정 (5번 참았는데 안 좋아지면 더 세밀하게 학습)
    reduce_lr = ReduceLROnPlateau(
        monitor='val_loss',
        factor=0.5,
        patience=5,
        min_lr=1e-6,
        verbose=1
    )

    # --------------------------------------------------------------------------
    # 7. 학습 시작
    # --------------------------------------------------------------------------
    print(">> 학습 시작 (Epochs=50)...")
    model.fit(
        x=[X_ts_train, X_tick_train, X_sec_train],
        y=y_train,
        validation_data=([X_ts_val, X_tick_val, X_sec_val], y_val),
        epochs=50,
        batch_size=32, # [중요] OOM 방지를 위해 32로 설정
        shuffle=True,
        callbacks=[chk_point, early_stop, reduce_lr] # 콜백 적용
    )
    
    # --------------------------------------------------------------------------
    # 8. 스케일러 저장 (필수)
    # --------------------------------------------------------------------------
    # 모델은 chk_point가 이미 저장했으므로, 스케일러만 따로 저장합니다.
    with open(scaler_save_path, "wb") as f:
        pickle.dump(info['scaler'], f)
        
    print(f"\n[완료] 학습 종료. 모델 및 스케일러가 저장되었습니다.")
    print(f" - 모델 경로: {model_save_path}")
    print(f" - 스케일러: {scaler_save_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train transformer signal model.")
    parser.add_argument("--save-dir", default=None, help="Optional artifact output directory override.")
    parser.add_argument(
        "--artifact-mode",
        default="tests",
        choices=["tests", "test", "prod", "production", "simulation", "live"],
        help="Artifact mode used for default filename resolution.",
    )
    args = parser.parse_args()

    train_single_pipeline(save_dir=args.save_dir, artifact_mode=args.artifact_mode)
