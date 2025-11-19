import styles from './Modal.module.css';
import FolderIcon from '../../assets/boardFolder.svg';
import CloseIcon from '../../assets/boardCloseIcon.svg';
import DropdownArrowIcon from '../../assets/boardSelectArrow.svg';

const Modal = ({ title, setTitle, content, setContent, onSave, onClose }) => {
  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <h2 className={styles.title}>게시글 작성</h2>
          <button className={styles.closeButton} onClick={onClose}>
            <img src={CloseIcon} alt="닫기" />
          </button>
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
            <label className={styles.label}>내용</label>
            <div className={styles.contentContainer}>
              <div
                className={styles.fileSection}
                onClick={() => document.getElementById('fileUpload').click()}
              >
                <img src={FolderIcon} alt="폴더" />
                <span className={styles.fileText}>파일추가</span>
                <input
                  type="file"
                  id="fileUpload"
                  className={styles.fileInput}
                  style={{ display: 'none' }}
                />
              </div>
              <div className={styles.divider}></div>
              <textarea
                className={styles.textarea}
                placeholder="내용을 입력해주세요."
                value={content}
                onChange={(e) => setContent(e.target.value)}
              />
            </div>
          </div>

          <div className={styles.accessField}>
            <label className={styles.accessLabel}>접근 권한</label>
            <div className={styles.selectWrapper}>
              <select className={styles.select} defaultValue="세션선택">
                <option value="세션선택">세션선택</option>
              </select>
              <div className={styles.selectIcon}>
                <img src={DropdownArrowIcon} alt="드롭다운 화살표" />
              </div>
            </div>
          </div>
        </div>

        <button className={styles.saveButton} onClick={onSave}>
          저장
        </button>
      </div>
    </div>
  );
};

export default Modal;
