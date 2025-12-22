import styles from './Filter.module.css';

const Filter = ({ items, value, onChange }) => {
  if (!items || items.length === 0) {
    return null;
  }

  return (
    <div className={styles.wrap}>
      <div className={styles.line} />

      <div className={styles.content}>
        <div className={styles.title}>Filter</div>

        <ul className={styles.list} role="list" aria-label="필터 옵션">
          {items.map((label) => {
            const isActive = value === label;

            return (
              <li key={label} className={styles.listItem}>
                <button
                  type="button"
                  className={`${styles.item} ${isActive ? styles.active : ''}`}
                  onClick={() => onChange(label)}
                  aria-pressed={isActive}
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
