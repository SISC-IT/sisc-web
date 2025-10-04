import styles from './EntryRulesCard.module.css';
import SectionCard from './common/SectionCard';
import RuleRow from './common/RuleRow';

const EntryRulesCard = () => {
  return (
    <SectionCard
      title="매수 조건"
      description="행을 추가/삭제하여 규칙을 생성하세요."
      actions={<button className={styles.button}>조건 추가 +</button>}
    >
      <RuleRow />
      <RuleRow />
    </SectionCard>
  );
};

export default EntryRulesCard;
