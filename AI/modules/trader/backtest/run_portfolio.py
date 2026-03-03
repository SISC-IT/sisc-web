# AI/modules/trader/backtest/run_portfolio.py
"""
[포트폴리오 통합 백테스트 엔진 - Ultimate Version]
- Bulk Load 및 Pre-processing을 적용하여 백테스트 속도를 극대화했습니다.
- 멀티 호라이즌 트랜스포머 모델 및 Scaler를 완벽히 로드하여 추론 무결성을 확보합니다.
- HDF5 Fallback 로직을 적용하여 Keras 포맷 에러를 방지합니다.
- 운영 파이프라인(daily_routine)과 완벽히 동일한 portfolio_logic을 사용합니다.
"""

import sys
import os
import warnings
import shutil
import pickle
from datetime import datetime

#경고 무시
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

import backtrader as bt
import pandas as pd
import numpy as np

current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# 핵심 모듈 Import
from AI.modules.signal.models.transformer.architecture import build_transformer_model
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.features.legacy.technical_features import add_technical_indicators
from AI.modules.trader.strategies.portfolio_logic import calculate_portfolio_allocation

class AIPortfolioStrategy(bt.Strategy):
    params = (
        ('model_path', None),
        ('scaler_path', None),
        ('raw_dfs', {}),       # 사전 계산된 전체 데이터프레임 딕셔너리
        ('ticker_ids', {}),    # 임베딩용 Ticker ID
        ('sector_ids', {}),    # 임베딩용 Sector ID
        ('strategy_config', {'seq_len': 60, 'top_k': 3, 'buy_threshold': 0.6}),
        ('rebalance_days', 1),
    )

    def log(self, txt, dt=None):
        dt = dt or self.datas[0].datetime.date(0)
        print(f'[{dt.isoformat()}] {txt}')

    def __init__(self):
        # 17개 피처 고정 (학습 데이터와 동일하게)
        self.feature_cols = [
            'log_return', 'open_ratio', 'high_ratio', 'low_ratio', 'vol_change',
            'ma5_ratio', 'ma20_ratio', 'ma60_ratio', 'rsi', 'macd_ratio', 'bb_position',
            'week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change',
            'month_ma12_ratio', 'month_rsi'
        ]
        
        # 모델 및 스케일러 미리 로드 (Eager Loading)
        self.model = self._load_model()
        self.scaler = self._load_scaler()

    def _load_model(self):
        """HDF5 Fallback 기능이 포함된 안전한 모델 로더"""
        path = self.p.model_path
        if not path or not os.path.exists(path):
            self.log(f"⚠️ 모델 파일이 없습니다: {path}")
            return None
        
        try:
            model = build_transformer_model(
                input_shape=(self.p.strategy_config['seq_len'], len(self.feature_cols)),
                n_tickers=len(self.p.ticker_ids), 
                n_sectors=len(self.p.sector_ids),
                n_outputs=4 
            )
            
            try:
                model.load_weights(path)
                self.log("✅ 모델 로드 완료 (Standard)")
                return model
            except Exception as e:
                if "not a zip file" in str(e) or "header" in str(e):
                    temp_h5_path = path.replace(".keras", "_temp_fallback.h5")
                    try:
                        shutil.copyfile(path, temp_h5_path)
                        model.load_weights(temp_h5_path)
                        self.log("✅ 모델 로드 완료 (HDF5 Fallback)")
                        return model
                    except Exception as e_h5:
                        self.log(f"❌ HDF5 로드 실패: {e_h5}")
                        return None
                    finally:
                        if os.path.exists(temp_h5_path):
                            os.remove(temp_h5_path)
                else:
                    return None
        except Exception as e:
            self.log(f"❌ 모델 초기화 실패: {e}")
            return None

    def _load_scaler(self):
        path = self.p.scaler_path
        if not path or not os.path.exists(path):
            self.log("⚠️ 스케일러 파일이 없습니다.")
            return None
        with open(path, "rb") as f:
            return pickle.load(f)

    def next(self):
        if not self.model or not self.scaler: return
        if len(self) % self.p.rebalance_days != 0: return

        current_date = self.datas[0].datetime.datetime(0)
        data_map = {}
        valid_datas = []
        
        # 1. 현재 날짜 기준으로 데이터 준비 (슬라이싱) - 실시간 연산 제거로 속도 극대화
        for d in self.datas:
            ticker = d._name
            raw_df = self.p.raw_dfs.get(ticker)
            if raw_df is None: continue
            
            try:
                # 현재 날짜까지의 과거 데이터만 추출
                past_data = raw_df.loc[:current_date]
                if len(past_data) >= self.p.strategy_config['seq_len']:
                    data_map[ticker] = past_data
                    valid_datas.append(d)
            except KeyError:
                continue

        if not data_map: return

        # 2. 통합 포트폴리오 로직 호출 (daily_routine과 동일한 함수 재사용!)
        target_weights, scores = calculate_portfolio_allocation(
            data_map=data_map,
            model=self.model,
            scaler=self.scaler,
            ticker_ids=self.p.ticker_ids,
            sector_ids=self.p.sector_ids,
            feature_columns=self.feature_cols,
            config=self.p.strategy_config
        )
        
        # 3. 주문 실행 (목표 비중과 현재 비중 비교)
        current_value = self.broker.getvalue()
        if current_value <= 0: return

        for d in valid_datas:
            ticker = d._name
            target_pct = target_weights.get(ticker, 0.0)
            
            pos_value = self.getposition(d).size * d.close[0]
            current_pct = pos_value / current_value
            
            # 비중이 1% 이상 차이날 때만 매매 (리밸런싱)
            if abs(target_pct - current_pct) > 0.01:
                self.order_target_percent(data=d, target=target_pct)

    def notify_order(self, order):
        if order.status in [order.Completed]:
            ticker = order.data._name
            if order.isbuy():
                self.log(f"🔵 [{ticker}] BUY 체결 @ {order.executed.price:,.0f}")
            elif order.issell():
                self.log(f"🔴 [{ticker}] SELL 체결 @ {order.executed.price:,.0f} (수익: {order.executed.pnl:,.0f})")

def run_backtest():
    print("\n=== 🚀 AI 포트폴리오 백테스트 시작 (최적화 버전) ===")
    
    cerebro = bt.Cerebro()
    start_date = "2023-01-01"
    end_date = "2024-12-31" # 평가 기간
    target_tickers = ["AAPL", "TSLA", "MSFT", "NVDA", "AMD"]
    
    # 1. 데이터 로더 초기화 (파라미터 없음)
    loader = DataLoader()
    
    # 2. 벌크 로드 (단 한 번의 DB 조회로 모든 데이터 가져오기)
    print(">> DB에서 데이터 벌크 로드 및 지표 생성 중...")
    # 지표 생성을 위해 150일 전부터 가져옴
    fetch_start = (pd.to_datetime(start_date) - pd.Timedelta(days=150)).strftime("%Y-%m-%d")
    bulk_df = loader.load_data_from_db(start_date=fetch_start, end_date=end_date, tickers=target_tickers)
    
    if bulk_df is None or bulk_df.empty:
        print("❌ 실행할 데이터가 없습니다.")
        return

    raw_dfs = {}
    
    # 3. 데이터 사전 처리 (Pre-processing)
    for ticker in target_tickers:
        df = bulk_df[bulk_df['ticker'] == ticker].copy()
        if df.empty or len(df) < 100:
            print(f"⚠️ {ticker}: 데이터 부족")
            continue
            
        try:
            # 엔진 시작 전 지표를 단 1번만 계산하여 저장!
            df = add_technical_indicators(df)
            df['date'] = pd.to_datetime(df['date'])
            df.set_index('date', inplace=True)
            
            raw_dfs[ticker] = df
            
            # Backtrader 엔진용 데이터는 start_date 이후만 주입
            backtest_df = df.loc[start_date:end_date]
            data_feed = bt.feeds.PandasData(dataname=backtest_df, name=ticker, plot=False)
            cerebro.adddata(data_feed)
            
        except Exception as e:
            print(f"❌ {ticker} 전처리 에러: {e}")

    if not raw_dfs:
        print("❌ 유효한 데이터가 없습니다.")
        return

    # 4. 전략 및 모델 설정
    weights_dir = os.path.join(project_root, "AI/data/weights/transformer")
    model_path = os.path.join(weights_dir, "tests/multi_horizon_model_test.keras")
    scaler_path = os.path.join(weights_dir, "tests/multi_horizon_scaler_test.pkl")

    cerebro.addstrategy(
        AIPortfolioStrategy,
        model_path=model_path,
        scaler_path=scaler_path,
        raw_dfs=raw_dfs,                   # 전처리 완료된 데이터맵 전달
        ticker_ids=loader.ticker_to_id,    # 임베딩 매핑
        sector_ids=loader.sector_to_id,    # 임베딩 매핑
        strategy_config={"seq_len": 60, "top_k": 3, "buy_threshold": 0.60},
        rebalance_days=1
    )

    initial_cash = 100_000_000
    cerebro.broker.setcash(initial_cash)
    cerebro.broker.setcommission(commission=0.0015)

    cerebro.addanalyzer(bt.analyzers.SharpeRatio, _name='sharpe', riskfreerate=0.02, timeframe=bt.TimeFrame.Days)
    cerebro.addanalyzer(bt.analyzers.DrawDown, _name='drawdown')

    print(f"\n초기 자산: {initial_cash:,.0f}원")
    print("⏳ 시뮬레이션 진행 중...")
    
    results = cerebro.run()
    strat = results[0]

    final_value = cerebro.broker.getvalue()
    profit = final_value - initial_cash
    profit_rate = (profit / initial_cash) * 100
    
    try:
        sharpe = strat.analyzers.sharpe.get_analysis().get('sharperatio', 0.0)
        mdd = strat.analyzers.drawdown.get_analysis().get('max', {}).get('drawdown', 0.0)
    except:
        sharpe = 0.0
        mdd = 0.0

    print("\n" + "="*40)
    print("📊 포트폴리오 백테스트 최종 결과")
    print("="*40)
    print(f"💰 최종 자산: {final_value:,.0f}원")
    print(f"📈 총 수익금: {profit:+,.0f}원 ({profit_rate:+.2f}%)")
    print(f"💎 Sharpe   : {sharpe:.4f}")
    print(f"📉 MDD      : {mdd:.2f}%")
    print("="*40 + "\n")

if __name__ == "__main__":
    run_backtest()