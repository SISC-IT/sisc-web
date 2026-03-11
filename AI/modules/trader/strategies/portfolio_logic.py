# AI/modules/trader/strategies/portfolio_logic.py
"""
[SISC AI 전략 코어 모듈] - Meta-Gating & Risk Overlay 반영 버전
- Data Flow Specification에 정의된 파이프라인(Phase 3 ~ 6)을 수행합니다.
- TCN, PatchTST, iTransformer의 개별 신호를 수집합니다.
- Gating 모듈을 통해 시장 상황(Macro/State)에 맞춰 가중치를 동적으로 결합합니다.
- Trader 모듈이 비중을 계산하고, Risk Overlay가 최종 익스포저를 스케일링합니다.
"""

import numpy as np
import pandas as pd
from typing import Dict, List, Any, Tuple

def calculate_portfolio_allocation(
    data_map: Dict[str, pd.DataFrame],
    macro_data: pd.DataFrame,       # [추가] Gating과 Risk Overlay에 쓰일 매크로/시장 상태 데이터
    model_wrappers: Dict[str, Any], # Base Models (TCN, PatchTST, iTransformer)
    ticker_ids: Dict[str, int],     
    ticker_to_sector_id: Dict[str, int], # [추가] 티커별 섹터 ID 매핑
    gating_model: Any,              # [추가] Gating 네트워크 모델 (Meta Learner)
    config: Dict[str, Any]
) -> Tuple[Dict[str, float], Dict[str, float], Dict[str, Dict[str, float]]]:
    
    # 설정값 로드
    top_k = config.get('top_k', 3)
    buy_threshold = config.get('buy_threshold', 0.6)
    
    scores = {}
    all_signals_map = {}
    
    # 모델의 순서를 고정하기 위해 리스트화 (가중치 곱셈 시 순서 꼬임 방지)
    model_names = list(model_wrappers.keys())
    
    # =========================================================================
    # [Phase 3] Base Models (Signal Generation) - 개별 종목별 시그널 추출
    # =========================================================================
    for ticker, df in data_map.items():
        if df is None or len(df) < 60:
            continue
            
        t_id = ticker_ids.get(ticker, 0) # 없는 티커는 기본값 0 처리
        # 💡 NameError 방지를 위해 파라미터로 받은 변수명 사용
        s_id = ticker_to_sector_id.get(ticker, 0) 
            
        ticker_signals = {}
        for model_name in model_names:
            wrapper = model_wrappers[model_name]
            try:
                # 각 Wrapper 내부에서 필요 데이터를 전처리하고 추론값을 반환
                preds_dict = wrapper.predict(df, ticker_id=t_id, sector_id=s_id)
                # preds_dict가 {'TCN': 0.65} 형태라고 가정
                ticker_signals.update(preds_dict)
            except Exception as e:
                print(f"[Phase 3] [{ticker}] {model_name} 추론 에러: {e}")
                # 에러 발생 시 중립(0.5) 스코어 부여
                ticker_signals[model_name] = 0.5 
                
        all_signals_map[ticker] = ticker_signals

    # =========================================================================
    # [Phase 4] Gating (Soft/Meta) - 상황별 모델 비중 동적 조절
    # =========================================================================
    # 💡  차원 에러(Shape Mismatch) 방지를 위해 2D 배열로 변환 (1, features) (맞는지 잘 모르겠음...)
    current_market_series = macro_data.iloc[-1]
    current_market_state_2d = current_market_series.values.reshape(1, -1)
    
    # Gating 가중치 추론 (for loop 밖에서 한 번만 연산하여 효율성 극대화)
    if gating_model:
        # 출력 예: [[0.2, 0.5, 0.3]] (TCN, PatchTST, iTransformer 가중치)
        weights_pred = gating_model.predict(current_market_state_2d)
        model_weights = weights_pred[0] # 1D 배열로 축소
    else:
        # Gating이 없으면 단순 평균 (Equal Weight)
        model_weights = np.ones(len(model_names)) / len(model_names)

    for ticker, signals in all_signals_map.items():
        if not signals:
            scores[ticker] = 0.5
            continue
            
        # 💡 keys() 순서가 꼬이는 것을 방지하기 위해 고정된 model_names 순서대로 벡터 생성
        base_signal_vector = np.array([signals.get(name, 0.5) for name in model_names])
        
        # [핵심] 가중 평균 (Dot Product)
        final_score = np.dot(base_signal_vector, model_weights)
        scores[ticker] = final_score

    # =========================================================================
    # [Phase 5] Trader (Rule / RL) - 비중, 제약 조건 최적화
    # =========================================================================
    sorted_scores = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    selected_tickers = [ticker for ticker, score in sorted_scores[:top_k] if score >= buy_threshold]
    
    # 목표 비중 계산 (Equal Weight)
    w_target = {ticker: 0.0 for ticker in data_map.keys()}
    if selected_tickers:
        weight_per_asset = 1.0 / len(selected_tickers)
        for ticker in selected_tickers:
            w_target[ticker] = weight_per_asset

    # =========================================================================
    # [Phase 6] Risk Overlay (System Brake) - 시장 위험 기반 익스포저 스케일링
    # =========================================================================
    # Pandas Series에서 안전하게 get 활용
    vix_z = current_market_series.get('vix_z_score', 0)
    system_brake_ratio = 1.0 # 기본 100% 진입
    
    # 극단적 공포장(VIX 2표준편차 이상) 시 투자 비중 강제 축소
    if vix_z > 3.0:
        system_brake_ratio = 0.0   # 전량 현금화 (안전 자산 회피)
    elif vix_z > 2.0:
        system_brake_ratio = 0.5   # 총 투자금의 50%만 사용
        
    #최종 익스포저 스케일링
    w_final = {ticker: weight * system_brake_ratio for ticker, weight in w_target.items()}

    return w_final, scores, all_signals_map