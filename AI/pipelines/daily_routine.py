#AI/pipelines/daily_routine.py
"""
[일일 자동화 파이프라인 (Refactored Complete Version)]
- strategy_core 모듈을 사용하여 종목 선정 로직을 백테스트와 일치시킴
- 멀티 호라이즌 트랜스포머 모델 및 Scaler를 완벽히 로드하여 추론 무결성 확보
- 전체 종목 데이터 로드 -> 포트폴리오 비중 산출 -> 매매 주문 실행(리스크 관리) 순서
- [추가] target_date 인자를 받아 특정 과거 날짜 기준으로 시뮬레이션(Backfill) 가능
"""
import os
import warnings

# 1. TensorFlow C++ 레벨 로그 및 oneDNN 안내문 완벽 차단
# 🚨 주의: 이 두 줄은 무조건 다른 모듈들(특히 tensorflow)을 import 하기 전에 있어야 합니다!
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3' 
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'

# 2. Pandas, Scikit-learn 등 파이썬 레벨의 모든 성가신 경고 차단
warnings.filterwarnings('ignore', category=UserWarning)
warnings.filterwarnings('ignore', category=FutureWarning)
warnings.filterwarnings('ignore', message='.*InconsistentVersionWarning.*')
warnings.filterwarnings('ignore', message='.*SQLAlchemy.*')

import sys
import argparse
import pickle
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
from AI.modules.signal.models.transformer.architecture import build_transformer_model # Transformer 전용 빌더
from AI.modules.trader.strategies.rule_based import decide_order
from AI.modules.trader.strategies.portfolio_logic import calculate_portfolio_allocation
from AI.modules.analysis.generator import ReportGenerator
from AI.libs.database.repository import save_executions_to_db, save_reports_to_db, get_current_position
from AI.modules.signal.core.data_loader import DataLoader
from AI.modules.features.legacy.technical_features import add_technical_indicators, add_multi_timeframe_features
from AI.libs.database.repository import save_portfolio_summary, save_portfolio_positions
from AI.modules.finder.screener import DynamicScreener
# 현재는 시뮬레이션 코드만 작성되어 있지만, 실제 운영에서는 API 연동 부분을 추가 예정. 
# 이때문에 mode 인자를 받아 시뮬레이션과 라이브 모드를 구분할 수 있도록 설계함. 
# 또한, XAI 리포트 생성 여부도 옵션으로 조정 가능하도록 함.

# target_date 인자 추가(백필용)
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
    
    # [핵심] 학습 때 사용한 것과 완벽히 동일한 17개 피처로 세팅
    feature_columns = [
        'log_return', 'open_ratio', 'high_ratio', 'low_ratio', 'vol_change',
        'ma5_ratio', 'ma20_ratio', 'ma60_ratio', 'rsi', 'macd_ratio', 'bb_position',
        'week_ma20_ratio', 'week_rsi', 'week_bb_pos', 'week_vol_change',
        'month_ma12_ratio', 'month_rsi'
    ]

    # DataLoader 초기화 (ID 매핑 가져오기)
    loader = DataLoader()

    # 2. 모델 및 스케일러 로드
    try:
        # DB 기준 종목 및 섹터 총개수 추출 (Embedding Layer용)
        real_n_tickers = len(loader.ticker_to_id)
        real_n_sectors = len(loader.sector_to_id)

        # 모델 껍데기 생성 (n_tickers, n_sectors 파라미터 주입 완료)
        model = build_transformer_model(
            input_shape=(strategy_config['seq_len'], len(feature_columns)),
            n_tickers=real_n_tickers, 
            n_sectors=real_n_sectors,
            n_outputs=4  # 1, 3, 5, 7일 예측
        )
        
        # 가중치 및 스케일러 파일 경로 세팅
        weights_dir = os.path.join(project_root, "AI/data/weights/transformer")
        weights_path = os.path.join(weights_dir, "tests/multi_horizon_model_test.keras")
        scaler_path = os.path.join(weights_dir, "tests/multi_horizon_scaler_test.pkl")
        
        # (1) 모델 가중치 로드 (HDF5 Fallback 적용)
        if os.path.exists(weights_path):
            try:
                # 1차 시도: 기본 로드 (.keras = Zip 포맷 가정)
                model.load_weights(weights_path)
                print("✅ AI 멀티 호라이즌 모델 로드 완료 (Standard)")
            except Exception as load_e:
                if "not a zip file" in str(load_e) or "header" in str(load_e):
                    #print("⚠️ Zip 포맷 로드 실패. HDF5 방식으로 재시도합니다.")
                    # 확장자를 .h5로 변경한 임시 파일 생성 (Keras가 확장자를 보고 로더를 결정함)
                    temp_h5_path = weights_path.replace(".keras", "_temp_fallback.h5")
                    try:
                        shutil.copyfile(weights_path, temp_h5_path)
                        model.load_weights(temp_h5_path)
                        #print("✅ AI 멀티 호라이즌 모델 로드 완료 (HDF5 Fallback)")
                    except Exception as e_h5:
                        print(f"❌ HDF5 로드 실패했습니다: {e_h5}")
                        raise e_h5
                    finally:
                        # 임시 파일 무조건 정리
                        if os.path.exists(temp_h5_path):
                            os.remove(temp_h5_path)
                else:
                    raise load_e
        else:
            print(f"⚠️ 모델 가중치 파일 없음 ({weights_path}). 중립 점수로 진행됨.")
            model = None
            
        # (2) 스케일러 로드
        scaler = None
        if os.path.exists(scaler_path):
            with open(scaler_path, "rb") as f:
                scaler = pickle.load(f)
            print("✅ 데이터 전처리용 스케일러 로드 완료")
        else:
            print("⚠️ 스케일러 파일이 없습니다. 데이터 정규화가 불가능하여 오작동할 수 있습니다.")
            
    except Exception as e:
        print(f"❌ 모델/스케일러 초기화 실패: {e}")
        return

    # XAI 초기화
    xai_generator = None
    if enable_xai:
        try:
            xai_generator = ReportGenerator(use_api_llm=False) #true로 하면 GroqClient 사용 (api 토큰 사용), False로 하면 OllamaClient 사용 (로컬 CPU)
        except:
            print("⚠️ XAI 초기화 실패. 리포트 생성 건너뜀.")

    # 3. 데이터 로딩 (한 번의 쿼리로 타겟 종목 모두 가져오기)
    data_map = {}
    print(f"3. 데이터 로딩 중 ({len(target_tickers)}종목)...")
    
    # [수정됨] end_date=exec_date_str 파라미터 유지
    bulk_df = loader.load_data_from_db(start_date="2023-01-01", end_date=exec_date_str, tickers=target_tickers)
    
    # [추가] 비교를 위한 target_timestamp 생성
    target_timestamp = pd.to_datetime(exec_date_str)
    
    if not bulk_df.empty:
        # 가져온 뭉치 데이터(bulk_df)를 종목별로 분리하여 지표를 달아줌
        for ticker in target_tickers:
            df = bulk_df[bulk_df['ticker'] == ticker].copy()
            
            if df.empty:
                print(f"   [Skip] {ticker}: DB에 데이터 없음")
                continue
                
            try:
                # 모델에 입력할 기술적 지표 생성
                df = add_technical_indicators(df)
                df = add_multi_timeframe_features(df)
                
                df.set_index('date', inplace=True)
                
                # [핵심 추가] target_date 미래 데이터가 딸려오는 것을 방지 (Look-ahead bias 방지)
                df = df.loc[:target_timestamp]
                
                # [핵심 추가] 목표하는 날짜(exec_date_str)에 주식 시장이 열렸는지(데이터가 있는지) 확인
                if df.empty or df.index[-1] != target_timestamp:
                    print(f"   [Skip] {ticker}: {exec_date_str} 주가 데이터 없음 (휴장일/데이터누락 등)")
                    continue
                
                if len(df) >= strategy_config['seq_len']:
                    data_map[ticker] = df
                else:
                    print(f"   [Skip] {ticker}: 데이터 부족 (최소 {strategy_config['seq_len']}일 필요)")
            except Exception as e:
                print(f"   [Error] {ticker} 데이터 전처리 중 오류: {e}")

    # 데이터맵이 비었으면 조기 종료
    if not data_map:
        print("⚠️ 오늘(해당일) 처리할 수 있는 데이터가 없습니다. 루틴을 종료합니다.")
        return

    # 4. [핵심] 포트폴리오 비중 계산 (Strategy Core 호출)
    print("4. AI 포트폴리오 전략 산출 중...")
    target_weights, scores = calculate_portfolio_allocation(
        data_map=data_map,
        model=model,
        scaler=scaler,                     # [수정] 스케일러 전달
        ticker_ids=loader.ticker_to_id,    # [수정] 임베딩용 ID 전달
        sector_ids=loader.sector_to_id,    # [수정] 임베딩용 ID 전달
        feature_columns=feature_columns,
        config=strategy_config
    )
    
    # 5. 종목별 주문 집행 (Execution Loop)
    execution_results = []
    report_results = []
    
    print("5. 매매 주문 및 리스크 관리 실행...")
    
    for ticker in target_tickers:
        try:
            if ticker not in data_map: continue
            
            score = scores.get(ticker, 0.5)
            target_weight = target_weights.get(ticker, 0.0)
            current_price = data_map[ticker].iloc[-1]['close']
            
            # XAI 리포트용 데이터 준비 (마지막 행 데이터)
            current_row = data_map[ticker].iloc[-1]
            data_date_str = current_row.name.strftime("%Y-%m-%d") if isinstance(current_row.name, (datetime, pd.Timestamp)) else exec_date_str

            # 내 자산 상태 조회 (DB에서 포지션 확인)
            pos_info = get_current_position(ticker, target_date=exec_date_str, initial_cash=0)
            
            # 종목당 최대 할당 예산 (단순화)
            allocation_cash = 10_000_000 
            
            my_qty = pos_info['qty']
            my_avg_price = pos_info['avg_price']
            current_val = my_qty * current_price
            
            # 주문 결정 (decide_order 위임 - 리스크 관리 및 수량 계산)
            action, qty, reason = "HOLD", 0, ""
            
            if target_weight > 0 or my_qty > 0:
                action, qty, reason = decide_order(
                    ticker, score, current_price, allocation_cash, my_qty, my_avg_price, current_val
                )
            else:
                action = "HOLD"
                reason = "대상 아님 (관망)"

            # 관망(HOLD) 시 로깅 생략하고 다음 종목으로 넘어감
            if action == "HOLD":
                continue
                
            print(f" >> [{ticker}] Score:{score*100:.1f}% | {action} {qty}주 | {reason}")

            # 거래 후 상태(After) 계산 및 P&L 변수 초기화
            next_cash = 0 
            next_qty = my_qty
            next_avg_price = my_avg_price
            pnl_realized = 0.0
            pnl_unrealized = 0.0

            # 시뮬레이션용 체결 및 평단가 갱신 로직
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

            # 미실현 손익
            if next_qty > 0 and next_avg_price > 0:
                pnl_unrealized = (current_price - next_avg_price) * next_qty

            # XAI 리포트 생성
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

            # 매매 결과 모음
            execution_results.append({
                "run_id": f"daily_{exec_date_str}",     # 실행 고유 ID
                "ticker": ticker,                   # 종목 코드
                "signal_date": data_date_str,       # 신호 발생 날짜
                "signal_price": current_price,      # 신호 발생 시 가격
                "signal": action,                   # 매매 신호
                "fill_date": exec_date_str,             # 주문 체결 날짜
                "fill_price": current_price,        # 주문 체결 가격
                "qty": qty,                         # 주문 수량
                "side": action,                     # 거래 방향
                "value": current_price * qty,       # 거래 금액
                "commission": 0,                    # 거래 수수료
                "cash_after": next_cash,            # 거래 후 현금
                "position_qty": next_qty,           # 거래 후 수량
                "avg_price": next_avg_price,        # 거래 후 평단가
                "pnl_realized": pnl_realized,       # 실현 손익
                "pnl_unrealized": pnl_unrealized,   # 미실현 손익
                "xai_report_id": None               # 매매이유 ID (DB 저장 후 매핑됨)
            })

        except Exception as e:
            print(f"   [Error] {ticker} 매매/리포트 처리 중 에러: {e}")
            traceback.print_exc()

    # 6. DB 일괄 저장 (Transaction)
    # (1) 리포트 먼저 저장 (고유 ID 발급)
    saved_report_map = {}
    if report_results:
        print(f"6-1. XAI 리포트 DB 저장 중... ({len(report_results)}건)")

        reports_tuple = [
            (r["ticker"], r["signal"], float(r["price"]), r["date"], r["text"])
            for r in report_results
        ]
        
        try:
            saved_report_ids = save_reports_to_db(reports_tuple)
            
            # 발급된 ID를 종목과 매핑
            saved_report_map = {
                r["ticker"]: saved_id 
                for r, saved_id in zip(report_results, saved_report_ids)
            }
        except Exception as db_e:
            print(f"   [Error] 리포트 저장 실패: {db_e}")

    # (2) 실행 내역에 리포트 ID 매핑
    for exe in execution_results:
        if exe['ticker'] in saved_report_map:
            exe['xai_report_id'] = saved_report_map[exe['ticker']]

    # (3) 실행 내역 최종 저장
    if execution_results:
        print(f"6-2. 매매 실행 내역 DB 저장 중... ({len(execution_results)}건)")
        try:
            df_results = pd.DataFrame(execution_results)
            save_executions_to_db(df_results)
        except Exception as db_e:
            print(f"   [Error] 실행 내역 저장 실패: {db_e}")
    # ---------------------------------------------------------
    # 7. 포트폴리오 일일 마감 (Settlement) - 스키마 완벽 대응
    # ---------------------------------------------------------
    print("7. 포트폴리오 일일 마감 및 스냅샷 저장 중...")
    
    INITIAL_CAPITAL = 100_000_000  # 투자 원금 (1억)
    
    total_market_value = 0.0       # 보유 주식 총 평가금액
    total_pnl_unrealized = 0.0     # 전체 미실현 손익
    total_pnl_realized_cum = 0.0   # 누적 확정 실현 손익
    total_invested_cash = 0.0      # 주식에 묶인 원금 (평단가 * 수량)
    
    daily_positions = []
    
    for ticker in target_tickers:
        # 그날 매매가 모두 끝난 후의 최종 상태 조회
        pos_info = get_current_position(ticker, target_date=exec_date_str, initial_cash=0)
        
        qty = pos_info['qty']
        avg_price = pos_info['avg_price']
        realized_cum = pos_info.get('pnl_realized_cum', 0.0)
        
        total_pnl_realized_cum += realized_cum
        
        # 보유 중인 주식이 있을 경우에만 포지션 스냅샷 계산
        if qty > 0:
            # 데이터맵에 현재 가격이 있으면 쓰고, 없으면 평단가를 임시로 씀
            current_price = float(data_map[ticker].iloc[-1]['close']) if ticker in data_map else avg_price
            
            market_value = qty * current_price
            pnl_unrealized = (current_price - avg_price) * qty
            
            total_market_value += market_value
            total_pnl_unrealized += pnl_unrealized
            total_invested_cash += (qty * avg_price)
            
            # DB 스키마에 맞춘 튜플 데이터 생성
            daily_positions.append((
                exec_date_str, ticker, 
                int(qty), float(avg_price), float(current_price), 
                float(market_value), float(pnl_unrealized), float(realized_cum)
            ))

    # 정밀한 현금(Cash) 및 자산(Asset) 계산
    # 잔고 = 초기자본금 - (주식 매수에 쓴 원금) + (매도를 통해 벌어들인 누적 수익금)
    cash = INITIAL_CAPITAL - total_invested_cash + total_pnl_realized_cum
    
    # 총자산 = 잔고 + 현재 주식들의 평가 금액
    total_asset = cash + total_market_value
    
    # 누적 수익률 = (총자산 / 투자원금) - 1
    return_rate = (total_asset / INITIAL_CAPITAL) - 1.0

    # DB 저장 실행
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
    # default 값을 비워둬서 무조건 스크리너가 작동하게 합니다. (테스트용으로만 쓰게 됨)
    parser.add_argument("--tickers", type=str, default="", help="특정 종목 테스트용 (생략 시 다이나믹 스크리닝 자동 실행)")
    parser.add_argument("--mode", type=str, default="simulation", choices=["simulation", "live"], help="실행 모드")
    parser.add_argument("--no-xai", action="store_true", help="XAI 리포트 생성 건너뛰기")
    parser.add_argument("--target_date", type=str, default=None, help="과거 시뮬레이션 기준 날짜 (YYYY-MM-DD)")
    
    args = parser.parse_args()
    
    ticker_list = [t.strip() for t in args.tickers.split(",")] if args.tickers else []
    
    run_daily_pipeline(ticker_list, args.mode, not args.no_xai, args.target_date)