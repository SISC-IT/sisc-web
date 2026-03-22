import traceback
from datetime import datetime

import pandas as pd

from AI.libs.database.repository import PortfolioRepository
from AI.modules.analysis.generator import ReportGenerator
from AI.modules.trader.strategies.rule_based import decide_order


def _execute_trade_plan(
    plan: dict,
    current_portfolio_cash: float,
    exec_date_str: str,
    enable_xai: bool,
    xai_generator: ReportGenerator,
    report_results: list,
) -> tuple:
    action = plan["action"]
    qty = plan["qty"]
    reason = plan["reason"]
    ticker = plan["ticker"]
    score = plan["score"]
    current_price = plan["current_price"]
    current_row = plan["current_row"]
    data_date_str = plan["data_date_str"]
    my_qty = plan["my_qty"]
    my_avg_price = plan["my_avg_price"]

    next_cash, next_qty, next_avg_price = current_portfolio_cash, my_qty, my_avg_price
    pnl_realized, pnl_unrealized = 0.0, 0.0

    if action == "BUY":
        next_qty = my_qty + qty
        total_val_old = my_qty * my_avg_price
        total_val_new = qty * current_price
        next_cash = current_portfolio_cash - (current_price * qty)
        if next_qty > 0:
            next_avg_price = (total_val_old + total_val_new) / next_qty
    elif action == "SELL":
        next_qty = my_qty - qty
        next_cash = current_portfolio_cash + (current_price * qty)
        if my_avg_price > 0:
            pnl_realized = (current_price - my_avg_price) * qty
        if next_qty <= 0:
            next_avg_price = 0.0

    if next_qty > 0 and next_avg_price > 0:
        pnl_unrealized = (current_price - next_avg_price) * next_qty

    print(f" >> [{ticker}] Score:{score*100:.1f}% | {action} {qty}주 | {reason}")

    if enable_xai and xai_generator:
        try:
            print(f"   ...[{ticker}] AI 리포트 생성 중...")
            row_dict = current_row.to_dict()
            row_dict["date"] = data_date_str
            report_text = xai_generator.generate_report(
                ticker, data_date_str, row_dict, score, action
            )

            if report_text:
                report_results.append(
                    {
                        "ticker": ticker,
                        "signal": action,
                        "price": current_price,
                        "date": data_date_str,
                        "text": report_text,
                    }
                )
        except Exception as xai_e:
            print(f"   [Warning] 리포트 생성 중 오류 무시 (파이프라인 계속 진행): {xai_e}")

    execution_row = {
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
        "xai_report_id": None,
    }

    return execution_row, next_cash


def execute_trades(
    repo: PortfolioRepository,
    target_tickers: list,
    data_map: dict,
    target_weights: dict,
    scores: dict,
    exec_date_str: str,
    mode: str,
    enable_xai: bool,
    xai_generator: ReportGenerator,
) -> tuple:
    """
    Execute rule-based buy/sell decisions from the target portfolio weights.

    Order independence matters here:
    - First decide all candidate actions.
    - Execute SELL orders first so released cash is available to BUY orders.
    """
    print("5. 매매 주문 및 리스크 관리 실행...")
    execution_results = []
    report_results = []

    current_portfolio_cash = repo.get_current_cash(target_date=exec_date_str, initial_cash=10000)
    total_budget = repo.get_latest_total_asset(target_date=exec_date_str, default_asset=10000)
    selected_target_weights = [weight for weight in target_weights.values() if weight > 0]
    max_target_weight = max(selected_target_weights, default=0.0)

    print(
        f"   -> [동적 예산] 전일 기준 총자산: ${total_budget:,.0f} | "
        f"최대 목표 비중: {max_target_weight*100:.1f}%"
    )

    trade_plans = []

    for ticker in target_tickers:
        try:
            if ticker not in data_map:
                continue

            score = scores.get(ticker, 0.5)
            target_weight = target_weights.get(ticker, 0.0)

            current_row = data_map[ticker].iloc[-1]
            current_price = current_row["close"]
            data_date_str = (
                current_row.name.strftime("%Y-%m-%d")
                if isinstance(current_row.name, (datetime, pd.Timestamp))
                else exec_date_str
            )

            pos_info = repo.get_current_position(ticker, target_date=exec_date_str, initial_cash=0)
            normalized_target_weight = max(0.0, min(1.0, target_weight))
            allocation_cash = total_budget * normalized_target_weight

            my_qty = pos_info["qty"]
            my_avg_price = pos_info["avg_price"]
            current_val = my_qty * current_price

            if target_weight > 0 or my_qty > 0:
                action, qty, reason = decide_order(
                    ticker, score, current_price, allocation_cash, my_qty, my_avg_price, current_val
                )
            else:
                action, qty, reason = "HOLD", 0, "대상 종목 아님 (관망)"

            trade_plans.append(
                {
                    "ticker": ticker,
                    "score": score,
                    "target_weight": target_weight,
                    "current_row": current_row,
                    "current_price": current_price,
                    "data_date_str": data_date_str,
                    "allocation_cash": allocation_cash,
                    "my_qty": my_qty,
                    "my_avg_price": my_avg_price,
                    "current_val": current_val,
                    "action": action,
                    "qty": qty,
                    "reason": reason,
                }
            )

        except Exception as e:
            print(f"   [Error] {ticker} 매매 계획 계산 중 치명적 에러: {e}")
            traceback.print_exc()

    sell_plans = [plan for plan in trade_plans if plan["action"] == "SELL" and plan["qty"] > 0]
    buy_plans = [plan for plan in trade_plans if plan["action"] == "BUY" and plan["qty"] > 0]

    for plan in sell_plans:
        try:
            execution_row, current_portfolio_cash = _execute_trade_plan(
                plan,
                current_portfolio_cash,
                exec_date_str,
                enable_xai,
                xai_generator,
                report_results,
            )
            execution_results.append(execution_row)
        except Exception as e:
            print(f"   [Error] {plan['ticker']} SELL 실행 중 치명적 에러: {e}")
            traceback.print_exc()

    for plan in buy_plans:
        try:
            max_affordable_qty = int(max(0, current_portfolio_cash) // plan["current_price"])
            executable_qty = min(plan["qty"], max_affordable_qty)
            if executable_qty <= 0:
                continue

            executable_plan = dict(plan)
            executable_plan["qty"] = executable_qty
            if executable_qty < plan["qty"]:
                executable_plan["reason"] = f"{plan['reason']} | 가용 현금 한도 반영"

            execution_row, current_portfolio_cash = _execute_trade_plan(
                executable_plan,
                current_portfolio_cash,
                exec_date_str,
                enable_xai,
                xai_generator,
                report_results,
            )
            execution_results.append(execution_row)
        except Exception as e:
            print(f"   [Error] {plan['ticker']} BUY 실행 중 치명적 에러: {e}")
            traceback.print_exc()

    return execution_results, report_results
