# AI/tests/evaluate_model.py
"""
[시장 전체 시그널 탐지 및 통합 모델 성능 평가]
- S&P500 전체 데이터 등으로 학습된 '단일 글로벌 모델'을 로드합니다.
- 대상 종목들의 데이터를 순차적으로 로드하여, 해당 모델로 예측을 수행합니다.
- 시장 전체에서 모델이 포착한 기회(Signal)들의 성과를 종합적으로 평가합니다.
"""

import os
import sys
import joblib
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score
import warnings
from tqdm import tqdm

# 경고 메시지 숨김
warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", category=FutureWarning)

# ─────────────────────────────────────────────────────────────────────────────
#  경로 및 모듈 로드
# ─────────────────────────────────────────────────────────────────────────────
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.models import get_model
from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.finder.selector import load_all_tickers_from_db

# ─────────────────────────────────────────────────────────────────────────────
#  평가 설정 (CONFIG)
# ─────────────────────────────────────────────────────────────────────────────
MODEL_TYPE = "transformer"
DATA_DIR = os.path.join(project_root, "AI", "data")

CONFIG = {
    "seq_len": 60,     # 학습 시 설정한 Window Size
    "pred_h": 1,       # 예측 기간 (Next Day Return)
    "hold_thr": 0.003, # 0.3% 이상 상승 기대 시 매수
    
    # 단일 글로벌 모델 및 스케일러 경로
    "weights_path": os.path.join(DATA_DIR, "weights", "transformer", "universal_transformer.keras"), # 파일명 확인 필요
    "scaler_path": os.path.join(DATA_DIR, "weights", "transformer", "universal_scaler.pkl"),
    
    # 평가 기간
    "eval_start_date": "2025-01-01",
    "eval_end_date": "2025-10-20",
    
    # 평가 대상: None이면 전체, 리스트면 특정 종목
    "test_tickers": ["AAPL", "TSLA", "MSFT", "NVDA", "GOOGL", "AMD"],
    
    "batch_size": 1024  # 대량 추론을 위해 배치 사이즈 키움
}

# ─────────────────────────────────────────────────────────────────────────────
#  Helper Functions
# ─────────────────────────────────────────────────────────────────────────────
def _label_by_future_return(close_prices: pd.Series, horizon: int, threshold: float) -> pd.Series:
    """미래 수익률 기반 라벨링 (1: 상승/BUY, 0: 하락/보합)"""
    future_ret = (close_prices.shift(-horizon) / close_prices) - 1.0
    labels = np.where(future_ret > threshold, 1, 0)
    labels[-horizon:] = -1 
    return pd.Series(labels, index=close_prices.index), future_ret

def _build_sequences(df: pd.DataFrame, feature_cols: list, seq_len: int) -> np.ndarray:
    data = df[feature_cols].values
    num_samples = len(data) - seq_len + 1
    X = []
    for i in range(num_samples):
        X.append(data[i : i+seq_len])
    return np.array(X)

def _align_labels(target_series: pd.Series, seq_len: int) -> np.ndarray:
    return target_series.iloc[seq_len-1:].values

# ─────────────────────────────────────────────────────────────────────────────
#  메인 평가 함수
# ─────────────────────────────────────────────────────────────────────────────
def evaluate_market_signals():
    print(f"\n[EVAL] 🚀 글로벌 모델(Universal Model) 성능 평가 시작")
    print(f"       기간: {CONFIG['eval_start_date']} ~ {CONFIG['eval_end_date']}")
    
    # 1. 필수 파일 확인
    if not os.path.exists(CONFIG['weights_path']) or not os.path.exists(CONFIG['scaler_path']):
        print(f"[ERR] ❌ 필수 모델 파일이 없습니다.")
        print(f"      Weights: {CONFIG['weights_path']}")
        print(f"      Scaler : {CONFIG['scaler_path']}")
        print("      -> 전체 데이터를 학습시킨 글로벌 모델이 필요합니다.")
        return

    # 2. 글로벌 자원 로드 (모델 & 스케일러)
    print("[EVAL] 모델 및 스케일러 로드 중...")
    try:
        # 스케일러 로드
        global_scaler = joblib.load(CONFIG['scaler_path'])
        
        # 모델 로드 (Config는 로드 시 파일에서 복원되므로 빈 dict)
        # 단, input_shape 등 구조 생성을 위해 기본값은 넣어줌
        model_wrapper = get_model(MODEL_TYPE, {
            "head_size": 256, "num_heads": 4, "ff_dim": 4, # 기본값 (필요시 수정)
            "num_blocks": 4, "mlp_units": [128],
            "dropout": 0.1
        })
        model_wrapper.load(CONFIG['weights_path'])
        print("      ✅ 로드 완료")
        
    except Exception as e:
        print(f"[ERR] 로드 실패: {e}")
        return

    # 3. 대상 종목 선정
    if CONFIG['test_tickers']:
        tickers = CONFIG['test_tickers']
    else:
        print("[EVAL] DB에서 전체 종목 리스트 조회 중...")
        tickers = load_all_tickers_from_db(verbose=False)
    
    print(f"[EVAL] 분석 대상 종목 수: {len(tickers)}개")

    # 4. 데이터 수집 기간 설정
    fetch_start = pd.to_datetime(CONFIG['eval_start_date']) - pd.Timedelta(days=150)
    fetch_start_str = fetch_start.strftime("%Y-%m-%d")

    # 종합 결과 누적용
    global_y_true = []
    global_y_pred = []
    global_returns = []
    
    processed_count = 0

    # 5. 종목별 데이터 로드 및 예측 (Loop)
    for ticker in tqdm(tickers, desc="시그널 스캔 중"):
        try:
            # (1) 데이터 로드 (DB)
            loader = SignalDataLoader(sequence_length=CONFIG['seq_len'])
            loader.scaler = global_scaler # ★ 글로벌 스케일러 주입
            
            df = loader.load_data(ticker, fetch_start_str, CONFIG['eval_end_date'])
            
            if df.empty or len(df) < CONFIG['seq_len']:
                continue

            # ✅ [수정된 부분] 날짜 컬럼을 인덱스로 설정 (안전장치)
            # ------------------------------------------------------------------
            if 'date' in df.columns:
                df['date'] = pd.to_datetime(df['date'])  # datetime 변환 보장
                df.set_index('date', inplace=True)       # 인덱스로 설정
            elif not isinstance(df.index, pd.DatetimeIndex):
                # date 컬럼도 없고 인덱스도 날짜가 아니면 처리 불가
                print(f"[Skip] {ticker}: 날짜 정보를 찾을 수 없습니다.")
                continue
            # ------------------------------------------------------------------

            # (2) 전처리 및 시퀀스 생성
            # feature_cols 생성 시 인덱스(날짜)는 자동으로 제외됨 (np.number만 포함하므로)
            feature_cols = df.select_dtypes(include=[np.number]).columns.tolist()
            
            # 라벨 및 미래 수익률 계산
            labels, future_ret = _label_by_future_return(df["close"], CONFIG['pred_h'], CONFIG['hold_thr'])
            
            # 유효 데이터 마스킹
            valid_mask = df[feature_cols].notna().all(axis=1) & (labels != -1) & future_ret.notna()
            df_valid = df[valid_mask]
            
            # 평가 기간 필터링
            # ✅ 이제 df_valid.index가 DatetimeIndex이므로 정상 작동함
            dates_seq = pd.to_datetime(df_valid.index[CONFIG['seq_len']-1:])
            
            # Timezone 정보가 있다면 제거 (비교를 위해)
            if dates_seq.tz is not None: 
                dates_seq = dates_seq.tz_localize(None)
            
            target_start = pd.to_datetime(CONFIG['eval_start_date'])
            target_end = pd.to_datetime(CONFIG['eval_end_date'])
            
            eval_mask = (dates_seq >= target_start) & (dates_seq <= target_end)
            
            if eval_mask.sum() == 0:
                continue

            # 데이터 변환 (transform only)
            # ★ 학습 때와 동일한 Global Scaler를 사용하여 transform 해야 함
            scaled_vals = loader.scaler.transform(df_valid[feature_cols])
            
            # DataFrame 재구성 (인덱스 유지)
            df_scaled = pd.DataFrame(scaled_vals, columns=feature_cols, index=df_valid.index)
            
            X_seq = _build_sequences(df_scaled, feature_cols, CONFIG['seq_len'])
            y_seq = _align_labels(labels[valid_mask], CONFIG['seq_len'])
            r_seq = _align_labels(future_ret[valid_mask], CONFIG['seq_len'])
            
            X_test = X_seq[eval_mask]
            y_test = y_seq[eval_mask]
            r_test = r_seq[eval_mask]
            
            if len(X_test) == 0: continue

            # (3) 모델 추론 (Loaded Model 사용)
            # verbose=0 으로 설정하여 루프 내 로그 최소화
            y_probs = model_wrapper.predict(X_test) # (N, 1)
            y_pred_class = (y_probs > 0.5).astype(int).flatten()
            
            # (4) 결과 누적
            global_y_true.extend(y_test)
            global_y_pred.extend(y_pred_class)
            global_returns.extend(r_test)
            
            processed_count += 1
            
        except Exception as e:
            # 데이터 불량 등으로 인한 개별 종목 에러는 무시하고 진행
            print(f"[Skip] {ticker}: {e}")
            pass

    # ─────────────────────────────────────────────────────────────────────────────
    #  종합 결과 리포트
    # ─────────────────────────────────────────────────────────────────────────────
    print("\n" + "="*60)
    print("📢 [MARKET-WIDE REPORT] 글로벌 모델 종합 성능 평가")
    print("="*60)
    
    print(f"분석 완료 종목 수 : {processed_count} / {len(tickers)}")
    
    if len(global_y_true) == 0:
        print("\n[ERR] 유효한 평가 데이터가 없습니다. 기간이나 데이터를 확인하세요.")
        return

    # Numpy 변환
    y_true = np.array(global_y_true)
    y_pred = np.array(global_y_pred)
    returns = np.array(global_returns)

    # 1. 분류 성능
    acc = accuracy_score(y_true, y_pred)
    print(f"\n1️⃣  예측 정확도 (Accuracy): {acc*100:.2f}%")
    print(classification_report(y_true, y_pred, target_names=["관망(0)", "매수(1)"]))

    # 2. 투자 성과 분석
    buy_mask = (y_pred == 1)
    n_buys = np.sum(buy_mask)
    
    print(f"\n2️⃣  투자 시뮬레이션 결과")
    print(f"    - 총 샘플(거래일) 수 : {len(y_true)}")
    print(f"    - 매수 시그널 발생   : {n_buys}회 (발생률 {n_buys/len(y_true)*100:.1f}%)")
    
    if n_buys > 0:
        # 매수 시그널 발생 시 실제 수익률 분포
        buy_returns = returns[buy_mask]
        
        avg_return = np.mean(buy_returns)
        win_rate = np.mean(buy_returns > 0)
        
        # 손익비 계산
        wins = buy_returns[buy_returns > 0]
        losses = buy_returns[buy_returns < 0]
        avg_win = np.mean(wins) if len(wins) > 0 else 0
        avg_loss = np.abs(np.mean(losses)) if len(losses) > 0 else 0
        profit_factor = avg_win / avg_loss if avg_loss > 0 else float('inf')

        print(f"    --------------------------------------------------")
        print(f"    ★ 기대 수익률 (Avg Return) : {avg_return*100:.3f}%")
        print(f"    ★ 적중률 (Win Rate)        : {win_rate*100:.2f}%")
        print(f"    ★ 손익비 (Profit Factor)   : {profit_factor:.2f}")
        print(f"    --------------------------------------------------")
        
        # 벤치마크 비교
        market_avg = np.mean(returns)
        print(f"    (시장 평균 수익률: {market_avg*100:.3f}%)")
        
        if avg_return > market_avg:
            print("    ✅ 모델이 시장 평균을 상회했습니다.")
        else:
            print("    ⚠️ 모델 성과가 시장 평균보다 낮습니다.")
    else:
        print("    [!] 매수 시그널이 발생하지 않았습니다.")

if __name__ == "__main__":
    evaluate_market_signals()