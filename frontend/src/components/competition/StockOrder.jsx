import { useEffect, useRef, useState } from 'react';
import { v4 as uuidv4 } from 'uuid';
import styles from './StockOrder.module.css';
import { getStockDetail, placeOrder, searchStocks } from '../../utils/competitionApi';
import { changeColor, daysUntil, ratePercent, shortDate, signedPercent, won } from '../../utils/competitionFormat';

// 서버가 tradable=false일 때 주는 이유
const UNAVAILABLE_TEXT = {
  COMPETITION_NOT_ACTIVE: '진행 중인 대회가 아니라 주문할 수 없어요.',
  MARKET_NOT_SUPPORTED: '국내 종목만 거래할 수 있어요.',
  ORDER_CLOSED: '주문 가능 시간(16:30 – 21:00)이 아니에요.',
  PRICE_UNAVAILABLE: '오늘 종가가 아직 없어요. 주말·공휴일이거나 종가를 모으는 중이에요.',
};

// 서버와 같은 계산: 수수료는 소수 4자리에서 버린다.
// 900 × 0.00015 × 10000이 1349.999…로 나오는 부동소수점 오차를 먼저 반올림으로 없앤 뒤 버린다.
const feeOf = (amount, feeRate) => Math.floor(Math.round(amount * feeRate * 1e8) / 1e4) / 1e4;

const maxBuyQuantity = (cash, price, feeRate) => {
  if (!price) return 0;
  let qty = Math.floor(cash / (price * (1 + feeRate)));
  while (qty > 0 && qty * price + feeOf(qty * price, feeRate) > cash) qty -= 1;
  return qty;
};

const StockOrder = ({ competition }) => {
  const { competitionId, myStatus, status, endDate } = competition;
  const isParticipant = myStatus === 'ACTIVE';
  // 종료일에는 대회가 아직 진행 중이지만 서버는 COMPETITION_NOT_ACTIVE로 주문을 막는다
  const isEndDay = status === 'ACTIVE' && daysUntil(endDate) === 0;

  const [query, setQuery] = useState('');
  const [stocks, setStocks] = useState([]);
  const [listError, setListError] = useState('');
  const [ticker, setTicker] = useState(null);
  const [detail, setDetail] = useState(null);
  const [detailError, setDetailError] = useState('');
  // 주문 뒤 현금·보유 수량을 다시 읽을 때 올린다
  const [reloadCount, setReloadCount] = useState(0);
  const [side, setSide] = useState('BUY');
  const [qty, setQty] = useState('1');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);
  // 응답을 못 받은 주문을 다시 보낼 때 같은 키를 써야 서버가 두 번 체결하지 않는다
  const pendingKey = useRef(null);

  // 입력이 멈추고 0.3초 뒤에 검색한다. 검색어가 바뀐 뒤 늦게 온 예전 응답은 버린다.
  useEffect(() => {
    let stale = false;
    const timer = setTimeout(() => {
      searchStocks(competitionId, { query: query.trim() })
        .then((res) => {
          if (stale) return;
          setStocks(res.stocks);
          setListError('');
          setTicker((current) => current ?? res.stocks[0]?.ticker ?? null);
        })
        .catch((err) => !stale && setListError(err.message));
    }, 300);
    return () => {
      stale = true;
      clearTimeout(timer);
    };
  }, [competitionId, query]);

  // 다른 종목을 고른 뒤 늦게 온 예전 종목 응답은 버린다. 그래야 화면과 주문 종목이 어긋나지 않는다.
  useEffect(() => {
    if (!ticker || !isParticipant) return undefined;
    let stale = false;
    getStockDetail(competitionId, ticker)
      .then((res) => {
        if (stale) return;
        setDetail(res);
        setDetailError('');
      })
      .catch((err) => !stale && setDetailError(err.message));
    return () => {
      stale = true;
    };
  }, [competitionId, ticker, isParticipant, reloadCount]);

  const reloadDetail = () => setReloadCount((count) => count + 1);

  const pick = (nextTicker) => {
    // 주문 중에 종목을 바꾸면 체결 결과가 다른 종목 화면에 뜬다
    if (nextTicker === ticker || submitting) return;
    setTicker(nextTicker);
    setDetail(null);
    setDetailError('');
    setResult(null);
  };

  const price = detail ? Number(detail.closePrice) : 0;
  const feeRate = detail ? Number(detail.tradingFeeRate) : 0;
  const cash = detail ? Number(detail.cashBalance) : 0;
  const isBuy = side === 'BUY';
  // 1.5나 1e3처럼 정수가 아닌 입력을 잘라서 주문하지 않도록 그대로 검사한다
  const quantity = Number(qty);
  const amount = price * quantity;
  const fee = feeOf(amount, feeRate);
  const total = isBuy ? amount + fee : amount - fee;
  const maxBuy = maxBuyQuantity(cash, price, feeRate);
  const maxQty = isBuy ? maxBuy : detail?.holdingQuantity ?? 0;

  let inputError = '';
  if (!Number.isInteger(quantity) || quantity <= 0) inputError = '수량은 1주 이상의 정수로 입력하세요.';
  else if (isBuy && total > cash) inputError = `현금이 부족해요. 최대 ${maxBuy.toLocaleString('ko-KR')}주까지 살 수 있어요.`;
  else if (!isBuy && quantity > detail?.holdingQuantity) {
    inputError = detail.holdingQuantity === 0
      ? '보유하지 않은 종목은 팔 수 없어요.'
      : `보유 수량(${detail.holdingQuantity}주)보다 많이 팔 수 없어요.`;
  }
  const canSubmit = detail?.tradable && !inputError && !submitting;

  const submit = async () => {
    if (!canSubmit) return;
    const order = { ticker: detail.ticker, side, quantity, closePriceId: detail.closePriceId };
    const signature = JSON.stringify(order);
    if (pendingKey.current?.signature !== signature) {
      pendingKey.current = { signature, key: uuidv4() };
    }
    setSubmitting(true);
    setResult(null);
    try {
      const trade = await placeOrder(competitionId, order, pendingKey.current.key);
      pendingKey.current = null;
      setResult({
        ok: true,
        text: `${trade.companyName} ${trade.quantity.toLocaleString('ko-KR')}주 ${isBuy ? '매수' : '매도'} 체결 — ${won(trade.price)}원 (${shortDate(detail.priceDate)} 종가)`,
      });
    } catch (err) {
      // 종가가 바뀌었으면 새 종가로는 다른 주문이라 키도 새로 만든다
      if (err.data?.errorCode === 'PRICE_CHANGED') pendingKey.current = null;
      setResult({ ok: false, text: err.message });
    } finally {
      // 성공해도 실패해도 현금·보유 수량·주문 가능 여부를 다시 읽는다
      reloadDetail();
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.layout}>
      <section aria-labelledby="search-title" className={styles.search}>
        <h2 id="search-title" className={styles.sectionTitle}>종목 검색</h2>
        <label htmlFor="stock-query" className={styles.srOnly}>종목명 또는 코드</label>
        <input
          id="stock-query"
          type="search"
          placeholder="종목명 또는 코드"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className={styles.searchInput}
        />
        {listError && <p className={styles.muted}>{listError}</p>}
        {!listError && stocks.length === 0 && <p className={styles.muted}>검색 결과가 없어요.</p>}
        <ul className={styles.stockList}>
          {stocks.map((s) => (
            <li key={s.ticker}>
              <button
                type="button"
                aria-pressed={s.ticker === ticker}
                onClick={() => pick(s.ticker)}
                className={`${styles.stockItem} ${s.ticker === ticker ? styles.selected : ''}`}
              >
                <span className={styles.stockName}>
                  <strong>{s.companyName}</strong>
                  <span className={styles.ticker}>{s.ticker}</span>
                </span>
                <span className={styles.stockPrice}>
                  <strong>{won(s.closePrice)}</strong>
                  <span style={{ color: changeColor(s.dailyChangeRate) }}>{signedPercent(s.dailyChangeRate)}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section aria-label="종목 상세와 주문" className={styles.detailColumn}>
        {!isParticipant && (
          <p className={styles.muted}>
            {myStatus === 'EXCLUDED'
              ? '대회에서 제외되어 거래할 수 없어요.'
              : '대회 참가자만 종목 상세를 보고 주문할 수 있어요.'}
          </p>
        )}
        {/* 주문 뒤 다시 읽기에 실패하면 화면의 현금·보유 수량이 예전 값이라 알려야 한다 */}
        {isParticipant && detailError && <p role="alert" className={styles.errorBox}>{detailError}</p>}
        {isParticipant && !detail && !detailError && <p className={styles.muted}>왼쪽에서 종목을 골라 주세요.</p>}
        {isParticipant && detail && (
          <>
            <p className={detail.tradable ? styles.openBar : styles.closedBar}>
              {detail.tradable
                ? `지금 주문 가능 · 21:00 마감 — 주문하면 ${shortDate(detail.priceDate)} 종가로 바로 체결됩니다`
                : isEndDay && detail.unavailableReason === 'COMPETITION_NOT_ACTIVE'
                  ? '오늘은 종료일이라 주문할 수 없어요. 오늘 종가로 최종 순위가 정해져요.'
                  : UNAVAILABLE_TEXT[detail.unavailableReason]}
            </p>

            <div className={styles.quote}>
              <div>
                <h2 className={styles.detailName}>{detail.companyName}</h2>
                <span className={styles.muted}>
                  {detail.ticker} · 보유 {detail.holdingQuantity.toLocaleString('ko-KR')}주
                </span>
              </div>
              <div className={styles.quotePrice}>
                <span className={styles.muted}>{detail.priceDate ? `${shortDate(detail.priceDate)} 종가` : '종가 없음'}</span>
                <strong className={styles.bigPrice}>{won(detail.closePrice)}원</strong>
                <span style={{ color: changeColor(detail.dailyChangeRate) }}>
                  전일 대비 {signedPercent(detail.dailyChangeRate)}
                </span>
              </div>
            </div>

            <div className={styles.orderBox}>
              <div role="group" aria-label="주문 종류" className={styles.sideToggle}>
                <button
                  type="button"
                  aria-pressed={isBuy}
                  className={isBuy ? styles.buyOn : styles.sideOff}
                  onClick={() => { setSide('BUY'); setResult(null); }}
                >
                  매수
                </button>
                <button
                  type="button"
                  aria-pressed={!isBuy}
                  className={!isBuy ? styles.sellOn : styles.sideOff}
                  onClick={() => { setSide('SELL'); setResult(null); }}
                >
                  매도
                </button>
              </div>

              <div className={styles.qtyRow}>
                <label htmlFor="order-qty" className={styles.qtyLabel}>수량</label>
                <button type="button" className={styles.maxButton} onClick={() => setQty(String(maxQty))}>
                  최대 {isBuy ? '매수' : '매도'} {maxQty.toLocaleString('ko-KR')}주
                </button>
              </div>
              <input
                id="order-qty"
                type="number"
                min="1"
                inputMode="numeric"
                value={qty}
                onChange={(e) => { setQty(e.target.value); setResult(null); }}
                className={styles.qtyInput}
              />

              <dl className={styles.costs}>
                <div><dt>체결가 ({shortDate(detail.priceDate)} 종가)</dt><dd>{won(price)}원</dd></div>
                <div><dt>거래 금액</dt><dd>{won(amount)}원</dd></div>
                <div><dt>수수료 ({ratePercent(feeRate)})</dt><dd>{won(fee)}원</dd></div>
                <div className={styles.totalRow}><dt>{isBuy ? '필요한 현금' : '받을 금액'}</dt><dd>{won(total)}원</dd></div>
                <div><dt>주문 가능 현금</dt><dd>{won(cash)}원</dd></div>
              </dl>
              <p className={styles.muted}>예금은 인출해야 주문에 쓸 수 있어요.</p>

              {detail.tradable && inputError && <p role="alert" className={styles.errorBox}>{inputError}</p>}
              {result && (
                <p role={result.ok ? 'status' : 'alert'} className={result.ok ? styles.successBox : styles.errorBox}>
                  {result.text}
                </p>
              )}
              <button
                type="button"
                disabled={!canSubmit}
                onClick={submit}
                className={`${styles.submit} ${isBuy ? styles.submitBuy : styles.submitSell}`}
              >
                {submitting ? '주문 중…' : isBuy ? '매수하기' : '매도하기'}
              </button>
            </div>
          </>
        )}
      </section>
    </div>
  );
};

export default StockOrder;
