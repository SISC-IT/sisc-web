import styles from './NotesCard.module.css';
import SectionCard from './common/SectionCard';

const NotesCard = () => {
  return (
    <SectionCard
      title="Note"
      description="전략 의도, 리스크, 특기 포인트 등을 기록하세요."
    >
      <textarea
        className={styles.textarea}
        placeholder="예) RSI 14 기준 모멘텀 반전 + SMA 장기 추세 필터…"
        rows={5}
      />
    </SectionCard>
  );
};

export default NotesCard;
