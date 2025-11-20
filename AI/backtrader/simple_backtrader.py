# backtrader/simple_backtrader.py
# -*- coding: utf-8 -*-
"""
한국어 주석:
- OHLCV 없이, Transformer 결정 로그(decision_log)의 price만으로
  간소화된 백테스트를 수행하는 환경(Environment) 역할.
- 수량/포지션 결정은 backtrader/order_policy.py 모듈로 분리됨.
"""

from __future__ import annotations
from dataclasses import dataclass
from typing import Optional, Dict, Tuple, List
import pandas as pd
import numpy as np

from backtrader.order_policy import decide_order  # 분리된 정책 모듈 import


# === 설정 클래스 ===
@dataclass
class BacktestConfig:
    """
    한국어 주석:
    - 간소화 백테스터 설정
    - 향후 강화학습 환경 초기화 시에도 그대로 사용 가능
    """
    initial_cash: float = 100_000.0
    slippage_bps: float = 5.0
    commission_bps: float = 3.0
    risk_frac: float = 0.2
    max_positions_per_ticker: int = 1
    fill_on_same_day: bool = True


# === 내부 유틸 ===
def _apply_price_with_slippage(price: float, side: str, slippage_bps: float) -> float:
    """슬리피지를 체결가에 반영"""
    adj = 1.0 + (slippage_bps / 10_000.0) * (1 if side.upper() == "BUY" else -1)
    return float(price) * adj


def _apply_commission(value: float, commission_bps: float) -> float:
    """체결 금액에 대해 bps 단위 수수료 계산"""
    return abs(value) * (commission_bps / 10_000.0)


def _fill_date_from_signal(sig_date: pd.Timestamp, same_day: bool) -> pd.Timestamp:
    """OHLCV 없이 동일일 또는 다음날 체결로 단순 처리"""
    return sig_date if same_day else (sig_date + pd.Timedelta(days=1))


# === 백테스트 본체 ===
def backtrader(
    decision_log: pd.DataFrame,
    config: Optional[BacktestConfig] = None,
    run_id: Optional[str] = None,
) -> Tuple[pd.DataFrame, Dict]:
    """
    한국어 주석:
    - 입력: Transformer 의사결정 로그(decision_log)
    - 처리: 가격 기반 슬리피지·수수료 반영 후 체결/포지션 갱신
    - 반환: (fills_df, summary)
    """
    if config is None:
        config = BacktestConfig()

    dl = decision_log.copy()
    if not {"ticker", "date", "action", "price"}.issubset(dl.columns):
        raise ValueError("decision_log에 'ticker','date','action','price' 컬럼이 필요합니다.")

    dl["date"] = pd.to_datetime(dl["date"])
    dl = dl.sort_values(["date", "ticker"]).reset_index(drop=True)

    cash = float(config.initial_cash)
    positions: Dict[str, Dict[str, float]] = {}
    records: List[Dict] = []

    for _, r in dl.iterrows():
        ticker = str(r["ticker"])
        sig_date = pd.Timestamp(r["date"])
        sig = str(r["action"]).upper()
        sig_price = float(r.get("price", np.nan))

        if sig not in ("BUY", "SELL"):
            continue

        fill_date = _fill_date_from_signal(sig_date, config.fill_on_same_day)
        fill_price = _apply_price_with_slippage(sig_price, sig, config.slippage_bps)

        pos = positions.get(ticker, {"qty": 0, "avg": 0.0})
        cur_qty = pos["qty"]
        avg_price = pos["avg"]
        side = "BUY" if sig == "BUY" else "SELL"

        # === 🔹 체결 정책 호출 (외부 모듈) ===
        qty, trade_value = decide_order(
            side=side,
            cash=cash,
            cur_qty=cur_qty,
            avg_price=avg_price,
            fill_price=fill_price,
            config=config,
        )

        if qty <= 0:
            continue

        # === 나머지는 환경의 기계적 계산 ===
        commission = _apply_commission(trade_value, config.commission_bps)
        cash_after = cash - trade_value - commission

        # 포지션 업데이트
        if side == "BUY":
            new_qty = cur_qty + qty
            new_avg = (avg_price * cur_qty + fill_price * qty) / max(1, new_qty)
        else:
            new_qty = cur_qty - qty
            new_avg = avg_price if new_qty > 0 else 0.0

        pnl_realized = 0.0
        if side == "SELL":
            pnl_realized = (fill_price - avg_price) * qty

        pnl_unrealized = 0.0

        # 상태 저장
        cash = cash_after
        positions[ticker] = {"qty": new_qty, "avg": new_avg}

        records.append({
            "run_id": run_id,
            "ticker": ticker,
            "signal_date": sig_date.date().isoformat(),
            "signal_price": float(sig_price),
            "signal": sig,
            "fill_date": fill_date.date().isoformat(),
            "fill_price": float(fill_price),
            "qty": int(qty),
            "side": side,
            "value": float(trade_value),
            "commission": float(commission),
            "cash_after": float(cash_after),
            "position_qty": int(new_qty),
            "avg_price": float(new_avg),
            "pnl_realized": float(pnl_realized),
            "pnl_unrealized": float(pnl_unrealized),
        })

    fills = pd.DataFrame.from_records(records)
    summary = {
        "run_id": run_id,
        "trades": int(len(fills)),
        "cash_final": float(cash),
        "pnl_realized_sum": float(fills["pnl_realized"].sum()) if not fills.empty else 0.0,
        "commission_sum": float(fills["commission"].sum()) if not fills.empty else 0.0,
    }
    return fills, summary
