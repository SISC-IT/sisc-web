import styles from './SessionSelectBox.module.css';
import { ClipboardCheck } from 'lucide-react';

const SessionSelectBox = ({
  sessions = [],
  selectedSession = '',
  onChange,
  disabled = false,
}) => {
  const sessionList = Array.from(
    new Set(
      sessions
        .map((session) => session.sessionTitle)
        .filter((sessionTitle) => typeof sessionTitle === 'string' && sessionTitle.trim() !== ''),
    ),
  );

  return (
    <div className={styles.box}>
      <div className={styles.title}>
        <ClipboardCheck aria-hidden="true" />
        <label htmlFor="session-select">세션선택</label>
      </div>
      <select
        id="session-select"
        className={styles.session}
        value={selectedSession}
        onChange={(event) => onChange?.(event.target.value)}
        disabled={disabled}
      >
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
