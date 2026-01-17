# AI/modules/trader/backtest/run_backtrader_single.py
"""
[Backtrader 기반 단일 종목 정밀 백테스트]
- Walk-Forward Validation 지원
- strategies/rule_based.py 의 RuleBasedStrategy 클래스 사용
- AI Score 시각화 기능 포함
"""

import sys
import os
import backtrader as bt
import pandas as pd
import numpy as np

current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model
# ★ [수정] 클래스 기반 전략 불러오기
from AI.modules.trader.strategies.rule_based import RuleBasedStrategy

class AIScoreObserver(bt.Observer):
    """차트 하단에 AI 모델 점수를 그리기 위한 클래스"""
    lines = ('score', 'limit_buy', 'limit_sell')
    plotinfo = dict(plot=True, subplot=True, plotname='AI Probability')
    plotlines = dict(
        score=dict(marker='o', markersize=3.0, color='blue', _fill_gt=(0.5, 'red'), _fill_lt=(0.5, 'green')),
        limit_buy=dict(color='red', linestyle='--'),
        limit_sell=dict(color='green', linestyle='--')
    )

    def next(self):
        score = getattr(self._owner, 'current_score', 0.5)
        self.lines.score[0] = score
        self.lines.limit_buy[0] = 0.65
        self.lines.limit_sell[0] = 0.40

class TransformerWalkForwardStrategy(bt.Strategy):
    params = (
        ('model_weights_path', None),
        ('raw_df', None),     
        ('features', None),   
        ('loader', None),     
        ('seq_len', 60),      
        ('model_name', "transformer"),
    )

    def __init__(self):
        self.model = self._load_model()
        self.order = None
        self.current_score = 0.5 
        # ★ [수정] 전략 객체 초기화
        self.strategy_logic = RuleBasedStrategy(buy_threshold=0.65, sell_threshold=0.40)

    def log(self, txt, dt=None):
        dt = dt or self.datas[0].datetime.date(0)
        print(f'[{dt.isoformat()}] {txt}')
        
    def _load_model(self):
        path = self.p.model_weights_path
        if not path or not os.path.exists(path):
            self.log("⚠️ 모델 가중치 파일 없음.")
            return None

        default_config = {
            "head_size": 256, "num_heads": 4, "ff_dim": 4,
            "num_blocks": 4, "mlp_units": [128], "dropout": 0.1
        }
        try:
            model = get_model(self.p.model_name, default_config)
            model.build((None, self.p.seq_len, len(self.p.features)))
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
                self.log(f"🔵 BUY 체결 @ {order.executed.price:,.0f}")
            elif order.issell():
                self.log(f"🔴 SELL 체결 @ {order.executed.price:,.0f}")
            self.order = None

    def next(self):
        if len(self) < self.p.seq_len:
            return

        current_date = self.datas[0].datetime.datetime(0)
        past_data = self.p.raw_df.loc[:current_date]
        if len(past_data) < self.p.seq_len:
            return

        # 1. Walk-Forward Prediction
        self.p.loader.scaler.fit(past_data[self.p.features])
        recent_data = past_data.iloc[-self.p.seq_len:]
        input_seq = np.expand_dims(self.p.loader.scaler.transform(recent_data[self.p.features]), axis=0)

        if self.model:
            pred = self.model.predict(input_seq, verbose=0) 
            score = float(pred[0][0])
        else:
            score = 0.5
        
        self.current_score = score

        # 2. 매매 판단 (RuleBasedStrategy 사용)
        if self.order: return # 이미 주문 중이면 패스

        position_qty = self.position.size
        # ★ [수정] 클래스 메서드 호출로 변경 (코드가 훨씬 깔끔해짐)
        decision = self.strategy_logic.get_action(score, position_qty)

        if decision['type'] == 'BUY':
            # 보유 현금의 95%만큼 매수 계산 (Backtrader 로직)
            cash = self.broker.get_cash()
            price = self.datas[0].close[0]
            # 수수료 고려하여 안전하게 계산
            size = int((cash * 0.95) / price)
            if size > 0:
                self.log(f"BUY 신호 (Score: {score:.2f})")
                self.order = self.buy(size=size)
                
        elif decision['type'] == 'SELL':
            if position_qty > 0:
                self.log(f"SELL 신호 (Score: {score:.2f})")
                self.order = self.close() # 전량 청산

def run_single_backtest(ticker="AAPL", start_date="2024-01-01", end_date="2024-06-01", enable_plot=True):
    print(f"\n=== [{ticker}] 단일 종목 백테스트 시작 ===")
    
    weight_path = os.path.join(project_root, "AI/data/weights/transformer/universal_transformer.keras")
    loader = SignalDataLoader(sequence_length=60)
    df = loader.load_data(ticker, start_date, end_date)
    
    if df is None or len(df) < 100:
        print("❌ 데이터 로드 실패")
        return

    if 'date' in df.columns:
        df['date'] = pd.to_datetime(df['date'])
        df.set_index('date', inplace=True)
    
    data_feed = bt.feeds.PandasData(dataname=df)
    features = df.select_dtypes(include=[np.number]).columns.tolist()

    cerebro = bt.Cerebro()
    cerebro.adddata(data_feed)
    
    cerebro.addstrategy(
        TransformerWalkForwardStrategy,
        model_weights_path=weight_path,
        raw_df=df,
        features=features,
        loader=loader
    )

    if enable_plot:
        cerebro.addobserver(AIScoreObserver)

    cerebro.broker.setcash(10_000_000)
    cerebro.broker.setcommission(commission=0.0015)
    cerebro.addanalyzer(bt.analyzers.SharpeRatio, _name='sharpe', riskfreerate=0.0)
    cerebro.addanalyzer(bt.analyzers.DrawDown, _name='drawdown')

    print(f"💰 초기 자산: {cerebro.broker.getvalue():,.0f}원")
    results = cerebro.run()
    
    strat = results[0]
    final_val = cerebro.broker.getvalue()
    mdd = strat.analyzers.drawdown.get_analysis()['max']['drawdown']
    sharpe = strat.analyzers.sharpe.get_analysis().get('sharperatio', 0.0)
    
    print(f"💰 최종 자산: {final_val:,.0f}원 ({(final_val/10000000 - 1)*100:.2f}%)")
    print(f"📉 MDD: {mdd:.2f}% | 📊 Sharpe: {sharpe:.4f}")

    if enable_plot:
        cerebro.plot(style='candlestick', volume=False)

if __name__ == "__main__":
    run_single_backtest()