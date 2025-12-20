import styles from './Filter.module.css';
import { useState } from 'react';

const Filter = ({ items }) => {
  const [selected, setSelected] = useState(items[0]);

  return (
    <div className={styles.wrap}>
      <div className={styles.line} />

      <div className={styles.content}>
        <div className={styles.title}>Filter</div>

        <ul className={styles.list}>
          {items.map((label) => {
            const isActive = selected === label;

            return (
              <li key={label} className={styles.listItem}>
                <button
                  type="button"
                  className={`${styles.item} ${isActive ? styles.active : ''}`}
                  onClick={() => setSelected(label)}
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
