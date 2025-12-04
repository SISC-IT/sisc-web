import styles from './BacktestTemplate.module.css';
import TemplateList from './TemplateList';

const BacktestTemplate = ({
  setTemplateModalOpen,
  templates,
  onClickTemplate,
  onClickSaveTemplate,
  onClickEditTemplate,
  onClickDeleteTemplate,
}) => {
  if (!templates || templates.length === 0) {
    return (
      <div className={styles.emptyTemplateBox}>
        아직 저장된 템플릿이 없습니다.
      </div>
    );
  }

  return (
    <div className={styles.modalOverlay} onClick={handleClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.modalHeader}>
          <div className={styles.modalHeaderLeft}>
            <h2 className={styles.modalTitle}>템플릿에 저장</h2>
            <p className={styles.modalDescription}>
              결과를 보관할 템플릿을 선택하거나, 새 템플릿을 즉시 생성하세요.
            </p>
          </div>

          <button
            type="button"
            className={styles.templateMain}
            onClick={() => onClickTemplate && onClickTemplate(tpl)}
          >
            <div className={styles.templateName}>{tpl.name}</div>
            {tpl.updatedAt ? (
              <div className={styles.templateUpdatedAt}>
                최근 수정: {tpl.updatedAt}
              </div>
            ) : null}
          </button>

        <div className={styles.modalBody}>
          <TemplateList
            templates={templates}
            onClickTemplate={onClickTemplate}
            onClickSaveTemplate={onClickSaveTemplate}
            onClickEditTemplate={onClickEditTemplate}
            onClickDeleteTemplate={onClickDeleteTemplate}
          />
        </div>

        <div className={styles.modalFooter}>
          <button
            type="button"
            className={styles.closeButton}
            onClick={handleClose}
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  );
};

export default BacktestTemplate;
