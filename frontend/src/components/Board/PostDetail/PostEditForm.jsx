import React, { useState } from 'react';
import styles from '../../../pages/PostDetail.module.css';
import RichTextEditor from '../../Board/RichTextEditor';
import * as boardApi from '../../../utils/boardApi';

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
  const [isDragOver, setIsDragOver] = useState(false);

  const getAttachmentIdentifier = (file) =>
    file?.postAttachmentId ||
    file?.mediaId ||
    file?.id ||
    file?.url ||
    file?.savedFilename ||
    file?.originalFilename ||
    file?.name ||
    '';

  const isImageFile = (file) => String(file?.type || '').startsWith('image/');


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
    setIsDragOver(false);

    const isDroppedOnEditor =
      e.target instanceof Element &&
      e.target.closest(`.${styles.richEditorWrapper}`);
    if (isDroppedOnEditor) {
      return;
    }

    const files = Array.from(e.dataTransfer?.files || []);
    const attachmentFiles = files.filter((file) => !isImageFile(file));
    if (attachmentFiles.length > 0) {
      onAddNewFile?.(attachmentFiles);
    }
  };

  return (
    <div className={styles.editFormContainer}>
      <label className={styles.label}>제목</label>
      <div className={styles.editTitleBox}>
        <input
          type="text"
          className={styles.editTitleInput}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="제목을 입력하세요"
        />
      </div>

      <div className={styles.editContentSection}>
        <div className={styles.editContentHeader}>
          <label className={styles.label}>내용</label>
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

          <div className={styles.richEditorWrapper}>
            <RichTextEditor
              value={content}
              onChange={setContent}
              editable={true}
              placeholder="내용을 입력하세요"
              onUploadImage={boardApi.uploadBoardImage}
              onAttachFiles={(files) => {
                const selectedFiles = Array.from(files || []);
                if (selectedFiles.length === 0) return [];
                onAddNewFile?.(selectedFiles);
                return selectedFiles;
              }}
            />
          </div>
        </div>
      </div>

      {(editFiles?.length > 0 || newFiles?.length > 0) && (
        <div className={styles.editFileSection}>
          <h4 className={styles.attachmentTitle}>
            첨부 파일 ({(editFiles?.length || 0) + (newFiles?.length || 0)})
          </h4>

          <div className={styles.editFileOutsideList}>
            {(editFiles || []).map((file, index) => {
              const identifier = getAttachmentIdentifier(file) || `existing-${index}`;
              return (
                <div
                  key={identifier}
                  className={styles.editFileOutsideItem}
                >
                  <p className={styles.editFileInlineName}>{file.originalFilename}</p>
                  <button
                    type="button"
                    className={styles.editFileRemoveButton}
                    onClick={() => onRemoveExistingFile(identifier)}
                    aria-label={`${file.originalFilename} 삭제`}
                  >
                    X
                  </button>
                </div>
              );
            })}

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
