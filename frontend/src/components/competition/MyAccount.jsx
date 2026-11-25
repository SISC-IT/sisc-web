import { useEffect, useState } from 'react';
import styles from './Account.module.css';
import TradeHistory from './TradeHistory';
import { getMyHoldings, getMySummary } from '../../utils/competitionApi';
import {
  changeColor,
  shortDate,
  signedPercent,
  won,
} from '../../utils/competitionFormat';

const signedWon = (value) =>
  value === null || value === undefined
    ? '-'
    : `${Number(value) > 0 ? '+' : ''}${won(value)}`;

const MyAccount = ({ competitionId }) => {
  const [summary, setSummary] = useState(null);
  const [holdings, setHoldings] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([getMySummary(competitionId), getMyHoldings(competitionId)])
      .then(([s, h]) => {
        setSummary(s);
        setHoldings(h);
      })
      .catch((err) => setError(err.message));
  }, [competitionId]);

  if (error) return <p className={styles.errorBox}>{error}</p>;
  if (!summary) return <p className={styles.muted}>불러오는 중…</p>;

  const pending = summary.valuationStatus === 'PENDING_DATA';

  return (
    <div className={styles.panel}>
      {pending && (
        <p className={styles.muted}>
          종가가 아직 없는 보유 종목이 있어 총자산을 계산할 수 없어요.
        </p>
      )}

      <section aria-label="요약" className={styles.cards}>
        <div className={styles.mainCard}>
          <span className={styles.cardLabel}>총자산</span>
          <span className={styles.cardValue}>{won(summary.totalAssets)}원</span>
          <span className={styles.cardNote}>
            {signedWon(summary.profitLoss)} ({signedPercent(summary.returnRate)}
            )
          </span>
        </div>
        <div className={styles.card}>
          <span className={styles.cardLabel}>주식 평가액</span>
          <span className={styles.cardValue}>{won(summary.stockValue)}원</span>
        </div>
        <div className={styles.card}>
          <span className={styles.cardLabel}>현금</span>
          <span className={styles.cardValue}>{won(summary.cashBalance)}원</span>
        </div>
        <div className={styles.card}>
          <span className={styles.cardLabel}>예금</span>
          <span className={styles.cardValue}>
            {won(summary.depositBalance)}원
          </span>
          <span className={styles.cardNote}>
            누적 이자 {won(summary.totalInterest)}원
          </span>
        </div>
      </section>

      <section aria-labelledby="holdings-title" className={styles.section}>
        <h2 id="holdings-title" className={styles.sectionTitle}>
          보유 종목
        </h2>
        {holdings.length === 0 ? (
          <p className={styles.muted}>보유한 종목이 없어요.</p>
        ) : (
          <div className={styles.tableBox}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">종목</th>
                  <th scope="col">수량</th>
                  <th scope="col">평균 매입가</th>
                  <th scope="col">종가</th>
                  <th scope="col">평가액</th>
                  <th scope="col">평가 손익</th>
                </tr>
              </thead>
              <tbody>
                {holdings.map((h) => (
                  <tr key={h.ticker}>
                    <td>
                      <span className={styles.strong}>{h.companyName}</span>
                      <span className={styles.sub}>{h.ticker}</span>
                    </td>
                    <td>{h.quantity.toLocaleString('ko-KR')}주</td>
                    <td>{won(h.avgBuyPrice)}</td>
                    <td>
                      {won(h.closePrice)}
                      {h.priceDate && (
                        <span className={styles.sub}>
                          {shortDate(h.priceDate)}
                        </span>
                      )}
                    </td>
                    <td className={styles.strong}>{won(h.evaluationAmount)}</td>
                    <td
                      className={styles.strong}
                      style={{ color: changeColor(h.profitRate) }}
                    >
                      {signedWon(h.profit)} ({signedPercent(h.profitRate)})
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className={styles.muted}>평가 손익에는 수수료가 들어가지 않아요.</p>
      </section>

      <section aria-labelledby="trades-title" className={styles.section}>
        <h2 id="trades-title" className={styles.sectionTitle}>
          매매 내역
        </h2>
        <TradeHistory competitionId={competitionId} />
      </section>
    </div>
  );
};

export default MyAccount;
