import styles from './Modal.module.css';

const ActivitySection = ({ items }) => {
  return (
    <ul className={styles.list}>
      {items.map((it) => (
        <li key={it.id} className={styles.row}>
          <div className={styles.left}>
            <div className={styles.title}>{it.content}</div>
            <div className={styles.time}>{it.time}</div>
          </div>
        </li>
      ))}
    </ul>
  );
};

export default ActivitySection;
