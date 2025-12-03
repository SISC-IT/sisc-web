import React from 'react';
import styles from '../../../pages/PostDetail.module.css';
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
    <div className={styles.editFormContainer}>
      {/* 제목 */}
      <input
        type="text"
        className={styles.editTitleInput}
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="제목을 입력하세요"
      />

      {/* 구분선 */}
      <div className={styles.editDivider} />

      {/* 내용 */}
      <textarea
        className={styles.editContentTextarea}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="내용을 입력하세요"
      />

      {/* 파일 영역 */}
      <div className={styles.editFileSection}>
        <h4 className={styles.attachmentTitle}>첨부파일 관리</h4>

        {/* 기존 파일 */}
        <FileAttachmentList
          files={editFiles}
          isEditMode={true}
          onRemove={onRemoveExistingFile}
        />

        {/* 새 파일 */}
        <FileAttachmentList
          files={newFiles}
          isEditMode={true}
          isNew={true}
          onRemove={onRemoveNewFile}
        />

        {/* 파일 추가 버튼 */}
        <div className={styles.fileAddSection}>
          <input
            type="file"
            multiple
            onChange={onAddNewFile}
            style={{ display: 'none' }}
            id="file-upload"
          />
          <label
            htmlFor="file-upload"
            className={styles.addFileButton}
            style={{ display: 'block', textAlign: 'center' }}
          >
            + 파일 추가하기
          </label>
        </div>
      </div>

      {/* 버튼 */}
      <div className={styles.editButtons}>
        <button onClick={onSave} className={styles.saveButton}>
          저장
        </button>
        <button onClick={onCancel} className={styles.cancelButton}>
          취소
        </button>
      </div>
    </div>
  );
};

export default PostEditForm;
