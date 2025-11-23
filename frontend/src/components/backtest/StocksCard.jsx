import { useMemo, useState } from 'react';
import { toast } from 'react-toastify';
import styles from './StocksCard.module.css';
import SectionCard from './common/SectionCard';
import useAvailableTickers from '../../api/backtest/useAvailableTickers';

const StocksCard = ({ tickers, setTickers }) => {
  const [input, setInput] = useState('');
  const { availableTickers, isLoading, error } = useAvailableTickers();

  const normalize = (value) => value.trim().toUpperCase();

  const addTicker = (rawValue) => {
    const value = normalize(rawValue ?? input);
    if (!value) return;

    // 1) 백엔드에서 내려온 종목만 허용
    if (!availableTickers.includes(value)) {
      toast.error('지원하지 않는 종목입니다.');
      setInput('');
      return;
    }

    // 2) 이미 선택된 종목이면 스킵
    if (tickers.includes(value)) {
      setInput('');
      return;
    }

    setTickers((prev) => [...prev, value]);
    setInput('');
  };

  const removeTicker = (symbol) => {
    setTickers((prev) => prev.filter((t) => t !== symbol));
  };

  const onKeyDown = (e) => {
    // IME 조합 중(한글 입력 확정 단계)에는 Enter를 무시
    if (e.nativeEvent.isComposing || e.keyCode === 229) return;

    if (e.key === 'Enter') {
      e.preventDefault();
      addTicker();
    }
  };

  // 입력값으로 자동완성 리스트 필터링
  const suggestions = useMemo(() => {
    const value = normalize(input);
    if (!value) return [];

    return availableTickers
      .filter(
        (t) =>
          t.startsWith(value) && // 앞에서부터 일치하는 심볼만
          !tickers.includes(t) // 이미 선택된 건 제외
      )
      .slice(0, 10); // 너무 많으면 상위 10개만
  }, [input, availableTickers, tickers]);

  return (
    <SectionCard title="주식" additionalInfo="*복수 선택 가능" actions={null}>
      {/* 로딩/에러 상태 표시 */}
      {isLoading && (
        <p className={styles.statusText}>티커 목록을 불러오는 중...</p>
      )}
      {error && (
        <p className={styles.errorText}>
          티커 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.
        </p>
      )}

      <div className={styles.stockRow}>
        <div className={styles.inputWrapper}>
          <input
            className={styles.input}
            placeholder="티커를 입력하거나 검색하세요. 예: AAPL"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={onKeyDown}
            disabled={isLoading || !!error}
          />

          {/* 자동완성 추천 목록 */}
          {suggestions.length > 0 && (
            <ul className={styles.suggestions}>
              {suggestions.map((symbol) => (
                <li
                  key={symbol}
                  className={styles.suggestionItem}
                  onClick={() => addTicker(symbol)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') addTicker(symbol);
                  }}
                  tabIndex={0}
                >
                  {symbol}
                </li>
              ))}
            </ul>
          )}
        </div>

        <button
          className={styles.button}
          type="button"
          onClick={() => addTicker()}
          disabled={isLoading || !!error}
        >
          Add
        </button>
      </div>

      <div className={styles.chips}>
        {tickers.map((symbol) => (
          <button
            key={symbol}
            type="button"
            className={styles.chip}
            onClick={() => removeTicker(symbol)}
          >
            {symbol} ✕
          </button>
        ))}
      </div>
    </SectionCard>
  );
};

export default StocksCard;
