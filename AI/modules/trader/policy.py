# AI/modules/trader/policy.py
"""
[주문 정책 모듈]
- AI 모델의 예측 점수(Score)와 현재 포지션 상태를 기반으로 매수/매도 여부와 수량을 결정합니다.
- 자금 관리(Money Management)와 리스크 관리(Risk Management) 로직이 포함됩니다.
"""

from typing import Tuple

def decide_order(
    ticker: str,
    score: float,
    current_price: float,
    cash: float,
    position_qty: int,
    avg_price: float,
    total_asset: float
) -> Tuple[str, int, str]:
    """
    매매 의사결정 함수
    
    Args:
        ticker (str): 종목 코드
        score (float): AI 모델 예측 점수 (0.0 ~ 1.0, 높을수록 상승 확률 높음)
        current_price (float): 현재가
        cash (float): 현재 보유 현금
        position_qty (int): 현재 보유 수량
        avg_price (float): 평균 매입 단가
        total_asset (float): 총 자산 가치
        
    Returns:
        Tuple[str, int, str]: (주문종류 'BUY'/'SELL'/'HOLD', 수량, 로그메시지)
    """
    
    # 1. 정책 설정 (상수)
    BUY_THRESHOLD = 0.65      # 매수 기준 점수
    SELL_THRESHOLD = 0.40     # 매도 기준 점수
    STOP_LOSS_PCT = 0.05      # 손절매 기준 (-5%)
    TAKE_PROFIT_PCT = 0.10    # 익절매 기준 (+10%)
    MAX_INVEST_RATIO = 0.95   # 최대 투자 비중 (현금 5% 남김)

    action = "HOLD"
    qty = 0
    reason = ""

    # 2. 리스크 관리 (손절/익절 우선 체크)
    if position_qty > 0:
        if avg_price <= 0:
            # 평단가가 없으면 리스크 관리 스킵
            reason = "평단가 정보 없음 (리스크 관리 불가)"
            return "HOLD", 0, reason
        else:
            pnl_rate = (current_price - avg_price) / avg_price

            if pnl_rate <= -STOP_LOSS_PCT:
                action = "SELL"
                qty = position_qty
                reason = f"손절매 발동 (수익률 {pnl_rate*100:.2f}%)"
                return action, qty, reason
        
            elif pnl_rate >= TAKE_PROFIT_PCT:
                action = "SELL"
                qty = position_qty
                reason = f"익절매 발동 (수익률 {pnl_rate*100:.2f}%)"
                return action, qty, reason


    # 3. AI 점수 기반 매매 판단
    # (1) 매수 조건
    if score >= BUY_THRESHOLD:
        if position_qty == 0:  # 포지션 없을 때만 진입 (단타 전략 예시)
            # 가용 현금의 95%까지 매수
            invest_amount = cash * MAX_INVEST_RATIO
            buy_qty = int(invest_amount // current_price)
            
            if buy_qty > 0:
                action = "BUY"
                qty = buy_qty
                reason = f"강력 매수 신호 (점수: {score:.4f})"
            else:
                reason = "매수 신호 발생했으나 현금 부족"
        else:
            reason = "이미 포지션 보유 중 (추가 매수 없음)"

    # (2) 매도 조건
    elif score <= SELL_THRESHOLD:
        if position_qty > 0:
            action = "SELL"
            qty = position_qty
            reason = f"매도 신호 (점수: {score:.4f})"
        else:
            reason = "매도 신호 발생했으나 보유 물량 없음"
            
    else:
        reason = f"관망 (점수: {score:.4f})"

    return action, qty, reason