import { useEffect } from 'react';
import styles from './ActivityModal.module.css';
import AttendanceSection from './AttendanceSection';
import ActivitySection from './ActivitySection';
import PointsSection from './PointsSection';

const ActivityModal = ({ isOpen, onClose, title, kind, data }) => {
  useEffect(() => {
    if (!isOpen) return;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      className={styles.modalOverlay}
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        <div className={styles.modalHeader}>
          <h2 className={styles.modalTitle}>{title}</h2>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
            aria-label="닫기"
          >
            &times;
          </button>
        </div>

        <hr className={styles.modalDivider} />

        <div className={styles.modalBody}>
          {kind === 'attendance' && <AttendanceSection items={data.items} />}
          {kind === 'activity' && <ActivitySection items={data.items} />}
          {kind === 'points' && <PointsSection items={data.items} />}
        </div>
      </div>
    </div>
  );
};

export default ActivityModal;
