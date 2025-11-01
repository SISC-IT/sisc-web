import React from 'react';
import styles from './BoardActions.module.css';
import PlusIcon from '../../assets/board_plus.svg';
import DropdownArrowIcon from '../../assets/boardSelectArrow.svg';

const BoardActions = ({ sortOption, onSortChange, onWrite }) => {
  return (
    <div className={styles.boardActions}>
      <div className={styles.selectWrapper}>
        <select
          className={styles.sortSelect}
          value={sortOption}
          onChange={(e) => onSortChange(e.target.value)}
        >
          <option value="latest">최신순</option>
          <option value="oldest">오래된순</option>
          <option value="popular">인기순</option>
        </select>
        <img
          src={DropdownArrowIcon}
          alt="드롭다운"
          className={styles.selectArrow}
        />
      </div>

      <button className={styles.writeButton} onClick={onWrite}>
        <span>글 작성하기</span>
        <img src={PlusIcon} alt="작성" />
      </button>
    </div>
  );
};

export default BoardActions;
