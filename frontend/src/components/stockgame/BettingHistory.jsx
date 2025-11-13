import styles from './BettingHistory.module.css';
import icon1 from '../../assets/at_icon_1.png';
import StockInfoItem from './StockInfoItem';
import { dailyBettingHistory } from '../../utils/dailyBettingHistory';
import { weeklyBettingHistory } from '../../utils/weeklyBettingHistory';
import Pagination from './Pagination';
import { useState, useEffect } from 'react';

const BettingHistory = ({ type }) => {
  const formatDate = (dateStr) => {
    const [year, month, day] = dateStr.split('-');
    return `${year}년 ${month}월 ${day}일`;
  };

  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  // 주간/일간 바뀔 때 페이지 초기화
  useEffect(() => {
    setCurrentPage(1);
  }, [type]);

  const mockBetHistory =
    type === 'weekly' ? weeklyBettingHistory : dailyBettingHistory;

  const totalPages = Math.ceil(mockBetHistory.length / itemsPerPage);

  // 현재 페이지에 해당하는 데이터만 slice
  const currentData = mockBetHistory.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  return (
    <>
      {currentData.map((history, index) => (
        <div
          key={index}
          className={`${styles['daily-betting-card']} ${history.isCorrect ? styles['correct-card'] : styles['incorrect-card']}`}
        >
          <button
            className={`${styles['result-icon']} ${history.isCorrect ? styles.correct : styles.incorrect}`}
          >
            {history.isCorrect ? 'v' : 'x'}
          </button>
          <span className={styles['date']}>
            {formatDate(history.date)} 베팅
          </span>
          <div className={styles['stock-info']}>
            <StockInfoItem label="종목" value={history.symbol} />
            <StockInfoItem
              label="다음 날 종가"
              value={history.nextClosePrice}
            />
            <StockInfoItem label="종가" value={history.closePrice} />
            <div className={styles['stock-change']}>
              <span className={styles['change-value']}>{'->'}</span>
              <span className={styles['change-value']}>
                {history.changePercent}%
              </span>
            </div>
            {/* 베팅 결과 */}
            <div className={styles['bet-result']}>
              <div className={styles['bet-point']}>
                <img src={icon1} className={styles['icon']} />+
                <span>{history.points}P</span>
              </div>
              <div className={styles['divider']} />
              <span>{history.participants}명</span>
              {history.result === 'UP' ? (
                <button className={styles['up-button']}>상승 ↑</button>
              ) : (
                <button className={styles['down-button']}>하락 ↓</button>
              )}
            </div>
          </div>
        </div>
      ))}
      <div className={styles.paginationContainer}>
        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={handlePageChange}
        />
      </div>
    </>
  );
};

export default BettingHistory;
