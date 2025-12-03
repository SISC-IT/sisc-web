import React from 'react';
import styles from './CategoryTabs.module.css';

const CategoryTabs = ({ activeTab, onTabChange, tabs, onCreateSubBoard }) => {
  return (
    <div className={styles.categoryTabsRow}>
      <div className={styles.tabsLeft}>
        {tabs.map((tab) => (
          <button
            key={tab.id}
            className={`${styles.tab} ${activeTab === tab.id ? styles.active : ''}`}
            onClick={() => onTabChange(tab.id)}
          >
            {tab.name}
          </button>
        ))}
      </div>

      <button className={styles.subBoardButton} onClick={onCreateSubBoard}>
        하위 게시판 추가 +
      </button>
    </div>
  );
};

export default CategoryTabs;
