import styles from './StockInfoItem.module.css';

const StockInfoItem = ({ label, value }) => {
  return (
    <div className={styles['stock-info-item']}>
      <span className={styles['stock-key']}>{label}</span>
      <span className={styles['stock-value']}>{value}</span>
    </div>
  );
};

export default StockInfoItem;
