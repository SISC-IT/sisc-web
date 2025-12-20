import styles from './Filter.module.css';

const Filter = ({ items, value, onChange }) => {
  return (
    <div className={styles.wrap}>
      <div className={styles.line} />

      <div className={styles.content}>
        <div className={styles.title}>Filter</div>

        <ul className={styles.list}>
          {items.map((label) => {
            const isActive = value === label;

            return (
              <li key={label} className={styles.listItem}>
                <button
                  type="button"
                  className={`${styles.item} ${isActive ? styles.active : ''}`}
                  onClick={() => onChange(label)}
                >
                  {label}
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
};

export default Filter;
