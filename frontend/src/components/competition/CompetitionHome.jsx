import styles from './CompetitionHome.module.css';
import { daysUntil, fullDate, ratePercent, won } from '../../utils/competitionFormat';

const STATUS_LABELS = {
  READY: '시작 전',
  ACTIVE: '진행 중',
  ENDED: '결과 집계 중',
  FINALIZED: '종료',
};

const MY_STATUS_TEXT = {
  ACTIVE: '참가 중이에요 — 활동 회원은 대회가 열릴 때 자동으로 참가돼요',
  EXCLUDED: '대회에서 제외되어 거래할 수 없어요',
};
const NOT_PARTICIPANT_TEXT = '이번 대회 참가자가 아니에요 — 대회가 열린 뒤 가입한 회원은 참가할 수 없어요';

const dDay = (days) => (days === 0 ? 'D-DAY' : `D-${days}`);

// 시작 전이면 시작까지, 진행 중이면 종료까지 남은 날
const countdown = ({ status, startDate, endDate }) => {
  if (status === 'READY') return `시작까지 ${dDay(daysUntil(startDate))}`;
  if (status === 'ACTIVE') return `종료까지 ${dDay(daysUntil(endDate))}`;
  return null;
};

const CompetitionHome = ({ competition }) => {
  const { name, status, startDate, endDate, initialCash, feeRate, depositInterestRate, myStatus } = competition;
  const interest = ratePercent(depositInterestRate);

  const rules = [
    { label: '초기 자금', value: `${won(initialCash)}원` },
    { label: '주문 가능 시간', value: '16:30 – 21:00', note: '그날 종가가 있는 날만 · 21시 마감' },
    { label: '체결 가격', value: '당일 종가', note: '주문 즉시 오늘 종가로 체결' },
    { label: '수수료', value: `거래 금액의 ${ratePercent(feeRate)}` },
    { label: '예금 이자', value: `연 ${interest}`, note: `매일 00시에 예금 × 연 ${interest} ÷ 365 지급` },
  ];

  const flow = [
    { time: '16:00', title: '오늘 종가 수집 · 순위 갱신', text: '오늘 종가를 모아 총자산과 순위를 새로 매깁니다.' },
    { time: '16:30 – 21:00', title: '사고팔기 · 예금 입출금', text: '오늘 종가로 바로 체결됩니다. 그날 거래는 다음 거래일 순위에 반영돼요.' },
    { time: '00:00', title: '예금 이자 지급', text: `예금 잔고에 연 ${interest} ÷ 365만큼 이자가 붙습니다.` },
  ];

  return (
    <div className={styles.home}>
      <section aria-labelledby="competition-name" className={styles.hero}>
        <div className={styles.badges}>
          <span className={styles.statusBadge}>{STATUS_LABELS[status]}</span>
          {countdown(competition) && <span className={styles.countdown}>{countdown(competition)}</span>}
        </div>
        <div className={styles.heroText}>
          <h2 id="competition-name" className={styles.name}>{name}</h2>
          <p className={styles.period}>
            {fullDate(startDate)} – {fullDate(endDate)} · 매일 종가로 체결되고, 수익률로 순위를 겨룹니다.
          </p>
        </div>
        <p className={styles.myStatus}>{MY_STATUS_TEXT[myStatus] ?? NOT_PARTICIPANT_TEXT}</p>
      </section>

      <section aria-labelledby="rules-title" className={styles.section}>
        <h2 id="rules-title" className={styles.sectionTitle}>대회 규칙</h2>
        <div className={styles.cards}>
          {rules.map((rule) => (
            <div key={rule.label} className={styles.card}>
              <span className={styles.cardLabel}>{rule.label}</span>
              <span className={styles.cardValue}>{rule.value}</span>
              {rule.note && <span className={styles.cardNote}>{rule.note}</span>}
            </div>
          ))}
        </div>
      </section>

      <section aria-labelledby="flow-title" className={styles.section}>
        <h2 id="flow-title" className={styles.sectionTitle}>하루 흐름</h2>
        <ol className={styles.flow}>
          {flow.map((step) => (
            <li key={step.time} className={styles.step}>
              <span className={styles.stepTime}>{step.time}</span>
              <span className={styles.stepTitle}>{step.title}</span>
              <span className={styles.stepText}>{step.text}</span>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
};

export default CompetitionHome;
