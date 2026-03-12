import { useRef, useState } from 'react';
import styles from './Modal.module.css';
import FolderIcon from '../../assets/boardFolder.svg';
import CloseIcon from '../../assets/boardCloseIcon.svg';
import DropdownArrowIcon from '../../assets/boardSelectArrow.svg';

const Modal = ({
  title,
  setTitle,
  content,
  setContent,
  isAnonymous,
  setIsAnonymous,
  boardOptions,
  selectedBoardId,
  onBoardChange,
  selectedFiles,
  onFileChange,
  onRemoveFile,
  onSave,
  onClose,
  isSaving,
}) => {
  const fileInputRef = useRef(null);
  const [isDragOver, setIsDragOver] = useState(false);

  const clearDragState = () => {
    setIsDragOver(false);
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
      clearDragState();
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    clearDragState();

    const droppedFiles = Array.from(e.dataTransfer.files);
    onFileChange({ target: { files: droppedFiles } });
  };

  const handleFileButtonClick = () => {
    fileInputRef.current?.click();
  };

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <h2 className={styles.title}>게시글 작성</h2>
          <div className={styles.headerRight}>
            <div className={styles.sessionFieldHeader}>
              <span className={styles.sessionLabel}>하위 게시판 선택</span>
              <div className={styles.selectWrapper}>
                <select
                  className={styles.select}
                  value={selectedBoardId}
                  onChange={(e) => onBoardChange?.(e.target.value)}
                >
                  <option value="">하위 게시판 선택</option>
                  {(boardOptions || []).map((board) => (
                    <option key={board.id} value={board.id}>
                      {board.name}
                    </option>
                  ))}
                </select>
                <div className={styles.selectIcon}>
                  <img src={DropdownArrowIcon} alt="드롭다운 화살표" />
                </div>
              </div>
            </div>
            <button className={styles.closeButton} onClick={onClose}>
              <img src={CloseIcon} alt="닫기" />
            </button>
          </div>
        </div>

        <div className={styles.form}>
          <div className={styles.titleField}>
            <label className={styles.label}>제목</label>
            <input
              className={styles.input}
              type="text"
              placeholder="제목을 입력해주세요."
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          <div className={styles.contentField}>
            <div className={styles.contentHeader}>
              <label className={styles.label}>내용</label>
              <div
                className={styles.fileSection}
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
                <img src={FolderIcon} alt="폴더" />
                <span className={styles.fileText}>파일 추가</span>
              </div>
              <input
                ref={fileInputRef}
                type="file"
                id="fileUpload"
                className={styles.fileInput}
                style={{ display: 'none' }}
                multiple
                onChange={onFileChange}
              />
            </div>
            <div
              className={`${styles.contentContainer} ${isDragOver ? styles.contentContainerDragOver : ''}`}
              onDragEnter={handleDragEnter}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
            >
              {isDragOver && (
                <div className={styles.dragOverlay}>
                  파일을 업로드하려면 여기에 놓아주세요.
                </div>
              )}

              <textarea
                className={styles.textarea}
                placeholder="내용을 입력해주세요."
                value={content}
                onChange={(e) => setContent(e.target.value)}
                onDragEnter={handleDragEnter}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
              />
            </div>

            {selectedFiles && selectedFiles.length > 0 && (
              <div className={styles.fileOutsideList}>
                {selectedFiles.map((file, index) => (
                  <div key={`${file.name}-${index}`} className={styles.fileOutsideItem}>
                    <p className={styles.fileInlineName}>{file.name}</p>
                    <button
                      type="button"
                      className={styles.fileRemoveButton}
                      onClick={() => onRemoveFile?.(index)}
                      aria-label={`${file.name} 삭제`}
                    >
                      X
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

        </div>

        <div className={styles.actionRow}>
          <label className={styles.anonymousOption}>
            <input
              type="checkbox"
              checked={Boolean(isAnonymous)}
              onChange={(e) => setIsAnonymous?.(e.target.checked)}
            />
            익명
          </label>
          <button
            className={styles.saveButton}
            onClick={onSave}
            disabled={isSaving}
          >
            {isSaving ? '게시글 작성 중...' : '게시글 작성'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default Modal;
