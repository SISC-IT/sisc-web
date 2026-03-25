from __future__ import annotations

"""
[룰 기반 전략 및 주문 실행 모듈]
1. RuleBasedStrategy: Backtrader 백테스트 환경에서 사용하는 단일 종목 전략 클래스
2. decide_order: 실전 파이프라인(daily_routine)에서 호출하는 포트폴리오 주문 수량 계산 및 리스크 관리 함수
"""

from AI.config import ExecutionConfig, load_trading_config


class RuleBasedStrategy:
    """[백테스트 전용] 단일 종목 점수 기반 매매 전략"""

    def __init__(self, buy_threshold: float | None = None, sell_threshold: float | None = None):
        trading_config = load_trading_config()
        self.buy_threshold = (
            buy_threshold if buy_threshold is not None else trading_config.portfolio.buy_threshold
        )
        self.sell_threshold = (
            sell_threshold if sell_threshold is not None else trading_config.execution.sell_score
        )

    def get_action(self, score: float, position_qty: float) -> dict:
        """
        AI 점수(score)를 보고 행동을 결정합니다.
        Return: {'type': str, 'amount': float}
        """
        if score >= self.buy_threshold and position_qty == 0:
            return {"type": "BUY", "amount": 0.99}
        if score <= self.sell_threshold and position_qty > 0:
            return {"type": "SELL", "amount": 1.0}
        return {"type": "HOLD", "amount": 0}


def _calculate_conviction_weight(score: float, execution_config: ExecutionConfig) -> float:
    if score >= execution_config.strong_buy_score:
        return execution_config.max_conviction_weight

    if score < execution_config.buy_score_floor:
        return 0.0

    score_span = execution_config.strong_buy_score - execution_config.buy_score_floor
    if score_span <= 0:
        return execution_config.max_conviction_weight

    scaled = (score - execution_config.buy_score_floor) / score_span
    weight_span = execution_config.max_conviction_weight - execution_config.min_conviction_weight
    conviction_weight = execution_config.min_conviction_weight + (scaled * weight_span)
    return min(max(conviction_weight, execution_config.min_conviction_weight), execution_config.max_conviction_weight)


def decide_order(
    ticker: str,
    score: float,
    current_price: float,
    allocation_cash: float,
    my_qty: int,
    my_avg_price: float,
    current_val: float,
    execution_config: ExecutionConfig,
) -> tuple[str, int, str]:
    """
    [멀티 호라이즌 모델 최적화] AI 스코어 기반 스윙 트레이딩 전략
    """
    if my_qty > 0 and my_avg_price > 0:
        return_rate = (current_price - my_avg_price) / my_avg_price

        # [방어 1] 기계적 손절: 설정된 손실률을 넘기면 즉시 청산합니다.
        if return_rate <= -execution_config.stop_loss_ratio:
            return "SELL", my_qty, f"Stop-loss triggered at {return_rate*100:.1f}%."

        # [방어 2] AI 스코어 붕괴: 상승 모멘텀이 꺾이면 전량 매도합니다.
        if score < execution_config.sell_score:
            return "SELL", my_qty, f"Score dropped to {score*100:.1f}%."

    # 점수 구간에 따라 conviction weight를 다르게 부여합니다.
    conviction_weight = _calculate_conviction_weight(score, execution_config)
    target_buy_amount = (allocation_cash * conviction_weight) - current_val

    # 사야 할 금액이 1주 가격보다 클 때만 실제 매수를 수행합니다.
    if target_buy_amount >= current_price:
        buy_qty = int(target_buy_amount // current_price)
        if buy_qty > 0:
            if my_qty == 0:
                return "BUY", buy_qty, f"Entry signal at {score*100:.1f}%."
            return "BUY", buy_qty, f"Increase position to conviction {conviction_weight*100:.0f}%."

    if my_qty > 0:
        return "HOLD", 0, f"Holding with score {score*100:.1f}%."

    return "HOLD", 0, f"No entry for {ticker}."
