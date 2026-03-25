"""
[SISC AI 전략 코어 모듈] - Meta-Gating & Risk Overlay 반영 버전
- Data Flow Specification에 정의된 파이프라인(Phase 3 ~ 6)을 수행합니다.
- TCN, PatchTST, iTransformer의 개별 신호를 수집합니다.
- Gating 모듈을 통해 시장 상황(Macro/State)에 맞춰 가중치를 동적으로 결합합니다.
- Trader 모듈이 비중을 계산하고, Risk Overlay가 최종 익스포저를 스케일링합니다.
"""

from typing import Any, Dict, Tuple

import numpy as np
import pandas as pd

from AI.config import DataConfig, PortfolioConfig


def calculate_portfolio_allocation(
    data_map: Dict[str, pd.DataFrame],
    macro_data: pd.DataFrame,
    model_wrappers: Dict[str, Any],
    ticker_ids: Dict[str, int],
    ticker_to_sector_id: Dict[str, int],
    gating_model: Any,
    data_config: DataConfig,
    portfolio_config: PortfolioConfig,
) -> Tuple[Dict[str, float], Dict[str, float], Dict[str, Dict[str, float]]]:
    """
    [포트폴리오 비중 계산]
    개별 모델 신호를 집계하고, 필요 시 게이팅과 리스크 오버레이를 반영해
    최종 목표 비중을 계산합니다.
    """
    minimum_history_length = max(data_config.seq_len, data_config.minimum_history_length)
    default_score = portfolio_config.default_score

    scores: Dict[str, float] = {}
    all_signals_map: Dict[str, Dict[str, float]] = {}
    # 모델의 순서를 고정하기 위해 리스트화 (가중치 곱셈 시 순서 꼬임 방지)
    model_names = list(model_wrappers.keys())

    # =========================================================================
    # [Phase 3] Base Models (Signal Generation) - 개별 종목별 시그널 추출
    # =========================================================================
    for ticker, df in data_map.items():
        if df is None or len(df) < minimum_history_length:
            continue

        ticker_id = ticker_ids.get(ticker, 0)
        sector_id = ticker_to_sector_id.get(ticker, 0)
        ticker_signals: Dict[str, float] = {}

        for model_name in model_names:
            wrapper = model_wrappers[model_name]
            try:
                # 래퍼에서 다중 호라이즌 예측값을 가져와 대표 점수(평균)로 압축합니다.
                preds_dict = wrapper.get_signals(df, ticker_id=ticker_id, sector_id=sector_id)
                ticker_signals[model_name] = float(np.mean(list(preds_dict.values())))
            except Exception as e:
                print(f"[Phase 3] [{ticker}] {model_name} signal extraction failed: {e}")
                ticker_signals[model_name] = default_score

        all_signals_map[ticker] = ticker_signals

    # =========================================================================
    # [Phase 4] Gating (Soft/Meta) - 상황별 모델 비중 동적 조절
    # =========================================================================
    current_market_series = macro_data.iloc[-1]
    current_market_state_2d = current_market_series.values.reshape(1, -1)

    if gating_model:
        weights_pred = gating_model.predict(current_market_state_2d)
        model_weights = weights_pred[0]
    else:
        model_weights = np.ones(len(model_names)) / len(model_names)

    for ticker, signals in all_signals_map.items():
        if not signals:
            scores[ticker] = default_score
            continue

        # 고정된 model_names 순서대로 벡터를 구성해 가중 합산 순서를 보장합니다.
        base_signal_vector = np.array([signals.get(name, default_score) for name in model_names])
        scores[ticker] = float(np.dot(base_signal_vector, model_weights))

    # =========================================================================
    # [Phase 5] Trader (Rule / RL) - 비중, 제약 조건 최적화
    # =========================================================================
    sorted_scores = sorted(scores.items(), key=lambda item: item[1], reverse=True)
    selected_tickers = [
        ticker
        for ticker, score in sorted_scores[: portfolio_config.top_k]
        if score >= portfolio_config.buy_threshold
    ]

    target_weights = {ticker: 0.0 for ticker in data_map.keys()}
    if selected_tickers:
        weight_per_asset = 1.0 / len(selected_tickers)
        for ticker in selected_tickers:
            target_weights[ticker] = weight_per_asset

    # =========================================================================
    # [Phase 6] Risk Overlay (System Brake) - 시장 위험 기반 익스포저 스케일링
    # =========================================================================
    vix_z = float(current_market_series.get("vix_z_score", 0.0))
    risk_overlay = portfolio_config.risk_overlay
    system_brake_ratio = 1.0

    if vix_z > risk_overlay.vix_exit_threshold:
        system_brake_ratio = risk_overlay.full_exit_ratio
    elif vix_z > risk_overlay.vix_reduce_exposure_threshold:
        system_brake_ratio = risk_overlay.reduced_exposure_ratio

    final_weights = {
        ticker: weight * system_brake_ratio for ticker, weight in target_weights.items()
    }
    return final_weights, scores, all_signals_map
