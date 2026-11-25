import { useState } from 'react';
import { v4 as uuidv4 } from 'uuid';
import styles from './StockOrder.module.css';
import { placeOrder } from '../../utils/competitionApi';
import {
  digitsOnly,
  ratePercent,
  shortDate,
  won,
} from '../../utils/competitionFormat';

// 서버와 같은 계산: 수수료는 소수 4자리에서 버린다. 부동소수점 오차를 반올림으로 없앤 뒤 버린다.
const feeOf = (amount, feeRate) =>
  Math.floor(Math.round(amount * feeRate * 1e8) / 1e4) / 1e4;

const maxBuyQuantity = (cash, price, feeRate) => {
  if (!price) return 0;
  const fits = (q) => q * price + feeOf(q * price, feeRate) <= cash;
  // 어림값에서 시작해, 수수료를 버린 실제 금액으로 위아래를 맞춘다
  let qty = Math.floor(cash / (price * (1 + feeRate)));
  while (qty > 0 && !fits(qty)) qty -= 1;
  while (fits(qty + 1)) qty += 1;
  return qty;
};

// pendingKeys(주문 내용 → 키)는 종목을 바꿔도 남아야 해서 부모(StockOrder)가 가진다
const OrderForm = ({
  competitionId,
  detail,
  stale,
  pendingKeys,
  onOrdered,
}) => {
  const [side, setSide] = useState('BUY');
  const [qty, setQty] = useState('1');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);

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
  if (quantity <= 0) inputError = '수량은 1주 이상 입력하세요.';
  else if (isBuy && total > cash)
    inputError = `현금이 부족해요. 최대 ${maxBuy.toLocaleString('ko-KR')}주까지 살 수 있어요.`;
  else if (!isBuy && quantity > detail.holdingQuantity) {
    inputError =
      detail.holdingQuantity === 0
        ? '보유하지 않은 종목은 팔 수 없어요.'
        : `보유 수량(${detail.holdingQuantity}주)보다 많이 팔 수 없어요.`;
  }
  // 다시 읽기에 실패하면 잔고가 예전 값이라 주문을 막는다
  const canSubmit = detail.tradable && !stale && !inputError && !submitting;

  const submit = async () => {
    // 종가가 바뀌면 closePriceId가 달라져 키도 새로 만든다
    const order = {
      ticker: detail.ticker,
      side,
      quantity,
      closePriceId: detail.closePriceId,
    };
    const signature = JSON.stringify(order);
    if (!pendingKeys.current.has(signature)) {
      pendingKeys.current.set(signature, uuidv4());
    }
    setSubmitting(true);
    setResult(null);
    const sentAt = Date.now();
    try {
      const trade = await placeOrder(
        competitionId,
        order,
        pendingKeys.current.get(signature)
      );
      pendingKeys.current.delete(signature);
      const what = `${trade.companyName} ${trade.quantity.toLocaleString('ko-KR')}주 ${isBuy ? '매수' : '매도'}`;
      // 같은 키로 다시 보내면 서버는 예전 체결을 돌려준다. 체결 시각(KST)이 보낸 시각보다 이르면 예전 체결이다.
      const replayed =
        Date.parse(`${trade.executedAt.slice(0, 23)}+09:00`) < sentAt - 3000;
      setResult({
        ok: true,
        text: replayed
          ? `이미 체결된 주문이에요 — ${what}, ${won(trade.price)}원`
          : `${what} 체결 — ${won(trade.price)}원 (${shortDate(detail.priceDate)} 종가)`,
      });
    } catch (err) {
      const uncertain = !err.status || err.status >= 500;
      setResult({
        ok: false,
        text: uncertain
          ? '주문 결과를 확인하지 못했어요. 매매 내역에서 체결 여부를 확인해 주세요.'
          : err.message,
      });
    } finally {
      // 성공해도 실패해도 현금·보유 수량·주문 가능 여부를 다시 읽는다
      onOrdered();
      setSubmitting(false);
    }
  };

  const changeQty = (next) => {
    setQty(String(next));
    setResult(null);
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
          disabled={maxQty === 0}
          onClick={() => setQty(String(maxQty))}
        >
          주문 가능 {maxQty.toLocaleString('ko-KR')}주
        </button>
      </div>
      <div className={styles.stepper}>
        <input
          id="order-qty"
          type="text"
          inputMode="numeric"
          value={qty}
          onChange={(e) => changeQty(digitsOnly(e.target.value))}
          className={styles.qtyInput}
        />
        <span className={styles.unit}>주</span>
        <button
          type="button"
          aria-label="1주 빼기"
          disabled={quantity <= 1}
          onClick={() => changeQty(quantity - 1)}
        >
          −
        </button>
        <button
          type="button"
          aria-label="1주 더하기"
          onClick={() => changeQty(quantity + 1)}
        >
          +
        </button>
      </div>

      <dl className={styles.costs}>
        <div>
          <dt>주문 금액</dt>
          <dd>{won(amount)}원</dd>
        </div>
        <div>
          <dt>수수료 ({ratePercent(feeRate)})</dt>
          <dd>{won(fee)}원</dd>
        </div>
        <div className={styles.totalRow}>
          <dt>{isBuy ? '총 주문 금액' : '정산 금액'}</dt>
          <dd>{won(total)}원</dd>
        </div>
        <div>
          <dt>주문 가능 금액</dt>
          <dd>{won(cash)}원</dd>
        </div>
      </dl>

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
