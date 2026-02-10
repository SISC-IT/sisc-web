# AI/modules/trader/backtest/run_backtrader_single.py
"""
[Multi-Horizon Model Backtest]
- 학습된 멀티 호라이즌 모델(1,3,5,7일 예측)을 로드합니다.
- 저장된 Scaler를 불러와 학습 때와 똑같은 기준으로 데이터를 변환합니다.
- [전략] 단기(1일) 노이즈는 무시하고, 중기(3,5,7일) 추세가 모두 좋을 때만 매수합니다.
"""

import sys
import os
import warnings

# 지저분한 경고 무시
# [설정 1] TensorFlow oneDNN 최적화 메시지 숨기기
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'

# [설정 2] TensorFlow 일반 로그(INFO, WARNING) 숨기기 (ERROR만 출력)
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

# [설정 3] NumPy/Keras의 np.object 관련 FutureWarning 숨기기
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning, module='sklearn')
warnings.filterwarnings("ignore", category=UserWarning, module='pandas')

import shutil
import pickle
import backtrader as bt
import pandas as pd
import numpy as np
import tensorflow as tf


# 프로젝트 루트 경로 설정
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

from AI.modules.signal.models.transformer.architecture import build_transformer_model
from AI.modules.features.legacy.technical_features import add_technical_indicators, add_multi_timeframe_features
from AI.modules.trader.strategies.rule_based import RuleBasedStrategy
from AI.modules.signal.core.data_loader import DataLoader 

class MultiHorizonScoreObserver(bt.Observer):
    """차트 하단에 AI의 종합 점수(Composite Score)를 그립니다."""
    lines = ('score', 'limit_buy', 'limit_sell')
    plotinfo = dict(plot=True, subplot=True, plotname='AI Trend Probability')
    plotlines = dict(
        score=dict(marker='o', markersize=3.0, color='blue'),
        limit_buy=dict(color='red', linestyle='--'),
        limit_sell=dict(color='green', linestyle='--')
    )

    def next(self):
        score = getattr(self._owner, 'current_score', 0.5)
        self.lines.score[0] = score
        self.lines.limit_buy[0] = 0.60  # 매수 기준선
        self.lines.limit_sell[0] = 0.40 # 매도 기준선

class MultiHorizonStrategy(bt.Strategy):
    params = (
        ('model_path', None),
        ('scaler_path', None),
        ('raw_df', None),     
        ('seq_len', 60),      
        ('ticker_id', 0),     # 종목 ID (Embedding용, 모르면 0)
        ('sector_id', 0),     # 섹터 ID (Embedding용, 모르면 0)
    )

    def __init__(self):
        self.model = self._load_model()
        self.scaler = self._load_scaler()
        self.order = None
        self.current_score = 0.5 
        
        # [전략] 확률이 60% 이상이면 매수, 40% 이하면 매도
        self.strategy_logic = RuleBasedStrategy(buy_threshold=0.60, sell_threshold=0.40)
        
        # 학습 때 사용한 Feature 순서 그대로 정의
        self.feature_cols = [
            'log_return', 
            'open_ratio', 'high_ratio', 'low_ratio', 
            'vol_change',
            'ma5_ratio', 'ma20_ratio', 'ma60_ratio', 
            'rsi', 
            'macd_ratio', 
            'bb_position',
            'week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change',
            'month_ma12_ratio', 'month_rsi'
        ]

    def log(self, txt, dt=None):
        dt = dt or self.datas[0].datetime.date(0)
        print(f'[{dt.isoformat()}] {txt}')
        
    def _load_model(self):
        path = self.p.model_path
        if not path or not os.path.exists(path):
            self.log(f"⚠️ 모델 파일이 없습니다: {path}")
            return None
        
        try:
            # DataLoader를 통해 실제 DB에 저장된 종목/섹터 개수를 가져옵니다.
            loader = DataLoader()
            real_n_tickers = len(loader.ticker_to_id)
            real_n_sectors = len(loader.sector_to_id)

            # 모델 껍데기 생성 (동적 크기 할당)
            model = build_transformer_model(
                input_shape=(60, 17),
                n_tickers=real_n_tickers, 
                n_sectors=real_n_sectors,
                n_outputs=4 
            )
            
            # ------------------------------------------------------------------
            # [핵심 수정] HDF5 / Zip 포맷 호환성 처리
            # ------------------------------------------------------------------
            try:
                # 1차 시도: 기본 로드 (.keras = Zip 포맷 가정)
                model.load_weights(path)
                self.log("✅ 멀티 호라이즌 AI 모델 로드 완료 (Standard)")
                return model

            except Exception as e:
                # 에러 메시지에 'zip' 혹은 'header' 관련 내용이 있다면 포맷 불일치 가능성 높음
                if "not a zip file" in str(e) or "header" in str(e):
                    self.log(f"⚠️ Zip 포맷 로드 실패 ({e}). HDF5 방식으로 재시도합니다.")
                    
                    # 확장자를 .h5로 변경한 임시 파일 생성 (Keras가 확장자를 보고 로더를 결정함)
                    temp_h5_path = path.replace(".keras", "_temp_fallback.h5")
                    
                    try:
                        shutil.copyfile(path, temp_h5_path)
                        # 임시 파일로 가중치 로드
                        model.load_weights(temp_h5_path)
                        self.log("✅ 멀티 호라이즌 AI 모델 로드 완료 (HDF5 Fallback)")
                        
                        # 성공 시 모델 반환 (임시 파일 삭제는 finally에서)
                        return model
                    except Exception as e_h5:
                        self.log(f"❌ HDF5 로드도 실패했습니다: {e_h5}")
                        return None
                    finally:
                        # 임시 파일 정리
                        if os.path.exists(temp_h5_path):
                            os.remove(temp_h5_path)
                else:
                    # 다른 종류의 에러라면 그대로 출력
                    self.log(f"⚠️ 모델 가중치 로드 중 알 수 없는 오류: {e}")
                    return None
            
        except Exception as e:
            self.log(f"⚠️ 모델 초기화 실패: {e}")
            return None

    def _load_scaler(self):
        path = self.p.scaler_path
        if not path or not os.path.exists(path):
            self.log("⚠️ 스케일러 파일이 없습니다.")
            return None
        with open(path, "rb") as f:
            return pickle.load(f)

    def notify_order(self, order):
        if order.status in [order.Completed]:
            if order.isbuy():
                self.log(f"🔵 BUY 체결 @ {order.executed.price:,.0f}")
            elif order.issell():
                self.log(f"🔴 SELL 체결 @ {order.executed.price:,.0f} (수익: {order.executed.pnl:,.0f})")
            self.order = None

    def next(self):
        if not self.model or not self.scaler: return
        if len(self) < self.p.seq_len: return

        # 1. 데이터 준비
        current_date = self.datas[0].datetime.datetime(0)
        
        try:
            past_data = self.p.raw_df.loc[:current_date].iloc[-self.p.seq_len:]
        except:
            return

        if len(past_data) < self.p.seq_len: return

        # 2. 전처리 (스케일링)
        try:
            features_data = past_data[self.feature_cols].values
            scaled_data = self.scaler.transform(features_data)
            
            # (1, 60, 17)
            input_seq = np.expand_dims(scaled_data, axis=0)
            
            # 임베딩용 ID
            t_input = np.array([self.p.ticker_id])
            s_input = np.array([self.p.sector_id])

            # 3. AI 예측
            # verbose=0 필수 (로그 폭탄 방지)
            pred = self.model.predict([input_seq, t_input, s_input], verbose=0)
            probs = pred[0] # [p1, p3, p5, p7]
            
        except Exception as e:
            return

        # 4. 종합 점수 계산 (3,5,7일 평균)
        trend_score = np.mean(probs[1:]) 
        self.current_score = trend_score

        # 5. 매매 판단
        if self.order: return 

        position_qty = self.position.size
        decision = self.strategy_logic.get_action(trend_score, position_qty)

        if decision['type'] == 'BUY':
            cash = self.broker.get_cash()
            price = self.datas[0].close[0]
            size = int((cash * 0.95) / price)
            if size > 0:
                self.log(f"⚡ 신호발생: 3/5/7일 상승확률 평균 {trend_score*100:.1f}% -> 매수")
                self.order = self.buy(size=size)
                
        elif decision['type'] == 'SELL':
            if position_qty > 0:
                self.log(f"⚡ 신호발생: 하락 반전 ({trend_score*100:.1f}%) -> 매도")
                self.order = self.close()

def run_single_backtest(ticker="AAPL", start_date="2024-01-01", end_date="2025-01-01", enable_plot=True):
    print(f"\n=== [{ticker}] AI 모델 실전 검증 (Backtest) ===")
    
    # 1. 경로 설정 (절대 경로 추천)
    current_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
    weights_dir = os.path.join(project_root, "AI/data/weights/transformer")
    
    # [주의] 파일 경로가 정확한지 확인 (경로 오류 방지)
    model_path = os.path.join(weights_dir, "tests/multi_horizon_model_test.keras")
    scaler_path = os.path.join(weights_dir, "tests/multi_horizon_scaler_test.pkl")
    
    # 2. 데이터 로드 (DB 연결)
    from AI.libs.database.connection import get_db_conn
    conn = get_db_conn()
    
    # [수정] SQLAlchemy 경고 방지를 위해 try-except 및 read_sql_query 권장
    query = f"""
        SELECT date, open, high, low, close, volume, adjusted_close, ticker 
        FROM price_data 
        WHERE ticker = '{ticker}' AND date >= '2022-01-01'
        ORDER BY date ASC
    """
    try:
        df = pd.read_sql(query, conn)
    except Exception as e:
        print(f"❌ 데이터베이스 조회 실패: {e}")
        conn.close()
        return

    conn.close()
    
    if df.empty:
        print("❌ 데이터 없음")
        return

    df['date'] = pd.to_datetime(df['date'])
    
    print(">> 지표 생성 중...")
    try:
        df = add_technical_indicators(df)
        df = add_multi_timeframe_features(df)
    except Exception as e:
        print(f"⚠️ 지표 생성 중 오류 발생: {e}")
        return
    
    mask = (df['date'] >= start_date) & (df['date'] <= end_date)
    backtest_df = df.loc[mask].copy()

    if backtest_df.empty:
        print(f"❌ 해당 기간({start_date}~{end_date}) 데이터 없음")
        return

    backtest_df.set_index('date', inplace=True)
    
    # 3. Backtrader 설정
    cerebro = bt.Cerebro()
    data_feed = bt.feeds.PandasData(dataname=backtest_df)
    cerebro.adddata(data_feed)
    
    # 전략 추가
    cerebro.addstrategy(
        MultiHorizonStrategy,
        model_path=model_path,
        scaler_path=scaler_path,
        raw_df=df.set_index('date'), 
        ticker_id=0, # 테스트용 0
        sector_id=0  # 테스트용 0
    )

    if enable_plot:
        cerebro.addobserver(MultiHorizonScoreObserver)

    cerebro.broker.setcash(10_000_000)
    cerebro.broker.setcommission(commission=0.0015) 
    
    cerebro.addanalyzer(bt.analyzers.SharpeRatio, _name='sharpe', riskfreerate=0.0)
    cerebro.addanalyzer(bt.analyzers.DrawDown, _name='drawdown')
    cerebro.addanalyzer(bt.analyzers.Returns, _name='returns')

    print(f"💰 시작 자산: {cerebro.broker.getvalue():,.0f}원")
    
    try:
        results = cerebro.run()
    except Exception as e:
        print(f"❌ 백테스트 실행 중 오류 발생: {e}")
        return

    strat = results[0]
    final_val = cerebro.broker.getvalue()
    
    # 결과 분석 (안전하게 가져오기)
    try:
        mdd_analysis = strat.analyzers.drawdown.get_analysis()
        mdd = mdd_analysis.get('max', {}).get('drawdown', 0.0)
        
        sharpe_analysis = strat.analyzers.sharpe.get_analysis()
        sharpe = sharpe_analysis.get('sharperatio')
        if sharpe is None: sharpe = 0.0
    except:
        mdd = 0.0
        sharpe = 0.0
    
    print("\n" + "="*40)
    print(f"📊 결과 요약 ({ticker})")
    print("="*40)
    print(f"💰 최종 자산: {final_val:,.0f}원")
    print(f"📈 수익률   : {(final_val/10000000 - 1)*100:.2f}%")
    print(f"📉 MDD      : {mdd:.2f}%")
    print(f"💎 Sharpe   : {sharpe:.4f}")
    print("="*40 + "\n")

    if enable_plot:
        try:
            cerebro.plot(style='candlestick', volume=False)
        except Exception as e:
            print(f"⚠️ 차트 그리기 실패: {e}")

if __name__ == "__main__":
    # 원하는 종목으로 변경해서 테스트 (예: AAPL, 005930, TSLA)
    print("멀티 호라이즌 AI 모델 백테스트 시작")
    print("대상:AAPL:상승안정형")
    run_single_backtest(ticker="AAPL", start_date="2024-01-01", end_date="2025-01-01")
    print("대상:MSFT,상승안정형")
    run_single_backtest(ticker="MSFT", start_date="2024-01-01", end_date="2025-01-01")
    print("대상:TSLA,변동성형")
    run_single_backtest(ticker="TSLA", start_date="2024-01-01", end_date="2025-01-01")
    print("대상:COIN,성장형")
    run_single_backtest(ticker="COIN", start_date="2024-01-01", end_date="2025-01-01")
    print("대상:ORCL,배당형")
    run_single_backtest(ticker="ORCL", start_date="2024-01-01", end_date="2025-01-01")
    print("대상:CSCO,횡보형")
    run_single_backtest(ticker="CSCO", start_date="2024-01-01", end_date="2025-01-01")
    print("대상:INTC,하락추세형")
    run_single_backtest(ticker="INTC", start_date="2024-01-01", end_date="2025-01-01")
    print("대상:BA,침체형")
    run_single_backtest(ticker="BA", start_date="2024-01-01", end_date="2025-01-01")
    print("대상:KO,안정형")
    run_single_backtest(ticker="KO", start_date="2024-01-01", end_date="2025-01-01")
    print("대상:PFE,방어형")
    run_single_backtest(ticker="PFE", start_date="2024-01-01", end_date="2025-01-01")
    print("멀티 호라이즌 AI 모델 백테스트 종료")