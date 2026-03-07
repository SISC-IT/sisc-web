import styles from './SessionSelectBox.module.css';
import { ClipboardCheck } from 'lucide-react';

const normalizeSessionTitle = (sessionTitle) =>
  typeof sessionTitle === 'string' ? sessionTitle.trim() : '';

const SessionSelectBox = ({
  sessions = [],
  selectedSession = '',
  onChange,
  disabled = false,
}) => {
  const sessionList = Array.from(
    new Set(
      sessions
        .map((session) => normalizeSessionTitle(session.sessionTitle))
        .filter((sessionTitle) => sessionTitle !== ''),
    ),
  );

  const currentValue = sessionList.includes(selectedSession) ? selectedSession : sessionList[0] || '';

  return (
    <div className={styles.box}>
      <div className={styles.title}>
        <ClipboardCheck aria-hidden="true" />
        <label htmlFor="session-select">세션선택</label>
      </div>
      <select
        id="session-select"
        className={styles.session}
        value={currentValue}
        onChange={(event) => onChange?.(event.target.value)}
        disabled={disabled}
      >
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
