import styles from './CompetitionHome.module.css';
import {
  ORDER_HOURS,
  daysUntil,
  fullDate,
  ratePercent,
  won,
} from '../../utils/competitionFormat';

// 참가 중이면 안내하지 않고, 거래할 수 없는 경우만 알린다
const MY_STATUS_TEXT = {
  EXCLUDED: '대회에서 제외되어 거래할 수 없어요',
  NOT_PARTICIPANT: '이번 대회 참가자가 아니에요',
};

const dDay = (days) => (days === 0 ? 'D-DAY' : `D-${days}`);

// 시작 전이면 시작까지, 진행 중이면 종료까지 남은 날.
// 상태 전환이 늦어 날짜가 이미 지났으면(음수) 보여주지 않는다.
const countdown = ({ status, startDate, endDate }) => {
  const target =
    status === 'READY' ? startDate : status === 'ACTIVE' ? endDate : null;
  const days = target ? daysUntil(target) : -1;
  if (days < 0) return null;
  return `${status === 'READY' ? '시작까지' : '종료까지'} ${dDay(days)}`;
};

const CompetitionHome = ({ competition }) => {
  const {
    name,
    startDate,
    endDate,
    initialCash,
    feeRate,
    depositInterestRate,
    myStatus,
  } = competition;
  const interest = ratePercent(depositInterestRate);
  const remaining = countdown(competition);

  const rules = [
    { label: '초기 자금', value: `${won(initialCash)}원` },
    {
      label: '주문 가능 시간',
      value: ORDER_HOURS,
      note: '주말·공휴일 제외 · 종료 전날까지',
    },
    { label: '체결 가격', value: '당일 종가' },
    { label: '수수료', value: ratePercent(feeRate) },
    {
      label: '예금 이자',
      value: `연 ${interest}`,
      note: '매일 00시 지급',
    },
  ];

  return (
    <div className={styles.home}>
      <section aria-labelledby="competition-name" className={styles.hero}>
        <div className={styles.heroText}>
          {remaining && <span className={styles.countdown}>{remaining}</span>}
          <h2 id="competition-name" className={styles.name}>
            {name}
          </h2>
          <p className={styles.period}>
            {fullDate(startDate)} – {fullDate(endDate)}
          </p>
        </div>
        {myStatus !== 'ACTIVE' && (
          <p className={styles.myStatus}>
            {MY_STATUS_TEXT[myStatus ?? 'NOT_PARTICIPANT']}
          </p>
        )}
      </section>

      <section aria-labelledby="rules-title" className={styles.section}>
        <h2 id="rules-title" className={styles.sectionTitle}>
          대회 규칙
        </h2>
        <div className={styles.cards}>
          {rules.map((rule) => (
            <div key={rule.label} className={styles.card}>
              <span className={styles.cardLabel}>{rule.label}</span>
              <span className={styles.cardValue}>{rule.value}</span>
              {rule.note && (
                <span className={styles.cardNote}>{rule.note}</span>
              )}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
};

export default CompetitionHome;
