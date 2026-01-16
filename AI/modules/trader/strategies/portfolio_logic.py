#AI/modules/trader/strategies/portfolio_logic.py
"""
[전략 코어 모듈]
- 투자 전략의 핵심 로직(종목 선정, 스코어링, 비중 계산)을 담당합니다.
- 이 모듈은 '데이터 로딩'이나 '주문 실행'을 하지 않는 순수 함수(Pure Function)로 구성됩니다.
- 운영 코드(daily_routine)와 백테스트 코드(backtrader)에서 공통으로 사용됩니다.
"""

import numpy as np
import pandas as pd
from typing import Dict, List, Any, Tuple
from sklearn.preprocessing import StandardScaler

def calculate_portfolio_allocation(
    data_map: Dict[str, pd.DataFrame],
    model: Any,
    feature_columns: List[str],
    config: Dict[str, Any]
) -> Tuple[Dict[str, float], Dict[str, float]]:
    """
    주어진 데이터와 모델을 사용하여 포트폴리오 비중을 계산합니다.

    Args:
        data_map (Dict[str, DataFrame]): 종목별 과거 데이터 (최소 lookback 길이 이상이어야 함)
        model (Any): 학습된 AI 모델 객체 (predict 메서드 보유)
        feature_columns (List[str]): 모델 입력에 사용할 피처 컬럼명 리스트
        config (Dict): 전략 설정값 (lookback, top_k, buy_threshold 등)

    Returns:
        target_weights (Dict[str, float]): 종목별 목표 비중 (예: {'AAPL': 0.33, ...})
        scores (Dict[str, float]): 종목별 계산된 AI Score
    """
    
    # 1. 설정값 로드
    lookback = config.get('seq_len', 60)
    top_k = config.get('top_k', 3)
    buy_threshold = config.get('buy_threshold', 0.6)
    
    scores = {}
    
    # 2. 전 종목 스코어링
    for ticker, df in data_map.items():
        # 데이터 길이 체크
        if df is None or len(df) < lookback:
            scores[ticker] = 0.5 # 데이터 부족 시 중립 점수
            continue
            
        try:
            # (1) 데이터 추출 (마지막 lookback 만큼)
            # DataFrame에서 필요한 컬럼만 추출하여 numpy 변환
            recent_df = df.iloc[-lookback:][feature_columns]
            raw_data = recent_df.values # (60, features)
            
            # (2) 전처리 (Standard Scaling)
            # 주의: 매일매일 해당 시점의 데이터로만 scaling 해야 함 (Walk-Forward)
            scaler = StandardScaler()
            scaled_data = scaler.fit_transform(raw_data)
            
            # (3) 모델 입력 형태 변환 (Batch 차원 추가)
            input_seq = np.expand_dims(scaled_data, axis=0) # (1, 60, features)
            
            # (4) 추론
            if model:
                # verbose=0: 진행바 출력 끄기
                pred = model.predict(input_seq, verbose=0)
                score = float(pred[0][0])
            else:
                score = 0.5 # 모델 없으면 랜덤/중립
                
            scores[ticker] = score
            
        except Exception as e:
            print(f"⚠️ [StrategyCore] {ticker} 계산 중 오류: {e}")
            scores[ticker] = 0.0

    # 3. 순위 선정 (Ranking)
    # 점수 내림차순 정렬
    sorted_scores = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    
    # 상위 K개 선정 (단, 임계값 넘어야 함)
    selected_tickers = [
        ticker for ticker, score in sorted_scores[:top_k] 
        if score >= buy_threshold
    ]
    
    # 4. 목표 비중 계산 (Allocation)
    target_weights = {ticker: 0.0 for ticker in data_map.keys()}
    
    if selected_tickers:
        # 동일 비중 배분 (Equal Weight)
        weight_per_asset = 1.0 / len(selected_tickers)
        for ticker in selected_tickers:
            target_weights[ticker] = weight_per_asset
            
    return target_weights, scores