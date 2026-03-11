# AI/pipelines/daily_routine.py

"""
[일일 자동화 파이프라인 (Meta-Ensemble Complete Version)]
- 다중 모델(TCN, PatchTST, iTransformer 등) 앙상블을 지원하는 신규 규격 적용
- 각 모델을 Wrapper 객체로 캡슐화하여 독립적인 데이터 전처리 및 추론 수행
- 게이팅(Gating) 및 리스크 오버레이(Risk Overlay)를 위한 매크로 데이터 연동 기반 마련
- target_date 인자를 받아 특정 과거 날짜 기준으로 시뮬레이션(Backfill) 가능
"""

import os
import warnings

# 1. TensorFlow C++ 레벨 로그 및 oneDNN 안내문 완벽 차단
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3' 
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'

# 2. Pandas, Scikit-learn 등 파이썬 레벨의 모든 성가신 경고 차단
warnings.filterwarnings('ignore', category=UserWarning)
warnings.filterwarnings('ignore', category=FutureWarning)
warnings.filterwarnings('ignore', message='.*InconsistentVersionWarning.*')
warnings.filterwarnings('ignore', message='.*SQLAlchemy.*')

import sys
import argparse
import traceback
import pandas as pd
import shutil
import json
from datetime import datetime

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# 모듈 Import
# [수정] 뼈대를 직접 가져오지 않고, 우리가 새로 만든 래퍼(Wrapper) 클래스를 가져옵니다.
from AI.modules.signal.models.transformer.wrapper import TransformerSignalModel 
from AI.modules.trader.strategies.rule_based import decide_order
from AI.modules.trader.strategies.portfolio_logic import calculate_portfolio_allocation
from AI.modules.analysis.generator import ReportGenerator
from AI.libs.database.repository import save_executions_to_db, save_reports_to_db, get_current_position
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.features.legacy.technical_features import add_technical_indicators, add_multi_timeframe_features
from AI.libs.database.repository import save_portfolio_summary, save_portfolio_positions
from AI.modules.finder.screener import DynamicScreener


def run_daily_pipeline(target_tickers: list = None, mode: str = "simulation", enable_xai: bool = True, target_date: str = None):
    if target_date:
        exec_date_str = target_date
    else:
        exec_date_str = datetime.now().strftime("%Y-%m-%d")
        
    print(f"\n[{exec_date_str}] === AI Daily Portfolio Routine (Mode: {mode}) ===")

    if not target_tickers:
        screener = DynamicScreener()
        target_tickers = screener.update_watchlist(exec_date_str, top_n=30)
        
        if not target_tickers:
            print("⚠️ 스크리닝 결과가 없어 루틴을 종료합니다.")
            return

    # 1. 초기 설정
    strategy_config = {
        "seq_len": 60,
        "top_k": 3,
        "buy_threshold": 0.60
    }
    
    # [핵심] 학습 때 사용한 것과 완벽히 동일한 17개 피처 세팅
    feature_columns = [
        'log_return', 'open_ratio', 'high_ratio', 'low_ratio', 'vol_change',
        'ma5_ratio', 'ma20_ratio', 'ma60_ratio', 'rsi', 'macd_ratio', 'bb_position',
        'week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change',
        'month_ma12_ratio', 'month_rsi'
    ]

    # DataLoader 초기화
    loader = DataLoader()

    # =========================================================================
    # 2. 다중 모델(Wrappers) 로드 및 초기화 (신규 메타 앙상블 로직)
    # =========================================================================
    model_wrappers = {} # 포트폴리오 로직에 넘길 모델 보관함
    
    try:
        real_n_tickers = len(loader.ticker_to_id)
        real_n_sectors = len(loader.sector_to_id)

        # 래퍼용 설정값 구성
        transformer_config = {
            "seq_len": strategy_config['seq_len'],
            "features": feature_columns,
            "n_tickers": real_n_tickers,
            "n_sectors": real_n_sectors
        }
        
        # 래퍼 인스턴스 생성 및 뼈대 빌드
        transformer_wrapper = TransformerSignalModel(config=transformer_config)
        transformer_wrapper.build(input_shape=(strategy_config['seq_len'], len(feature_columns)))
        
        weights_dir = os.path.join(project_root, "AI/data/weights/transformer")
        weights_path = os.path.join(weights_dir, "tests/multi_horizon_model_test.keras")
        scaler_path = os.path.join(weights_dir, "tests/multi_horizon_scaler_test.pkl")
        
        if os.path.exists(weights_path):
            try:
                # 1차 시도: 기본 Keras 포맷으로 가중치 로드
                transformer_wrapper.model.load_weights(weights_path)
                print("✅ [Transformer V1] 모델 가중치 로드 완료 (Standard)")
            except Exception as load_e:
                # 2차 시도: HDF5 Fallback (기존 로직 완벽 보존)
                if "not a zip file" in str(load_e) or "header" in str(load_e):
                    temp_h5_path = weights_path.replace(".keras", "_temp_fallback.h5")
                    try:
                        shutil.copyfile(weights_path, temp_h5_path)
                        transformer_wrapper.model.load_weights(temp_h5_path)
                        print("✅ [Transformer V1] 모델 가중치 로드 완료 (HDF5 Fallback)")
                    except Exception as e_h5:
                        print(f"❌ HDF5 로드 실패: {e_h5}")
                        raise e_h5
                    finally:
                        if os.path.exists(temp_h5_path):
                            os.remove(temp_h5_path)
                else:
                    raise load_e
            
            # 스케일러 로드 (래퍼 내부 함수 호출)
            if os.path.exists(scaler_path):
                transformer_wrapper.load_scaler(scaler_path)
            else:
                print("⚠️ [Transformer V1] 스케일러 파일이 없습니다. 오작동할 수 있습니다.")

            model_wrappers["transformer_v1"] = transformer_wrapper
        else:
            print(f"⚠️ 모델 가중치 파일 없음 ({weights_path}). 중립 점수로 진행됨.")
            
    except Exception as e:
        print(f"❌ 모델/스케일러 초기화 실패: {e}")
        traceback.print_exc()
        return

    # XAI 초기화
    xai_generator = None
    if enable_xai:
        try:
            xai_generator = ReportGenerator(use_api_llm=True) 
        except Exception as e:
            print(f"⚠️ XAI 초기화 실패. 리포트 생성 건너뜀. {e}")

    # =========================================================================
    # 3. 주가 데이터 로딩 및 전처리
    # =========================================================================
    data_map = {}
    print(f"3. 데이터 로딩 중 ({len(target_tickers)}종목)...")
    
    bulk_df = loader.load_data_from_db(start_date="2023-01-01", end_date=exec_date_str, tickers=target_tickers)
    target_timestamp = pd.to_datetime(exec_date_str)
    
    if not bulk_df.empty:
        for ticker in target_tickers:
            df = bulk_df[bulk_df['ticker'] == ticker].copy()
            if df.empty: continue
                
            try:
                df = add_technical_indicators(df)
                df = add_multi_timeframe_features(df)
                df.set_index('date', inplace=True)
                
                df = df.loc[:target_timestamp] # Look-ahead bias 방지
                
                if df.empty or df.index[-1] != target_timestamp:
                    continue
                
                if len(df) >= strategy_config['seq_len']:
                    data_map[ticker] = df
            except Exception as e:
                print(f"   [Error] {ticker} 데이터 전처리 중 오류: {e}")

    if not data_map:
        print("⚠️ 오늘(해당일) 처리할 수 있는 데이터가 없습니다. 루틴을 종료합니다.")
        return

    # 게이팅/리스크 오버레이용 매크로 더미 데이터 (향후 DB 연결 시 교체)
    dummy_macro_data = pd.DataFrame([{
        "vix_z_score": 0.0, 
        "mkt_breadth_nh_nl": 0.0,
        "ma_trend_score": 0.5
    }])

    # =========================================================================
    # 4. 포트폴리오 비중 계산 (다중 모델 앙상블 호출)
    # =========================================================================
    print("4. AI 앙상블 포트폴리오 전략 산출 중...")
    try:
        target_weights, scores, all_signals_map = calculate_portfolio_allocation(
            data_map=data_map,
            macro_data=dummy_macro_data,    # 거시 지표
            model_wrappers=model_wrappers,  # 준비된 모델 딕셔너리 전달
            ticker_ids=loader.ticker_to_id, 
            sector_ids=loader.sector_to_id,
            gating_model=None,              # 아직 강화학습 모델이 없으므로 None
            config=strategy_config
        )
    except Exception as e:
        print(f"❌ 포트폴리오 산출 중 치명적 오류 발생: {e}")
        traceback.print_exc()
        return
    
    # =========================================================================
    # 5. 종목별 주문 집행 (Execution Loop)
    # =========================================================================
    execution_results = []
    report_results = []
    
    print("5. 매매 주문 및 리스크 관리 실행...")
    for ticker in target_tickers:
        try:
            if ticker not in data_map: continue
            
            score = scores.get(ticker, 0.5)
            target_weight = target_weights.get(ticker, 0.0)
            current_price = data_map[ticker].iloc[-1]['close']
            
            current_row = data_map[ticker].iloc[-1]
            data_date_str = current_row.name.strftime("%Y-%m-%d") if isinstance(current_row.name, (datetime, pd.Timestamp)) else exec_date_str

            pos_info = get_current_position(ticker, target_date=exec_date_str, initial_cash=0)
            allocation_cash = 10_000_000 
            
            my_qty = pos_info['qty']
            my_avg_price = pos_info['avg_price']
            current_val = my_qty * current_price
            
            action, qty, reason = "HOLD", 0, ""
            
            if target_weight > 0 or my_qty > 0:
                action, qty, reason = decide_order(
                    ticker, score, current_price, allocation_cash, my_qty, my_avg_price, current_val
                )
            else:
                action = "HOLD"
                reason = "대상 아님 (관망)"

            if action == "HOLD":
                continue
                
            print(f" >> [{ticker}] Score:{score*100:.1f}% | {action} {qty}주 | {reason}")

            next_cash = 0 
            next_qty = my_qty
            next_avg_price = my_avg_price
            pnl_realized = 0.0
            pnl_unrealized = 0.0

            if action == 'BUY':
                next_qty = my_qty + qty
                total_val_old = my_qty * my_avg_price
                total_val_new = qty * current_price
                if next_qty > 0:
                    next_avg_price = (total_val_old + total_val_new) / next_qty
            elif action == 'SELL':
                next_qty = my_qty - qty
                if my_avg_price > 0:
                    pnl_realized = (current_price - my_avg_price) * qty
                if next_qty <= 0:
                    next_avg_price = 0.0

            if next_qty > 0 and next_avg_price > 0:
                pnl_unrealized = (current_price - next_avg_price) * next_qty

            if enable_xai and xai_generator:
                try:
                    print(f"   ...[{ticker}] AI 리포트 생성 중...")
                    row_dict = current_row.to_dict()
                    row_dict['date'] = data_date_str 
                    
                    report_text = xai_generator.generate_report(ticker, data_date_str, row_dict, score, action)
                    
                    if report_text:
                        report_results.append({
                            "ticker": ticker,
                            "signal": action,
                            "price": current_price,
                            "date": data_date_str,
                            "text": report_text
                        })
                except Exception as xai_e:
                    print(f"   [Warning] 리포트 생성 중 오류 무시: {xai_e}")

            execution_results.append({
                "run_id": f"daily_{exec_date_str}",
                "ticker": ticker,
                "signal_date": data_date_str,
                "signal_price": current_price,
                "signal": action,
                "fill_date": exec_date_str,
                "fill_price": current_price,
                "qty": qty,
                "side": action,
                "value": current_price * qty,
                "commission": 0,
                "cash_after": next_cash,
                "position_qty": next_qty,
                "avg_price": next_avg_price,
                "pnl_realized": pnl_realized,
                "pnl_unrealized": pnl_unrealized,
                "xai_report_id": None
            })

        except Exception as e:
            print(f"   [Error] {ticker} 매매/리포트 처리 중 에러: {e}")
            traceback.print_exc()

    # =========================================================================
    # 6. DB 일괄 저장 (Transaction)
    # =========================================================================
    saved_report_map = {}
    if report_results:
        print(f"6-1. XAI 리포트 DB 저장 중... ({len(report_results)}건)")
        reports_tuple = [(r["ticker"], r["signal"], float(r["price"]), r["date"], r["text"]) for r in report_results]
        try:
            saved_report_ids = save_reports_to_db(reports_tuple)
            saved_report_map = {r["ticker"]: saved_id for r, saved_id in zip(report_results, saved_report_ids)}
        except Exception as db_e:
            print(f"   [Error] 리포트 저장 실패: {db_e}")

    for exe in execution_results:
        if exe['ticker'] in saved_report_map:
            exe['xai_report_id'] = saved_report_map[exe['ticker']]

    if execution_results:
        print(f"6-2. 매매 실행 내역 DB 저장 중... ({len(execution_results)}건)")
        try:
            df_results = pd.DataFrame(execution_results)
            save_executions_to_db(df_results)
        except Exception as db_e:
            print(f"   [Error] 실행 내역 저장 실패: {db_e}")
            
    # =========================================================================
    # 7. 포트폴리오 일일 마감 (Settlement)
    # =========================================================================
    print("7. 포트폴리오 일일 마감 및 스냅샷 저장 중...")
    
    INITIAL_CAPITAL = 100_000_000 
    
    total_market_value = 0.0
    total_pnl_unrealized = 0.0
    total_pnl_realized_cum = 0.0
    total_invested_cash = 0.0
    
    daily_positions = []
    
    for ticker in target_tickers:
        pos_info = get_current_position(ticker, target_date=exec_date_str, initial_cash=0)
        
        qty = pos_info['qty']
        avg_price = pos_info['avg_price']
        realized_cum = pos_info.get('pnl_realized_cum', 0.0)
        
        total_pnl_realized_cum += realized_cum
        
        if qty > 0:
            current_price = float(data_map[ticker].iloc[-1]['close']) if ticker in data_map else avg_price
            market_value = qty * current_price
            pnl_unrealized = (current_price - avg_price) * qty
            
            total_market_value += market_value
            total_pnl_unrealized += pnl_unrealized
            total_invested_cash += (qty * avg_price)
            
            daily_positions.append((
                exec_date_str, ticker, 
                int(qty), float(avg_price), float(current_price), 
                float(market_value), float(pnl_unrealized), float(realized_cum)
            ))

    cash = INITIAL_CAPITAL - total_invested_cash + total_pnl_realized_cum
    total_asset = cash + total_market_value
    return_rate = (total_asset / INITIAL_CAPITAL) - 1.0

    save_portfolio_summary(
        date=exec_date_str, 
        total_asset=total_asset, 
        cash=cash, 
        market_value=total_market_value, 
        pnl_unrealized=total_pnl_unrealized, 
        pnl_realized_cum=total_pnl_realized_cum, 
        initial_capital=INITIAL_CAPITAL, 
        return_rate=return_rate
    )
    
    if daily_positions:
        save_portfolio_positions(exec_date_str, daily_positions)
        
    print(f"   => [마감] 총자산: ₩{total_asset:,.0f} | 수익률: {return_rate*100:.2f}%")
    print(f"=== Daily Routine Finished ===\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tickers", type=str, default="", help="특정 종목 테스트용 (생략 시 다이나믹 스크리닝 자동 실행)")
    parser.add_argument("--mode", type=str, default="simulation", choices=["simulation", "live"], help="실행 모드")
    parser.add_argument("--no-xai", action="store_true", help="XAI 리포트 생성 건너뛰기")
    parser.add_argument("--target_date", type=str, default=None, help="과거 시뮬레이션 기준 날짜 (YYYY-MM-DD)")
    
    args = parser.parse_args()
    ticker_list = [t.strip() for t in args.tickers.split(",")] if args.tickers else []
    
    run_daily_pipeline(ticker_list, args.mode, not args.no_xai, args.target_date)