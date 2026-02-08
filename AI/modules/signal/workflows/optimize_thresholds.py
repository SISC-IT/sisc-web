#AI/modules/signal/workflows/optimize_thresholds.py
"""
[Threshold Optimizer]
- 학습된 범용 모델을 로드합니다.
- 각 종목별로 과거 데이터를 예측해봅니다.
- "0.55점에 살 때", "0.60점에 살 때"... 등 시뮬레이션을 돌려
- 수익률이 가장 좋았던 '최적의 매수 커트라인'을 찾아 JSON으로 저장합니다.
"""

import os
import sys
import json
import pickle
import numpy as np
import pandas as pd
import tensorflow as tf
from tqdm import tqdm

# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.libs.database.connection import get_db_conn
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.signal.models.PatchTST.architecture import build_transformer_model
from AI.modules.features.legacy.technical_features import add_technical_indicators, add_multi_timeframe_features

def optimize_thresholds():
    print("==================================================")
    print(" 🎯 종목별 최적 매수/매도 기준(Threshold) 찾기")
    print("==================================================")

    # 1. 경로 설정 (절대 경로 확인!)
    current_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
    weights_dir = os.path.join(project_root, "AI/data/weights/transformer")
    model_path = os.path.join(weights_dir, "tests", "multi_horizon_model.keras")
    scaler_path = os.path.join(weights_dir, "tests", "multi_horizon_scaler.pkl")
    output_path = os.path.join(weights_dir, "ticker_thresholds.json")

    if not os.path.exists(model_path):
        print("❌ 학습된 모델이 없습니다. train_single.py를 먼저 실행하세요.")
        return
    
    # 순서 변경: DataLoader를 먼저 불러서, 학습할 때 섹터가 몇 개였는지 알아냅니다.
    print(">> 메타데이터 확인 중...")
    loader = DataLoader() 
    
    # 학습된 DB 상태에 따라 개수가 달라지므로, 직접 세어봅니다.
    real_n_tickers = len(loader.ticker_to_id)
    real_n_sectors = len(loader.sector_to_id)
    print(f"   - 감지된 Tickers: {real_n_tickers}, Sectors: {real_n_sectors}")

    # 2. 모델 빌드 (이제 숫자를 정확하게 넣습니다)
    print(">> 모델 로드 중...")
    model = build_transformer_model(
        input_shape=(60, 17), 
        n_tickers=real_n_tickers,
        n_sectors=real_n_sectors,
        n_outputs=4 
    )

    try:
        model.load_weights(model_path)
    except ValueError as e:
        print(f"\n🚨 [치명적 에러] 모델 구조 불일치!")
        print(f"학습된 가중치와 현재 설정된 종목/섹터 수가 다릅니다.")
        print(f"에러 메시지: {e}")
        return

    with open(scaler_path, "rb") as f:
        scaler = pickle.load(f)

    # 3. 데이터 로드 (최근 2년치)
    print(">> 데이터 로드 중 (최근 2년)...")
    df = loader.load_data_from_db(start_date="2022-01-01")
    # 검증용이므로 최근 데이터만 가져옴 (너무 옛날 데이터로 최적화하면 안 맞을 수 있음)
    df = loader.load_data_from_db(start_date="2022-01-01")
    
    # 4. 피처 엔지니어링 (학습 때와 동일)
    tickers = df['ticker'].unique()
    optimized_results = {}

    print(f">> {len(tickers)}개 종목에 대해 시뮬레이션 시작...")
    
    # 테스트할 임계값 후보들 (0.55 ~ 0.80)
    # 너무 낮으면(0.5) 아무거나 다 사서 위험, 너무 높으면(0.9) 살 게 없음
    threshold_candidates = [0.55, 0.60, 0.65, 0.70, 0.75]
    
    feature_cols = [
        'log_return', 'open_ratio', 'high_ratio', 'low_ratio', 'vol_change',
        'ma5_ratio', 'ma20_ratio', 'ma60_ratio', 'rsi', 'macd_ratio', 'bb_position',
        'week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change',
        'month_ma12_ratio', 'month_rsi'
    ]

    for ticker in tqdm(tickers):
        try:
            # 종목별 데이터 추출
            sub_df = df[df['ticker'] == ticker].copy().sort_values('date')
            
            # 지표 생성
            sub_df = add_technical_indicators(sub_df)
            try:
                sub_df = add_multi_timeframe_features(sub_df)
            except:
                continue # 데이터 부족 등으로 실패 시 패스

            sub_df.dropna(subset=feature_cols, inplace=True)
            
            if len(sub_df) < 80: continue # 데이터 너무 적으면 패스

            # ----------------------------------------------------
            # 모델 예측 (Inference)
            # ----------------------------------------------------
            # 전처리 (Scaling) - 중요: fit 금지, transform만!
            values = sub_df[feature_cols].values
            scaled_values = scaler.transform(values)

            # 시퀀스 데이터 만들기 (Sliding Window)
            X = []
            valid_indices = [] # 예측값이 실제 데이터프레임의 어디에 해당하는지
            
            lookback = 60
            for i in range(len(scaled_values) - lookback):
                window = scaled_values[i : i + lookback]
                X.append(window)
                # i + lookback - 1 이 현재 시점
                valid_indices.append(i + lookback - 1)
            
            if not X: continue

            X = np.array(X)
            # 임베딩용 ID (여기선 0으로 통일해도 무방, 최적화 목적이므로)
            dummy_ticker = np.zeros((len(X), 1))
            dummy_sector = np.zeros((len(X), 1))

            # 예측 실행 (Batch로 한방에)
            preds = model.predict([X, dummy_ticker, dummy_sector], verbose=0)
            
            # [1,3,5,7]일 예측 평균값 사용 (Trend Score)
            # preds shape: (N, 4) -> (N,)
            trend_scores = np.mean(preds[:, 1:], axis=1) # 1,3,5,7 중 3,5,7 평균

            # ----------------------------------------------------
            # 시뮬레이션 (Threshold Optimization)
            # ----------------------------------------------------
            # 예측 시점의 다음날부터 수익률 계산을 위해 데이터 매칭
            # sub_df의 'close' 가격이 필요함
            closes = sub_df['close'].values
            
            best_thresh = 0.60 # 기본값
            best_return = -9999
            
            # 후보군 순회
            for thresh in threshold_candidates:
                total_return = 0.0
                trade_count = 0
                
                # 벡터화 연산 대신 이해하기 쉽게 루프 (최적화 가능하지만 가독성 위주)
                for idx, score in enumerate(trend_scores):
                    df_idx = valid_indices[idx]
                    
                    # 매수 조건
                    if score >= thresh:
                        # 3일 뒤 수익률로 검증 (단기 스윙 기준)
                        try:
                            buy_price = closes[df_idx]
                            # 3일 뒤 가격 (없으면 마지막 가격)
                            sell_idx = min(df_idx + 3, len(closes)-1)
                            sell_price = closes[sell_idx]
                            
                            ret = (sell_price - buy_price) / buy_price
                            total_return += ret
                            trade_count += 1
                        except:
                            pass
                
                # 평가 기준: 수익률이 높아야 하고, 거래 횟수도 최소 5번은 있어야 함
                if trade_count >= 5:
                    if total_return > best_return:
                        best_return = total_return
                        best_thresh = thresh
            
            # 너무 거래가 없었으면 기본값 0.60 유지, 아니면 찾은 값 저장
            if best_return != -9999:
                optimized_results[ticker] = {
                    "buy_threshold": float(best_thresh),
                    "sell_threshold": 0.40, # 매도는 일단 고정 (원하면 이것도 최적화 가능)
                    "expected_return_3d": float(best_return / max(1, trade_count)) # 평균 수익률
                }
            else:
                # 거래가 너무 없어서 검증 못한 경우 (안전하게 높게 잡음)
                optimized_results[ticker] = {
                    "buy_threshold": 0.65, 
                    "sell_threshold": 0.40,
                    "note": "Not enough trades"
                }

        except Exception as e:
            # 에러 나면 그냥 패스 (로그 너무 많이 찍히면 지저분하니까)
            continue

    # 5. 결과 저장 (JSON)
    print(f"\n>> 최적화 완료! 결과 저장 중... ({output_path})")
    with open(output_path, "w", encoding='utf-8') as f:
        json.dump(optimized_results, f, indent=4, ensure_ascii=False)
        
    print(f"✅ 총 {len(optimized_results)}개 종목에 대한 맞춤형 기준 설정 완료.")
    
    # 샘플 출력
    print("\n[Sample Results]")
    for t in list(optimized_results.keys())[:5]:
        print(f" - {t}: {optimized_results[t]}")

if __name__ == "__main__":
    optimize_thresholds()