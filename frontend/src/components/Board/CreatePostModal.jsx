import React, { useState } from 'react';
import styles from './CreatePostModal.module.css';

function CreatePostModal({ isOpen, onClose, onSubmit }) {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [permission, setPermission] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();

    if (!permission) {
      alert('접근 권한을 선택해주세요.');
      return;
    }

    onSubmit({ title, content, permission });
    setTitle('');
    setContent('');
    setPermission('');
  };

  if (!isOpen) return null;

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        <div className={styles.modalHeader}>
          <h2>게시글 작성</h2>
          <button className={styles.closeButton} onClick={onClose}>
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.fieldGroup}>
            <label className={styles.fieldLabel}>제목</label>
            <input
              type="text"
              placeholder="제목을 입력해주세요"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className={styles.titleInput}
            />
          </div>

          <div className={styles.fieldGroup}>
            <label className={styles.fieldLabel}>내용</label>
            <div className={styles.contentContainer}>
              <div className={styles.toolbar}>
                <span className={styles.toolbarIcon}>📁</span>
                <span className={styles.toolbarText}>파일 추가</span>
              </div>
              <textarea
                placeholder="내용을 입력해주세요."
                value={content}
                onChange={(e) => setContent(e.target.value)}
                className={styles.contentTextarea}
                rows={6}
              />
            </div>
          </div>

          <div className={styles.fieldGroup}>
            <label className={styles.fieldLabel}>접근 권한</label>
            <select
              value={permission}
              onChange={(e) => setPermission(e.target.value)}
              className={`${styles.permissionSelect} ${!permission ? styles.placeholder : ''}`}
            >
              <option value="" disabled>
                세션선택
              </option>
              <option value="전체공개">전체공개</option>
              <option value="회원공개">회원공개</option>
              <option value="관리자만">관리자만</option>
            </select>
          </div>
        </form>
      </div>
    </div>
  );
}

export default CreatePostModal;
