# AI/pipelines/components/trade_executor.py

import traceback
import pandas as pd
from datetime import datetime
from AI.libs.database.repository import PortfolioRepository
from AI.modules.trader.strategies.rule_based import decide_order
from AI.modules.analysis.generator import ReportGenerator

def execute_trades(repo: PortfolioRepository, target_tickers: list, data_map: dict, 
                   target_weights: dict, scores: dict, exec_date_str: str, 
                   enable_xai: bool, xai_generator: ReportGenerator) -> tuple:
    """
    [매매 주문 및 리스크 관리 집행 담당]
    산출된 포트폴리오 비중(target_weights)을 기반으로 매수/매도/홀드 주문을 결정하고, 
    가상 체결 내역과 XAI 분석 리포트 데이터를 생성합니다.
    """
    print("5. 매매 주문 및 리스크 관리 실행...")
    execution_results = []
    report_results = []
    
    # 기준일 이전의 현재 포트폴리오 잔고(현금)를 가져옴
    current_portfolio_cash = repo.get_current_cash(target_date=exec_date_str, initial_cash=100_000_000)
    
    for ticker in target_tickers:
        try:
            if ticker not in data_map: continue
            
            # AI 산출 점수 및 비중 매핑
            score = scores.get(ticker, 0.5)
            target_weight = target_weights.get(ticker, 0.0)
            
            # 현재(기준일) 시장 가격 정보
            current_row = data_map[ticker].iloc[-1]
            current_price = current_row['close']
            data_date_str = current_row.name.strftime("%Y-%m-%d") if isinstance(current_row.name, (datetime, pd.Timestamp)) else exec_date_str

            # DB에서 해당 종목의 현재 보유 정보(Position) 조회
            pos_info = repo.get_current_position(ticker, target_date=exec_date_str, initial_cash=0)
            allocation_cash = 10_000_000  # 종목당 1회 최대 할당 가능 예산 (고정값 또는 동적 로직 적용 가능)
            
            my_qty = pos_info['qty']
            my_avg_price = pos_info['avg_price']
            current_val = my_qty * current_price
            
            # Rule-based 주문 로직 처리 (비중이 0보다 크거나, 이미 보유 중이면 판단 수행)
            if target_weight > 0 or my_qty > 0:
                action, qty, reason = decide_order(
                    ticker, score, current_price, allocation_cash, my_qty, my_avg_price, current_val
                )
            else:
                action, qty, reason = "HOLD", 0, "대상 아님 (관망)"

            if action == "HOLD":
                continue
                
            print(f" >> [{ticker}] Score:{score*100:.1f}% | {action} {qty}주 | {reason}")

            # 가상 체결 로직 및 잔고/평단가 갱신 계산
            next_cash, next_qty, next_avg_price = current_portfolio_cash, my_qty, my_avg_price
            pnl_realized, pnl_unrealized = 0.0, 0.0

            if action == 'BUY':
                next_qty = my_qty + qty
                total_val_old = my_qty * my_avg_price
                total_val_new = qty * current_price
                next_cash = current_portfolio_cash - (current_price * qty) 
                if next_qty > 0:
                    next_avg_price = (total_val_old + total_val_new) / next_qty
                    
            elif action == 'SELL':
                next_qty = my_qty - qty
                next_cash = current_portfolio_cash + (current_price * qty)
                if my_avg_price > 0:
                    pnl_realized = (current_price - my_avg_price) * qty
                if next_qty <= 0:
                    next_avg_price = 0.0

            current_portfolio_cash = next_cash

            if next_qty > 0 and next_avg_price > 0:
                pnl_unrealized = (current_price - next_avg_price) * next_qty

            # Explainable AI (XAI) 리포트 텍스트 생성
            if enable_xai and xai_generator:
                try:
                    print(f"   ...[{ticker}] AI 리포트 생성 중...")
                    row_dict = current_row.to_dict()
                    row_dict['date'] = data_date_str 
                    report_text = xai_generator.generate_report(ticker, data_date_str, row_dict, score, action)
                    
                    if report_text:
                        report_results.append({
                            "ticker": ticker, "signal": action, "price": current_price,
                            "date": data_date_str, "text": report_text
                        })
                except Exception as xai_e:
                    print(f"   [Warning] 리포트 생성 중 오류 무시 (파이프라인 진행): {xai_e}")

            # 체결 이력 (Execution Results) 저장용 데이터 적재
            execution_results.append({
                "run_id": f"daily_{exec_date_str}", "ticker": ticker, "signal_date": data_date_str,
                "signal_price": current_price, "signal": action, "fill_date": exec_date_str,
                "fill_price": current_price, "qty": qty, "side": action, "value": current_price * qty,
                "commission": 0, "cash_after": next_cash, "position_qty": next_qty,
                "avg_price": next_avg_price, "pnl_realized": pnl_realized, "pnl_unrealized": pnl_unrealized,
                "xai_report_id": None
            })

        except Exception as e:
            print(f"   [Error] {ticker} 매매/리포트 처리 중 치명적 에러: {e}")
            traceback.print_exc()

    return execution_results, report_results