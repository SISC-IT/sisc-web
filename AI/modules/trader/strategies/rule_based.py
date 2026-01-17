# AI/modules/trader/strategies/rule_based.py
"""
[룰 기반 전략]
- AI Score를 입력받아 매수/매도/관망을 결정하는 고전적인 전략 로직입니다.
"""

class RuleBasedStrategy:
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