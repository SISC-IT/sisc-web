import { useState, useMemo } from 'react';
import { onlyDigits } from '../../utils/attendancemanageUtils';

const SessionForm = ({ styles, onCreate }) => {
  const [title, setTitle] = useState('');
  const [code, setCode] = useState('');
  const [hh, setHh] = useState('');
  const [mm, setMm] = useState('');
  const [ss, setSs] = useState('');

  const durationSec = useMemo(() => {
    const H = parseInt(hh || '0', 10) || 0;
    const M = parseInt(mm || '0', 10) || 0;
    const S = parseInt(ss || '0', 10) || 0;
    return Math.max(0, H * 3600 + M * 60 + S);
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

  return (
    <section className={styles.card}>
      <div className={styles.cardHeader}>
        <div className={styles.cardTitle}>세션 설정</div>
      </div>

      <form className={styles.form} onSubmit={handleSubmit}>
        <div className={styles.field}>
          <label>세션 제목</label>
          <input
            className={styles.input}
            placeholder="세션 제목"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>

        <div className={styles.field}>
          <label>세션 번호</label>
          <input
            className={styles.input}
            placeholder="세션 번호"
            value={code}
            onChange={(e) => setCode(e.target.value)}
          />
        </div>

        <div className={styles.field}>
          <label>세션 시간</label>
          <div className={styles.timefield}>
            <input
              className={styles.input}
              placeholder="시(HH)"
              value={hh}
              inputMode="numeric"
              onChange={(e) => setHh(onlyDigits(e.target.value).slice(0, 2))}
            />
            <input
              className={styles.input}
              placeholder="분(MM)"
              value={mm}
              inputMode="numeric"
              onChange={(e) => setMm(onlyDigits(e.target.value).slice(0, 2))}
            />
            <input
              className={styles.input}
              placeholder="초(SS)"
              value={ss}
              inputMode="numeric"
              onChange={(e) => setSs(onlyDigits(e.target.value).slice(0, 2))}
            />
          </div>
        </div>

        <button className={styles.btn}>생성</button>
      </form>
    </section>
  );
};

export default SessionForm;
