import React, { useRef, useState } from 'react';
import styles from '../../../pages/PostDetail.module.css';
import FolderIcon from '../../../assets/boardFolder.svg';

const PostEditForm = ({
  title,
  setTitle,
  content,
  setContent,
  editFiles,
  newFiles,
  onRemoveExistingFile,
  onRemoveNewFile,
  onAddNewFile,
  onSave,
  onCancel,
  isSaving = false,
}) => {
  const fileInputRef = useRef(null);
  const [isDragOver, setIsDragOver] = useState(false);

  const handleFileButtonClick = () => {
    fileInputRef.current?.click();
  };

  const handleDragEnter = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (!e.currentTarget.contains(e.relatedTarget)) {
      setIsDragOver(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);

    const droppedFiles = Array.from(e.dataTransfer.files || []);
    if (droppedFiles.length > 0) {
      onAddNewFile({ target: { files: droppedFiles } });
    }
  };

  return (
    <div className={styles.editFormContainer}>
      <label className={styles.label}>제목</label>
      <input
        type="text"
        className={styles.editTitleInput}
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="제목을 입력하세요"
      />

      <div className={styles.editContentSection}>
        <div className={styles.editContentHeader}>
          <label className={styles.label}>내용</label>
          <div
            className={styles.editFileAddButton}
            onClick={handleFileButtonClick}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                handleFileButtonClick();
              }
            }}
          >
            <img src={FolderIcon} alt="파일" />
            <span className={styles.editFileText}>파일 추가</span>
          </div>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            onChange={onAddNewFile}
            style={{ display: 'none' }}
          />
        </div>

        <div
          className={`${styles.editContentContainer} ${isDragOver ? styles.editContentContainerDragOver : ''}`}
          onDragEnter={handleDragEnter}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
        >
          {isDragOver && (
            <div className={styles.editDragOverlay}>
              파일을 업로드하려면 여기에 놓아주세요.
            </div>
          )}

          <textarea
            className={styles.editContentTextarea}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="내용을 입력하세요"
            onDragEnter={handleDragEnter}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
          />
        </div>
      </div>

      {(editFiles?.length > 0 || newFiles?.length > 0) && (
        <div className={styles.editFileSection}>
          <h4 className={styles.attachmentTitle}>
            첨부 파일 ({(editFiles?.length || 0) + (newFiles?.length || 0)})
          </h4>

          <div className={styles.editFileOutsideList}>
            {(editFiles || []).map((file) => (
              <div
                key={file.postAttachmentId}
                className={styles.editFileOutsideItem}
              >
                <p className={styles.editFileInlineName}>{file.originalFilename}</p>
                <button
                  type="button"
                  className={styles.editFileRemoveButton}
                  onClick={() => onRemoveExistingFile(file.postAttachmentId)}
                  aria-label={`${file.originalFilename} 삭제`}
                >
                  X
                </button>
              </div>
            ))}

            {(newFiles || []).map((file, index) => (
              <div
                key={`new-${file.name}-${index}`}
                className={styles.editFileOutsideItem}
              >
                <p className={styles.editFileInlineName}>
                  {file.name}
                  <span className={styles.newBadge}>새파일</span>
                </p>
                <button
                  type="button"
                  className={styles.editFileRemoveButton}
                  onClick={() => onRemoveNewFile(index)}
                  aria-label={`${file.name} 삭제`}
                >
                  X
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className={styles.editButtons}>
        <button
          onClick={onSave}
          className={styles.saveButton}
          disabled={isSaving}
        >
          {isSaving ? '수정 중...' : '게시글 수정'}
        </button>
        <button
          onClick={onCancel}
          className={styles.cancelButton}
          disabled={isSaving}
        >
          취소
        </button>
      </div>
    </div>
  );
};

export default PostEditForm;
