# AI/modules/trader/train/train_ppo.py
"""
[PPO 학습 실행기]
- rl_env.py 환경을 불러와 AI를 훈련시킵니다.
- 학습된 모델(.zip)을 'AI/data/weights/rl_agent_ppo' 경로에 저장합니다.
"""

import os
import sys
from stable_baselines3 import PPO
from stable_baselines3.common.vec_env import DummyVecEnv

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.trader.train.rl_env import StockTradingEnv

def train_agent():
    print("🚀 [RL] PPO 트레이딩 에이전트 학습 시작")
    
    ticker = "AAPL" # 학습할 종목
    start_date = "2020-01-01"
    end_date = "2023-12-31"
    
    # 1. 환경 생성 (벡터화된 환경)
    env = DummyVecEnv([lambda: StockTradingEnv(ticker, start_date, end_date)])
    
    # 2. 모델 설정 (MlpPolicy: 수치 데이터용 신경망)
    model = PPO(
        "MlpPolicy", 
        env, 
        verbose=1, 
        learning_rate=0.0003,
        batch_size=64,
        gamma=0.99, # 미래 보상 할인율
        ent_coef=0.01 # 탐험 장려
    )
    
    # 3. 학습 (Timesteps: 학습 횟수)
    total_timesteps = 50_000
    print(f"   - 학습 기간: {start_date} ~ {end_date}")
    print(f"   - 총 스텝: {total_timesteps}")
    
    model.learn(total_timesteps=total_timesteps)
    
    # 4. 저장
    save_dir = os.path.join(project_root, "AI", "data", "weights")
    os.makedirs(save_dir, exist_ok=True)
    save_path = os.path.join(save_dir, "rl_agent_ppo")
    
    model.save(save_path)
    print(f"✅ 모델 저장 완료: {save_path}.zip")

if __name__ == "__main__":
    train_agent()