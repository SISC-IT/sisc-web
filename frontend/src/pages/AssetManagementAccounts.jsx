import { useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Banknote,
  BarChart3,
  RefreshCw,
  ShieldCheck,
  TrendingUp,
  WalletCards,
} from 'lucide-react';
import { toast } from 'react-toastify';
import styles from './AssetManagementAccounts.module.css';
import {
  getAssetManagementAccounts,
  getAssetManagementDailyBalance,
  getAssetManagementEvaluation,
} from '../utils/assetManagementAccountApi';

const EXCHANGE_TYPES = [
  { value: 'KRX', label: 'KRX' },
  { value: 'NXT', label: 'NXT' },
];

const todayInputValue = () => {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const toQueryDate = (inputDate) => String(inputDate || '').replaceAll('-', '');

const parseNumber = (value) => {
  if (value === null || value === undefined) return null;
  const rawValue = String(value).trim();
  if (!rawValue) return null;

  const normalizedValue = rawValue.replace(/[^0-9.-]/g, '');
  if (!normalizedValue || normalizedValue === '-' || normalizedValue === '.') {
    return null;
  }

  const numeric = Number(normalizedValue);
  return Number.isFinite(numeric) ? numeric : null;
};

const formatWon = (value) => {
  const numeric = parseNumber(value);
  if (numeric === null) return value || '-';
  return `${Math.round(numeric).toLocaleString()}원`;
};

const formatRate = (value) => {
  if (value === null || value === undefined || value === '') return '-';
  const text = String(value).trim();
  if (text.includes('%')) return text;
  const numeric = parseNumber(text);
  if (numeric === null) return text;
  return `${numeric.toFixed(2)}%`;
};

const formatQuantity = (value) => {
  const numeric = parseNumber(value);
  if (numeric === null) return value || '-';

  return Number.isInteger(numeric)
    ? numeric.toLocaleString()
    : numeric.toLocaleString(undefined, { maximumFractionDigits: 4 });
};

const formatPlain = (value) => value || '-';

const getToneClass = (value) => {
  const numeric = parseNumber(value);
  if (numeric > 0) return styles.positive;
  if (numeric < 0) return styles.negative;
  return styles.neutral;
};

const MetricCard = ({ icon: Icon, label, value, subValue, tone }) => (
  <section className={styles.metricCard}>
    <div className={styles.metricIcon} aria-hidden="true">
      <Icon size={20} strokeWidth={2} />
    </div>
    <div>
      <p>{label}</p>
      <strong className={tone || undefined}>{value}</strong>
      {subValue && <span>{subValue}</span>}
    </div>
  </section>
);

const StatusBlock = ({ title, message }) => (
  <div className={styles.statusBlock}>
    <AlertTriangle size={20} />
    <div>
      <strong>{title}</strong>
      <p>{message}</p>
    </div>
  </div>
);

const EmptyRows = ({ colSpan, message }) => (
  <tr>
    <td className={styles.emptyCell} colSpan={colSpan}>
      {message}
    </td>
  </tr>
);

function AssetManagementAccounts() {
  const [date, setDate] = useState(todayInputValue);
  const [exchangeType, setExchangeType] = useState('KRX');
  const [accounts, setAccounts] = useState([]);
  const [dailyBalance, setDailyBalance] = useState(null);
  const [evaluation, setEvaluation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const queryDate = useMemo(() => toQueryDate(date), [date]);
  const dailyStocks = Array.isArray(dailyBalance?.dailyBalanceRate)
    ? dailyBalance.dailyBalanceRate
    : [];
  const holdings = Array.isArray(evaluation?.stockEvaluations)
    ? evaluation.stockEvaluations
    : [];

  const loadAccountData = async ({ silent = false } = {}) => {
    if (!silent) setLoading(true);
    setRefreshing(silent);
    setErrorMessage('');

    try {
      const [accountList, dailyInfo, evaluationInfo] = await Promise.all([
        getAssetManagementAccounts(),
        getAssetManagementDailyBalance(queryDate),
        getAssetManagementEvaluation(exchangeType),
      ]);

      setAccounts(Array.isArray(accountList) ? accountList : []);
      setDailyBalance(dailyInfo);
      setEvaluation(evaluationInfo);
    } catch (error) {
      const message =
        error?.status === 403
          ? '자산운용팀 계좌 조회 권한이 없습니다.'
          : error?.message || '계좌 정보를 불러오지 못했습니다.';
      setErrorMessage(message);
      if (silent) toast.error(message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadAccountData();
  }, []);

  const handleSubmit = (event) => {
    event.preventDefault();
    loadAccountData();
  };

  const handleRefresh = () => {
    loadAccountData({ silent: true });
  };

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div>
          <div className={styles.eyebrow}>
            <ShieldCheck size={16} />
            자산운용팀 전용
          </div>
          <h1>자산운용팀 계좌 조회</h1>
          <p>키움증권 계좌 잔고와 보유 종목 현황</p>
        </div>

        <form className={styles.controls} onSubmit={handleSubmit}>
          <label>
            조회일
            <input
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
            />
          </label>
          <label>
            거래소
            <select
              value={exchangeType}
              onChange={(event) => setExchangeType(event.target.value)}
            >
              {EXCHANGE_TYPES.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className={styles.primaryButton}>
            조회
          </button>
          <button
            type="button"
            className={styles.iconButton}
            onClick={handleRefresh}
            disabled={refreshing}
            aria-label="새로고침"
            title="새로고침"
          >
            <RefreshCw size={18} className={refreshing ? styles.spinning : ''} />
          </button>
        </form>
      </header>

      {loading && <div className={styles.loading}>계좌 정보를 불러오는 중...</div>}

      {!loading && errorMessage && (
        <StatusBlock title="조회 실패" message={errorMessage} />
      )}

      {!loading && !errorMessage && (
        <>
          <section className={styles.accountStrip}>
            <div>
              <span>조회 계좌</span>
              <strong>{accounts.length.toLocaleString()}개</strong>
            </div>
            <div className={styles.accountList}>
              {accounts.length > 0 ? (
                accounts.map((account) => (
                  <span key={account.maskedAccountNumber}>
                    {account.maskedAccountNumber}
                  </span>
                ))
              ) : (
                <span>계좌 없음</span>
              )}
            </div>
          </section>

          <section className={styles.metricsGrid}>
            <MetricCard
              icon={WalletCards}
              label="예탁자산평가액"
              value={formatWon(evaluation?.assetEvaluationAmount)}
              subValue={`예수금 ${formatWon(evaluation?.deposit)}`}
            />
            <MetricCard
              icon={TrendingUp}
              label="누적 수익률"
              value={formatRate(evaluation?.accumulatedProfitRate)}
              subValue={formatWon(evaluation?.accumulatedProfitLoss)}
              tone={getToneClass(evaluation?.accumulatedProfitRate)}
            />
            <MetricCard
              icon={Banknote}
              label="일별 평가손익"
              value={formatWon(dailyBalance?.totalEvaluationProfit)}
              subValue={formatRate(dailyBalance?.totalProfitRate)}
              tone={getToneClass(dailyBalance?.totalEvaluationProfit)}
            />
            <MetricCard
              icon={BarChart3}
              label="총 평가금액"
              value={formatWon(dailyBalance?.totalEvaluationAmount)}
              subValue={`매입 ${formatWon(dailyBalance?.totalBuyAmount)}`}
            />
          </section>

          <section className={styles.dashboardGrid}>
            <div className={styles.panel}>
              <div className={styles.panelHeader}>
                <div>
                  <h2>일별 잔고 수익률</h2>
                  <p>{dailyBalance?.date || queryDate}</p>
                </div>
                <strong className={getToneClass(dailyBalance?.totalProfitRate)}>
                  {formatRate(dailyBalance?.totalProfitRate)}
                </strong>
              </div>
              <div className={styles.tableWrap}>
                <table>
                  <thead>
                    <tr>
                      <th>종목</th>
                      <th>수량</th>
                      <th>평가금액</th>
                      <th>손익률</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dailyStocks.length > 0 ? (
                      dailyStocks.map((stock) => (
                        <tr key={`${stock.stockCode}-${stock.stockName}`}>
                          <td>
                            <strong>{formatPlain(stock.stockName)}</strong>
                            <span>{formatPlain(stock.stockCode)}</span>
                          </td>
                          <td>{formatQuantity(stock.remainderQuantity)}</td>
                          <td>{formatWon(stock.evaluationAmount)}</td>
                          <td className={getToneClass(stock.profitRate)}>
                            {formatRate(stock.profitRate)}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <EmptyRows colSpan={4} message="표시할 잔고 데이터가 없습니다." />
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            <div className={styles.panel}>
              <div className={styles.panelHeader}>
                <div>
                  <h2>계좌 평가 현황</h2>
                  <p>{exchangeType}</p>
                </div>
                <strong>{formatWon(evaluation?.totalEstimatedAmount)}</strong>
              </div>
              <div className={styles.tableWrap}>
                <table className={styles.evaluationTable}>
                  <thead>
                    <tr>
                      <th>종목</th>
                      <th>보유</th>
                      <th>평균단가</th>
                      <th>현재가</th>
                      <th>평가금액</th>
                      <th>손익</th>
                    </tr>
                  </thead>
                  <tbody>
                    {holdings.length > 0 ? (
                      holdings.map((stock) => (
                        <tr key={`${stock.stockCode}-${stock.stockName}`}>
                          <td>
                            <strong>{formatPlain(stock.stockName)}</strong>
                            <span>{formatPlain(stock.stockCode)}</span>
                          </td>
                          <td>{formatQuantity(stock.remainingQuantity)}</td>
                          <td>{formatWon(stock.averagePrice)}</td>
                          <td>{formatWon(stock.currentPrice)}</td>
                          <td>{formatWon(stock.evaluationAmount)}</td>
                          <td className={getToneClass(stock.profitLossAmount)}>
                            {formatWon(stock.profitLossAmount)}
                            <span>{formatRate(stock.profitLossRate)}</span>
                          </td>
                        </tr>
                      ))
                    ) : (
                      <EmptyRows colSpan={6} message="표시할 평가 데이터가 없습니다." />
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        </>
      )}
    </div>
  );
}

export default AssetManagementAccounts;
