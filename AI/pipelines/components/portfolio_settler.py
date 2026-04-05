from AI.config import PipelineConfig
from AI.libs.database.repository import PortfolioRepository


def settle_portfolio(
    repo: PortfolioRepository,
    target_tickers: list[str],
    data_map: dict,
    exec_date_str: str,
    pipeline_config: PipelineConfig,
    current_cash: float,
) -> None:
    """
    [포트폴리오 일일 마감 및 정산 담당]
    전체 보유 종목의 평가액을 계산하고 포트폴리오 스냅샷(총자산, 현금,
    미실현/실현 손익 등)을 DB에 저장합니다.
    """
    print("7. Saving end-of-day portfolio snapshot...")

    total_market_value = 0.0
    total_pnl_unrealized = 0.0
    total_pnl_realized_cum = 0.0
    daily_positions = []

    tracked_tickers = set(target_tickers or [])
    tracked_tickers.update(repo.get_open_tickers(exec_date_str))

    for ticker in sorted(tracked_tickers):
        # 각 종목의 현재 보유 정보 (마감 기준) 조회
        pos_info = repo.get_current_position(ticker, target_date=exec_date_str, initial_cash=0)
        qty = pos_info["qty"]
        avg_price = pos_info["avg_price"]
        realized_cum = pos_info.get("pnl_realized_cum", 0.0)
        total_pnl_realized_cum += realized_cum

        # 보유 수량이 있는 경우 평가액 산출
        if qty <= 0:
            continue

        # 종가 데이터가 map에 있으면 최신 종가를, 없으면 평단가를 보수적으로 적용
        current_price = float(data_map[ticker].iloc[-1]["close"]) if ticker in data_map else avg_price
        market_value = qty * current_price
        pnl_unrealized = (current_price - avg_price) * qty

        total_market_value += market_value
        total_pnl_unrealized += pnl_unrealized
        # Position 이력 저장을 위한 튜플화
        daily_positions.append(
            (
                exec_date_str,
                ticker,
                int(qty),
                float(avg_price),
                float(current_price),
                float(market_value),
                float(pnl_unrealized),
                float(realized_cum),
            )
        )

    # 최종 현금 및 자산 계산
    total_asset = current_cash + total_market_value
    if pipeline_config.initial_capital <= 0:
        raise ValueError("pipeline.initial_capital must be greater than 0")
    return_rate = (total_asset / pipeline_config.initial_capital) - 1.0

    # 1) 포트폴리오 요약(Summary) 테이블 저장
    repo.save_portfolio_summary(
        date=exec_date_str,
        total_asset=total_asset,
        cash=current_cash,
        market_value=total_market_value,
        pnl_unrealized=total_pnl_unrealized,
        pnl_realized_cum=total_pnl_realized_cum,
        initial_capital=pipeline_config.initial_capital,
        return_rate=return_rate,
    )

    # 2) 개별 종목 포지션(Position) 상세 테이블 저장
    if daily_positions:
        repo.save_portfolio_positions(exec_date_str, daily_positions)

    print(f"   => total asset ${total_asset:,.0f} | return {return_rate*100:.2f}%")
