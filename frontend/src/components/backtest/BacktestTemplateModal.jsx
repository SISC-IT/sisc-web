import { useState } from 'react';
import styles from './BacktestTemplateModal.module.css';
import TemplateList from './TemplateList';

const BacktestTemplateModal = ({
  setTemplateModalOpen,
  templates = [],
  // API 연결 시 사용할 콜백들
  onCreateTemplate, // (name: string) => void
  onRenameTemplate, // (id: string | number, newName: string) => void
  onDeleteTemplate, // (id: string | number) => void
  onSaveToTemplate, // (id: string | number) => void
}) => {
  const [selectedId, setSelectedId] = useState(null);
  const [newName, setNewName] = useState('');

  const handleClose = () => {
    setTemplateModalOpen(false);
  };

  const handleCreate = () => {
    const trimmed = newName.trim();
    if (!trimmed) return;
    if (onCreateTemplate) {
      onCreateTemplate(trimmed);
    }
    setNewName('');
    // 새로 만든 템플릿을 선택하고 싶다면, API 응답에서 id 받아서
    // 부모에서 templates 갱신 후, selectedId를 거기서 세팅해주면 됨
  };

  const handleSave = () => {
    if (!selectedId) return;
    if (onSaveToTemplate) {
      onSaveToTemplate(selectedId);
    }
    setTemplateModalOpen(false);
  };

  return (
    <div className={styles.modalOverlay} onClick={handleClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        {/* 헤더 */}
        <div className={styles.modalHeader}>
          <div className={styles.modalHeaderLeft}>
            <h2 className={styles.modalTitle}>템플릿에 저장</h2>
            <p className={styles.modalDescription}>
              결과를 보관할 템플릿을 선택하거나, 새 템플릿을 즉시 생성하세요.
            </p>
          </div>

          <button
            type="button"
            className={styles.modalCloseBtn}
            onClick={handleClose}
          >
            ×
          </button>
        </div>

        {/* 바디 */}
        <div className={styles.modalBody}>
          <div className={styles.modalBodyGrid}>
            {/* 좌측: 새 템플릿 생성 */}
            <div className={styles.newTemplateSection}>
              <h3 className={styles.sectionTitle}>새 템플릿 생성</h3>
              <div className={styles.newTemplateForm}>
                <input
                  type="text"
                  className={styles.textInput}
                  placeholder="템플릿 이름"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                />
                <button
                  type="button"
                  className={styles.primaryButton}
                  onClick={handleCreate}
                  disabled={!newName.trim()}
                >
                  + 생성
                </button>
              </div>
            </div>

            {/* 우측: 템플릿 목록 */}
            <div className={styles.templateListSection}>
              <div className={styles.templateListHeader}>
                <span className={styles.sectionTitle}>템플릿 목록</span>
              </div>

              <div className={styles.templateListScroll}>
                <TemplateList
                  templates={templates}
                  selectedId={selectedId}
                  onSelect={setSelectedId}
                  onRename={onRenameTemplate}
                  onDelete={onDeleteTemplate}
                />
              </div>
            </div>
          </div>
        </div>

        {/* 푸터 */}
        <div className={styles.modalFooter}>
          <div className={styles.footerDescription}>
            선택한 템플릿에 현재 백테스트 결과(지표 + 자산 곡선)를 저장합니다.
          </div>

          <div className={styles.footerActions}>
            <button
              type="button"
              className={styles.secondaryButton}
              onClick={handleClose}
            >
              취소
            </button>
            <button
              type="button"
              className={styles.primaryButton}
              disabled={!selectedId}
              onClick={handleSave}
            >
              저장
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BacktestTemplateModal;
