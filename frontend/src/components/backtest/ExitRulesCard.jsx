import styles from './BacktestCard.module.css';
import SectionCard from './common/SectionCard';
import RulesCard from './common/RulesCard';

const ExitRulesCard = ({
  rules,
  setRules,
  defaultExitDays,
  setDefaultExitDays,
}) => {
  const handleChangeDefaultExitDays = (e) => {
    const value = e.target.value;

    if (value === '') {
      setDefaultExitDays(0);
      return;
    }

    const num = Number(value);
    if (Number.isNaN(num) || num < 0) return;

    setDefaultExitDays(num);
  };

  return (
    <SectionCard
      title="매도 조건"
      description="기본 청산 기간과 행을 추가/삭제하여 조건을 설정하세요."
      actions={null}
    >
      {/* 👉 기본 청산 기간 설정 영역 */}
      <div className={styles.defaultExitRow}>
        <label className={styles.defaultExitLabel}>
          기본 청산 기간(일)
          <input
            type="number"
            min={0}
            className={styles.defaultExitInput}
            value={defaultExitDays ?? 0}
            onChange={handleChangeDefaultExitDays}
          />
        </label>
        <p className={styles.defaultExitHint}>
          설정한 일수 동안 보유 후, 별도의 매도 조건이 충족되지 않더라도
          청산합니다.
        </p>
      </div>

      <RulesCard rules={rules} setRules={setRules} />
    </SectionCard>
  );
};

export default ExitRulesCard;
