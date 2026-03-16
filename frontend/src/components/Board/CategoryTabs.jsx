import React from 'react';
import styles from './CategoryTabs.module.css';

const CategoryTabs = ({
  activeTab,
  onTabChange,
  tabs,
  onCreateSubBoard,
  canCreateSubBoard = false,
  canDeleteSubBoard = false,
  deletingTabId = '',
  onDeleteSubBoard,
}) => {
  return (
    <div className={styles.categoryTabsRow}>
      <div className={styles.tabsLeft}>
        {tabs?.map((tab) => (
          <div
            key={tab.id}
            className={styles.tabItem}
          >
            <button
              className={`${styles.tab} ${activeTab === tab.id ? styles.active : ''}`}
              onClick={() => onTabChange(tab.id)}
            >
              {tab.name}
            </button>

            {canDeleteSubBoard && tab.id !== 'all' && (
              <button
                type="button"
                className={styles.deleteTabButton}
                onClick={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  onDeleteSubBoard?.(tab.id, tab.name);
                }}
                disabled={Boolean(deletingTabId)}
                aria-label={`${tab.name} 하위 게시판 삭제`}
                title="하위 게시판 삭제"
              >
                {deletingTabId === tab.id ? '...' : 'x'}
              </button>
            )}
          </div>
        ))}
      </div>

      {canCreateSubBoard && (
        <button
          className={styles.subBoardButton}
          onClick={onCreateSubBoard}
        >
          하위 게시판 생성
        </button>
      )}
    </div>
  );
};

export default CategoryTabs;
