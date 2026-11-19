# AI/modules/signal/models/transformer/train_kaggle.py
"""
Transformer 학습 스크립트 - Kaggle 크론잡 버전
-----------------------------------------------
[train.py와의 차이점]
- DB 연결 없음 (DataLoader.load_data_from_db 사용 안 함)
- parquet 파일에서 직접 로드
- 서버 크론잡이 Kaggle 커널을 push할 때 사용

[train.py는 그대로 유지]
- 로컬/서버에서 DB 연결로 학습할 때 사용
-----------------------------------------------
"""
import os
import sys
import json
import pickle
import warnings
import logging
import numpy as np
import pandas as pd

# 불필요한 로그 억제
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'
warnings.filterwarnings('ignore')
logging.getLogger('tensorflow').setLevel(logging.ERROR)

import tensorflow as tf
tf.get_logger().setLevel('ERROR')
from tensorflow.keras.callbacks import ModelCheckpoint, EarlyStopping, ReduceLROnPlateau

# ─────────────────────────────────────────────────────────────────────────────
# GPU 설정
# ─────────────────────────────────────────────────────────────────────────────
print("텐서플로우 버전:", tf.__version__)
print("GPU 목록:", tf.config.list_physical_devices('GPU'))

gpus = tf.config.list_physical_devices('GPU')
print(f"[INFO] GPU devices: {gpus}")
print("[INFO] Using GPU" if gpus else "[INFO] Using CPU")
if gpus:
    try:
        for gpu in gpus:
            tf.config.experimental.set_memory_growth(gpu, True)
        print(f"GPU {len(gpus)}대 사용")
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
from AI.modules.signal.core.time_split import chronological_split, resolve_validation_start
from sklearn.preprocessing import StandardScaler
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
    'end_date'    : os.environ.get('END_DATE', pd.Timestamp.today().strftime('%Y-%m-%d')),
    'seq_len'     : 60,
    'batch_size'  : 32,
    'epochs'      : 50,
    'validation_start': os.environ.get('VALIDATION_START') or None,

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
                raise RuntimeError("피처 계산 실패가 20개를 초과했습니다.") from e

    if not processed:
        raise ValueError(f"전처리된 데이터가 없습니다. 실패 종목 수={fail_count}")

    full_df = pd.concat(processed).reset_index(drop=True)
    print(f">> 피처 계산 완료: {len(full_df):,}행 (실패: {fail_count}개)")
    return full_df


def build_sequences_transformer(full_df, scaler, ticker_to_id, sector_to_id):
    """학습 구간에 fit한 scaler로 전체 시퀀스와 실제 라벨 종료일을 생성한다."""
    seq_len    = CONFIG['seq_len']
    horizons   = [1, 3, 5, 7]
    max_h      = max(horizons)
    available  = [c for c in TRANSFORMER_TRAIN_FEATURES if c in full_df.columns]

    full_df = full_df.dropna(subset=available).copy()
    full_df[available] = scaler.transform(full_df[available])

    X_ts, X_tick, X_sec, y_list = [], [], [], []
    sample_rows = []

    for ticker, group in full_df.groupby('ticker'):
        group   = group.sort_values('date').reset_index(drop=True)
        sector  = group['sector'].iloc[0] if 'sector' in group.columns else 'Unknown'
        tick_id = ticker_to_id.get(ticker, 0)
        sec_id  = sector_to_id.get(sector, 0)

        if len(group) < seq_len + max_h:
            continue

        feat_vals = group[available].values
        closes    = group['close'].values
        dates     = pd.to_datetime(group['date']).to_numpy()

        for i in range(len(group) - seq_len - max_h + 1):
            window = feat_vals[i:i + seq_len]
            curr   = closes[i + seq_len - 1]
            labels = [1.0 if closes[i + seq_len + h - 1] > curr else 0.0 for h in horizons]
            X_ts.append(window)
            X_tick.append(tick_id)
            X_sec.append(sec_id)
            y_list.append(labels)
            sample_rows.append({
                'ticker': ticker,
                'asof_date': dates[i + seq_len - 1],
                'label_end_max': dates[i + seq_len + max_h - 1],
            })

    return (np.array(X_ts, dtype=np.float32),
            np.array(X_tick, dtype=np.int32),
            np.array(X_sec,  dtype=np.int32),
            np.array(y_list, dtype=np.float32),
            len(ticker_to_id), len(sector_to_id),
            pd.DataFrame(sample_rows, columns=['ticker', 'asof_date', 'label_end_max']))


def train_single_pipeline():
    print("=" * 50)
    print(" Transformer 학습 시작 (Kaggle 크론잡 버전)")
    print("=" * 50)

    # 1. 데이터 로드
    full_df = load_and_preprocess()

    # stock_info parquet에서 sector 정보 병합
    stock_info_path = os.path.join(CONFIG['parquet_dir'], 'stock_info.parquet')
    if os.path.exists(stock_info_path):
        stock_info = pd.read_parquet(stock_info_path)[['ticker', 'sector']]
        full_df = full_df.merge(stock_info, on='ticker', how='left')
        full_df['sector'] = full_df['sector'].fillna('Unknown')
        print(f">> sector 병합 완료")

    horizons  = [1, 3, 5, 7]
    n_outputs = len(horizons)

    # 2. 모든 종목에 공통 날짜 경계 적용; scaler는 경계 이전 행에만 fit.
    available = [c for c in TRANSFORMER_TRAIN_FEATURES if c in full_df.columns]
    full_df = full_df.dropna(subset=available).copy()
    validation_start = resolve_validation_start(
        full_df['date'], validation_start=CONFIG['validation_start']
    )
    scaler_frame = full_df[full_df['date'] < validation_start]
    if scaler_frame.empty:
        raise ValueError("scaler fit에 사용할 train feature row가 없습니다.")
    scaler = StandardScaler()
    scaler.fit(scaler_frame[available])

    # 3. 전체 이력으로 윈도우를 만든 뒤 분할하여 검증의 과거 입력 문맥을 유지.
    all_tickers = sorted(full_df['ticker'].unique())
    all_sectors = sorted(full_df['sector'].unique() if 'sector' in full_df.columns else ['Unknown'])
    ticker_to_id = {ticker: idx for idx, ticker in enumerate(all_tickers)}
    sector_to_id = {sector: idx for idx, sector in enumerate(all_sectors)}
    X_ts, X_tick, X_sec, y, n_tickers, n_sectors, rows = build_sequences_transformer(
        full_df, scaler, ticker_to_id, sector_to_id,
    )
    train_mask, val_mask = chronological_split(
        rows['asof_date'], rows['label_end_max'], validation_start
    )
    X_ts_train, X_tick_train, X_sec_train, y_train = (
        values[train_mask] for values in (X_ts, X_tick, X_sec, y)
    )
    X_ts_val, X_tick_val, X_sec_val, y_val = (
        values[val_mask] for values in (X_ts, X_tick, X_sec, y)
    )
    train_rows, val_rows = rows.loc[train_mask], rows.loc[val_mask]
    split_summary = {
        'method': 'purged_time_split',
        'validation_start': str(validation_start.date()),
        'n_candidates': len(rows),
        'n_train': int(train_mask.sum()),
        'n_validation': int(val_mask.sum()),
        'n_purged': int((~(train_mask | val_mask)).sum()),
        'train_asof_min': str(train_rows['asof_date'].min().date()),
        'train_asof_max': str(train_rows['asof_date'].max().date()),
        'train_label_end_max': str(train_rows['label_end_max'].max().date()),
        'validation_asof_min': str(val_rows['asof_date'].min().date()),
        'validation_asof_max': str(val_rows['asof_date'].max().date()),
        'validation_label_end_max': str(val_rows['label_end_max'].max().date()),
        'scaler_fit_rows': len(scaler_frame),
        'scaler_fit_date_min': str(scaler_frame['date'].min().date()),
        'scaler_fit_date_max': str(scaler_frame['date'].max().date()),
        'seq_len': CONFIG['seq_len'],
        'horizons': horizons,
    }
    print(f">> 날짜 분할: {json.dumps(split_summary, ensure_ascii=False)}")

    print(f"\n>> 시퀀스 생성 완료: {X_ts_train.shape}")
    print(f">> horizon: {horizons}")

    # 4. 모델 빌드
    model = build_transformer_model(
        input_shape=(X_ts_train.shape[1], X_ts_train.shape[2]),
        n_tickers=n_tickers,
        n_sectors=n_sectors,
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
    split_path = os.path.splitext(model_path)[0] + '.split.json'

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
        callbacks=callbacks,
        verbose=2
    )

    # 8. 스케일러 저장
    with open(scaler_path, "wb") as f:
        pickle.dump(scaler, f)
    with open(split_path, "w", encoding="utf-8") as f:
        json.dump(split_summary, f, ensure_ascii=False, indent=2)

    print(f"\n>> 완료")
    print(f"   모델    : {model_path}")
    print(f"   스케일러: {scaler_path}")
    print(f"   분할 기록: {split_path}")


if __name__ == "__main__":
    train_single_pipeline()


def train():
    """노트북에서 module.train()으로 호출하기 위한 래퍼"""
    train_single_pipeline()
