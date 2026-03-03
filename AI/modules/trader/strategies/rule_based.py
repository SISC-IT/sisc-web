# AI/modules/trader/strategies/rule_based.py
"""
[룰 기반 전략 및 주문 실행 모듈]
1. RuleBasedStrategy: Backtrader 백테스트 환경에서 사용하는 단일 종목 전략 클래스
2. decide_order: 실전 파이프라인(daily_routine)에서 호출하는 포트폴리오 주문 수량 계산 및 리스크 관리 함수
"""

class RuleBasedStrategy:
    """[백테스트 전용] 단일 종목 점수 기반 매매 전략"""
    def __init__(self, buy_threshold=0.65, sell_threshold=0.40):
        self.buy_threshold = buy_threshold
        self.sell_threshold = sell_threshold

    def get_action(self, score: float, position_qty: float) -> dict:
        """
        AI 점수(score)를 보고 행동을 결정합니다.
        Return: {'type': str, 'amount': float}
        """
        # 1. 매수 조건
        if score >= self.buy_threshold:
            if position_qty == 0:
                # 점수가 높으면 풀매수 (현금 99%)
                return {'type': 'BUY', 'amount': 0.99} 
                
        # 2. 매도 조건
        elif score <= self.sell_threshold:
            if position_qty > 0:
                # 점수가 낮으면 전량 매도
                return {'type': 'SELL', 'amount': 1.0}
                
        # 3. 관망
        return {'type': 'HOLD', 'amount': 0}


def decide_order(
    ticker: str, 
    score: float, 
    current_price: float, 
    allocation_cash: float, 
    my_qty: int, 
    my_avg_price: float, 
    current_val: float
) -> tuple:
    """
    [멀티 호라이즌 모델 최적화] AI 스코어 기반 스윙 트레이딩 전략
    """
    
    # AI 스코어 기준점 (사용자 설정에 따라 튜닝 가능)
    STRONG_BUY_SCORE = 0.75  # 75% 이상이면 초강력 매수 (풀베팅)
    SELL_SCORE = 0.50        # 50% 밑으로 떨어지면 모멘텀 소멸 (전량 매도)

    # ---------------------------------------------------------
    # 1. 포지션 청산 (Exit) 로직 : AI의 마음이 변했거나, 손절 라인을 깼을 때
    # ---------------------------------------------------------
    if my_qty > 0 and my_avg_price > 0:
        return_rate = (current_price - my_avg_price) / my_avg_price
        
        # [방어 1] 기계적 손절 (단기 스윙이므로 -7%면 예측 실패로 인정하고 즉각 컷)
        if return_rate <= -0.07:
            return "SELL", my_qty, f"단기 추세 이탈 ({return_rate*100:.1f}%) -> 기계적 손절"
            
        # [방어 2] AI 스코어 붕괴 (고정 15% 익절을 없애고 AI 판단에 맡김)
        # 예: 수익이 +20% 나고 있더라도 오늘 스코어가 50% 밑으로 떨어지면 팔고 나옴
        if score < SELL_SCORE:
            return "SELL", my_qty, f"AI 상승 모멘텀 소멸 (Score: {score*100:.1f}%) -> 전량 매도 (청산)"

    # ---------------------------------------------------------
    # 2. 확신도 비례 베팅 (Position Sizing) 로직
    # ---------------------------------------------------------
    # 점수에 따라 예산(비중)을 다르게 씁니다.
    if score >= STRONG_BUY_SCORE:
        # 75% 이상: 할당된 예산 100% 사용 (강한 확신)
        conviction_weight = 1.0 
    elif score >= 0.60:
        # 60~75% 사이: 확신도에 비례해서 30% ~ 90% 예산만 사용
        # 계산식: (score - 0.60) / (0.75 - 0.60) 로 비율 산출 후 0.3~1.0 사이로 매핑
        base_weight = 0.3 + ((score - 0.60) / 0.15) * 0.7
        conviction_weight = min(max(base_weight, 0.3), 1.0)
    else:
        # 60% 미만: 신규 매수 안 함
        conviction_weight = 0.0

    # 최종 목표 보유 금액 계산
    target_buy_amount = (allocation_cash * conviction_weight) - current_val

    # ---------------------------------------------------------
    # 3. 매수 (Buy) 실행
    # ---------------------------------------------------------
    # 사야 할 금액이 1주 가격보다 비쌀 때만 매수
    if target_buy_amount >= current_price:
        buy_qty = int(target_buy_amount // current_price)
        
        if my_qty == 0:
            return "BUY", buy_qty, f"AI 스코어 진입 ({score*100:.1f}%) -> 예산의 {conviction_weight*100:.0f}% 비중 신규매수"
        else:
            return "BUY", buy_qty, f"AI 확신도 상승 ({score*100:.1f}%) -> 비중 {conviction_weight*100:.0f}% 로 추가매수"

    # 매수도, 매도도 아니면 홀딩 (AI가 여전히 좋게 보고 있으나, 이미 목표 비중만큼 들고 있는 경우)
    if my_qty > 0:
        return "HOLD", 0, f"AI 스코어 유지 ({score*100:.1f}%), 목표 비중 도달 -> 수익 극대화 홀딩"
        
    return "HOLD", 0, "진입 조건 미달 (관망)"