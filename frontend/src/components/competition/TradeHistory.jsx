import { useCallback } from 'react';
import styles from './Account.module.css';
import usePagedList from '../../hooks/usePagedList';
import { getMyTrades } from '../../utils/competitionApi';
import { shortDateTime, won } from '../../utils/competitionFormat';

const TradeHistory = ({ competitionId }) => {
  const fetchPage = useCallback(
    (page) => getMyTrades(competitionId, page),
    [competitionId]
  );
  const { items, last, error, loading, loadMore } = usePagedList(
    fetchPage,
    'tradeId'
  );

  if (error && !items?.length) {
    return (
      <>
        <p className={styles.errorBox}>{error}</p>
        <button type="button" className={styles.moreButton} onClick={loadMore}>
          다시 시도
        </button>
      </>
    );
  }
  if (!items) return <p className={styles.muted}>불러오는 중…</p>;
  if (items.length === 0) {
    return <p className={styles.muted}>아직 거래가 없어요.</p>;
  }

  return (
    <>
      <div className={styles.tableBox}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">주문 시각</th>
              <th scope="col" className={styles.center}>
                구분
              </th>
              <th scope="col" className={styles.left}>
                종목
              </th>
              <th scope="col">수량</th>
              <th scope="col">체결가</th>
              <th scope="col">거래 금액</th>
              <th scope="col">수수료</th>
            </tr>
          </thead>
          <tbody>
            {items.map((t) => {
              const isBuy = t.side === 'BUY';
              return (
                <tr key={t.tradeId}>
                  <td>{shortDateTime(t.executedAt)}</td>
                  <td className={styles.center}>
                    <span
                      className={`${styles.tag} ${isBuy ? styles.tagBuy : styles.tagSell}`}
                    >
                      {isBuy ? '매수' : '매도'}
                    </span>
                  </td>
                  <td className={`${styles.left} ${styles.strong}`}>
                    {t.companyName}
                  </td>
                  <td>{t.quantity.toLocaleString('ko-KR')}주</td>
                  <td>{won(t.price)}</td>
                  <td className={styles.strong}>
                    {won(Number(t.price) * t.quantity)}
                  </td>
                  <td>{won(t.fee)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {error && <p className={styles.errorBox}>{error}</p>}
      {(!last || error) && (
        <button
          type="button"
          className={styles.moreButton}
          disabled={loading}
          onClick={loadMore}
        >
          {loading ? '불러오는 중…' : error ? '다시 시도' : '더 보기'}
        </button>
      )}
    </>
  );
};

export default TradeHistory;
