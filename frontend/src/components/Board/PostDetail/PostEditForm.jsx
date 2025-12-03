import React from 'react';
import styles from './PostDetail.module.css';
import FileAttachmentList from './FileAttachmentList';

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
}) => {
  return (
    <>
      <div className={styles.titleWrapper}>
        <input
          className={styles.editTitleInput}
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="제목을 입력하세요"
        />
      </div>

      <div className={styles.divider} />

      <textarea
        className={styles.editContentTextarea}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="내용을 입력하세요"
        rows={10}
      />

      <div className={styles.attachments}>
        <h3 className={styles.attachmentTitle}>
          첨부 파일 ({editFiles.length + newFiles.length})
        </h3>

        <FileAttachmentList
          files={editFiles}
          isEditMode={true}
          onRemove={onRemoveExistingFile}
        />

        <FileAttachmentList
          files={newFiles}
          isEditMode={true}
          isNew={true}
          onRemove={onRemoveNewFile}
        />

        <div className={styles.fileAddSection}>
          <input
            type="file"
            id="editFileUpload"
            multiple
            onChange={onAddNewFile}
            className={styles.hiddenInput}
          />
          <button
            className={styles.addFileButton}
            onClick={() => document.getElementById('editFileUpload').click()}
          >
            파일 추가
          </button>
        </div>
      </div>

      <div className={styles.editButtons}>
        <button className={styles.saveButton} onClick={onSave}>
          저장
        </button>
        <button className={styles.cancelButton} onClick={onCancel}>
          취소
        </button>
      </div>
    </>
  );
};

export default PostEditForm;
