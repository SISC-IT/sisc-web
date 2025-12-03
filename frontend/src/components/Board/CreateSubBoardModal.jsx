import React from 'react';
import styles from './CreateSubBoardModal.module.css';

const CreateSubBoardModal = ({ value, onChange, onSave, onClose }) => {
  const handleSubmit = (e) => {
    e.preventDefault();
    onSave();
  };

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        <h2 className={styles.modalTitle}>하위 게시판 생성</h2>

        <form onSubmit={handleSubmit}>
          <input
            type="text"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder="하위 게시판 이름을 입력하세요"
            className={styles.input}
            autoFocus
          />

          <div className={styles.buttonGroup}>
            <button
              type="button"
              onClick={onClose}
              className={styles.cancelButton}
            >
              취소
            </button>
            <button type="submit" className={styles.saveButton}>
              생성
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateSubBoardModal;
