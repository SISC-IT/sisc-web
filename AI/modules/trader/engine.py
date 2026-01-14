# AI/modules/trader/engine.py
"""
[백테스트 엔진]
- 과거 데이터를 사용하여 AI 모델의 매매 성과를 시뮬레이션합니다.
- Data Loader -> Model Inference -> Order Policy -> Execution Loop 순서로 실행됩니다.
- ★ Walk-Forward Validation 적용: 매 시점마다 과거 데이터로만 스케일러를 학습하여 미래 정보 유출(Data Leakage)을 방지합니다.
"""

import sys
import os
import pandas as pd
import numpy as np
from tqdm import tqdm
from typing import Dict, Any

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model
from AI.modules.trader.policy import decide_order
from AI.libs.database.repository import save_executions_to_db

class BacktestEngine:
    def __init__(self, ticker: str, start_date: str, end_date: str, initial_cash: float = 10000000):
        self.ticker = ticker
        self.start_date = start_date
        self.end_date = end_date
        self.initial_cash = initial_cash
        
        # 상태 변수
        self.cash = initial_cash
        self.position_qty = 0
        self.avg_price = 0.0
        self.total_asset = initial_cash
        
        # 로그 저장용
        self.trade_logs = []
        
        # 모델명 (기본값)
        self.model_name = "transformer"

    def run(self, model_weights_path: str = None):
        """백테스트 실행 메인 함수"""
        print(f"=== [{self.ticker}] 백테스트 시작 ({self.start_date} ~ {self.end_date}) ===")
        print("NOTE: Walk-Forward 검증 적용으로 인해 속도가 다소 느릴 수 있습니다.")
        
        # 1. 데이터 준비
        loader = SignalDataLoader(sequence_length=60)
        df = loader.load_data(self.ticker, self.start_date, self.end_date)
        
        if len(df) < 100:
            print("데이터 부족으로 백테스트 중단")
            return None

        # 2. 모델 준비
        # 백테스트는 '학습된 모델'을 사용하는 것이 원칙
        if model_weights_path and os.path.exists(model_weights_path):
            print(f"모델 로드: {model_weights_path}")
            # 설정값은 로드 시 파일에서 복원되므로 빈 dict 전달
            # 단, 가중치 파일(.h5)인 경우 기본 구조 필요할 수 있음
            default_config = {
                "head_size": 256, "num_heads": 4, "ff_dim": 4,
                "num_blocks": 4, "mlp_units": [128], "dropout": 0.1
            }
            model = get_model(self.model_name, default_config)
            
            try:
                model.load(model_weights_path)
            except Exception:
                # .h5 파일인 경우 구조 빌드 후 가중치 로드
                # (입력 형태는 (60, features)로 가정)
                features = df.select_dtypes(include=[np.number]).columns.tolist()
                model.build((60, len(features)))
                model.model.load_weights(model_weights_path)
        else:
            print("[Warning] 모델 가중치 파일이 지정되지 않았습니다. 결과가 무의미할 수 있습니다.")
            return None

        # 3. 일별 시뮬레이션 루프 (Walk-Forward Validation)
        # 시퀀스 길이(60일) 이후부터 매매 가능
        start_idx = 60
        features = df.select_dtypes(include=[np.number]).columns.tolist()
        
        # tqdm으로 진행률 표시
        for i in tqdm(range(start_idx, len(df) - 1)):
            # (1) 오늘 날짜 및 데이터
            today_date = df.iloc[i]['date']
            current_close = df.iloc[i]['close']
            
            # ─────────────────────────────────────────────────────────────────
            # [Walk-Forward Logic]
            # 미래 데이터를 보지 않기 위해, 매일매일 '오늘까지의 데이터'로만 Scaler를 학습(Fit)합니다.
            # ─────────────────────────────────────────────────────────────────
            
            # 1. Fit: 처음부터 오늘(i)까지의 데이터
            past_data = df.iloc[:i+1] 
            loader.scaler.fit(past_data[features])
            
            # 2. Transform: 모델 입력용 최근 60일 데이터 (i-59 ~ i)
            recent_data = df.iloc[i-59 : i+1] 
            
            if len(recent_data) < 60:
                continue
                
            input_seq_scaled = loader.scaler.transform(recent_data[features])
            
            # (2) AI 모델 추론 (오늘 장 마감 후 내일 예측)
            input_seq = np.expand_dims(input_seq_scaled, axis=0) # (1, 60, features)
            
            # verbose=0으로 로그 숨김
            pred = model.predict(input_seq)
            score = float(pred[0][0])
            
            # (3) 매매 판단 (내일 시초가에 주문한다고 가정 -> 여기서는 간소화하여 오늘 종가 기준 판단)
            action, qty, reason = decide_order(
                self.ticker, score, current_close, self.cash, 
                self.position_qty, self.avg_price, self.total_asset
            )
            
            # (4) 주문 집행 (가정: 슬리피지/수수료 반영)
            self._execute_order(today_date, action, qty, current_close, score, reason)
            
            # (5) 자산 가치 업데이트
            self.total_asset = self.cash + (self.position_qty * current_close)

        # 4. 결과 정리 및 리포팅
        result_df = pd.DataFrame(self.trade_logs)
        if not result_df.empty:
            final_return = (self.total_asset - self.initial_cash) / self.initial_cash * 100
            print(f"=== 백테스트 종료 ===")
            print(f"최종 자산: {self.total_asset:,.0f}원")
            print(f"수익률: {final_return:.2f}%")
            print(f"총 거래 횟수: {len(result_df)}회")
            
            # DB 저장 (선택 사항)
            # save_executions_to_db(result_df)
            
        return result_df

    def _execute_order(self, date, action, qty, price, score, reason):
        """주문 체결 및 장부 업데이트"""
        if qty == 0:
            return

        commission_rate = 0.0015 # 수수료 0.15%
        trade_amount = price * qty
        commission = trade_amount * commission_rate
        
        if action == "BUY":
            cost = trade_amount + commission
            if self.cash >= cost:
                self.cash -= cost
                # 평단가 갱신 (이동평균법)
                total_cost = (self.position_qty * self.avg_price) + trade_amount
                self.position_qty += qty
                self.avg_price = total_cost / self.position_qty
                
                self._log_trade(date, "BUY", price, qty, commission, score, reason)
                
        elif action == "SELL":
            revenue = trade_amount - commission
            if self.position_qty >= qty:
                self.cash += revenue
                
                # 실현 손익 계산
                realized_pnl = (price - self.avg_price) * qty - commission
                
                self.position_qty -= qty
                if self.position_qty == 0:
                    self.avg_price = 0
                    
                self._log_trade(date, "SELL", price, qty, commission, score, reason, realized_pnl)

    def _log_trade(self, date, side, price, qty, commission, score, reason, pnl=0.0):
        """거래 기록 생성"""
        log = {
            "run_id": "backtest_run", # 임시 ID
            "ticker": self.ticker,
            "signal_date": date,
            "signal_price": price,
            "signal": side,
            "fill_date": date,
            "fill_price": price,
            "qty": qty,
            "side": side,
            "value": price * qty,
            "commission": commission,
            "cash_after": self.cash,
            "position_qty": self.position_qty,
            "avg_price": self.avg_price,
            "pnl_realized": pnl,
            "pnl_unrealized": 0.0, # 단순화
            "score": score,
            "reason": reason
        }
        self.trade_logs.append(log)

        