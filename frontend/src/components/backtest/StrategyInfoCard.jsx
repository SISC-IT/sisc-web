import styles from './StrategyInfoCard.module.css';
import SectionCard from './common/SectionCard';

const StrategyInfoCard = ({
  strategyName,
  setStrategyName,
  startDate,
  setStartDate,
  endDate,
  setEndDate,
  initialCapital,
  setInitialCapital,
  onRunBacktest,
}) => {
  return (
    <SectionCard title="전략 기본정보">
      <div>
        <div className={styles.fieldGroup}>
          <div className={styles.field}>
            <label className={styles.label}>전략 이름</label>
            <input
              className={styles.input}
              placeholder="나만의 전략 이름"
              value={strategyName}
              onChange={(e) => setStrategyName(e.target.value)}
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label}>시작일</label>
            <input
              type="date"
              className={styles.input}
              placeholder="YYYY-MM-DD"
              min="1900-01-01"
              max="2100-12-31"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label}>종료일</label>
            <input
              type="date"
              className={styles.input}
              placeholder="YYYY-MM-DD"
              min="1900-01-01"
              max="2100-12-31"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </div>
        </div>

        <div className={styles.fieldGroup}>
          <div className={styles.field}>
            <label className={styles.label}>타임 프레임</label>
            <select className={styles.select} disabled={true}>
              <option>Day</option>
            </select>
          </div>

          <div className={styles.field}>
            <label className={styles.label}>
              초기 자본<span className={styles.span}>($)</span>
            </label>
            <input
              className={styles.input}
              type="text"
              placeholder="0"
              inputMode="numeric"
              value={initialCapital}
              onChange={(e) => setInitialCapital(e.target.value)}
            />
          </div>

          <div className={`${styles.field} ${styles.fieldButton}`}>
            <label className={styles.label} aria-hidden="true">
              &nbsp;
            </label>
            <button className={styles.button} onClick={onRunBacktest}>
              실행하기
            </button>
          </div>
        </div>
      </div>
    </SectionCard>
  );
};

export default StrategyInfoCard;
