import styles from './ActivityModal.module.css';

const AttendanceSection = ({ items }) => {
  return (
    <ul className={styles.list}>
      {items.map((r) => (
        <li key={r.id} className={styles.row}>
          <div className={styles.left}>
            <div className={styles.title}>{r.title}</div>
            <div className={styles.time}>{r.time}</div>
          </div>
        </li>
      ))}
    </ul>
  );
};

export default AttendanceSection;
