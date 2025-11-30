# backtrade/order_policy.py
# -*- coding: utf-8 -*-
"""
한국어 주석:
- 백테스터의 '체결 수량 및 포지션 결정 로직'을 별도 모듈로 분리.
- 현재는 단순 rule-based이며, 향후 강화학습(Agent) 정책으로 교체할 수 있음.
"""

from __future__ import annotations
from typing import Dict, Tuple


def decide_order(
    side: str,
    cash: float,
    cur_qty: int,
    avg_price: float,
    fill_price: float,
    config,
) -> Tuple[int, float]:
    """
    한국어 주석:
    - 체결 수량 및 거래금액을 결정하는 핵심 함수.
    - 강화학습 Agent가 교체할 대상 부분.
    -----------------------------
    입력값:
        side: "BUY" 또는 "SELL"
        cash: 현재 현금 잔고
        cur_qty: 현재 보유 주식 수량
        avg_price: 현재 보유 평균단가
        fill_price: 이번 체결 기준가 (슬리피지 반영 전)
        config: BacktradeConfig 인스턴스
    반환값:
        (qty, trade_value)
        qty: 매수/매도 수량
        trade_value: 체결 총액(+BUY 지출, -SELL 유입)
    """

    qty = 0
    trade_value = 0.0

    if side == "BUY":
        # 현금 중 risk_frac 비율만큼 투자
        cash_to_use = max(0.0, cash * config.risk_frac)
        qty = int(cash_to_use // fill_price)

        # 동시 보유 제한
        if config.max_positions_per_ticker == 1 and cur_qty > 0:
            qty = 0

        trade_value = fill_price * qty  # BUY → 현금 지출 (+)

    elif side == "SELL":
        qty = cur_qty  # 전량 청산
        trade_value = -fill_price * qty  # SELL → 현금 유입 (-)

    return qty, trade_value


# === 확장용 RL 정책 클래스 ===
class RLOrderPolicy:
    """
    한국어 주석:
    - 강화학습 Agent를 위한 placeholder 클래스.
    - 현재는 rule-based decide_order를 그대로 호출하지만,
      이후 RL 모델의 action 출력을 이용해 수량/금액을 결정할 수 있다.
    """

    def __init__(self, model=None):
        self.model = model  # RL 네트워크 or 정책 객체

    def decide(self, state: Dict, config) -> Tuple[int, float]:
        """
        state: {'cash':..., 'price':..., 'pos':..., 'side':...}
        반환: (qty, trade_value)
        """
        side = state.get("side", "BUY")
        return decide_order(
            side=side,
            cash=state.get("cash", 0.0),
            cur_qty=state.get("cur_qty", 0),
            avg_price=state.get("avg_price", 0.0),
            fill_price=state.get("price", 0.0),
            config=config,
        )
