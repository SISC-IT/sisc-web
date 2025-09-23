import { useState, useMemo } from 'react';
import { onlyDigits } from '../../utils/attendancemanageUtils';

const SessionForm = ({ styles, onCreate }) => {
  const [title, setTitle] = useState('');
  const [code, setCode] = useState('');
  const [hh, setHh] = useState('');
  const [mm, setMm] = useState('');
  const [ss, setSs] = useState('');

  const durationSec = useMemo(() => {
    const h = parseInt(hh || '0', 10) || 0;
    const m = parseInt(mm || '0', 10) || 0;
    const s = parseInt(ss || '0', 10) || 0;
    return Math.max(0, h * 3600 + m * 60 + s);
  }, [hh, mm, ss]);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!title.trim() || !code || durationSec <= 0) {
      alert('세션 제목, 번호, 시간을 모두 입력해주세요.');
      return;
    }
    onCreate({ title: title.trim(), code, durationSec });
    // reset
    setTitle('');
    setCode('');
    setHh('');
    setMm('');
    setSs('');
  };

  const handleTimeChange = (setter, max) => (e) => {
    let value = onlyDigits(e.target.value);
    if (parseInt(value, 10) > max) {
      value = max.toString();
    }
    setter(value.slice(0, 2));
  };

  return (
    <section className={styles.card}>
      <div className={styles.cardHeader}>
        <div className={styles.cardTitle}>세션 설정</div>
      </div>

      <form className={styles.form} onSubmit={handleSubmit}>
        <div className={styles.field}>
          <label htmlFor="session-title">세션 제목</label>
          <input
            id="session-title"
            className={styles.input}
            placeholder="(ex: 금융IT 출석)"
            value={title}
            type="text"
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>

        <div className={styles.field}>
          <label htmlFor="session-code">세션 번호</label>
          <input
            id="session-code"
            className={styles.input}
            placeholder="(ex: 12345678)"
            value={code}
            type="text"
            onChange={(e) => setCode(e.target.value)}
          />
        </div>

        <div className={styles.field}>
          <label htmlFor="session-ss">세션 시간</label>
          <div className={styles.timefield}>
            <input
              id="session-hh"
              className={styles.input}
              placeholder="시(HH)"
              value={hh}
              inputMode="numeric"
              type="text"
              onChange={handleTimeChange(setHh, 23)}
            />
            <input
              id="session-mm"
              className={styles.input}
              placeholder="분(MM)"
              value={mm}
              inputMode="numeric"
              type="text"
              onChange={handleTimeChange(setMm, 59)}
            />
            <input
              id="session-ss"
              className={styles.input}
              placeholder="초(SS)"
              value={ss}
              inputMode="numeric"
              type="text"
              onChange={handleTimeChange(setSs, 59)}
            />
          </div>
        </div>

        <button className={styles.btn}>생성</button>
      </form>
    </section>
  );
};

export default SessionForm;
