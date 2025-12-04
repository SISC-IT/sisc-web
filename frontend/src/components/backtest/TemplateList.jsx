import styles from './TemplateList.module.css';

const TemplateList = ({
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
    <ul className={styles.templateList}>
      {templates.map((tpl) => (
        <li key={tpl.id} className={styles.templateItem}>
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

          <div className={styles.templateActions}>
            {onClickSaveTemplate && (
              <button
                type="button"
                className={styles.templateActionBtn}
                onClick={() => onClickSaveTemplate(tpl)}
              >
                저장
              </button>
            )}
            {onClickEditTemplate && (
              <button
                type="button"
                className={styles.templateActionBtn}
                onClick={() => onClickEditTemplate(tpl)}
              >
                수정
              </button>
            )}
            {onClickDeleteTemplate && (
              <button
                type="button"
                className={styles.templateActionBtnDanger}
                onClick={() => onClickDeleteTemplate(tpl)}
              >
                삭제
              </button>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
};

export default TemplateList;
