import styles from './RuleRow.module.css';

const RuleRow = () => {
  return (
    <div className={styles.ruleRow}>
      <select className={styles.select}>
        <option>SMA</option>
        <option>EMA</option>
        <option>RSI</option>
        <option>MACD</option>
      </select>

      <select className={styles.select}>
        <option>Crosses Above</option>
        <option>Crosses Below</option>
        <option>Greater Than</option>
        <option>Less Than</option>
      </select>

      <input className={styles.input} defaultValue="50" />

      <select className={styles.select}>
        <option>SMA 200</option>
        <option>EMA 200</option>
        <option>Price</option>
      </select>

      <select className={styles.select}>
        <option>D</option>
        <option>W</option>
        <option>M</option>
      </select>
      <button className={styles.btnIcon} aria-label="more options">
        🗑️
      </button>
    </div>
  );
};

export default RuleRow;
