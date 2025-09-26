import { useEffect, useState } from 'react';

const RemainingTime = ({ styles, expiresAt }) => {
  const [remainingTime, setRemainingTime] = useState(0);

  useEffect(() => {
    if (!expiresAt) {
      setRemainingTime(0);
      return;
    }
    const calc = () => setRemainingTime(Math.max(0, expiresAt - Date.now()));
    calc();
    const id = setInterval(calc, 1000);
    return () => clearInterval(id);
  }, [expiresAt]);

  const formatTime = (ms) => {
    if (ms <= 0) return '00:00:00';
    const totalSeconds = Math.floor(ms / 1000);
    const h = String(Math.floor(totalSeconds / 3600)).padStart(2, '0');
    const m = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, '0');
    const s = String(totalSeconds % 60).padStart(2, '0');
    return `${h}:${m}:${s}`;
  };

  return (
    <div className={styles.remainingTime}>
      남은 시간: {formatTime(remainingTime)}
    </div>
  );
};

export default RemainingTime;
