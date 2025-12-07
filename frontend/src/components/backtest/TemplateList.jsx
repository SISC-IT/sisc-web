import { useState } from 'react';
import styles from './TemplateList.module.css';
import { formatKoreanDateTime } from '../../utils/dateFormat';
import { LuPencil, LuCheck, LuX } from 'react-icons/lu';
import { FaRegTrashCan } from 'react-icons/fa6';

const TemplateList = ({
  templates = [],
  selectedId,
  onSelect,
  onRename,
  onDelete,
}) => {
  const [editingId, setEditingId] = useState(null);
  const [editName, setEditName] = useState('');
  const [isSubmittingId, setIsSubmittingId] = useState(null);

  if (!templates || templates.length === 0) {
    return (
      <div className={styles.emptyTemplateBox}>
        아직 템플릿이 없습니다. 좌측에서 생성하세요.
      </div>
    );
  }

  const startEdit = (tpl) => {
    setEditingId(tpl.templateId);
    setEditName(tpl.title || '');
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditName('');
    setIsSubmittingId(null);
  };

  const confirmEdit = async (tpl) => {
    const trimmed = editName.trim();
    if (!trimmed || trimmed === tpl.title) {
      cancelEdit();
      return;
    }

    if (!onRename) {
      cancelEdit();
      return;
    }

    try {
      setIsSubmittingId(tpl.templateId);
      await onRename(tpl.templateId, trimmed);
    } finally {
      cancelEdit();
    }
  };

  const handleDeleteClick = async (tpl) => {
    if (!onDelete) return;
    try {
      setIsSubmittingId(tpl.templateId);
      await onDelete(tpl.templateId);
    } finally {
      setIsSubmittingId(null);
    }
  };

  return (
    <ul className={styles.templateList}>
      {templates.map((tpl) => {
        const isSelected = selectedId === tpl.templateId;
        const isEditing = editingId === tpl.templateId;
        const isBusy = isSubmittingId === tpl.templateId;

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
            {/* 왼쪽 영역 */}
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
                    disabled={isBusy}
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

            {/* 오른쪽 영역 */}
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
                    disabled={isBusy}
                    title="수정 완료"
                  >
                    <LuCheck size={16} />
                  </button>

                  <button
                    type="button"
                    className={styles.iconButton}
                    onClick={cancelEdit}
                    disabled={isBusy}
                    title="수정 취소"
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
                    disabled={isBusy}
                    title="이름 수정"
                  >
                    <LuPencil size={18} />
                  </button>

                  <button
                    type="button"
                    className={styles.iconButtonDanger}
                    onClick={() => handleDeleteClick(tpl)}
                    disabled={isBusy}
                    title="삭제"
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
