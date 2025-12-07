import { useState } from 'react';
import styles from './TemplateList.module.css';
import { formatKoreanDateTime } from '../../utils/dateFormat';

import { LuPencil } from 'react-icons/lu';
import { FaRegTrashCan } from 'react-icons/fa6';
import { LuCheck, LuX } from 'react-icons/lu';

const TemplateList = ({
  templates = [],
  selectedId,
  onSelect,
  onRename,
  onDelete,
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
    setEditingId(tpl.templateId);
    setEditName(tpl.name || tpl.title || '');
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditName('');
  };

  const confirmEdit = (tpl) => {
    const trimmed = editName.trim();
    if (!trimmed) return;
    onRename?.(tpl.templateId, trimmed);
    setEditingId(null);
    setEditName('');
  };

  return (
    <ul className={styles.templateList}>
      {templates.map((tpl) => {
        const isSelected = selectedId === tpl.templateId;
        const isEditing = editingId === tpl.templateId;

        return (
          <li
            key={tpl.templateId}
            className={`${styles.templateItem} ${
              isSelected ? styles.templateItemSelected : ''
            }`}
            onClick={() => {
              if (!isEditing) onSelect?.(tpl.templateId);
            }}
          >
            <div className={styles.leftArea}>
              {isEditing ? (
                <div
                  className={styles.editRow}
                  onClick={(e) => e.stopPropagation()}
                >
                  <input
                    className={styles.editInput}
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                  />
                </div>
              ) : (
                <div className={styles.textBlock}>
                  <div className={styles.templateName}>{tpl.title}</div>
                  <div className={styles.templateUpdatedAt}>
                    업데이트:{' '}
                    {tpl.updatedDate
                      ? formatKoreanDateTime(tpl.updatedDate)
                      : '-'}
                  </div>
                </div>
              )}
            </div>

            <div
              className={styles.rightArea}
              onClick={(e) => e.stopPropagation()}
            >
              {isEditing ? (
                <>
                  <button
                    type="button"
                    className={styles.iconButton}
                    onClick={() => confirmEdit(tpl)}
                  >
                    <LuCheck size={16} />
                  </button>

                  <button
                    type="button"
                    className={styles.iconButton}
                    onClick={cancelEdit}
                  >
                    <LuX size={16} />
                  </button>
                </>
              ) : (
                <>
                  <button
                    type="button"
                    className={styles.iconButton}
                    onClick={() => startEdit(tpl)}
                  >
                    <LuPencil size={18} />
                  </button>

                  <button
                    type="button"
                    className={styles.iconButtonDanger}
                    onClick={() => onDelete?.(tpl.templateId)}
                  >
                    <FaRegTrashCan size={18} />
                  </button>
                </>
              )}
            </div>
          </li>
        );
      })}
    </ul>
  );
};

export default TemplateList;
