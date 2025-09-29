import styles from './StocksCard.module.css';
import SectionCard from './common/SectionCard';

const StocksCard = () => {
  return (
    <SectionCard title="주식" additionalInfo="*복수 선택 가능" actions={null}>
      <div className={styles.stockRow}>
        <input className={styles.input} placeholder="주식을 입력해주세요." />
        <button className={styles.button}>Add</button>
      </div>

      <div className={styles.chips}>
        <span className={styles.chip}>AAPL ✕</span>
        <span className={styles.chip}>MSFT ✕</span>
      </div>
    </SectionCard>
  );
};

export default StocksCard;
