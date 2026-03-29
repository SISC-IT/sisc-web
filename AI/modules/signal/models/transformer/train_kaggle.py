# AI/modules/signal/models/transformer/train_kaggle.py
"""
Transformer 학습 스크립트 - Kaggle/GitHub Actions 버전
-----------------------------------------------
[train.py와의 차이점]
- DB 연결 없음 (DataLoader.load_data_from_db 사용 안 함)
- parquet 파일에서 직접 로드
- GitHub Actions 자동화 파이프라인에서 사용

[train.py는 그대로 유지]
- 로컬/서버에서 DB 연결로 학습할 때 사용
-----------------------------------------------
"""
import os
import sys
import pickle
import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
import tensorflow as tf
from tensorflow.keras.callbacks import ModelCheckpoint, EarlyStopping, ReduceLROnPlateau

# ─────────────────────────────────────────────────────────────────────────────
# GPU 설정
# ─────────────────────────────────────────────────────────────────────────────
print("텐서플로우 버전:", tf.__version__)
print("GPU 목록:", tf.config.list_physical_devices('GPU'))

gpus = tf.config.list_physical_devices('GPU')
if gpus:
    try:
        for gpu in gpus:
            tf.config.experimental.set_memory_growth(gpu, True)
        print(f"🚀 GPU {len(gpus)}대 사용")
    except RuntimeError as e:
        print(e)
else:
    print("🐢 CPU 사용")

# ─────────────────────────────────────────────────────────────────────────────
# 경로 설정
# ─────────────────────────────────────────────────────────────────────────────
current_dir  = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.models.transformer.architecture import build_transformer_model
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.features.legacy.technical_features import (
    add_technical_indicators,
    add_multi_timeframe_features
)

# ─────────────────────────────────────────────────────────────────────────────
# CONFIG
# ─────────────────────────────────────────────────────────────────────────────
CONFIG = {
    # parquet 경로 (환경변수로 주입 가능)
    'parquet_dir' : os.environ.get(
        'PARQUET_DIR',
        '/kaggle/input/datasets/jihyeongkimm/sisc-ai-trading-dataset'
    ),
    'start_date'  : '2015-01-01',
    'end_date'    : '2023-12-31',
    'seq_len'     : 60,
    'batch_size'  : 32,
    'epochs'      : 50,

    # 저장 경로 (환경변수로 주입 가능)
    'weights_dir' : os.environ.get('WEIGHTS_DIR', '/kaggle/working'),
    'model_name'  : 'multi_horizon_model.keras',
    'scaler_name' : 'multi_horizon_scaler.pkl',
}

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


# ─────────────────────────────────────────────────────────────────────────────
# [핵심 변경] 데이터 로드
# train.py: DataLoader.load_data_from_db() → DB 연결 필요
# train_kaggle.py: pd.read_parquet() → DB 연결 불필요
# ─────────────────────────────────────────────────────────────────────────────
def load_and_preprocess():
    parquet_path = os.path.join(CONFIG['parquet_dir'], 'price_data.parquet')
    print(f">> parquet 로드 중: {parquet_path}")

    raw_df = pd.read_parquet(parquet_path)
    raw_df['date'] = pd.to_datetime(raw_df['date'])

    raw_df = raw_df[
        (raw_df['date'] >= CONFIG['start_date']) &
        (raw_df['date'] <= CONFIG['end_date'])
    ].copy()

    print(f">> 로드 완료: {len(raw_df):,}행, {raw_df['ticker'].nunique()}개 종목")

    # 피처 계산
    print(">> 피처 계산 중...")
    processed  = []
    fail_count = 0

    for ticker in raw_df['ticker'].unique():
        df_t = raw_df[raw_df['ticker'] == ticker].copy()
        try:
            df_t = add_technical_indicators(df_t)
            df_t = add_multi_timeframe_features(df_t)
            processed.append(df_t)
        except Exception as e:
            fail_count += 1
            if fail_count >= 20:
                raise RuntimeError("피처 계산 실패가 20개를 초과했습니다.")

    full_df = pd.concat(processed).reset_index(drop=True)
    print(f">> 피처 계산 완료: {len(full_df):,}행 (실패: {fail_count}개)")
    return full_df


def train_single_pipeline():
    print("=" * 50)
    print(" Transformer 학습 시작 (Kaggle/Actions 버전)")
    print("=" * 50)

    # 1. 데이터 로드
    full_df = load_and_preprocess()

    # 2. DataLoader로 시퀀스 생성
    # DataLoader의 create_dataset만 사용 (load_data_from_db는 사용 안 함)
    loader = DataLoader(lookback=CONFIG['seq_len'])

    X_ts, X_ticker, X_sector, y_class, _, info = loader.create_dataset(
        full_df,
        feature_columns=TRANSFORMER_TRAIN_FEATURES,
    )

    horizons  = info.get("horizons", [1, 3, 5, 7])
    n_outputs = len(horizons)

    print(f"\n>> 시퀀스 생성 완료: {X_ts.shape}")
    print(f">> horizon: {horizons}")

    # 3. Train/Val 분리
    X_ts_train, X_ts_val, \
    X_tick_train, X_tick_val, \
    X_sec_train, X_sec_val, \
    y_train, y_val = train_test_split(
        X_ts, X_ticker, X_sector, y_class,
        test_size=0.2, shuffle=True, random_state=42
    )

    # 4. 모델 빌드
    model = build_transformer_model(
        input_shape=(X_ts.shape[1], X_ts.shape[2]),
        n_tickers=info['n_tickers'],
        n_sectors=info['n_sectors'],
        n_outputs=n_outputs
    )

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.0001),
        loss="binary_crossentropy",
        metrics=["accuracy"]
    )

    # 5. 저장 경로
    save_dir    = CONFIG['weights_dir']
    os.makedirs(save_dir, exist_ok=True)
    model_path  = os.path.join(save_dir, CONFIG['model_name'])
    scaler_path = os.path.join(save_dir, CONFIG['scaler_name'])

    # 6. 콜백
    callbacks = [
        ModelCheckpoint(
            filepath=model_path,
            monitor='val_loss',
            save_best_only=True,
            verbose=1
        ),
        EarlyStopping(
            monitor='val_loss',
            patience=10,
            restore_best_weights=True
        ),
        ReduceLROnPlateau(
            monitor='val_loss',
            factor=0.5,
            patience=5,
            min_lr=1e-6,
            verbose=1
        ),
    ]

    # 7. 학습
    print(f">> 학습 시작 (epochs={CONFIG['epochs']})")
    model.fit(
        x=[X_ts_train, X_tick_train, X_sec_train],
        y=y_train,
        validation_data=([X_ts_val, X_tick_val, X_sec_val], y_val),
        epochs=CONFIG['epochs'],
        batch_size=CONFIG['batch_size'],
        shuffle=True,
        callbacks=callbacks
    )

    # 8. 스케일러 저장
    with open(scaler_path, "wb") as f:
        pickle.dump(info['scaler'], f)

    print(f"\n>> 완료")
    print(f"   모델    : {model_path}")
    print(f"   스케일러: {scaler_path}")


if __name__ == "__main__":
    train_single_pipeline()


def train():
    """노트북에서 module.train()으로 호출하기 위한 래퍼"""
    train_single_pipeline()
