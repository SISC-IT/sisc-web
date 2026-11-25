import { useCallback, useEffect, useRef, useState } from 'react';
import styles from './StockOrder.module.css';
import StockSearch from './StockSearch';
import OrderForm from './OrderForm';
import { getStockDetail } from '../../utils/competitionApi';
import {
  ORDER_HOURS,
  changeColor,
  daysUntil,
  shortDate,
  signedPercent,
  won,
} from '../../utils/competitionFormat';

// 서버가 tradable=false일 때 주는 이유
const UNAVAILABLE_TEXT = {
  COMPETITION_NOT_ACTIVE: '진행 중인 대회가 아니라 주문할 수 없어요.',
  ORDER_CLOSED: `주문 가능 시간(${ORDER_HOURS})이 아니에요.`,
  PRICE_UNAVAILABLE:
    '오늘 종가가 아직 없어요. 주말·공휴일이거나 종가를 모으는 중이에요.',
};

const StockOrder = ({ competition }) => {
  const { competitionId, myStatus, endDate } = competition;
  const isParticipant = myStatus === 'ACTIVE';

  const [ticker, setTicker] = useState(null);
  const [detail, setDetail] = useState(null);
  const [detailError, setDetailError] = useState('');
  // 주문 뒤 현금·보유 수량을 다시 읽을 때 올린다
  const [reloadCount, setReloadCount] = useState(0);
  // 응답을 못 받은 주문을 다시 보낼 때 같은 키를 써야 서버가 두 번 체결하지 않는다
  const pendingKey = useRef(null);
  const reload = () => setReloadCount((count) => count + 1);

  // 처음 검색 결과가 오면 첫 종목을 골라 둔다
  const selectFirst = useCallback((stocks) => {
    setTicker((current) => current ?? stocks[0]?.ticker ?? null);
  }, []);

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

  const select = (nextTicker) => {
    // 같은 종목을 다시 누르면 새로 읽는다. 페이지를 열어 둔 사이 주문 가능 시간이 바뀔 수 있다.
    if (nextTicker === ticker) {
      reload();
      return;
    }
    setTicker(nextTicker);
    setDetail(null);
    setDetailError('');
  };

  const unavailableText = (reason) =>
    // 종료일에는 대회가 아직 진행 중이지만 서버는 COMPETITION_NOT_ACTIVE로 주문을 막는다
    reason === 'COMPETITION_NOT_ACTIVE' && daysUntil(endDate) === 0
      ? '오늘은 종료일이라 주문할 수 없어요. 오늘 종가로 최종 순위가 정해져요.'
      : (UNAVAILABLE_TEXT[reason] ?? '지금은 주문할 수 없어요.');

  return (
    <div className={styles.layout}>
      <StockSearch
        competitionId={competitionId}
        selectedTicker={ticker}
        onSelect={select}
        onResults={selectFirst}
      />

      <section aria-label="종목 상세와 주문" className={styles.detailColumn}>
        {!isParticipant && (
          <p className={styles.muted}>
            {myStatus === 'EXCLUDED'
              ? '대회에서 제외되어 거래할 수 없어요.'
              : '대회 참가자만 종목 상세를 보고 주문할 수 있어요.'}
          </p>
        )}
        {isParticipant && detailError && (
          <p role="alert" className={styles.errorBox}>
            {detailError}
          </p>
        )}
        {isParticipant && !detail && !detailError && (
          <p className={styles.muted}>
            {ticker ? '불러오는 중…' : '왼쪽에서 종목을 골라 주세요.'}
          </p>
        )}
        {isParticipant && detail && (
          <>
            <p className={detail.tradable ? styles.openBar : styles.closedBar}>
              {detail.tradable
                ? `지금 주문 가능(${ORDER_HOURS}) — 주문하면 ${shortDate(detail.priceDate)} 종가로 바로 체결됩니다`
                : unavailableText(detail.unavailableReason)}
            </p>

            <div className={styles.quote}>
              <div>
                <h2 className={styles.detailName}>{detail.companyName}</h2>
                <span className={styles.muted}>
                  {detail.ticker} · 보유{' '}
                  {detail.holdingQuantity.toLocaleString('ko-KR')}주
                </span>
              </div>
              <div className={styles.quotePrice}>
                <span className={styles.muted}>
                  {detail.priceDate
                    ? `${shortDate(detail.priceDate)} 종가`
                    : '종가 없음'}
                </span>
                <strong className={styles.bigPrice}>
                  {won(detail.closePrice)}원
                </strong>
                <span style={{ color: changeColor(detail.dailyChangeRate) }}>
                  전일 대비 {signedPercent(detail.dailyChangeRate)}
                </span>
              </div>
            </div>

            {/* 종목이 바뀌면 새로 그려서 이전 종목의 수량·주문 결과가 남지 않게 한다 */}
            {!detailError && (
              <OrderForm
                key={detail.ticker}
                competitionId={competitionId}
                detail={detail}
                pendingKey={pendingKey}
                onOrdered={reload}
              />
            )}
          </>
        )}
      </section>
    </div>
  );
};

export default StockOrder;
