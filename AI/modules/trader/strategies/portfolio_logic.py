"""
[전략 코어 모듈] - Multi-Horizon 대응 버전
- 투자 전략의 핵심 로직(종목 선정, 스코어링, 비중 계산)을 담당합니다.
- 사전 학습된 Scaler와 Embedding(Ticker/Sector ID)을 활용하여 정확한 추론을 수행합니다.
- 1일 노이즈를 제외한 3/5/7일 상승 확률 평균(Ensemble)으로 순위를 매깁니다.
"""

import numpy as np
import pandas as pd
from typing import Dict, List, Any, Tuple

def calculate_portfolio_allocation(
    data_map: Dict[str, pd.DataFrame],
    model: Any,
    scaler: Any,           # [추가] 외부에서 로드한 학습용 Scaler 주입
    ticker_ids: Dict[str, int], # [추가] 종목별 Embedding ID 매핑
    sector_ids: Dict[str, int], # [추가] 종목별 Sector ID 매핑
    feature_columns: List[str],
    config: Dict[str, Any]
) -> Tuple[Dict[str, float], Dict[str, float]]:
    
    # 1. 설정값 로드
    lookback = config.get('seq_len', 60)
    top_k = config.get('top_k', 3)
    buy_threshold = config.get('buy_threshold', 0.6)
    
    scores = {}
    
    # 2. 전 종목 스코어링
    for ticker, df in data_map.items():
        if df is None or len(df) < lookback:
            scores[ticker] = 0.5 
            continue
            
        try:
            # (1) 데이터 추출
            recent_df = df.iloc[-lookback:][feature_columns]
            raw_data = recent_df.values # (60, features)
            
            # (2) 전처리 (반드시 transform만 사용!)
            # 학습 시점의 기준을 그대로 적용하여 데이터의 왜곡 방지
            scaled_data = scaler.transform(raw_data)
            
            # (3) 모델 입력 형태 변환
            input_seq = np.expand_dims(scaled_data, axis=0)
            
            # (4) 임베딩 ID 매핑 (없으면 0번 처리)
            t_input = np.array([ticker_ids.get(ticker, 0)])
            s_input = np.array([sector_ids.get(ticker, 0)])
            
            # (5) 추론
            if model:
                # 3개의 입력을 리스트 형태로 전달
                pred = model.predict([input_seq, t_input, s_input], verbose=0)
                probs = pred[0] # [1일, 3일, 5일, 7일]
                
                # [핵심 전략] 1일 노이즈 제외, 3/5/7일 상승 확률 평균
                score = float(np.mean(probs[1:])) 
            else:
                score = 0.5
                
            scores[ticker] = score
            
        except Exception as e:
            # 추론 실패 시 중립 점수 부여
            scores[ticker] = 0.5

    # 3. 순위 선정 (Ranking)
    sorted_scores = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    
    # 상위 K개 선정 (임계값 이상)
    selected_tickers = [
        ticker for ticker, score in sorted_scores[:top_k] 
        if score >= buy_threshold
    ]
    
    # 4. 목표 비중 계산 (Equal Weight Allocation)
    target_weights = {ticker: 0.0 for ticker in data_map.keys()}
    
    if selected_tickers:
        weight_per_asset = 1.0 / len(selected_tickers)
        for ticker in selected_tickers:
            target_weights[ticker] = weight_per_asset
            
    return target_weights, scores