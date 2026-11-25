import { useRef, useState } from 'react';
import { v4 as uuidv4 } from 'uuid';
import styles from './StockOrder.module.css';
import { placeOrder } from '../../utils/competitionApi';
import { ratePercent, shortDate, won } from '../../utils/competitionFormat';

// 서버와 같은 계산: 수수료는 소수 4자리에서 버린다. 부동소수점 오차를 반올림으로 없앤 뒤 버린다.
const feeOf = (amount, feeRate) =>
  Math.floor(Math.round(amount * feeRate * 1e8) / 1e4) / 1e4;

const maxBuyQuantity = (cash, price, feeRate) => {
  if (!price) return 0;
  let qty = Math.floor(cash / (price * (1 + feeRate)));
  while (qty > 0 && qty * price + feeOf(qty * price, feeRate) > cash) qty -= 1;
  return qty;
};

const OrderForm = ({ competitionId, detail, onOrdered }) => {
  const [side, setSide] = useState('BUY');
  const [qty, setQty] = useState('1');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);
  // 응답을 못 받은 주문을 다시 보낼 때 같은 키를 써야 서버가 두 번 체결하지 않는다
  const pendingKey = useRef(null);

  const price = Number(detail.closePrice);
  const feeRate = Number(detail.tradingFeeRate);
  const cash = Number(detail.cashBalance);
  const isBuy = side === 'BUY';
  const quantity = Number(qty);
  const amount = price * quantity;
  const fee = feeOf(amount, feeRate);
  const total = isBuy ? amount + fee : amount - fee;
  const maxBuy = maxBuyQuantity(cash, price, feeRate);
  const maxQty = isBuy ? maxBuy : detail.holdingQuantity;

  let inputError = '';
  if (!Number.isInteger(quantity) || quantity <= 0)
    inputError = '수량은 1주 이상의 정수로 입력하세요.';
  else if (isBuy && total > cash)
    inputError = `현금이 부족해요. 최대 ${maxBuy.toLocaleString('ko-KR')}주까지 살 수 있어요.`;
  else if (!isBuy && quantity > detail.holdingQuantity) {
    inputError =
      detail.holdingQuantity === 0
        ? '보유하지 않은 종목은 팔 수 없어요.'
        : `보유 수량(${detail.holdingQuantity}주)보다 많이 팔 수 없어요.`;
  }
  const canSubmit = detail.tradable && !inputError && !submitting;

  const submit = async () => {
    // 종가가 바뀌면 closePriceId가 달라져 키도 새로 만든다
    const order = {
      ticker: detail.ticker,
      side,
      quantity,
      closePriceId: detail.closePriceId,
    };
    const signature = JSON.stringify(order);
    if (pendingKey.current?.signature !== signature) {
      pendingKey.current = { signature, key: uuidv4() };
    }
    setSubmitting(true);
    setResult(null);
    try {
      const trade = await placeOrder(
        competitionId,
        order,
        pendingKey.current.key
      );
      pendingKey.current = null;
      setResult({
        ok: true,
        text: `${trade.companyName} ${trade.quantity.toLocaleString('ko-KR')}주 ${isBuy ? '매수' : '매도'} 체결 — ${won(trade.price)}원 (${shortDate(detail.priceDate)} 종가)`,
      });
    } catch (err) {
      setResult({ ok: false, text: err.message });
    } finally {
      // 성공해도 실패해도 현금·보유 수량·주문 가능 여부를 다시 읽는다
      onOrdered();
      setSubmitting(false);
    }
  };

  const changeSide = (next) => {
    setSide(next);
    setResult(null);
  };

  return (
    <div className={styles.orderBox}>
      <div role="group" aria-label="주문 종류" className={styles.sideToggle}>
        <button
          type="button"
          aria-pressed={isBuy}
          className={isBuy ? styles.buyOn : styles.sideOff}
          onClick={() => changeSide('BUY')}
        >
          매수
        </button>
        <button
          type="button"
          aria-pressed={!isBuy}
          className={!isBuy ? styles.sellOn : styles.sideOff}
          onClick={() => changeSide('SELL')}
        >
          매도
        </button>
      </div>

      <div className={styles.qtyRow}>
        <label htmlFor="order-qty" className={styles.qtyLabel}>
          수량
        </label>
        <button
          type="button"
          className={styles.maxButton}
          onClick={() => setQty(String(maxQty))}
        >
          최대 {isBuy ? '매수' : '매도'} {maxQty.toLocaleString('ko-KR')}주
        </button>
      </div>
      <input
        id="order-qty"
        type="number"
        min="1"
        inputMode="numeric"
        value={qty}
        onChange={(e) => {
          setQty(e.target.value);
          setResult(null);
        }}
        className={styles.qtyInput}
      />

      <dl className={styles.costs}>
        <div>
          <dt>체결가 ({shortDate(detail.priceDate)} 종가)</dt>
          <dd>{won(price)}원</dd>
        </div>
        <div>
          <dt>거래 금액</dt>
          <dd>{won(amount)}원</dd>
        </div>
        <div>
          <dt>수수료 ({ratePercent(feeRate)})</dt>
          <dd>{won(fee)}원</dd>
        </div>
        <div className={styles.totalRow}>
          <dt>{isBuy ? '필요한 현금' : '받을 금액'}</dt>
          <dd>{won(total)}원</dd>
        </div>
        <div>
          <dt>주문 가능 현금</dt>
          <dd>{won(cash)}원</dd>
        </div>
      </dl>
      <p className={styles.muted}>예금은 인출해야 주문에 쓸 수 있어요.</p>

      {detail.tradable && inputError && (
        <p role="alert" className={styles.errorBox}>
          {inputError}
        </p>
      )}
      {result && (
        <p
          role={result.ok ? 'status' : 'alert'}
          className={result.ok ? styles.successBox : styles.errorBox}
        >
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
  );
};

export default OrderForm;
