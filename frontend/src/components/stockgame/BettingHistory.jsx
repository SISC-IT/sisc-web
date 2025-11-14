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
  const itemsPerPage = 5;

  useEffect(() => {
    async function fetchData() {
      if (type === 'daily') {
        const res = await getDailyBetHistory();
        setData(res);
      } else {
        const res = await getWeeklyBetHistory();
        setData(res);
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

  if (!data) return <div>Loading...</div>;
  else if (data.length === 0) {
    return <div>베팅 내역이 없습니다.</div>;
  }

  return (
    <>
      {currentData.map((data, index) => (
        <div
          key={index}
          className={`${styles['daily-betting-card']} ${data.collect ? styles['correct-card'] : styles['incorrect-card']}`}
        >
          <button
            className={`${styles['result-icon']} ${data.collect ? styles.correct : styles.incorrect}`}
          >
            {data.collect ? 'v' : 'x'}
          </button>
          <span className={styles['date']}>{data.round.title}</span>
          <div className={styles['stock-info']}>
            <StockInfoItem label="종목" value={data.round.symbol} />
            <StockInfoItem
              label="다음 날 종가"
              value={data.round.settleClosePrice}
            />
            <StockInfoItem label="종가" value={data.round.previousClosePrice} />
            <div className={styles['stock-change']}>
              <span className={styles['change-value']}>{'->'}</span>
              <span className={styles['change-value']}>
                {getPercentChange(
                  data.round.previousClosePrice,
                  data.round.settleClosePrice
                )}
                %
              </span>
            </div>
            {/* 베팅 결과 */}
            <div className={styles['bet-result']}>
              <div className={styles['bet-point']}>
                <img src={icon1} className={styles['icon']} />+
                <span>{data.points}P</span>
              </div>
              <div className={styles['divider']} />
              <span>{data.participants}명</span>
              {data.round.resultOption === 'RISE' ? (
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
