import styles from './SessionSelectBox.module.css';
import { ClipboardCheck } from 'lucide-react';

const sessionList = [
  '증권 1팀',
  '증권 2팀',
  '증권 3팀',
  '자신운용팀',
  '금융 IT팀',
  '매크로팀',
  '트레이딩팀',
];

const SessionSelectBox = () => {
  return (
    <div className={styles.box}>
      <div className={styles.title}>
        <ClipboardCheck />
        <span>출석하기</span>
      </div>
      <select id="session-select" className={styles.session}>
        <option value="">세션선택</option>
        {sessionList.map((item) => (
          <option key={item} value={item}>
            {item}
          </option>
        ))}
      </select>
    </div>
  );
};

export default SessionSelectBox;
