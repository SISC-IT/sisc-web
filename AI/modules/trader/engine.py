"""
[백테스트 엔진]
- 과거 데이터를 사용하여 AI 모델의 매매 성과를 시뮬레이션합니다.
- Data Loader -> Model Inference -> Order Policy -> Execution Loop 순서로 실행됩니다.
"""

import sys
import os
import pandas as pd
import numpy as np
from tqdm import tqdm
from typing import Dict, Any

# ─────────────────────────────────────────────────────────────────────────────
#  경로 설정 (프로젝트 루트 추가)
# ─────────────────────────────────────────────────────────────────────────────
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
        
        # 1. 데이터 준비
        loader = SignalDataLoader(sequence_length=60)
        df = loader.load_data(self.ticker, self.start_date, self.end_date)
        
        if len(df) < 100:
            print("데이터 부족으로 백테스트 중단")
            return None
        
        # ✅ 안전장치: date 컬럼이 있다면 인덱스로 설정 (DataLoader 리팩토링 전이라도 동작 보장)
        if 'date' in df.columns:
            df['date'] = pd.to_datetime(df['date'])
            df.set_index('date', inplace=True)
            df.sort_index(inplace=True) # 시간순 정렬 보장

        # 2. 모델 준비
        # 백테스트는 '학습된 모델'을 사용하는 것이 원칙
        if model_weights_path and os.path.exists(model_weights_path):
            print(f"모델 로드: {model_weights_path}")
            model = get_model(self.model_name, {})
            model.load(model_weights_path)
        else:
            print("[Warning] 모델 가중치 파일이 지정되지 않았습니다. (테스트 모드)")
            # 실제 운영 시에는 여기서 return None 하는 것이 안전함
            # return None

        # 3. 일별 시뮬레이션 루프
        # 시퀀스 길이(60일) 이후부터 매매 가능
        start_idx = 60
        
        # 수치형 피처만 선택 (날짜 제외)
        features = df.select_dtypes(include=[np.number]).columns.tolist()
        
        # 전체 데이터 스케일링 (Local Scaling)
        # 주의: 엄밀한 백테스팅을 위해서는 매 시점마다 과거 데이터로 fit해야 Data Leakage를 막을 수 있음
        # 속도 문제로 여기서는 전체 기간 fit을 사용하나, 실제 퀀트 시스템에선 Walk-forward 방식 권장
        scaled_data = loader.scaler.fit_transform(df[features])
        
        for i in tqdm(range(start_idx, len(df) - 1)):
            # (1) 오늘 날짜 가져오기 (수정된 핵심 부분)
            # 인덱스로 접근하여 KeyError 방지
            today_date = df.index[i]
            
            # (2) 현재가(종가) 확인
            # 인덱스가 날짜이므로 iloc(위치 기반)을 사용하여 i번째 행 접근
            current_close = df.iloc[i]['close']
            
            # (3) AI 모델 추론 (오늘 장 마감 후 내일 예측)
            # 입력: i-59 ~ i 까지의 60일 데이터
            input_seq = scaled_data[i-59 : i+1] # (60, features)
            
            if model_weights_path:
                input_seq = np.expand_dims(input_seq, axis=0)
                pred = model.predict(input_seq, verbose=0)
                score = float(pred[0][0])
            else:
                score = 0.5 # 모델 없으면 관망
            
            # (4) 매매 판단 (정책 모듈 위임)
            action, qty, reason = decide_order(
                self.ticker, score, current_close, self.cash, 
                self.position_qty, self.avg_price, self.total_asset
            )
            
            # (5) 주문 집행 (가상 체결)
            self._execute_order(today_date, action, qty, current_close, score, reason)
            
            # (6) 자산 가치 업데이트
            self.total_asset = self.cash + (self.position_qty * current_close)

        # 4. 결과 정리 및 리포팅
        result_df = pd.DataFrame(self.trade_logs)
        
        if not result_df.empty:
            final_return = (self.total_asset - self.initial_cash) / self.initial_cash * 100
            print(f"=== 백테스트 종료 ===")
            print(f"최종 자산: {self.total_asset:,.0f}원")
            print(f"수익률: {final_return:.2f}%")
            print(f"총 거래 횟수: {len(result_df)}회")
            
            # DB 저장 (필요 시 주석 해제)
            # save_executions_to_db(result_df)
            
        return result_df

    def _execute_order(self, date, action, qty, price, score, reason):
        """주문 체결 및 장부 업데이트"""
        if qty == 0:
            return

        commission_rate = 0.0015 # 수수료 0.15% (가정)
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
            "run_id": "backtest_run", # 임시 ID (실제로는 UUID 등 사용 권장)
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
            "pnl_unrealized": 0.0, # (단순화) 매도 시점에만 PnL 기록
            "score": score,
            "reason": reason
        }
        self.trade_logs.append(log)

if __name__ == "__main__":
    # 테스트 실행용
    engine = BacktestEngine("AAPL", "2024-01-01", "2025-01-01")
    # engine.run("path/to/model.keras")