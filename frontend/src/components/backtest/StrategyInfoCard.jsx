import styles from './StrategyInfoCard.module.css';
import SectionCard from './common/SectionCard';

const StrategyInfoCard = () => {
  return (
    <SectionCard title="전략 기본정보">
      <div>
        <div className={styles.fieldGroup}>
          <div className={styles.field}>
            <label className={styles.label}>전략 이름</label>
            <input
              className={styles.input}
              placeholder="변호를 입력해주세요."
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label}>시작일</label>
            <input className={styles.input} placeholder="YYYY / MM / DD" />
          </div>

          <div className={styles.field}>
            <label className={styles.label}>종료일</label>
            <input className={styles.input} placeholder="YYYY / MM / DD" />
          </div>
        </div>

        <div className={styles.fieldGroup}>
          <div className={styles.field}>
            <label className={styles.label}>타임 프레임</label>
            <select className={styles.select}>
              <option>D</option>
              <option>W</option>
              <option>M</option>
            </select>
          </div>

          <div className={styles.field}>
            <label className={styles.label}>
              초기 자본<span className={styles.span}>($)</span>
            </label>
            <input
              className={styles.input}
              type="text"
              placeholder="100000"
              inputMode="numeric"
            />
          </div>

          <div className={`${styles.field} ${styles.fieldButton}`}>
            <label className={styles.label} aria-hidden="true">
              &nbsp;
            </label>
            <button className={styles.button}>전략 폴더에 넣기</button>
          </div>
        </div>
      </div>
    </SectionCard>
  );
};

export default StrategyInfoCard;
