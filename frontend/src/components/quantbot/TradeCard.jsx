// src/components/trade/TradeCard.jsx

export default function TradeCard({ trade, onReportClick }) {
  return (
    <div className="trade-card">
      <div className="trade-row">
        <span className="trade-label">종목</span>
        <span className="trade-value">{trade.ticker}</span>
      </div>

      <div className="trade-row">
        <span className="trade-label">포지션</span>
        <span className="trade-value">
          {trade.side === 'BUY' ? '매수' : '매도'}
        </span>
      </div>

      <div className="trade-row">
        <span className="trade-label">가격</span>
        <span className="trade-value">{trade.fillPrice}$</span>
      </div>

      <div className="trade-row">
        <span className="trade-label">수량</span>
        <span className="trade-value">{trade.qty}주</span>
      </div>

      <button className="report-btn" onClick={() => onReportClick(trade.id)}>
        리포트 보기
      </button>
    </div>
  );
}
