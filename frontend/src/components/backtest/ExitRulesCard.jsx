import SectionCard from './common/SectionCard';
import RulesCard from './common/RulesCard';

const ExitRulesCard = ({ rules, setRules }) => {
  return (
    <SectionCard
      title="매도 조건"
      description="행을 추가/삭제하여 규칙을 생성하세요."
      actions={null}
    >
      <RulesCard rules={rules} setRules={setRules} />
    </SectionCard>
  );
};

export default ExitRulesCard;
('');
