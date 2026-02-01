# AI/modules/trader/strategies/rl_agent.py
"""
[RL 기반 전략]
- 학습된 PPO 모델 파일을 로드하여 행동을 결정합니다.
- 백테스트(run_portfolio.py)나 실전 매매에서 이 클래스를 호출하여 사용합니다.
"""

import os
import numpy as np
from stable_baselines3 import PPO

class RLAgentStrategy:
    def __init__(self, model_path=None):
        if model_path is None:
            # 기본 경로 설정
            current_dir = os.path.dirname(os.path.abspath(__file__))
            project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
            model_path = os.path.join(project_root, "AI/data/weights/rl_agent_ppo.zip")
            
        if os.path.exists(model_path):
            self.model = PPO.load(model_path)
            print(f"🤖 RL 에이전트 로드 성공: {model_path}")
        else:
            print(f"⚠️ RL 모델 파일 없음: {model_path} (랜덤 모드로 동작)")
            self.model = None

    def get_action(self, obs_vector: np.ndarray) -> dict:
        """
        Args:
            obs_vector (np.array): [현금비율, 수익률, RSI, MACD, 변동성, 거래량]
        Returns:
            dict: {'type': 'BUY'/'SELL', 'amount': float}
        """
        if self.model:
            action, _ = self.model.predict(obs_vector, deterministic=True)
            val = float(action[0])
        else:
            val = np.random.uniform(-1, 1) # 모델 없으면 랜덤

        # 행동 해석 (rl_env.py의 로직과 동일해야 함)
        if val > 0.05:
            return {'type': 'BUY', 'amount': val}
        elif val < -0.05:
            return {'type': 'SELL', 'amount': abs(val)}
        else:
            return {'type': 'HOLD', 'amount': 0}