import React from 'react';
import styles from '../../../pages/PostDetail.module.css';
import FolderIcon from '../../../assets/boardFolder.svg';

const FileAttachmentList = ({
  files,
  isEditMode = false,
  isNew = false,
  onRemove,
  onDownload,
}) => {
  if (!files || files.length === 0) return null;

  const handleDownload = (file) => {
    if (isEditMode || !onDownload) return;
    onDownload(file);
  };

  return (
    <div className={styles.attachmentList}>
      {files.map((file, index) => {
        const key = file.postAttachmentId || `new-${index}`;
        const fileName = file.originalFilename || file.name;
        const fileSize = file.size
          ? `(${(file.size / 1024).toFixed(1)} KB)`
          : '';

        return (
          <div
            key={key}
            className={`${styles.attachmentItem} ${!isEditMode ? styles.attachmentItemClickable : ''}`}
            onClick={() => handleDownload(file)}
            role={!isEditMode ? 'button' : undefined}
            tabIndex={!isEditMode ? 0 : undefined}
            onKeyDown={(e) => {
              if (isEditMode) return;
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                handleDownload(file);
              }
            }}
          >
            <img
              src={FolderIcon}
              alt="파일"
              className={`${styles.attachmentIcon} ${!isEditMode ? styles.attachmentIconButton : ''}`}
            />
            <span className={styles.attachmentName}>
              {fileName}{' '}
              {isNew && <span className={styles.newBadge}>새파일</span>}
            </span>
            {fileSize && (
              <span className={styles.attachmentSize}>{fileSize}</span>
            )}

            {isEditMode && onRemove && (
              <button
                type="button"
                className={styles.removeFileButton}
                onClick={() => onRemove(isNew ? index : file.postAttachmentId)}
              >
                ✕
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
};

export default FileAttachmentList;
