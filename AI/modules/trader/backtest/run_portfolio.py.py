# AI/modules/trader/backtest/run_portfolio.py
"""
[포트폴리오 통합 백테스트 엔진 (BugFixed)]
- 모델 로드 시 wrapper 객체 구조 대신 model.load() 사용
- Scikit-Learn UserWarning 해결 (DataFrame 형태로 transform 수행)
- 실시간 데이터 스케일링 적용
"""

import sys
import os
import backtrader as bt
import pandas as pd
import numpy as np
from datetime import datetime

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.modules.signal.models import get_model
from AI.modules.trader.strategies.portfolio_logic import calculate_portfolio_allocation

class AIPortfolioStrategy(bt.Strategy):
    params = (
        ('model_path', None),        # .keras 모델 파일 경로
        ('strategy_config', {'seq_len': 60, 'top_k': 3, 'buy_threshold': 0.6}),
        ('rebalance_days', 1),
        ('raw_data_map', {}),        # 원본 데이터프레임 (보조지표 포함) 전달용
        ('scaler', None),            # 학습 시 사용한 스케일러
        ('feature_columns', []),     # 학습에 사용된 컬럼 순서 리스트
    )

    def log(self, txt, dt=None):
        dt = dt or self.datas[0].datetime.date(0)
        print(f'[{dt.isoformat()}] {txt}')

    def __init__(self):
        self.model = self._load_model_safe()
        self.daily_value = []
        self.order_list = [] # 미체결 주문 관리

    def _load_model_safe(self):
        """AI 모델 로드 (Wrapper의 load 메서드 활용)"""
        path = self.p.model_path
        
        # 껍데기 모델 생성 (Config는 로드 시 덮어써지므로 빈 딕셔너리 전달)
        model = get_model("transformer", {}) 
        
        try:
            if path and os.path.exists(path):
                # ★ 핵심 수정: 가중치만 로드하는 것이 아니라 모델 전체 구조를 로드
                model.load(path)
                print(f"✅ 모델 로드 성공: {path}")
                return model
            else:
                print("⚠️ 모델 파일 없음. 랜덤 예측 모드로 동작합니다.")
                return None
        except Exception as e:
            print(f"❌ 모델 로드 중 치명적 오류: {e}")
            return None

    def next(self):
        # 리밸런싱 주기 체크
        if len(self) % self.p.rebalance_days != 0:
            return

        current_date = self.datas[0].datetime.date(0)
        ts_date = pd.Timestamp(current_date)
        
        seq_len = self.p.strategy_config['seq_len']
        
        # 1. 모델 입력 데이터 준비 (스케일링 및 시퀀싱)
        current_data_map = {} 
        valid_tickers = []
        
        for d in self.datas:
            ticker = d._name
            full_df = self.p.raw_data_map.get(ticker)
            
            if full_df is None: 
                continue

            try:
                # 현재 날짜 포함 이전 데이터 가져오기 (Lookahead bias 방지)
                # loc[:ts_date]는 해당 날짜까지의 데이터를 포함함
                subset = full_df.loc[:ts_date]
                
                if len(subset) < seq_len:
                    continue
                
                # 최근 seq_len 만큼 추출
                recent_df = subset.iloc[-seq_len:]
                
                # ★ [수정] .values를 제거하여 DataFrame 형태 유지 (Warning 해결)
                # DataFrame을 그대로 전달해야 Scikit-Learn이 컬럼 이름을 확인하고 경고를 띄우지 않음
                if self.p.feature_columns:
                    features_input = recent_df[self.p.feature_columns]
                else:
                    features_input = recent_df.select_dtypes(include=[np.number])

                # 스케일링 수행
                if self.p.scaler:
                    scaled_array = self.p.scaler.transform(features_input)
                else:
                    scaled_array = features_input.values # 스케일러 없으면 값만 사용
                
                # DataFrame 형태로 다시 포장 (전략 로직 전달용, 인덱스 유지)
                df_prepared = pd.DataFrame(
                    scaled_array, 
                    index=recent_df.index, 
                    columns=self.p.feature_columns if self.p.feature_columns else None
                )
                
                current_data_map[ticker] = df_prepared
                valid_tickers.append(d)
                
            except KeyError:
                # 해당 날짜 데이터가 없는 종목 (휴장 등)
                continue

        if not current_data_map:
            return

        # 2. 포트폴리오 비중 계산 (외부 로직)
        target_weights, scores = calculate_portfolio_allocation(
            data_map=current_data_map, # 스케일링된 데이터프레임들
            model=self.model,
            feature_columns=self.p.feature_columns,
            config=self.p.strategy_config
        )
        
        # 3. 주문 실행
        current_val = self.broker.getvalue()
        self.daily_value.append((current_date, current_val))
        
        if current_val <= 0: return

        # (1) 매도 처리 (현금 확보)
        for d in valid_tickers:
            ticker = d._name
            target_pct = target_weights.get(ticker, 0.0)
            
            # 현재 포지션 비중 계산
            pos = self.getposition(d)
            pos_value = pos.size * d.close[0]
            curr_pct = pos_value / current_val if current_val > 0 else 0
            
            # 비중 축소 (매도) - 오차 1% 이상일 때만 실행
            if target_pct < curr_pct and abs(target_pct - curr_pct) > 0.01:
                self.order_target_percent(d, target=target_pct)
                
        # (2) 매수 처리
        for d in valid_tickers:
            ticker = d._name
            target_pct = target_weights.get(ticker, 0.0)
            
            pos = self.getposition(d)
            pos_value = pos.size * d.close[0]
            curr_pct = pos_value / current_val if current_val > 0 else 0
            
            # 비중 확대 (매수)
            if target_pct > curr_pct and abs(target_pct - curr_pct) > 0.01:
                self.order_target_percent(d, target=target_pct)

    def notify_order(self, order):
        if order.status in [order.Completed]:
            # 너무 많은 로그 방지를 위해 주석 처리하거나 필요시 해제
            # type_str = 'BUY' if order.isbuy() else 'SELL'
            # self.log(f'{type_str} 체결: {order.data._name} @ {order.executed.price:.2f}')
            pass

def run_backtest():
    print("\n=== 🚀 AI 포트폴리오 백테스트 시작 (Fixed) ===")
    
    cerebro = bt.Cerebro()
    
    # 설정
    start_date = "2023-01-01"
    end_date = datetime.now().strftime("%Y-%m-%d")
    target_tickers = ["AAPL", "TSLA", "MSFT", "NVDA", "AMD"] # 테스트 대상 종목
    
    # 1. 데이터 로드 및 전역 저장소 준비
    loader = SignalDataLoader(sequence_length=60)
    raw_data_map = {}
    all_data_list = [] # 스케일러 학습용 데이터 리스트
    
    print("1. 데이터 로드 및 전처리 중...")
    
    for ticker in target_tickers:
        try:
            df = loader.load_data(ticker, start_date, end_date)
            
            if df is not None and not df.empty and len(df) > 100:
                # 날짜 인덱스 보장
                if 'date' in df.columns:
                    df['date'] = pd.to_datetime(df['date'])
                    df.set_index('date', inplace=True)
                
                # 원본 데이터 저장 (보조지표 포함)
                raw_data_map[ticker] = df
                all_data_list.append(df)
                
                # Backtrader용 데이터 피드 추가 (OHLCV만 사용됨)
                data_feed = bt.feeds.PandasData(dataname=df, name=ticker, plot=False)
                cerebro.adddata(data_feed)
            else:
                print(f"⚠️ {ticker}: 데이터 부족 또는 로드 실패")
                
        except Exception as e:
            print(f"❌ {ticker} 로드 중 에러: {e}")
    
    if not all_data_list:
        print("❌ 실행할 데이터가 없습니다.")
        return

    # 2. 스케일러 설정 및 피처 컬럼 확정
    # 모든 종목의 데이터를 합쳐서 스케일러를 학습(Fit)시킵니다.
    # 주의: 실제 운영 시에는 Training Data로만 Fit 해야 Data Leakage가 없습니다.
    # 여기서는 백테스트 편의상 전체 기간 데이터로 Fit 합니다.
    sample_df = all_data_list[0]
    
    # 숫자형 컬럼만 피처로 사용
    feature_columns = sample_df.select_dtypes(include=[np.number]).columns.tolist()
    
    print(f"   - Feature Columns ({len(feature_columns)}개): {feature_columns[:5]} ...")
    
    # 통합 Fit
    combined_df = pd.concat(all_data_list)
    # DataFrame 형태로 fit하여 컬럼 이름 저장
    loader.scaler.fit(combined_df[feature_columns]) 
    print("   - Scaler 학습 완료")

    # 3. 전략 실행 설정
    # 모델 파일 경로 (실제 파일이 있는 경로로 수정 필요)
    model_path = os.path.join(project_root, "AI/data/weights/transformer/universal_transformer.keras")
    
    cerebro.addstrategy(
        AIPortfolioStrategy,
        model_path=model_path,
        strategy_config={"seq_len": 60, "top_k": 3},
        # ★ 핵심: 전략에 원본 데이터와 스케일러, 피처 정보를 주입
        raw_data_map=raw_data_map,
        scaler=loader.scaler,
        feature_columns=feature_columns,
        rebalance_days=1
    )

    initial_cash = 100_000_000
    cerebro.broker.setcash(initial_cash)
    cerebro.broker.setcommission(commission=0.0015) # 수수료 0.15%
    
    # 분석기 추가
    cerebro.addanalyzer(bt.analyzers.SharpeRatio, _name='sharpe', riskfreerate=0.02, timeframe=bt.TimeFrame.Days)
    cerebro.addanalyzer(bt.analyzers.DrawDown, _name='drawdown')
    cerebro.addanalyzer(bt.analyzers.Returns, _name='returns')

    print(f"초기 자산: {initial_cash:,.0f}원")
    print("⏳ 시뮬레이션 진행 중...")
    
    results = cerebro.run()
    strat = results[0]

    # 결과 출력
    final_value = cerebro.broker.getvalue()
    profit = final_value - initial_cash
    profit_rate = (profit / initial_cash) * 100
    
    # 샤프지수 안전하게 가져오기
    sharpe_res = strat.analyzers.sharpe.get_analysis()
    sharpe = sharpe_res.get('sharperatio', 0.0)
    if sharpe is None: sharpe = 0.0

    # MDD 안전하게 가져오기
    dd_res = strat.analyzers.drawdown.get_analysis()
    mdd = dd_res['max']['drawdown'] if dd_res else 0.0

    print("\n=== 📊 백테스트 최종 결과 ===")
    print(f"최종 자산: {final_value:,.0f}원")
    print(f"총 수익금: {profit:+,.0f}원 ({profit_rate:+.2f}%)")
    print(f"Sharpe Ratio: {sharpe:.4f}")
    print(f"Max Drawdown (MDD): {mdd:.2f}%")

if __name__ == "__main__":
    run_backtest()