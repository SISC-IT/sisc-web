import { useEffect, useState } from 'react';
import styles from './StockOrder.module.css';
import { searchStocks } from '../../utils/competitionApi';
import { changeColor, signedPercent, won } from '../../utils/competitionFormat';

const StockSearch = ({
  competitionId,
  selectedTicker,
  onSelect,
  onResults,
}) => {
  const [query, setQuery] = useState('');
  // 첫 응답 전에는 null이라 '검색 결과가 없어요'가 잠깐 뜨지 않는다
  const [stocks, setStocks] = useState(null);
  const [totalCount, setTotalCount] = useState(0);
  const [error, setError] = useState('');

  // 입력이 멈추고 0.3초 뒤에 검색한다. 검색어가 바뀐 뒤 늦게 온 예전 응답은 버린다.
  useEffect(() => {
    let stale = false;
    const timer = setTimeout(() => {
      searchStocks(competitionId, { query: query.trim(), size: 100 })
        .then((res) => {
          if (stale) return;
          setStocks(res.stocks);
          setTotalCount(res.totalCount);
          setError('');
          onResults(res.stocks);
        })
        .catch((err) => !stale && setError(err.message));
    }, 300);
    return () => {
      stale = true;
      clearTimeout(timer);
    };
  }, [competitionId, query, onResults]);

  return (
    <section aria-labelledby="search-title" className={styles.search}>
      <h2 id="search-title" className={styles.sectionTitle}>
        종목 검색
      </h2>
      <label htmlFor="stock-query" className={styles.srOnly}>
        종목명 또는 코드
      </label>
      <input
        id="stock-query"
        type="search"
        placeholder="종목명 또는 코드"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        className={styles.searchInput}
      />
      {error && <p className={styles.muted}>{error}</p>}
      {!error && stocks?.length === 0 && (
        <p className={styles.muted}>검색 결과가 없어요.</p>
      )}
      <ul className={styles.stockList}>
        {stocks?.map((s) => (
          <li key={s.ticker}>
            <button
              type="button"
              aria-pressed={s.ticker === selectedTicker}
              onClick={() => onSelect(s.ticker)}
              className={`${styles.stockItem} ${s.ticker === selectedTicker ? styles.selected : ''}`}
            >
              <span className={styles.stockName}>
                <strong>{s.companyName}</strong>
                <span className={styles.ticker}>{s.ticker}</span>
              </span>
              <span className={styles.stockPrice}>
                <strong>{won(s.closePrice)}</strong>
                <span style={{ color: changeColor(s.dailyChangeRate) }}>
                  {signedPercent(s.dailyChangeRate)}
                </span>
              </span>
            </button>
          </li>
        ))}
      </ul>
      {stocks && totalCount > stocks.length && (
        <p className={styles.muted}>
          {stocks.length}개만 보여요. 나머지는 검색해 주세요.
        </p>
      )}
    </section>
  );
};

export default StockSearch;
