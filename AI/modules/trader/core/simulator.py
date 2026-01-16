# AI/modules/trader/core/simulator.py
"""
[트레이딩 시뮬레이터]
- AI 모델과 전략을 받아 과거 데이터를 순회하며 매매를 수행합니다.
- '학습용(Gym Env)'과 '단순 시뮬레이션' 양쪽에서 엔진으로 사용됩니다.
"""

import numpy as np
import pandas as pd
from .account import TradingAccount

class Simulator:
    def __init__(self, ticker: str, data: pd.DataFrame):
        self.ticker = ticker
        self.data = data
        self.current_idx = 0
        self.max_idx = len(data) - 1
        
        # 계좌 생성
        self.account = TradingAccount()
        
        # 로그
        self.history = []

    def reset(self):
        self.current_idx = 0
        self.account = TradingAccount()
        self.history = []
        return self._get_state()

    def _get_state(self):
        """현재 시점의 데이터 반환"""
        return self.data.iloc[self.current_idx]

    def step(self, action: dict):
        """
        하루 진행 (매매 수행 -> 날짜 이동)
        action: {'type': 'BUY'/'SELL'/'HOLD', 'amount': 0.0~1.0}
        """
        row = self.data.iloc[self.current_idx]
        current_price = row['close']
        current_date = row.name # index가 날짜라고 가정
        
        executed = False
        msg = ""

        # 1. 매매 실행 (Account 클래스 위임)
        if action['type'] == 'BUY':
            # amount는 현금 대비 비율 (0.5 = 현금의 50% 매수)
            invest_money = self.account.cash * action.get('amount', 0)
            executed, msg = self.account.buy(self.ticker, current_price, invest_money)
            
        elif action['type'] == 'SELL':
            # amount는 보유량 대비 비율 (0.5 = 보유주식 50% 매도)
            sell_ratio = action.get('amount', 0)
            executed, msg = self.account.sell(self.ticker, current_price, sell_ratio)

        # 2. 자산 평가
        total_asset = self.account.get_total_asset({self.ticker: current_price})
        
        # 3. 로그 기록
        self.history.append({
            'date': current_date,
            'price': current_price,
            'asset': total_asset,
            'action': action['type'],
            'msg': msg
        })

        # 4. 다음 날짜로 이동
        self.current_idx += 1
        done = self.current_idx >= self.max_idx
        
        # 보상(Reward) 계산 (강화학습용): 당일 수익률
        prev_asset = self.history[-2]['asset'] if len(self.history) > 1 else self.account.initial_balance
        reward = (total_asset - prev_asset) / prev_asset
        
        return self._get_state() if not done else None, reward, done