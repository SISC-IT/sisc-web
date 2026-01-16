# AI/modules/trader/backtest/run_backtrader_single.py
"""
[Backtrader 기반 단일 종목 백테스트 엔진]
- Walk-Forward Validation (매일 Scaler 재학습) 지원
- AI Score 시각화 (Observer) 포함
- 용도: 특정 종목에 대한 모델의 타점 정밀 분석
"""

import sys
import os
import backtrader as bt
import pandas as pd
import numpy as np

# 프로젝트 루트 경로 추가 (모듈 임포트 문제 해결)
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model

# ★ 리팩토링된 경로 확인 (아직 파일명 변경 전이면 policy.py에서 import)
try:
    from AI.modules.trader.strategies.rule_based import decide_order
except ImportError:
    from AI.modules.trader.policy import decide_order

# ─────────────────────────────────────────────────────────────────────────────
#  1. AI Score Observer (차트 시각화용)
# ─────────────────────────────────────────────────────────────────────────────
class AIScoreObserver(bt.Observer):
    """차트 하단에 AI 모델 점수와 임계값을 그립니다."""
    lines = ('score', 'limit_buy', 'limit_sell')
    plotinfo = dict(plot=True, subplot=True, plotname='AI Probability')
    plotlines = dict(
        score=dict(marker='o', markersize=4.0, color='blue', _fill_gt=(0.5, 'red'), _fill_lt=(0.5, 'green')),
        limit_buy=dict(color='red', linestyle='--', linewidth=1.0),
        limit_sell=dict(color='green', linestyle='--', linewidth=1.0)
    )

    def next(self):
        # Strategy에서 current_score 값을 가져옴
        score = getattr(self._owner, 'current_score', 0.5)
        self.lines.score[0] = score
        self.lines.limit_buy[0] = 0.65  # 매수 기준선 (Policy와 일치시킴)
        self.lines.limit_sell[0] = 0.40 # 매도 기준선

# ─────────────────────────────────────────────────────────────────────────────
#  2. Walk-Forward Strategy
# ─────────────────────────────────────────────────────────────────────────────
class TransformerWalkForwardStrategy(bt.Strategy):
    params = (
        ('model_weights_path', None),
        ('raw_df', None),     
        ('features', None),   
        ('loader', None),     
        ('seq_len', 60),      
        ('model_name', "transformer"),
    )

    def log(self, txt, dt=None):
        dt = dt or self.datas[0].datetime.date(0)
        print(f'[{dt.isoformat()}] {txt}')

    def __init__(self):
        self.model = self._load_model()
        self.order = None
        self.current_score = 0.5 
        
    def _load_model(self):
        path = self.p.model_weights_path
        if not path or not os.path.exists(path):
            self.log("⚠️ 모델 가중치 파일 없음. (Score=0.5 고정)")
            return None

        # 모델 Config (학습시 설정과 동일해야 함)
        default_config = {
            "head_size": 256, "num_heads": 4, "ff_dim": 4,
            "num_blocks": 4, "mlp_units": [128], "dropout": 0.1
        }
        try:
            model = get_model(self.p.model_name, default_config)
            # 입력 형태 빌드 (None, 60, features)
            model.build((None, self.p.seq_len, len(self.p.features)))
            
            # Wrapper 대응
            if hasattr(model, 'model'):
                model.model.load_weights(path)
            else:
                model.load_weights(path)
                
            return model
        except Exception as e:
            self.log(f"⚠️ 모델 로드 에러: {e}")
            return None

    def notify_order(self, order):
        if order.status in [order.Completed]:
            if order.isbuy():
                self.log(f"🔵 BUY 체결 | 가격: {order.executed.price:,.0f}")
            elif order.issell():
                self.log(f"🔴 SELL 체결 | 가격: {order.executed.price:,.0f} | 수익: {order.executed.pnl:,.0f}")
            self.order = None

    def next(self):
        if len(self) < self.p.seq_len:
            return

        # Backtrader의 현재 날짜 가져오기
        current_date = self.datas[0].datetime.datetime(0)
        
        # [Walk-Forward Logic]
        # 미래 데이터(Look-ahead Bias) 방지를 위해 현재 시점까지의 데이터만 슬라이싱
        past_data = self.p.raw_df.loc[:current_date]
        if len(past_data) < self.p.seq_len:
            return

        # 1. Scaler Fit (과거 데이터로만 학습)
        self.p.loader.scaler.fit(past_data[self.p.features])
        
        # 2. Transform (최근 60일 데이터 변환)
        recent_data = past_data.iloc[-self.p.seq_len:]
        input_seq_scaled = self.p.loader.scaler.transform(recent_data[self.p.features])
        input_seq = np.expand_dims(input_seq_scaled, axis=0)

        # 3. Predict
        if self.model:
            # verbose=0: 진행바 숨김
            pred = self.model.predict(input_seq, verbose=0) 
            score = float(pred[0][0])
        else:
            score = 0.5
        
        self.current_score = score # Observer로 전달

        # 4. 매매 판단 (Rule-Based Policy 사용)
        current_close = self.datas[0].close[0]
        cash = self.broker.get_cash()
        position_size = self.position.size
        avg_price = self.position.price
        total_value = self.broker.get_value()

        action, qty, reason = decide_order(
            "Target", score, current_close, cash, 
            position_size, avg_price, total_value
        )

        if self.order: return

        if action == "BUY" and qty > 0:
            self.log(f"BUY 신호 (점수: {score:.2f}) - {reason}")
            self.order = self.buy(size=qty)
        elif action == "SELL" and qty > 0:
            self.log(f"SELL 신호 (점수: {score:.2f}) - {reason}")
            self.order = self.sell(size=qty)

# ─────────────────────────────────────────────────────────────────────────────
#  실행 함수
# ─────────────────────────────────────────────────────────────────────────────
def run_single_backtest(ticker="AAPL", start_date="2024-01-01", end_date="2024-06-01", enable_plot=True):
    
    print(f"\n=== [{ticker}] 정밀 백테스트 시작 (Walk-Forward) ===")
    
    # 가중치 파일 경로 자동 설정
    weight_path = os.path.join(project_root, "AI/data/weights/transformer/universal_transformer.keras")
    
    # 1. 데이터 로드
    loader = SignalDataLoader(sequence_length=60)
    df = loader.load_data(ticker, start_date, end_date)
    
    if df is None or len(df) < 100:
        print("❌ 데이터 부족 또는 로드 실패")
        return

    # Backtrader용 데이터 피드 생성
    if 'date' in df.columns:
        df['date'] = pd.to_datetime(df['date'])
        df.set_index('date', inplace=True)
    
    data_feed = bt.feeds.PandasData(dataname=df)
    features = df.select_dtypes(include=[np.number]).columns.tolist()

    # 2. 엔진 설정
    cerebro = bt.Cerebro()
    cerebro.adddata(data_feed)
    
    cerebro.addstrategy(
        TransformerWalkForwardStrategy,
        model_weights_path=weight_path,
        raw_df=df, # 원본 DF 전달 (Walk-Forward용)
        features=features,
        loader=loader
    )

    if enable_plot:
        cerebro.addobserver(AIScoreObserver)

    # 3. 자금 설정
    initial_cash = 10_000_000
    cerebro.broker.setcash(initial_cash)
    cerebro.broker.setcommission(commission=0.0015) # 0.15% 수수료

    # 4. 분석기
    cerebro.addanalyzer(bt.analyzers.SharpeRatio, _name='sharpe', riskfreerate=0.0)
    cerebro.addanalyzer(bt.analyzers.DrawDown, _name='drawdown')

    # 5. 실행
    print(f"💰 초기 자산: {initial_cash:,.0f}원")
    results = cerebro.run()
    final_value = cerebro.broker.getvalue()
    
    # 6. 결과 출력
    profit_pct = (final_value - initial_cash) / initial_cash * 100
    print(f"💰 최종 자산: {final_value:,.0f}원")
    print(f"📈 수익률: {profit_pct:.2f}%")

    strat = results[0]
    mdd = strat.analyzers.drawdown.get_analysis()['max']['drawdown']
    sharpe = strat.analyzers.sharpe.get_analysis().get('sharperatio', 0.0)
    
    print(f"📉 MDD: {mdd:.2f}%")
    print(f"📊 Sharpe: {sharpe:.4f}")

    if enable_plot:
        # 차트 출력 (브라우저 또는 팝업)
        cerebro.plot(style='candlestick', volume=False, iplot=False)

if __name__ == "__main__":
    run_single_backtest()