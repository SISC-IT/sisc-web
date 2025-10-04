import styles from './Modal.module.css';
import PointIcon from '../../assets/coin4.svg';

function PointsSection({ items }) {
  return (
    <ul className={styles.list}>
      {items.map((it) => (
        <li key={it.id} className={styles.row}>
          <div className={styles.left}>
            <div className={styles.title}>{it.content}</div>
            <div className={styles.time}>{it.time}</div>
          </div>
          <div className={styles.right}>
            <img
              src={PointIcon}
              alt=""
              aria-hidden="true"
              className={styles.pointIcon}
            />

            <span
              className={it.point >= 0 ? styles.pointPlus : styles.pointMinus}
            >
              {it.point >= 0 ? `+${it.point}` : it.point}P
            </span>
          </div>
        </li>
      ))}
    </ul>
  );
}

export default PointsSection;
