# AI/pipelines/daily_routine.py
"""
[일일 자동화 파이프라인 (Refactored)]
- strategy_core 모듈을 사용하여 종목 선정 로직을 백테스트와 일치시킴
- 전체 종목 데이터 로드 -> 포트폴리오 비중 산출 -> 매매 주문 실행 순서
"""

import sys
import os
import argparse
from datetime import datetime
import pandas as pd
import traceback

# 프로젝트 루트 경로 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# 모듈 Import
from AI.modules.signal.models import get_model
from AI.modules.trader.strategies.rule_based import decide_order
from AI.modules.trader.strategies.portfolio_logic import calculate_portfolio_allocation
from AI.modules.analysis.generator import ReportGenerator
from AI.libs.database.repository import save_executions_to_db, save_reports_to_db, get_current_position
from AI.modules.signal.core.data_loader import DataLoader 

#현재는 시뮬레이션 코드만 작성되어 있지만, 실제 운영에서는 API 연동 부분을 추가 예정. 이때문에 mode 인자를 받아 시뮬레이션과 라이브 모드를 구분할 수 있도록 설계함. 또한, XAI 리포트 생성 여부도 옵션으로 조정 가능하도록 함.

def run_daily_pipeline(target_tickers: list, mode: str = "simulation", enable_xai: bool = True):
    today_str = datetime.now().strftime("%Y-%m-%d")
    print(f"\n[{today_str}] === AI Daily Portfolio Routine (Mode: {mode}) ===")

    # 1. 초기 설정 및 모델 로드
    model_config = {
        "head_size": 256, "num_heads": 4, "ff_dim": 4,
        "num_blocks": 4, "mlp_units": [128], "dropout": 0.1
    }
    strategy_config = {
        "seq_len": 60,
        "top_k": 3,
        "buy_threshold": 0.6
    }
    feature_columns = ['open', 'high', 'low', 'close', 'volume'] # 모델 피처

    try:
        # 모델 가중치 로드 (경로는 환경에 맞게 수정)
        weights_path = os.path.join(project_root, "AI/data/weights/transformer/multi_horizon_model.keras")
        model = get_model("transformer", model_config)
        # Build model (dummy input)
        model.build((None, strategy_config['seq_len'], len(feature_columns)))
        
        if os.path.exists(weights_path):
            model.load_weights(weights_path)
            print("✅ AI 모델 로드 완료")
        else:
            print("⚠️ 모델 가중치 파일 없음. 랜덤 스코어로 진행됨.")
            model = None
            
    except Exception as e:
        print(f"❌ 모델 초기화 실패: {e}")
        return

    # XAI 초기화
    xai_generator = None
    if enable_xai:
        try:
            xai_generator = ReportGenerator(use_gpu_llm=True)
        except:
            print("⚠️ XAI 초기화 실패. 리포트 생성 건너뜀.")

    # 모든 종목의 데이터 로드 (strategy_core에 넘기기 위함)
    loader = DataLoader(sequence_length=60)
    data_map = {}
    
    print(f"2. 데이터 로딩 중 ({len(target_tickers)}종목)...")
    for ticker in target_tickers:
        df = loader.load_data(ticker, start_date="2023-01-01", end_date=today_str)
        if not df.empty and len(df) >= strategy_config['seq_len']:
            data_map[ticker] = df
        else:
            print(f"   [Skip] {ticker}: 데이터 부족")

    # 3. [핵심] 포트폴리오 비중 계산 (Strategy Core 호출)
    print("3. AI 포트폴리오 전략 산출 중...")
    target_weights, scores = calculate_portfolio_allocation(
        data_map=data_map,
        model=model,
        feature_columns=feature_columns,
        config=strategy_config
    )
    
    # 4. 종목별 주문 집행 (Execution Loop)
    execution_results = []
    report_results = []
    
    print("4. 매매 주문 실행...")
    
    # 보유 중인 종목도 체크해야 하므로 합집합 생성
    # (실제로는 get_current_position으로 보유 종목 리스트를 DB에서 가져와야 함)
    # 여기서는 target_tickers 내에서만 관리한다고 가정
    
    for ticker in target_tickers:
        try:
            if ticker not in data_map: continue
            
            score = scores.get(ticker, 0.5)
            target_weight = target_weights.get(ticker, 0.0)
            current_price = data_map[ticker].iloc[-1]['close']
            
            # XAI 리포트용 데이터 준비 (미리 추출)
            # data_map의 마지막 행 사용
            current_row = data_map[ticker].iloc[-1]
            data_date_str = current_row.name.strftime("%Y-%m-%d") if isinstance(current_row.name, (datetime, pd.Timestamp)) else today_str

            # 내 자산 상태 조회
            initial_seed = 30_000_000 # 전체 운용 자금 (예시)
            # 포트폴리오 관점이므로, 종목당 예산이 아니라 '전체 자산 * 목표비중'으로 접근해야 함
            # 하지만 기존 policy 함수와의 호환성을 위해 개별 포지션 조회 사용
            pos_info = get_current_position(ticker, initial_cash=0) # cash는 전체 계좌 레벨에서 관리 필요
            
            # 여기서는 간단히 '종목당 최대 1000만원' 할당 가정 (단순화)
            allocation_cash = 10_000_000 
            
            # 주문 결정 로직 (policy.py 위임)
            # strategy_core에서 이미 선정 여부를 결정했으므로, policy는 리스크 관리 및 수량 계산 담당
            
            # 선정되지 않은 종목(target_weight=0) -> 강제 매도 또는 매수 금지
            # 선정된 종목(target_weight>0) -> 매수 진행
            
            my_qty = pos_info['qty']
            my_avg_price = pos_info['avg_price']
            # 현금은 가상의 할당액 사용
            current_val = my_qty * current_price
            
            # Policy 호출을 위한 조정
            # 만약 target_weight가 0이면 score를 낮게 주어 매도를 유도하거나, 별도 로직 처리
            action, qty, reason = "HOLD", 0, ""
            
            if target_weight > 0:
                # 선정됨: 매수 시그널 전달
                # 기존 decide_order는 score 기반이므로 score를 그대로 전달
                action, qty, reason = decide_order(
                    ticker, score, current_price, allocation_cash, my_qty, my_avg_price, current_val
                )
            else:
                # 선정 탈락: 보유 중이면 전량 매도
                if my_qty > 0:
                    action = "SELL"
                    qty = my_qty
                    reason = "포트폴리오 제외 (전량 매도)"
                else:
                    action = "HOLD"
                    reason = "대상 아님"

            if action == "HOLD":
                continue
                
            print(f" >> [{ticker}] Score:{score:.2f} | {action} {qty}주 | {reason}")

            # 거래 후 상태(After) 계산 및 P&L 변수 초기화
            next_cash = 0 # 개별 종목 cash 추적은 여기선 생략
            next_qty = my_qty
            next_avg_price = my_avg_price
            pnl_realized = 0.0
            pnl_unrealized = 0.0

            # (간단한 시뮬레이션 로직 - 실제 체결 가정)
            if action == 'BUY':
                next_qty = my_qty + qty
                # 평단가 갱신 로직 등은 실제 체결시점에 처리되나, 여기선 시뮬레이션 값 계산
                total_val_old = my_qty * my_avg_price
                total_val_new = qty * current_price
                if next_qty > 0:
                    next_avg_price = (total_val_old + total_val_new) / next_qty
            elif action == 'SELL':
                next_qty = my_qty - qty
                if my_avg_price > 0:
                    pnl_realized = (current_price - my_avg_price) * qty
                if next_qty == 0:
                    next_avg_price = 0.0

            # 미실현 손익
            if next_qty > 0 and next_avg_price > 0:
                pnl_unrealized = (current_price - next_avg_price) * next_qty

            # (4) XAI 리포트 생성
            # XAI 생성 실패가 매매 기록 저장을 방해하지 않도록 별도 try-except 처리
            if enable_xai and xai_generator:
                try:
                    print(f"   ...리포트 생성 중...")
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

            # (5) 결과 모음
            execution_results.append({
                "run_id": f"daily_{today_str}",    # 실행 고유 ID
                "ticker": ticker,                   # 종목 코드
                "signal_date": data_date_str,       # 신호 발생 날짜
                "signal_price": current_price,      # 신호 발생 시 가격
                "signal": action,                   # 매매 신호
                "fill_date": today_str,             # 주문 체결 날짜
                "fill_price": current_price,        # 주문 체결 가격
                "qty": qty,                         # 주문 수량
                "side": action,                     # 거래 방향
                "value": current_price * qty,       # 거래 금액
                "commission": 0,                    # 거래 수수료
                "cash_after": next_cash,            # 거래 후 현금 (placeholder)
                "position_qty": next_qty,           # 거래 후 수량
                "avg_price": next_avg_price,        # 거래 후 평단가
                "pnl_realized": pnl_realized,       # 실현 손익
                "pnl_unrealized": pnl_unrealized,   # 미실현 손익
                "xai_report_id": None               # 매매이유 ID
            })

        except Exception as e:
            print(f"   [Error] {ticker} 처리 중 에러: {e}")
            traceback.print_exc()

    # 5. DB 저장
    # (1) 리포트 저장
    saved_report_map = {}
    if report_results:
        print(f"5-1. XAI 리포트 DB 저장 중... ({len(report_results)}건)")

        reports_tuple = [
            (r["ticker"], r["signal"], r["price"], r["date"], r["text"])
            for r in report_results
        ]
        
        try:
            saved_report_ids = save_reports_to_db(reports_tuple)
            
            saved_report_map = {
                r["ticker"]: saved_id 
                for r, saved_id in zip(report_results, saved_report_ids)
            }
        except Exception as db_e:
            print(f"   [Error] 리포트 저장 실패: {db_e}")

    # (2) 매매이유 ID 매핑
    for exe in execution_results:
        if exe['ticker'] in saved_report_map:
            exe['xai_report_id'] = saved_report_map[exe['ticker']]

    # (3) 실행 내역 저장
    if execution_results:
        print(f"5-2. 매매 실행 내역 DB 저장 중... ({len(execution_results)}건)")
        try:
            df_results = pd.DataFrame(execution_results)
            save_executions_to_db(df_results)
        except Exception as db_e:
            print(f"   [Error] 실행 내역 저장 실패: {db_e}")
        
    print(f"=== Daily Routine Finished ===\n")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tickers", type=str, default="AAPL,TSLA,MSFT", help="대상 종목 코드 (콤마 구분)")
    parser.add_argument("--mode", type=str, default="simulation", choices=["simulation", "live"], help="실행 모드")
    parser.add_argument("--no-xai", action="store_true", help="XAI 리포트 생성 건너뛰기")
    
    args = parser.parse_args()
    
    ticker_list = [t.strip() for t in args.tickers.split(",")]
    run_daily_pipeline(ticker_list, args.mode, not args.no_xai)