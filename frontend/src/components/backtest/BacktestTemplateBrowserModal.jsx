import { useState, useEffect } from 'react';
import styles from './BacktestTemplateModal.module.css';
import { useBacktestTemplates } from '../../api/backtest/useBacktestTemplates';
import { ImSpinner } from 'react-icons/im';
import { toast } from 'react-toastify';
import { fetchBacktestTemplateDetail } from '../../api/backtest/useTemplateApi';

const BacktestTemplateBrowserModal = ({ onClose, onOpenRun }) => {
  const { templates, isLoading, error } = useBacktestTemplates();

  const [selectedTemplateId, setSelectedTemplateId] = useState(null);
  const [selectedTemplate, setSelectedTemplate] = useState(null);

  const [runs, setRuns] = useState([]);
  const [isRunsLoading, setRunsLoading] = useState(false);
  const [selectedRunId, setSelectedRunId] = useState(null);

  useEffect(() => {
    if (!selectedTemplateId) return;

    async function loadRuns() {
      try {
        setRunsLoading(true);
        setSelectedRunId(null);

        const { template, runs } =
          await fetchBacktestTemplateDetail(selectedTemplateId);

        setSelectedTemplate(template);
        setRuns(runs);
      } catch (err) {
        console.error('Failed to load runs for template', err);
        toast.error(
          '템플릿의 백테스트 목록을 불러오는 중 오류가 발생했습니다.'
        );
        setSelectedTemplate(null);
        setRuns([]);
      } finally {
        setRunsLoading(false);
      }
    }

    loadRuns();
  }, [selectedTemplateId]);

  const handleOpen = () => {
    if (!selectedRunId) return;
    onOpenRun?.(selectedRunId);
  };

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        {/* 헤더 */}
        <div className={styles.modalHeader}>
          <div className={styles.modalHeaderLeft}>
            <h2 className={styles.modalTitle}>템플릿에서 결과 불러오기</h2>
            <p className={styles.modalDescription}>
              좌측에서 템플릿을 선택하고, 우측에서 불러올 백테스트 결과를
              선택하세요.
            </p>
          </div>

          <button
            type="button"
            className={styles.modalCloseBtn}
            onClick={onClose}
          >
            ×
          </button>
        </div>

        {/* 바디: 좌측 템플릿 / 우측 run 리스트 */}
        <div className={styles.modalBody}>
          <div className={styles.modalBodyGrid}>
            {/* 왼쪽: 템플릿 목록 */}
            <div className={styles.newTemplateSection}>
              <h3 className={styles.sectionTitle}>템플릿 목록</h3>
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

                {!isLoading && !error && templates.length === 0 && (
                  <div className={styles.emptyTemplateBox}>
                    템플릿이 없습니다.
                  </div>
                )}

                {!isLoading && !error && templates.length > 0 && (
                  <ul className={styles.templateList}>
                    {templates.map((tpl) => {
                      const isSelected = selectedTemplateId === tpl.templateId;

                      return (
                        <li
                          key={tpl.templateId}
                          className={`${styles.templateItem} ${
                            isSelected ? styles.templateItemSelected : ''
                          }`}
                          onClick={() => setSelectedTemplateId(tpl.templateId)}
                        >
                          <div className={styles.textBlock}>
                            <div className={styles.templateName}>
                              {tpl.title}
                            </div>
                            {tpl.updatedDate && (
                              <div className={styles.templateUpdatedAt}>
                                최근 수정: {tpl.updatedDate.slice(0, 10)}
                              </div>
                            )}
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            </div>

            {/* 오른쪽: 선택된 템플릿의 run 리스트 */}
            <div className={styles.templateListSection}>
              <div className={styles.templateListHeader}>
                <span className={styles.sectionTitle}>
                  {selectedTemplate
                    ? `${selectedTemplate.title}에 저장된 백테스트 결과 `
                    : '저장된 백테스트 결과'}
                </span>
              </div>

              <div className={styles.templateListScroll}>
                {!selectedTemplateId && (
                  <div className={styles.emptyTemplateBox}>
                    좌측에서 템플릿을 먼저 선택하세요.
                  </div>
                )}

                {selectedTemplateId && isRunsLoading && (
                  <div className={styles.loadingState}>
                    <ImSpinner className={styles.spinner} />
                    <span>백테스트 결과 불러오는 중...</span>
                  </div>
                )}

                {selectedTemplateId && !isRunsLoading && runs.length === 0 && (
                  <div className={styles.emptyTemplateBox}>
                    이 템플릿에 저장된 백테스트 결과가 없습니다.
                  </div>
                )}

                {selectedTemplateId && !isRunsLoading && runs.length > 0 && (
                  <ul className={styles.templateList}>
                    {runs.map((run) => {
                      const isSelectedRun = selectedRunId === run.id;
                      return (
                        <li
                          key={run.id}
                          className={`${styles.templateItem} ${
                            isSelectedRun ? styles.templateItemSelected : ''
                          }`}
                          onClick={() => setSelectedRunId(run.id)}
                        >
                          <div className={styles.textBlock}>
                            <div className={styles.templateName}>
                              {run.title}
                            </div>
                            <div className={styles.templateUpdatedAt}>
                              기간: {run.startDate} ~ {run.endDate}
                            </div>
                            {/* 필요하면 status, 수익률 등 추가 가능 */}
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* 푸터: 열기 / 닫기 */}
        <div className={styles.modalFooter}>
          <div className={styles.footerDescription}>
            선택한 백테스트 결과를 현재 화면에 불러옵니다.
          </div>

          <div className={styles.footerActions}>
            <button
              type="button"
              className={styles.secondaryButton}
              onClick={onClose}
            >
              닫기
            </button>
            <button
              type="button"
              className={styles.primaryButton}
              disabled={!selectedRunId}
              onClick={handleOpen}
            >
              열기
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BacktestTemplateBrowserModal;
