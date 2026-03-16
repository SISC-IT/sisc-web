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
      <div className={`${styles.modal} ${styles.sessionEditModal}`}>
        <div className={styles.modalHeader}>
          <h1>세션 수정하기</h1>
        </div>

        <div className={styles.sessionEditForm}>
          <div className={styles.sessionEditField}>
            <label htmlFor="sessionTitle" className={commonStyles.label}>
              세션 이름
            </label>
            <input
              className={styles.sessionEditInput}
              type="text"
              id="sessionTitle"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="기존 세션 이름입니다"
            />
          </div>

          <div className={styles.sessionEditField}>
            <label htmlFor="sessionDescription" className={commonStyles.label}>
              세션 설명
            </label>
            <input
              className={styles.sessionEditInput}
              type="text"
              id="sessionDescription"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="기존 세션 설명입니다"
            />
          </div>

          <div className={styles.sessionEditField}>
            <label htmlFor="sessionAllowedMinutes" className={commonStyles.label}>
              출석 가능 시간
            </label>
            <div className={styles.sessionEditMinutesWrap}>
              <input
                className={styles.sessionEditMinutesInput}
                type="number"
                id="sessionAllowedMinutes"
                value={allowedMinutes}
                min="0"
                onChange={(e) => setAllowedMinutes(e.target.value)}
                placeholder="30분"
              />
            </div>
          </div>

          <div className={styles.sessionEditActions}>
            <button
              className={`${styles.sessionEditActionButton} ${styles.sessionEditCancelButton}`}
              onClick={onClose}
            >
              취소
            </button>
            <button
              className={`${styles.sessionEditActionButton} ${styles.sessionEditSubmitButton}`}
              onClick={handleModifyClick}
            >
              세션 수정하기
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SessionModifyModal;
