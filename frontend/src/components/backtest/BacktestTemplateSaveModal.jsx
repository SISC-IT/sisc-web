import { useState } from 'react';
import styles from './BacktestTemplateSaveModal.module.css';
import { useBacktestTemplates } from '../../api/backtest/useBacktestTemplates';
import TemplateList from './TemplateList';
import { ImSpinner } from 'react-icons/im';
import { toast } from 'react-toastify';
import { toastConfirm } from '../../utils/toastConfirm';
import {
  patchBacktestTemplateTitle,
  deleteBacktestTemplate,
  createBacktestTemplate,
  saveBacktestRunToTemplate,
} from '../../api/backtest/useTemplateApi';

const BacktestTemplateSaveModal = ({
  setTemplateSaveModalOpen,
  runId,
  runSavePayload,
}) => {
  const [selectedId, setSelectedId] = useState(null);
  const [newName, setNewName] = useState('');

  const { templates, isLoading, error, reload } = useBacktestTemplates();

  const handleClose = () => {
    setTemplateSaveModalOpen(false);
  };

  const handleCreate = async () => {
    const trimmed = newName.trim();
    if (!trimmed) return;

    try {
      await createBacktestTemplate(trimmed);
      toast.success('템플릿이 생성되었습니다.');
      setNewName('');
      await reload();
    } catch (err) {
      console.error('템플릿 생성 실패', err);
      toast.error('템플릿 생성 중 오류가 발생했습니다.');
    }
  };

  // 선택한 템플릿에 현재 run 저장
  const handleSave = async () => {
    if (!selectedId) return;

    try {
      if (!runId) {
        toast.error('백테스트 ID를 찾을 수 없습니다.');
        return;
      }

      if (!runSavePayload) {
        toast.error('템플릿에 저장할 백테스트 정보가 없습니다.');
        return;
      }

      const { title, startDate, endDate, strategy } = runSavePayload;

      const body = {
        templateId: selectedId,
        backtestRunId: runId,
        title,
        startDate,
        endDate,
        strategy,
        backtestRunIds: [runId],
      };

      await saveBacktestRunToTemplate(runId, body);

      toast.success('현재 백테스트 결과가 템플릿에 저장되었습니다.');
      setTemplateSaveModalOpen(false);
    } catch (err) {
      console.error('템플릿 저장 실패', err);
      toast.error('템플릿 저장 중 오류가 발생했습니다.');
    }
  };

  const handleRename = async (templateId, newTitle) => {
    try {
      await patchBacktestTemplateTitle(templateId, newTitle);
      toast.success('템플릿 이름이 수정되었습니다.');
      await reload();
    } catch (error) {
      console.error('템플릿 수정 실패:', error);
      toast.error('템플릿 수정 중 오류가 발생했습니다.');
    }
  };

  const handleDelete = async (templateId) => {
    const ok = await toastConfirm('이 템플릿을 삭제하시겠습니까?', {
      title: '템플릿 삭제',
      confirmText: '삭제',
    });
    if (!ok) return;

    try {
      await deleteBacktestTemplate(templateId);
      toast.success('템플릿이 삭제되었습니다.');
      if (selectedId === templateId) {
        setSelectedId(null);
      }
      await reload();
    } catch (error) {
      console.error('템플릿 삭제 실패:', error);
      toast.error('템플릿 삭제 중 오류가 발생했습니다.');
    }
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
                {isLoading && (
                  <div className={styles.loadingState}>
                    <ImSpinner className={styles.spinner} />
                    <span>템플릿 불러오는 중...</span>
                  </div>
                )}

                {error && !isLoading && (
                  <div className={styles.errorMessage}>
                    템플릿을 불러오는 중 오류가 발생했습니다.
                  </div>
                )}

                {!isLoading && !error && (
                  <TemplateList
                    templates={templates}
                    selectedId={selectedId}
                    onSelect={setSelectedId}
                    onRename={handleRename}
                    onDelete={handleDelete}
                  />
                )}
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

export default BacktestTemplateSaveModal;
