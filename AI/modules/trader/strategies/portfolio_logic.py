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
    sector_ids: Dict[str, int],
    gating_model: Any,              # [추가] Gating 네트워크 모델 (Meta Learner)
    config: Dict[str, Any]
) -> Tuple[Dict[str, float], Dict[str, float], Dict[str, Dict[str, float]]]:
    
    # 설정값 로드 (Trader 설정)
    top_k = config.get('top_k', 3)
    buy_threshold = config.get('buy_threshold', 0.6)
    
    scores = {}
    all_signals_map = {}
    
    # =========================================================================
    # [Phase 3] Base Models (Signal Generation) - 개별 종목별 시그널 추출
    # =========================================================================
    for ticker, df in data_map.items():
        if df is None or len(df) < 60:
            continue
        t_id = ticker_ids.get(ticker, -1)
        s_id = sector_ids.get(ticker, 0)
            
        ticker_signals = {}
        for model_name, wrapper in model_wrappers.items():
            try:
                # 명세서에 따라 각 모델은 자신만의 필요 데이터(Tech, Log_return, Macro)를
                # Wrapper 내부에서 추출하여 추론합니다.
                preds_dict = wrapper.predict(df, ticker_id=t_id, sector_id=s_id)
                ticker_signals.update(preds_dict)
            except Exception as e:
                print(f"[{ticker}] {model_name} 추론 에러: {e}")
                continue
                
        all_signals_map[ticker] = ticker_signals

    # =========================================================================
    # [Phase 4] Gating (Soft/Meta) - 상황별 모델 비중 동적 조절
    # =========================================================================
    # 현재 시장 상태 추출 (명세서 기준: vix_z_score, atr_rank, ma_trend_score 등)
    current_market_state = macro_data.iloc[-1]
    
    for ticker, signals in all_signals_map.items():
        if not signals:
            scores[ticker] = 0.5
            continue
            
        # 신호들을 하나의 벡터로 정렬
        base_signal_vector = np.array(list(signals.values()))
        
        # [핵심] Gating Network를 통해 모델별 가중치(weights) 산출
        # 입력: [시장 상태 데이터 + 종목의 트렌드 + 오차율] -> 출력: [TCN가중치, Patch가중치, iTrans가중치]
        if gating_model:
            model_weights = gating_model.predict(current_market_state)
            # 가중 평균으로 최종 점수 도출
            final_score = np.dot(base_signal_vector, model_weights)
        else:
            # Gating 모델이 아직 학습 전이라면 단순 평균(Phase 1.5용 임시 처리)
            final_score = np.mean(base_signal_vector)
            
        scores[ticker] = final_score

    # =========================================================================
    # [Phase 5] Trader (Rule / RL) - 비중, 제약 조건 최적화
    # =========================================================================
    sorted_scores = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    selected_tickers = [ticker for ticker, score in sorted_scores[:top_k] if score >= buy_threshold]
    
    # 1차 목표 비중 계산 (Rule: Equal Weight. 향후 RL 도입 시 교체)
    w_target = {ticker: 0.0 for ticker in data_map.keys()}
    if selected_tickers:
        weight_per_asset = 1.0 / len(selected_tickers)
        for ticker in selected_tickers:
            w_target[ticker] = weight_per_asset

    # =========================================================================
    # [Phase 6] Risk Overlay (System Brake) - 시장 위험 기반 익스포저 스케일링
    # =========================================================================
    # 명세서 기준: VIX Z-score, 환율 변동성 등에 따른 계단형 브레이크 적용
    vix_z = current_market_state.get('vix_z_score', 0)
    system_brake_ratio = 1.0 # 기본 100% 진입
    
    # 극단적 공포장(VIX 2표준편차 이상) 시 투자 비중 강제 축소
    if vix_z > 2.0:
        system_brake_ratio = 0.5   # 총 투자금의 50%만 사용
    elif vix_z > 3.0:
        system_brake_ratio = 0.0   # 전량 현금화 (안전 자산 회피)
        
    # w_target 에 브레이크 비율을 곱하여 최종 비중(w_final) 산출
    w_final = {ticker: weight * system_brake_ratio for ticker, weight in w_target.items()}

    return w_final, scores, all_signals_map