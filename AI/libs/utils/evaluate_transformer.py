import os
import sys
import pickle
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score
import warnings

# 경고 메시지 숨김 (버전 차이 경고 등)
warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", category=FutureWarning)

# ─────────────────────────────────────────────────────────────────────────────
#  설정 및 경로
# ─────────────────────────────────────────────────────────────────────────────
_this_file = os.path.abspath(__file__)
project_root = os.path.dirname(os.path.dirname(_this_file))           # .../transformer
repo_root    = os.path.dirname(project_root)         # .../AI

if project_root not in sys.path: sys.path.append(project_root)
if repo_root not in sys.path: sys.path.append(repo_root)

from modules.models import build_transformer_classifier
from modules.features import FEATURES, build_features
from AI.transformer.training.training_transformer import (
    _fetch_db_ohlcv_for_tickers, 
    load_all_tickers_from_db,
    _label_by_future_return,
    _build_sequences,
    _align_labels
)

# ─────────────────────────────────────────────────────────────────────────────
#  평가 설정 (CONFIG)
# ─────────────────────────────────────────────────────────────────────────────
CONFIG = {
    "seq_len": 128,
    "pred_h": 7,       
    "hold_thr": 0.003, 
    
    # 경로 호환성을 위해 os.path.join 사용
    "weights_path": os.path.join(project_root, "weights", "initial.weights.h5"),
    "scaler_path": os.path.join(project_root, "scaler", "scaler.pkl"),
    
    # 평가하고 싶은 "결과"의 기간
    "eval_start_date": "2025-01-01", 
    "eval_end_date": "2025-10-31",
    
    # None이면 전체 종목, 리스트면 특정 종목만
    "test_tickers": None, 
    "batch_size": 512
}

def evaluate_model():
    print(f"\n[EVAL] 🚀 Transformer 모델 평가 시작")
    print(f"       평가 대상 기간: {CONFIG['eval_start_date']} ~ {CONFIG['eval_end_date']}")
    
    # 1. 파일 확인
    if not os.path.exists(CONFIG['weights_path']) or not os.path.exists(CONFIG['scaler_path']):
        print(f"[ERR] ❌ 필수 파일 누락.\n      {CONFIG['weights_path']}\n      먼저 학습(train_transformer.py)을 실행하세요.")
        return

    # 2. 스케일러 로드
    try:
        with open(CONFIG['scaler_path'], "rb") as f:
            scaler = pickle.load(f)
        print("[EVAL] ✅ 스케일러 로드 완료")
    except Exception as e:
        print(f"[ERR] 스케일러 로드 중 오류: {e}")
        return

    # 3. 티커 로드
    if CONFIG['test_tickers']:
        tickers = CONFIG['test_tickers']
    else:
        print("[EVAL] DB에서 전체 티커 목록 조회 중...")
        tickers = load_all_tickers_from_db(verbose=False)
    
    # 4. 데이터 수집 (Warm-up 기간 포함하여 넉넉하게 조회)
    fetch_start = pd.to_datetime(CONFIG['eval_start_date']) - pd.Timedelta(days=365)
    fetch_start_str = fetch_start.strftime("%Y-%m-%d")
    
    print(f"[EVAL] 데이터 수집 중... (조회 시작일: {fetch_start_str}, 대상: {len(tickers)}개)")
    
    raw_df = _fetch_db_ohlcv_for_tickers(tickers, fetch_start_str, CONFIG['eval_end_date'])
    
    if raw_df.empty:
        print("[ERR] ❌ 해당 기간의 데이터가 DB에 전혀 없습니다. DB 상태를 확인하세요.")
        return

    print(f"[EVAL] 수집된 데이터 행 수: {len(raw_df)} rows")

    # 5. 전처리
    model_feats = [f for f in FEATURES if f != "CLOSE_RAW"]
    X_all, y_all = [], []
    returns_all = [] 

    print("[EVAL] 피처 엔지니어링 및 시퀀스 변환 중...")
    
    count_skipped = 0
    count_ok = 0

    for t, g in raw_df.groupby("ticker"):
        g = g.rename(columns={'ts_local': 'date'}).set_index('date')
        g = g.sort_index()
        
        # 피처 생성
        feats = build_features(g)
        
        # 라벨 생성
        labels = _label_by_future_return(feats["CLOSE_RAW"], CONFIG['pred_h'], CONFIG['hold_thr'])
        
        # 수익률 (단순화: 종가 대 종가)
        future_ret = (feats["CLOSE_RAW"].shift(-CONFIG['pred_h']) / feats["CLOSE_RAW"]) - 1.0
        
        # 유효 데이터 필터링
        valid = feats.notna().all(axis=1) & labels.notna() & future_ret.notna()
        feats = feats[valid]
        labels = labels[valid]
        future_ret = future_ret[valid]
        
        if len(feats) < CONFIG['seq_len']:
            count_skipped += 1
            continue

        # 시퀀스 생성
        X_seq = _build_sequences(feats, model_feats, CONFIG['seq_len'])
        y_seq = _align_labels(feats, labels, CONFIG['seq_len'])
        r_seq = _align_labels(feats, future_ret, CONFIG['seq_len'])
        
        # 날짜 인덱스도 같이 정렬해서 가져옴
        dates_seq = feats.index[CONFIG['seq_len']-1:]

        # 길이 맞추기
        min_len = min(len(X_seq), len(y_seq), len(r_seq), len(dates_seq))
        X_seq = X_seq[:min_len]
        y_seq = y_seq[:min_len]
        r_seq = r_seq[:min_len]
        dates_seq = dates_seq[:min_len]
        
        # ─────────────────────────────────────────────────────────────────────────────
        # [수정] 인덱스 정규화 및 타임존 처리 (Robust Fix)
        # ─────────────────────────────────────────────────────────────────────────────
        # 1) MultiIndex 처리 (Date, Ticker)
        if isinstance(dates_seq, pd.MultiIndex):
            dates_seq = dates_seq.get_level_values(0)
        
        # 2) 튜플 형태의 일반 Index 처리 (dtype=object)
        #    예: Index([(Timestamp(...), 'KRW-BTC'), ...], dtype='object')
        elif dates_seq.dtype == 'object' and len(dates_seq) > 0 and isinstance(dates_seq[0], tuple):
            dates_seq = pd.Index([x[0] for x in dates_seq])

        # 3) DatetimeIndex로 확실하게 변환
        dates_seq = pd.to_datetime(dates_seq)

        # 4) 타임존(Timezone) 호환성 처리
        target_start = pd.to_datetime(CONFIG['eval_start_date'])
        target_end = pd.to_datetime(CONFIG['eval_end_date']) + pd.Timedelta(days=1)

        if dates_seq.tz is not None:
            # 데이터가 TZ-aware라면, 비교 날짜도 해당 TZ로 변환
            if target_start.tz is None:
                target_start = target_start.tz_localize(dates_seq.tz)
            else:
                target_start = target_start.tz_convert(dates_seq.tz)
                
            if target_end.tz is None:
                target_end = target_end.tz_localize(dates_seq.tz)
            else:
                target_end = target_end.tz_convert(dates_seq.tz)
        else:
            # 데이터가 Naive라면, 비교 날짜도 Naive로 유지 (혹시 Aware라면 제거)
            if target_start.tz is not None:
                target_start = target_start.tz_localize(None)
            if target_end.tz is not None:
                target_end = target_end.tz_localize(None)

        # ★ 평가 기간 필터링
        eval_mask = (dates_seq >= target_start) & (dates_seq <= target_end)
        
        if eval_mask.sum() == 0:
            count_skipped += 1
            continue

        X_all.append(X_seq[eval_mask])
        y_all.append(y_seq[eval_mask])
        returns_all.append(r_seq[eval_mask])
        count_ok += 1

    if not X_all:
        print(f"\n[ERR] ❌ 유효 시퀀스 생성 실패.")
        print(f"      - 총 종목 수: {len(tickers)}")
        print(f"      - 데이터 부족으로 스킵된 종목: {count_skipped}")
        return

    X = np.concatenate(X_all, axis=0)
    y = np.concatenate(y_all, axis=0).astype(int)
    r = np.concatenate(returns_all, axis=0)

    print(f"[EVAL] 생성된 테스트 샘플 수: {len(X)}개 (사용 종목: {count_ok}개)")

    # 6. 스케일링 & 예측
    n, s, f = X.shape
    X_reshaped = X.reshape(-1, f)
    X_scaled = scaler.transform(X_reshaped).reshape(n, s, f)

    print("[EVAL] 모델 예측 수행 중...")
    model = build_transformer_classifier(CONFIG['seq_len'], f)
    model.load_weights(CONFIG['weights_path'])
    
    y_pred_probs = model.predict(X_scaled, batch_size=CONFIG['batch_size'], verbose=1)
    y_pred = np.argmax(y_pred_probs, axis=1)

    # ─────────────────────────────────────────────────────────────────────────────
    #  결과 리포트
    # ─────────────────────────────────────────────────────────────────────────────
    print("\n" + "="*60)
    print("📢 [RESULT] Transformer 모델 성능 평가 리포트")
    print("="*60)

    acc = accuracy_score(y, y_pred)
    print(f"\n1️⃣  정확도 (Accuracy): {acc*100:.2f}%")
    print(classification_report(y, y_pred, target_names=["BUY", "HOLD", "SELL"]))

    print("\n2️⃣  혼동 행렬 (Confusion Matrix):")
    cm = confusion_matrix(y, y_pred)
    print(f"      Pred: BUY  HOLD  SELL")
    print(f"Actual BUY  {cm[0]}")
    print(f"Actual HOLD {cm[1]}")
    print(f"Actual SELL {cm[2]}")
    
    print("\n3️⃣  수익률 시뮬레이션 (단순 가정)")
    buy_signals = (y_pred == 0)
    avg_ret_buy = np.mean(r[buy_signals]) if np.sum(buy_signals) > 0 else 0.0
    mkt_avg = np.mean(r)
    
    real_wins = r[buy_signals] > CONFIG['hold_thr']
    win_rate = np.mean(real_wins) if len(real_wins) > 0 else 0.0

    print(f"총 거래 기회: {len(r)}")
    print(f"BUY 신호 수 : {np.sum(buy_signals)}")
    print("-" * 30)
    print(f"BUY 평균 수익률 : {avg_ret_buy*100:.4f}%  (vs 시장평균 {mkt_avg*100:.4f}%)")
    print(f"BUY 적중률      : {win_rate*100:.2f}%")
    print("-" * 30)

if __name__ == "__main__":
    evaluate_model()