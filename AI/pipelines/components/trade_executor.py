"""
[주문 실행 컴포넌트]
- 목표 비중과 현재 포지션을 비교해 실제 실행 가능한 주문 계획을 만듭니다.
- 매도 우선 실행으로 현금을 확보한 뒤 매수를 진행해 순서 의존성을 줄입니다.
- XAI 리포트와 체결 결과를 함께 수집해 상위 루틴으로 반환합니다.
"""

import traceback
from datetime import datetime
from typing import TYPE_CHECKING

import pandas as pd

from AI.config import ExecutionConfig, PipelineConfig, PortfolioConfig
from AI.libs.database.repository import PortfolioRepository
from AI.modules.trader.strategies.rule_based import decide_order

if TYPE_CHECKING:
    from AI.modules.analysis.generator import ReportGenerator


def _execute_trade_plan(
    plan: dict,
    current_portfolio_cash: float,
    exec_date_str: str,
    enable_xai: bool,
    xai_generator: "ReportGenerator | None",
    report_results: list,
    commission_rate: float,
) -> tuple[dict, float]:
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

    notional = current_price * qty
    commission = notional * commission_rate

    if action == "BUY":
        next_qty = my_qty + qty
        total_val_old = my_qty * my_avg_price
        total_val_new = notional
        next_cash = current_portfolio_cash - (notional + commission)
        if next_qty > 0:
            next_avg_price = (total_val_old + total_val_new) / next_qty
    elif action == "SELL":
        next_qty = my_qty - qty
        next_cash = current_portfolio_cash + notional - commission
        if my_avg_price > 0:
            pnl_realized = ((current_price - my_avg_price) * qty) - commission
        if next_qty <= 0:
            next_avg_price = 0.0

    if next_qty > 0 and next_avg_price > 0:
        pnl_unrealized = (current_price - next_avg_price) * next_qty

    print(f" >> [{ticker}] Score:{score*100:.1f}% | {action} {qty} shares | {reason}")

    if enable_xai and xai_generator:
        try:
            print(f"   ...[{ticker}] generating XAI report...")
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
            print(f"   [Warning] report generation skipped: {xai_e}")

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
        "value": notional,
        "commission": commission,
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
    target_tickers: list[str],
    data_map: dict,
    target_weights: dict,
    scores: dict,
    exec_date_str: str,
    mode: str,
    enable_xai: bool,
    xai_generator: "ReportGenerator | None",
    pipeline_config: PipelineConfig,
    portfolio_config: PortfolioConfig,
    execution_config: ExecutionConfig,
) -> tuple[list[dict], list[dict], float]:
    """
    [주문 실행 메인]
    목표 포트폴리오 비중을 기준으로 룰 기반 매수/매도 결정을 수행합니다.

    Order independence matters here:
    - First decide all candidate actions.
    - Execute SELL orders first so released cash is available to BUY orders.
    """
    print(f"5. Executing trade plans ({mode})...")
    execution_results: list[dict] = []
    report_results: list[dict] = []

    current_portfolio_cash = repo.get_current_cash(
        target_date=exec_date_str,
        initial_cash=pipeline_config.initial_capital,
    )
    total_budget = repo.get_latest_total_asset(
        target_date=exec_date_str,
        default_asset=pipeline_config.initial_capital,
    )
    selected_target_weights = [weight for weight in target_weights.values() if weight > 0]
    max_target_weight = max(selected_target_weights, default=0.0)

    print(
        f"   -> total budget ${total_budget:,.0f} | "
        f"largest target weight {max_target_weight*100:.1f}%"
    )

    # 1) 각 종목별 주문 계획을 먼저 계산합니다.
    trade_plans = []

    for ticker in target_tickers:
        try:
            if ticker not in data_map:
                continue

            score = scores.get(ticker, portfolio_config.default_score)
            target_weight = target_weights.get(ticker, 0.0)

            current_row = data_map[ticker].iloc[-1]
            current_price = float(current_row["close"])
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
                    ticker=ticker,
                    score=score,
                    current_price=current_price,
                    allocation_cash=allocation_cash,
                    my_qty=my_qty,
                    my_avg_price=my_avg_price,
                    current_val=current_val,
                    execution_config=execution_config,
                )
            else:
                action, qty, reason = "HOLD", 0, "Ticker not in target portfolio."

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
            print(f"   [Error] {ticker} trade plan failed: {e}")
            traceback.print_exc()

    sell_plans = [plan for plan in trade_plans if plan["action"] == "SELL" and plan["qty"] > 0]
    buy_plans = [plan for plan in trade_plans if plan["action"] == "BUY" and plan["qty"] > 0]

    # 2) 매도를 먼저 실행해 가용 현금을 확보합니다.
    for plan in sell_plans:
        try:
            execution_row, current_portfolio_cash = _execute_trade_plan(
                plan=plan,
                current_portfolio_cash=current_portfolio_cash,
                exec_date_str=exec_date_str,
                enable_xai=enable_xai,
                xai_generator=xai_generator,
                report_results=report_results,
                commission_rate=execution_config.commission,
            )
            execution_results.append(execution_row)
        except Exception as e:
            print(f"   [Error] {plan['ticker']} SELL failed: {e}")
            traceback.print_exc()

    # 3) 매수는 확보된 현금 한도 안에서만 실행합니다.
    for plan in buy_plans:
        try:
            max_affordable_qty = int(
                max(0, current_portfolio_cash)
                // (plan["current_price"] * (1.0 + execution_config.commission))
            )
            executable_qty = min(plan["qty"], max_affordable_qty)
            if executable_qty <= 0:
                continue

            executable_plan = dict(plan)
            executable_plan["qty"] = executable_qty
            if executable_qty < plan["qty"]:
                executable_plan["reason"] = f"{plan['reason']} | clipped by available cash"

            execution_row, current_portfolio_cash = _execute_trade_plan(
                plan=executable_plan,
                current_portfolio_cash=current_portfolio_cash,
                exec_date_str=exec_date_str,
                enable_xai=enable_xai,
                xai_generator=xai_generator,
                report_results=report_results,
                commission_rate=execution_config.commission,
            )
            execution_results.append(execution_row)
        except Exception as e:
            print(f"   [Error] {plan['ticker']} BUY failed: {e}")
            traceback.print_exc()

    return execution_results, report_results, current_portfolio_cash
