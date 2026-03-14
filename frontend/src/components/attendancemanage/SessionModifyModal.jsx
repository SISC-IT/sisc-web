import { useState, useEffect } from 'react';
import styles from '../VerificationModal.module.css';

const SessionModifyModal = ({
  styles: commonStyles,
  onClose,
  session,
  onSave,
}) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [allowedMinutes, setAllowedMinutes] = useState(0);

  useEffect(() => {
    if (session && session.session) {
      setTitle(session.session.title || '');
      setDescription(session.session.description || '');
      setAllowedMinutes(session.session.allowedMinutes || 0);
    }
  }, [session]);

  const handleModifyClick = () => {
    const minutes = parseInt(allowedMinutes, 10);
    if (isNaN(minutes) || minutes < 0) {
      alert('체크인 허용 시간은 0 이상의 숫자로 입력해주세요.');
      return;
    }

    onSave(session.sessionId, {
      title,
      description,
      allowedMinutes: minutes,
      status: session.session.status || 'ACTIVE'
    });

    onClose();
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <h1>세션 정보 수정</h1>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
          >
            &times;
          </button>
        </div>

        <div className={styles.form}>
          <div className={commonStyles.modalInputGroup}>
            <label htmlFor="sessionTitle" className={commonStyles.label}>
              세션 제목
            </label>
            <input
              type="text"
              id="sessionTitle"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="세션 제목"
              style={{ padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', width: '100%', boxSizing: 'border-box' }}
            />
          </div>

          <div className={commonStyles.modalInputGroup} style={{ marginTop: '1rem' }}>
            <label htmlFor="sessionDescription" className={commonStyles.label}>
              세션 설명
            </label>
            <input
              type="text"
              id="sessionDescription"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="세션 설명"
              style={{ padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', width: '100%', boxSizing: 'border-box' }}
            />
          </div>

          <div className={commonStyles.modalInputGroup} style={{ marginTop: '1rem' }}>
            <label htmlFor="sessionAllowedMinutes" className={commonStyles.label}>
              체크인 허용 시간 (분)
            </label>
            <input
              type="number"
              id="sessionAllowedMinutes"
              value={allowedMinutes}
              min="0"
              onChange={(e) => setAllowedMinutes(e.target.value)}
              placeholder="분(MM)"
              style={{ padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', width: '100%', boxSizing: 'border-box' }}
            />
          </div>

          <div className={styles.modifyButtonGroup} style={{ marginTop: '2rem' }}>
            <button
              className={`${styles.button} ${styles.submitButton}`}
              onClick={handleModifyClick}
            >
              완료
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SessionModifyModal;
