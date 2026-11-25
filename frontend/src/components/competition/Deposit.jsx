import { useCallback, useEffect, useState } from 'react';
import styles from './Account.module.css';
import usePagedList from './usePagedList';
import {
  getDepositTransactions,
  getMySummary,
  transferDeposit,
} from '../../utils/competitionApi';
import {
  ORDER_HOURS,
  digitsOnly,
  ratePercent,
  shortDateTime,
  won,
} from '../../utils/competitionFormat';

const KIND = {
  DEPOSIT: { label: '예치', tag: 'tagBuy' },
  WITHDRAW: { label: '인출', tag: 'tagSell' },
  INTEREST: { label: '이자', tag: 'tagInterest' },
};

const Deposit = ({ competition }) => {
  const { competitionId, depositInterestRate, myStatus } = competition;
  const [balance, setBalance] = useState(null);
  const [balanceError, setBalanceError] = useState('');
  const [type, setType] = useState('DEPOSIT');
  const [amount, setAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);

  const fetchPage = useCallback(
    (page) => getDepositTransactions(competitionId, page),
    [competitionId]
  );
  const history = usePagedList(fetchPage);

  useEffect(() => {
    getMySummary(competitionId)
      .then((s) =>
        setBalance({
          cash: Number(s.cashBalance),
          deposit: Number(s.depositBalance),
          interest: Number(s.totalInterest),
        })
      )
      .catch((err) => setBalanceError(err.message));
  }, [competitionId]);

  if (balanceError) return <p className={styles.errorBox}>{balanceError}</p>;
  if (!balance) return <p className={styles.muted}>불러오는 중…</p>;

  const isDeposit = type === 'DEPOSIT';
  // 원 미만은 옮길 수 없어서 전액도 원 단위로 버린다
  const limit = Math.trunc(isDeposit ? balance.cash : balance.deposit);
  const value = Number(amount);
  let inputError = '';
  if (amount === '') {
    // 아직 입력 전이면 에러를 띄우지 않는다
  } else if (value <= 0) {
    inputError = '금액은 1원 이상 입력하세요.';
  } else if (value > limit) {
    inputError = `${isDeposit ? '현금' : '예금'}(${won(limit)}원)보다 많이 옮길 수 없어요.`;
  }
  const canSubmit =
    myStatus === 'ACTIVE' && amount !== '' && !inputError && !submitting;

  const changeType = (next) => {
    setType(next);
    setResult(null);
  };

  const submit = async () => {
    setSubmitting(true);
    setResult(null);
    try {
      const ledger = await transferDeposit(competitionId, type, value);
      setBalance((prev) => ({
        ...prev,
        cash: Number(ledger.cashBalance),
        deposit: Number(ledger.depositBalance),
      }));
      setAmount('');
      setResult({
        ok: true,
        text: `${won(value)}원 ${isDeposit ? '예치' : '인출'} 완료`,
      });
      history.reload();
    } catch (err) {
      setResult({ ok: false, text: err.message });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.panel}>
      <section aria-label="예금 요약" className={styles.cards}>
        <div className={styles.mainCard}>
          <span className={styles.cardLabel}>예금 잔고</span>
          <span className={styles.cardValue}>{won(balance.deposit)}원</span>
          <span className={styles.cardNote}>
            연 {ratePercent(depositInterestRate)} · 매일 00시 지급
          </span>
        </div>
        <div className={styles.card}>
          <span className={styles.cardLabel}>현금</span>
          <span className={styles.cardValue}>{won(balance.cash)}원</span>
        </div>
        <div className={styles.card}>
          <span className={styles.cardLabel}>누적 이자</span>
          <span className={styles.cardValue}>{won(balance.interest)}원</span>
        </div>
      </section>

      <div className={styles.columns}>
        <section aria-labelledby="move-title" className={styles.formBox}>
          <h2 id="move-title" className={styles.sectionTitle}>
            입출금
          </h2>
          <div
            role="group"
            aria-label="예치 또는 인출"
            className={styles.toggle}
          >
            <button
              type="button"
              aria-pressed={isDeposit}
              className={isDeposit ? styles.toggleOn : undefined}
              onClick={() => changeType('DEPOSIT')}
            >
              예치
            </button>
            <button
              type="button"
              aria-pressed={!isDeposit}
              className={!isDeposit ? styles.toggleOn : undefined}
              onClick={() => changeType('WITHDRAW')}
            >
              인출
            </button>
          </div>

          <div className={styles.amountRow}>
            <label htmlFor="deposit-amount" className={styles.label}>
              금액 (원)
            </label>
            <button
              type="button"
              className={styles.linkButton}
              disabled={limit === 0}
              onClick={() => setAmount(String(limit))}
            >
              전액 {won(limit)}원
            </button>
          </div>
          <input
            id="deposit-amount"
            type="text"
            placeholder="금액 입력"
            inputMode="numeric"
            value={amount && Number(amount).toLocaleString('ko-KR')}
            onChange={(e) => {
              setAmount(digitsOnly(e.target.value));
              setResult(null);
            }}
            className={styles.input}
          />
          <p className={styles.muted}>입출금 가능 시간 {ORDER_HOURS}</p>

          {inputError && (
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
            className={styles.submit}
            disabled={!canSubmit}
            onClick={submit}
          >
            {submitting ? '처리 중…' : isDeposit ? '예치하기' : '인출하기'}
          </button>
        </section>

        <section aria-labelledby="history-title" className={styles.historyBox}>
          <h2 id="history-title" className={styles.sectionTitle}>
            예금 내역
          </h2>
          <DepositHistory history={history} />
        </section>
      </div>
    </div>
  );
};

const DepositHistory = ({ history }) => {
  const { items, last, error, loading, loadMore } = history;
  if (error && !items) return <p className={styles.errorBox}>{error}</p>;
  if (!items) return <p className={styles.muted}>불러오는 중…</p>;
  if (items.length === 0) {
    return <p className={styles.muted}>아직 예금 내역이 없어요.</p>;
  }

  return (
    <>
      <div className={styles.tableBox}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">일시</th>
              <th scope="col" className={styles.center}>
                구분
              </th>
              <th scope="col">금액</th>
              <th scope="col">예금 잔고</th>
            </tr>
          </thead>
          <tbody>
            {items.map((r) => {
              const kind = KIND[r.type];
              const out = r.type === 'WITHDRAW';
              return (
                <tr key={r.ledgerId}>
                  <td>{shortDateTime(r.executedAt)}</td>
                  <td className={styles.center}>
                    <span className={`${styles.tag} ${styles[kind.tag]}`}>
                      {kind.label}
                    </span>
                  </td>
                  <td className={styles.strong}>
                    {out ? '−' : '+'}
                    {won(r.amount)}
                  </td>
                  <td>{won(r.depositBalance)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {error && <p className={styles.errorBox}>{error}</p>}
      {!last && (
        <button
          type="button"
          className={styles.moreButton}
          disabled={loading}
          onClick={loadMore}
        >
          {loading ? '불러오는 중…' : '더 보기'}
        </button>
      )}
    </>
  );
};

export default Deposit;
