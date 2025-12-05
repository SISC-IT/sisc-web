import { useState } from 'react';
import styles from './TemplateList.module.css';

const TemplateList = ({
  templates = [],
  selectedId,
  onSelect, // (id) => void
  onRename, // (id, newName) => void
  onDelete, // (id) => void
}) => {
  const [editingId, setEditingId] = useState(null);
  const [editName, setEditName] = useState('');

  if (!templates || templates.length === 0) {
    return (
      <div className={styles.emptyTemplateBox}>
        아직 템플릿이 없습니다. 좌측에서 생성하세요.
      </div>
    );
  }

  const startEdit = (tpl) => {
    setEditingId(tpl.id);
    setEditName(tpl.name || '');
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditName('');
  };

  const confirmEdit = (tpl) => {
    const trimmed = editName.trim();
    if (!trimmed) return;
    if (onRename) {
      onRename(tpl.id, trimmed);
    }
    setEditingId(null);
    setEditName('');
  };

  return (
    <ul className={styles.templateList}>
      {templates.map((tpl) => {
        const isSelected = selectedId === tpl.id;
        const isEditing = editingId === tpl.id;

        return (
          <li
            key={tpl.id}
            className={`${styles.templateItem} ${
              isSelected ? styles.templateItemSelected : ''
            }`}
          >
            {/* 메인 영역: 선택 / 이름 편집 */}
            <button
              type="button"
              className={styles.templateMain}
              onClick={() => onSelect && onSelect(tpl.id)}
            >
              {isEditing ? (
                <div className={styles.editRow}>
                  <input
                    className={styles.editInput}
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                  />
                  <button
                    type="button"
                    className={styles.iconButton}
                    onClick={() => confirmEdit(tpl)}
                  >
                    ✓
                  </button>
                  <button
                    type="button"
                    className={styles.iconButton}
                    onClick={cancelEdit}
                  >
                    ✕
                  </button>
                </div>
              ) : (
                <div className={styles.templateText}>
                  <div className={styles.templateName}>{tpl.name}</div>
                  <div className={styles.templateUpdatedAt}>
                    업데이트: {tpl.updatedAt ? tpl.updatedAt : '-'}
                  </div>
                </div>
              )}
            </button>

            {/* 우측 액션 버튼들 */}
            {!isEditing && (
              <div className={styles.templateActions}>
                <button
                  type="button"
                  className={styles.templateActionBtn}
                  onClick={() => startEdit(tpl)}
                >
                  수정
                </button>
                <button
                  type="button"
                  className={styles.templateActionBtnDanger}
                  onClick={() => onDelete && onDelete(tpl.id)}
                >
                  삭제
                </button>
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
};

export default TemplateList;
