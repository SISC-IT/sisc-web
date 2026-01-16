# AI/modules/trader/core/account.py
"""
[계좌 관리 모듈]
- 현금, 보유 주식, 평가금액, 수익률을 관리하는 클래스입니다.
- 매수/매도 시 수수료와 정산을 담당합니다.
"""

from typing import Dict, Tuple

class TradingAccount:
    def __init__(self, initial_balance: float = 10_000_000, commission_rate: float = 0.0015):
        self.initial_balance = initial_balance
        self.cash = initial_balance        # 현재 현금
        self.commission_rate = commission_rate
        
        # 포트폴리오 상태: { 'AAPL': {'qty': 10, 'avg_price': 150.0}, ... }
        self.positions: Dict[str, Dict] = {} 
        
        self.realized_pnl = 0.0 # 실현 손익

    def get_total_asset(self, current_prices: Dict[str, float]) -> float:
        """현재가 기준 총 자산 가치 계산"""
        stock_value = 0.0
        for ticker, info in self.positions.items():
            price = current_prices.get(ticker, info['avg_price']) # 현재가 없으면 평단가로 계산
            stock_value += info['qty'] * price
        return self.cash + stock_value

    def buy(self, ticker: str, price: float, amount: float) -> Tuple[bool, str]:
        """매수 주문 (금액 기준)"""
        if amount <= 0:
            return False, "매수 금액 0 이하"
            
        cost = amount * (1 + self.commission_rate)
        if self.cash < cost:
            return False, "현금 부족"
            
        qty = amount / price
        if qty <= 0:
            return False, "수량 계산 오류"
            
        # 포지션 갱신 (평단가 이동평균법)
        if ticker not in self.positions:
            self.positions[ticker] = {'qty': 0.0, 'avg_price': 0.0}
            
        pos = self.positions[ticker]
        total_cost = (pos['qty'] * pos['avg_price']) + amount
        pos['qty'] += qty
        pos['avg_price'] = total_cost / pos['qty']
        
        self.cash -= cost
        return True, "매수 성공"

    def sell(self, ticker: str, price: float, pct: float) -> Tuple[bool, str]:
        """매도 주문 (보유 비중 기준, 0.0 ~ 1.0)"""
        if ticker not in self.positions or self.positions[ticker]['qty'] <= 0:
            return False, "보유 물량 없음"
            
        pos = self.positions[ticker]
        sell_qty = pos['qty'] * pct
        
        revenue = sell_qty * price
        fee = revenue * self.commission_rate
        net_revenue = revenue - fee
        
        # 실현 손익 계산
        buy_cost = sell_qty * pos['avg_price']
        self.realized_pnl += (net_revenue - buy_cost)
        
        self.cash += net_revenue
        pos['qty'] -= sell_qty
        
        if pos['qty'] < 1e-6: # 잔여량이 0에 가까우면 삭제
            del self.positions[ticker]
            
        return True, "매도 성공"