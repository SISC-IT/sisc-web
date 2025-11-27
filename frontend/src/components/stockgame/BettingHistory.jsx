import styles from './BettingHistory.module.css';
import icon1 from '../../assets/at_icon_1.png';
import StockInfoItem from './StockInfoItem';
import Pagination from './Pagination';
import { useState, useEffect } from 'react';
import {
  getDailyBetHistory,
  getWeeklyBetHistory,
} from '../../utils/bettingHistory';

const BettingHistory = ({ type }) => {
  const [data, setData] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const itemsPerPage = 5;

  useEffect(() => {
    async function fetchData() {
      setLoading(true);
      setError(null);
      try {
        if (type === 'daily') {
          const res = await getDailyBetHistory();
          setData(res);
        } else {
          const res = await getWeeklyBetHistory();
          setData(res);
        }
      } catch (err) {
        setError(err.message || '데이터를 불러오는데 실패했습니다.');
        setData([]);
      } finally {
        setLoading(false);
      }
    }
    fetchData();
  }, [type]);

  // 주간/일간 바뀔 때 페이지 초기화
  useEffect(() => {
    setCurrentPage(1);
  }, [type]);

  const totalPages = Math.ceil(data.length / itemsPerPage);

  // 현재 페이지에 해당하는 데이터만 slice
  const currentData = data.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  const getPercentChange = (prev, next) => {
    return (((next - prev) / prev) * 100).toFixed(2);
  };

  if (loading) return <div>Loading...</div>;
  else if (error) return <div>에러: {error}</div>;
  else if (data.length === 0) {
    return <div>베팅 내역이 없습니다.</div>;
  }

  return (
    <>
      {currentData.map((item, index) => (
        <div
          key={index}
          className={`${styles['daily-betting-card']} ${item.correct ? styles['correct-card'] : styles['incorrect-card']}`}
        >
          <button
            className={`${styles['result-icon']} ${item.correct ? styles.correct : styles.incorrect}`}
          >
            {item.correct ? 'v' : 'x'}
          </button>
          <span className={styles['date']}>{item.roundTitle}</span>
          <div className={styles['stock-info']}>
            <StockInfoItem label="종목" value={item.symbol} />
            <StockInfoItem label="다음 날 종가" value={item.settleClosePrice} />
            <StockInfoItem label="종가" value={item.previousClosePrice} />
            <div className={styles['stock-change']}>
              <span className={styles['change-value']}>{'->'}</span>
              <span className={styles['change-value']}>
                {getPercentChange(
                  item.previousClosePrice,
                  item.settleClosePrice
                )}
                %
              </span>
            </div>
            {/* 베팅 결과 */}
            <div className={styles['bet-result']}>
              <div className={styles['bet-point']}>
                <img src={icon1} className={styles['icon']} />+
                <span>{item.earnedPoints}P</span>
              </div>
              <div className={styles['divider']} />
              <span>
                {item.resultOption === 'RISE'
                  ? item.upBetCount
                  : item.downBetCount}
                명
              </span>
              {item.resultOption === 'RISE' ? (
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
