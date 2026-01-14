"""
[일일 자동화 파이프라인]
- 매일 장 마감 후 실행되는 메인 스크립트입니다.
- 데이터 수집 -> AI 모델 추론 -> 매매 신호 생성 -> XAI 분석 -> 결과 저장 순서로 진행됩니다.
- Crontab이나 Airflow 등의 스케줄러에 의해 호출됩니다.
"""

import sys
import os
import argparse
from datetime import datetime
import pandas as pd

# 프로젝트 루트 경로 추가 (절대 경로 import 위함)
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../.."))
if project_root not in sys.path:
    sys.path.append(project_root)

# 모듈 Import
from AI.modules.signal.workflows.inference import run_inference
from AI.modules.trader.policy import decide_order
from AI.modules.analysis.generator import ReportGenerator
from AI.modules.collector.market_data import update_market_data
from AI.libs.database.repository import save_executions_to_db, save_reports_to_db
from AI.modules.signal.core.data_loader import SignalDataLoader
from AI.libs.database.repository import save_executions_to_db, save_reports_to_db, get_current_position 

def run_daily_pipeline(target_tickers: list, mode: str = "simulation", enable_xai: bool = True):
    """
    일일 파이프라인 실행 함수
    """
    # 시스템 실행 기준 오늘 날짜 (파일명, 로그용)
    today_str = datetime.now().strftime("%Y-%m-%d")
    print(f"\n[{today_str}] === AI Daily Routine Started (Mode: {mode}, XAI: {enable_xai}) ===")

    # 0. 모듈 초기화
    xai_generator = None
    if enable_xai:
        try:
            xai_generator = ReportGenerator(use_gpu_llm=True)
            print("0. XAI 리포트 생성기 초기화 완료 (Groq)")
        except Exception as e:
            print(f"[Warning] XAI 초기화 실패: {e}. 리포트 생성을 건너뜁니다.")
            enable_xai = False

    # 1. 데이터 수집 단계
    print("1. 시장 데이터 업데이트 중...")
    try:
        update_market_data(target_tickers)
    except Exception as e:
        print(f"[Warning] 데이터 수집 중 오류 발생 (기존 데이터로 진행): {e}")

    execution_results = []
    report_results = [] 

    # 2. 종목별 분석 시작
    print(f"2. {len(target_tickers)}개 종목 분석 시작...")
    
    # DataLoader가 이제 Date 인덱스를 가진 DF를 반환한다고 가정
    loader = SignalDataLoader(sequence_length=60)

    for ticker in target_tickers:
        try:
            print(f" >> [{ticker}] 처리 중...")
            
            # (1) 데이터 준비
            df = loader.load_data(ticker, start_date="2023-01-01", end_date=today_str)
            if df.empty:
                print(f"   [Skip] 데이터 없음")
                continue
                
            # 인덱스(날짜) 기반 데이터 추출
            # fetch_ohlcv가 df.set_index(date_col, inplace=True)를 수행했으므로 df.index는 DatetimeIndex입니다.
            
            # 마지막 날짜 (Timestamp 객체)
            last_date_obj = df.index[-1]
            
            # 문자열 변환 (YYYY-MM-DD)
            data_date_str = last_date_obj.strftime("%Y-%m-%d")
            
            # 마지막 행 데이터 (Series)
            current_row = df.iloc[-1]
            current_price = current_row['close']
            
            # (2) AI 모델 추론
            score = run_inference(ticker, model_type="transformer")
            if score < 0:
                print(f"   [Skip] 추론 실패")
                continue

            # (3) 매매 정책 결정 (DB 기반 잔고 조회)
            
            # DB에서 현재 내 상태(현금, 보유수량, 평단가)를 가져옵니다.
            # (단, 전체 자산 관리 차원에서 '현금'은 종목별로 쪼개져 있지 않으므로, 
            #  여기서는 시뮬레이션 편의상 종목별로 1,000만원씩 할당되었다고 가정하거나,
            #  실제로는 전체 계좌 예수금을 조회해야 합니다. 여기선 종목별 할당 방식 유지)
            
            initial_seed = 10000000 # 종목당 배정 예산
            pos_info = get_current_position(ticker, initial_cash=initial_seed)
            
            my_cash = pos_info["cash"]
            my_qty = pos_info["qty"]
            my_avg_price = pos_info["avg_price"]
            
            # 총 자산 가치 (현금 + 주식 평가액)
            total_asset = my_cash + (my_qty * current_price)

            print(f"   [Status] 보유: {my_qty}주 (평단 {my_avg_price:,.0f}), 현금: {my_cash:,.0f}")

            # 매매 결정
            action, qty, reason = decide_order(
                ticker, score, current_price, 
                my_cash, my_qty, my_avg_price, total_asset
            )
            
            print(f"   Score: {score:.4f} -> Action: {action} ({qty}주) | {reason}")

            # ─────────────────────────────────────────────────────────────

            # (4) XAI 리포트 생성
            if enable_xai and xai_generator:
                print(f"   ...리포트 생성 중...")
                
                # ✅ [수정] row_dict 생성 및 date 명시적 추가
                # current_row(Series)에는 인덱스(날짜)가 포함되지 않으므로 수동으로 넣어줘야 함
                row_dict = current_row.to_dict()
                row_dict['date'] = data_date_str 
                
                report_text = xai_generator.generate_report(ticker, data_date_str, row_dict, score, action)
                
                report_results.append({
                    "ticker": ticker,
                    "signal": action,
                    "price": current_price,
                    "date": data_date_str, # 실제 데이터 날짜 사용
                    "text": report_text
                })

            # (5) 결과 모음
            execution_results.append({
                "run_id": f"daily_{today_str}",
                "ticker": ticker,
                "signal_date": data_date_str, # 데이터 날짜 사용
                "signal_price": current_price,
                "signal": action,
                "fill_date": today_str, # 체결일은 실제 실행일(오늘)
                "fill_price": current_price,
                "qty": qty,
                "side": action,
                "value": current_price * qty,
                "commission": 0,
                "cash_after": my_cash,
                "position_qty": my_qty + qty if action == 'BUY' else my_qty - qty,
                "avg_price": current_price,
                "pnl_realized": 0,
                "pnl_unrealized": 0,
                "xai_report_id": None 
            })

        except Exception as e:
            print(f"   [Error] {ticker} 처리 중 에러: {e}")
            import traceback
            traceback.print_exc()

    # 3. 결과 DB 저장
    # (1) 리포트 저장
    saved_report_ids = []
    if report_results:
        print(f"3-1. XAI 리포트 DB 저장 중... ({len(report_results)}건)")
        reports_tuple = [
            (r['ticker'], r['signal'], r['price'], r['date'], r['text']) 
            for r in report_results
        ]
        saved_report_ids = save_reports_to_db(reports_tuple)
        
        # ID 매핑
        if len(saved_report_ids) == len(report_results):
            report_map = {r['ticker']: saved_id for r, saved_id in zip(report_results, saved_report_ids)}
            
            for exe in execution_results:
                if exe['ticker'] in report_map:
                    exe['xai_report_id'] = report_map[exe['ticker']]

    # (2) 실행 내역 저장
    if execution_results:
        print(f"3-2. 매매 실행 내역 DB 저장 중... ({len(execution_results)}건)")
        df_results = pd.DataFrame(execution_results)
        save_executions_to_db(df_results)
        
    print(f"=== Daily Routine Finished ===\n")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tickers", type=str, default="AAPL,TSLA,MSFT", help="대상 종목 코드 (콤마 구분)")
    parser.add_argument("--mode", type=str, default="simulation", choices=["simulation", "live"], help="실행 모드")
    parser.add_argument("--no-xai", action="store_true", help="XAI 리포트 생성 건너뛰기")
    
    args = parser.parse_args()
    
    ticker_list = [t.strip() for t in args.tickers.split(",")]
    run_daily_pipeline(ticker_list, args.mode, not args.no_xai)