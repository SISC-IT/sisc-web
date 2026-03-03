# AI/modules/trader/train/rl_env.py
"""
[강화학습 환경]
- core.simulator를 OpenAI Gym 환경으로 래핑합니다.
- PPO 알고리즘이 학습할 수 있도록 상태(State)와 보상(Reward)을 제공합니다.
"""

import gymnasium as gym
from gymnasium import spaces
import numpy as np
import pandas as pd

from AI.modules.trader.core.simulator import Simulator
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.features.legacy.technical_features import add_technical_indicators 

class StockTradingEnv(gym.Env):
    metadata = {'render_modes': ['human']}

    def __init__(self, ticker: str, start_date: str, end_date: str, initial_balance=10_000_000):
        super(StockTradingEnv, self).__init__()
        
        # 1. 데이터 준비
        self.loader = DataLoader()
        raw_df = self.loader.load_data_from_db(
            start_date=start_date, 
            end_date=end_date, 
            tickers=[ticker]
        )
        
        if raw_df is None or raw_df.empty:
            raise ValueError(f"데이터 부족: {ticker}")

        # 강화학습 AI의 관측(Observation)을 위한 기술적 지표 생성
        try:
            self.df = add_technical_indicators(raw_df)
        except Exception as e:
            raise ValueError(f"지표 생성 중 오류 발생: {e}")

        # Simulator 내부 동작을 위해 날짜를 인덱스로 세팅
        self.df.set_index('date', inplace=True)

        if len(self.df) < 100:
            raise ValueError(f"데이터 부족 (지표 생성 후): {ticker}")

        # Simulator 인스턴스 생성 (우리가 만든 엔진 사용)
        self.simulator = Simulator(ticker, self.df, initial_balance=initial_balance)

        # 2. Action Space (행동 정의: -1.0(전량매도) ~ +1.0(전량매수))
        self.action_space = spaces.Box(low=-1, high=1, shape=(1,), dtype=np.float32)

        # 3. Observation Space (관측 정의)
        # [현금비율, 수익률, RSI, MACD비율, 볼린저밴드위치, 거래량변화] (6개 특징)
        self.observation_space = spaces.Box(low=-np.inf, high=np.inf, shape=(6,), dtype=np.float32)

    def reset(self, seed=None, options=None):
        super().reset(seed=seed)
        
        # 시뮬레이터 초기화
        first_row = self.simulator.reset()
        return self._get_observation(first_row), {}

    def step(self, action):
        # AI의 행동(float) -> 시뮬레이터 행동(dict) 변환
        action_val = float(action[0])
        
        sim_action = {'type': 'HOLD', 'amount': 0}
        
        # 임계값(0.05)을 두어 불필요한 매매 방지 (수수료 절약 효과)
        if action_val > 0.05:
            sim_action = {'type': 'BUY', 'amount': action_val}
        elif action_val < -0.05:
            sim_action = {'type': 'SELL', 'amount': abs(action_val)}

        # 시뮬레이터 진행 (핵심!)
        next_row, reward, done = self.simulator.step(sim_action)
        
        # 상태 관측 생성
        obs = self._get_observation(next_row) if not done else np.zeros(6, dtype=np.float32)
        
        # 추가 정보
        info = {
            'asset': self.simulator.account.get_total_asset({self.simulator.ticker: next_row['close']}) if not done else 0
        }
        
        return obs, reward, done, False, info

    def _get_observation(self, row):
        """현재 시점의 시장 데이터 + 내 계좌 상태를 벡터로 변환"""
        if row is None:
            return np.zeros(6, dtype=np.float32)

        # 1. 계좌 상태
        acc = self.simulator.account
        current_price = row['close']
        total_asset = acc.get_total_asset({self.simulator.ticker: current_price})
        cash_ratio = acc.cash / total_asset if total_asset > 0 else 0
        
        profit_rate = 0.0
        if self.simulator.ticker in acc.positions:
            avg_price = acc.positions[self.simulator.ticker]['avg_price']
            profit_rate = (current_price - avg_price) / avg_price
        
        # 2. 시장 데이터 (기술적 지표)
        rsi = row.get('rsi', 50) / 100.0
        macd_ratio = row.get('macd_ratio', 0)       
        bb_position = row.get('bb_position', 0)     
        vol_change = row.get('vol_change', 0)       

        # 정규화하여 AI에게 전달
        obs = np.array([
            cash_ratio,      # 0~1
            profit_rate,     # -inf ~ inf
            rsi,             # 0~1
            macd_ratio,      # -inf ~ inf
            bb_position,     # -inf ~ inf
            vol_change       # -inf ~ inf
        ], dtype=np.float32)
        
        return obs