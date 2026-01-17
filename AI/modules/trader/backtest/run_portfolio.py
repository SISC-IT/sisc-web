# AI/modules/trader/backtest/run_portfolio.py
"""
[포트폴리오 통합 백테스트 엔진 (Lazy Loading 적용)]
- 더미 데이터 없이, 실제 데이터가 들어오는 시점에 모델을 동적으로 로드합니다.
- 기술적 지표(RSI, MACD 등)를 자동 반영하여 모델 입력 차원을 맞춥니다.
"""

import sys
import os
import backtrader as bt
import pandas as pd
import numpy as np
from datetime import datetime

current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model
from AI.modules.signal.core.features import add_technical_indicators
from AI.modules.trader.strategies.portfolio_logic import calculate_portfolio_allocation

class AIPortfolioStrategy(bt.Strategy):
    params = (
        ('model_weights_path', None),
        ('strategy_config', {'seq_len': 60, 'top_k': 3, 'buy_threshold': 0.6}),
        ('rebalance_days', 1),
    )

    def log(self, txt, dt=None):
        dt = dt or self.datas[0].datetime.date(0)
        print(f'[{dt.isoformat()}] {txt}')

    def __init__(self):
        # ★ 개선: 여기서 모델을 로드하지 않습니다. (Lazy Loading)
        # 실제 데이터가 들어와서 피처 개수가 확정될 때까지 기다립니다.
        self.model = None 
        self.feature_columns = None
        self.daily_value = []

    def _initialize_model(self, input_dim):
        """실제 데이터의 피처 개수(input_dim)를 확인한 후 모델을 로드합니다."""
        print(f"🔄 모델 초기화 중... (감지된 입력 차원: {input_dim})")
        path = self.p.model_weights_path
        config = self.p.strategy_config
        
        model_config = {
            "head_size": 256, "num_heads": 4, "ff_dim": 4,
            "num_blocks": 4, "mlp_units": [128], "dropout": 0.25
        }

        try:
            model = get_model("transformer", model_config)
            dummy_input = (None, config['seq_len'], input_dim)
            model.build(dummy_input)
            
            if path and os.path.exists(path):
                if hasattr(model, 'model') and hasattr(model.model, 'load_weights'):
                    model.model.load_weights(path)
                else:
                    model.load_weights(path)
                print(f"✅ 모델 로드 성공: {path}")
            else:
                print("⚠️ 모델 가중치 파일 없음. (랜덤 예측 모드)")
            return model
        except Exception as e:
            print(f"❌ 모델 로드 실패: {e}")
            return None

    def next(self):
        if len(self) % self.p.rebalance_days != 0:
            return

        current_date = self.datas[0].datetime.date(0)
        lookback = self.p.strategy_config['seq_len']
        fetch_len = lookback + 50 
        
        # 1. 데이터 수집 및 전처리
        data_map = {}
        valid_datas = []
        
        for d in self.datas:
            ticker = d._name
            if len(d) < fetch_len: continue
            
            try:
                o = d.open.get(ago=0, size=fetch_len)
                h = d.high.get(ago=0, size=fetch_len)
                l = d.low.get(ago=0, size=fetch_len)
                c = d.close.get(ago=0, size=fetch_len)
                v = d.volume.get(ago=0, size=fetch_len)
                
                if len(o) < fetch_len: continue

                df = pd.DataFrame({'open': o, 'high': h, 'low': l, 'close': c, 'volume': v})
                
                # ★ 피처 엔지니어링 수행
                df = add_technical_indicators(df)
                df = df.fillna(0)
                
                # ★ [핵심] 첫 실행 시점에 모델 초기화
                if self.model is None:
                    # 'date', 'ticker' 등 불필요한 컬럼 제외하고 숫자형 컬럼만 선택
                    feature_cols = [col for col in df.columns if col not in ['date', 'ticker', 'target']]
                    self.feature_columns = feature_cols
                    # 여기서 실제 피처 개수를 세서 모델을 만듭니다.
                    self.model = self._initialize_model(len(feature_cols))

                if len(df) >= lookback:
                    data_map[ticker] = df 
                    valid_datas.append(d)
                
            except Exception:
                continue

        if not data_map: return

        # 모델 로드 실패 시 중단 방지
        if self.model is None and self.feature_columns is None:
             return

        # 2. 전략 코어 호출
        target_weights, scores = calculate_portfolio_allocation(
            data_map=data_map,
            model=self.model,
            feature_columns=self.feature_columns,
            config=self.p.strategy_config
        )
        
        # 3. 주문 실행
        current_value = self.broker.getvalue()
        self.daily_value.append((current_date, current_value))
        
        if current_value <= 0: return

        buy_orders = []

        for d in valid_datas:
            ticker = d._name
            target_pct = target_weights.get(ticker, 0.0)
            pos_value = self.getposition(d).size * d.close[0]
            current_pct = pos_value / current_value
            
            if abs(target_pct - current_pct) > 0.01:
                if target_pct < current_pct:
                    self.order_target_percent(data=d, target=target_pct)
                else:
                    buy_orders.append((d, target_pct))

        for d, target_pct in buy_orders:
            self.order_target_percent(data=d, target=target_pct)

    def notify_order(self, order):
        pass

def run_backtest():
    print("\n=== 🚀 AI 포트폴리오 백테스트 시작 (Lazy Loading) ===")
    
    cerebro = bt.Cerebro()
    start_date = "2023-01-01"
    end_date = datetime.now().strftime("%Y-%m-%d")
    target_tickers = ["AAPL", "TSLA", "MSFT", "NVDA", "AMD"]
    
    print(f"대상 종목: {target_tickers}")

    loader = SignalDataLoader(sequence_length=60)
    loaded_count = 0
    
    for ticker in target_tickers:
        try:
            df = loader.load_data(ticker, start_date=start_date, end_date=end_date)
            if df is None or df.empty or len(df) < 100:
                print(f"⚠️ {ticker}: 데이터 부족")
                continue

            if 'date' in df.columns:
                df['date'] = pd.to_datetime(df['date'])
                df.set_index('date', inplace=True)
            
            data_feed = bt.feeds.PandasData(dataname=df, name=ticker, plot=False)
            cerebro.adddata(data_feed)
            loaded_count += 1
        except Exception as e:
            print(f"❌ {ticker} 로드 에러: {e}")

    if loaded_count == 0:
        print("❌ 실행할 데이터가 없습니다.")
        return

    model_path = os.path.join(project_root, "AI/data/weights/transformer/universal_transformer.keras")
    cerebro.addstrategy(
        AIPortfolioStrategy,
        model_weights_path=model_path,
        strategy_config={"seq_len": 60, "top_k": 3, "buy_threshold": 0.6}
    )

    initial_cash = 100_000_000
    cerebro.broker.setcash(initial_cash)
    cerebro.broker.setcommission(commission=0.0015)

    cerebro.addanalyzer(bt.analyzers.SharpeRatio, _name='sharpe', riskfreerate=0.02, timeframe=bt.TimeFrame.Days)
    cerebro.addanalyzer(bt.analyzers.DrawDown, _name='drawdown')

    print(f"초기 자산: {initial_cash:,.0f}원")
    print("⏳ 시뮬레이션 진행 중...")
    
    results = cerebro.run()
    strat = results[0]

    final_value = cerebro.broker.getvalue()
    profit = final_value - initial_cash
    profit_rate = (profit / initial_cash) * 100
    
    sharpe_dict = strat.analyzers.sharpe.get_analysis()
    sharpe_val = sharpe_dict.get('sharperatio')
    sharpe = sharpe_val if sharpe_val is not None else 0.0
    
    dd_dict = strat.analyzers.drawdown.get_analysis()
    mdd = dd_dict['max']['drawdown'] if dd_dict else 0.0

    print("\n=== 📊 백테스트 최종 결과 ===")
    print(f"최종 자산: {final_value:,.0f}원")
    print(f"총 수익금: {profit:+,.0f}원 ({profit_rate:+.2f}%)")
    print(f"Sharpe Ratio: {sharpe:.4f}")
    print(f"Max Drawdown (MDD): {mdd:.2f}%")

if __name__ == "__main__":
    run_backtest()