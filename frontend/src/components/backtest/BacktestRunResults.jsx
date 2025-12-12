import { useState, useMemo } from 'react';
import styles from './BacktestRunResults.module.css';
import BacktestTemplateSaveModal from './BacktestTemplateSaveModal';
import BacktestTemplateListModal from './BacktestTemplateListModal';
import {
  formatCurrency,
  formatPercent,
  formatNumber,
  formatSharpe,
  addDaysToDate,
  formatDateYYYYMMDD,
  formatTwoDecimal,
  formatCurrencyTwoDecimal,
  downloadEquityCsv,
} from '../../utils/backtestingFormat';
import {
  LineChart,
  Line,
  ResponsiveContainer,
  XAxis,
  YAxis,
  Tooltip,
} from 'recharts';

export function MetricCard({ label, value, sub }) {
  return (
    <div className={styles.metricCard}>
      <div className={styles.metricLabel}>{label}</div>
      <div className={styles.metricSub}>{sub}</div>
      <div className={styles.metricValue}>{value}</div>
    </div>
  );
}

const BacktestRunResults = (props) => {
  const {
    title,
    rangeLabel,
    baseCurrency = '$',
    startCapital,
    metrics = {},
    startDate,
    endDate,
    strategy,
    runId,
    onOpenSavedRun,
  } = props;

  const [yMode, setYMode] = useState('multiple');
  const [isTemplateSaveModalOpen, setTemplateSaveModalOpen] = useState(false);
  const [isTemplateListModalOpen, setTemplateListModalOpen] = useState(false);

  const {
    totalReturn,
    maxDrawdown,
    sharpeRatio,
    avgHoldDays,
    tradesCount,
    assetCurveJson,
  } = metrics;

  const equitySeries = useMemo(() => {
    if (!assetCurveJson) return [];

    try {
      const arr = JSON.parse(assetCurveJson);
      if (!Array.isArray(arr)) return [];

      return arr.map((v, idx) => {
        const equity = Number(v);
        const day = idx + 1;
        const sc = Number(startCapital);
        const multiple = Number.isFinite(sc) && sc !== 0 ? equity / sc : null;

        const dateObj =
          typeof startDate === 'string' ? addDaysToDate(startDate, idx) : null;
        const dateLabel = dateObj ? formatDateYYYYMMDD(dateObj) : null;
        return { day, equity, multiple, date: dateLabel };
      });
    } catch (e) {
      console.error('Failed to parse assetCurveJson', e);
      return [];
    }
  }, [assetCurveJson, startCapital, startDate]);

  const handleDownloadCsv = () => {
    const safeTitle = (title || 'backtest-result').replace(/\s+/g, '-');
    downloadEquityCsv(`${safeTitle}-equity.csv`, equitySeries);
  };

  return (
    <div className={styles.wrapper}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>{title || '백테스트 결과'}</h1>
          {rangeLabel && (
            <p className={styles.rangeLabel}>
              {rangeLabel} • 기준통화{' '}
              {baseCurrency === '$' ? 'USD' : baseCurrency}
            </p>
          )}
        </div>

        <div className={styles.headerActions}>
          <button
            type="button"
            className={styles.secondaryButton}
            onClick={handleDownloadCsv}
          >
            CSV 내보내기
          </button>
          <button
            type="button"
            className={styles.secondaryButton}
            onClick={() => setTemplateListModalOpen(true)}
          >
            템플릿 목록 열기
          </button>
          <button
            type="button"
            className={styles.primaryButton}
            onClick={() => setTemplateSaveModalOpen(true)}
          >
            템플릿에 저장
          </button>
        </div>
      </header>

      <main className={styles.main}>
        {/* 지표 카드 */}
        <div className={styles.metricsGrid}>
          <MetricCard
            label="누적 수익률"
            value={formatPercent(totalReturn)}
            sub="Total Return"
          />
          <MetricCard
            label="최대 낙폭"
            value={formatPercent(maxDrawdown)}
            sub="Max Drawdown"
          />
          <MetricCard
            label="샤프 지수"
            value={formatSharpe(sharpeRatio)}
            sub="Sharpe Ratio"
          />
          <MetricCard
            label="평균 보유일수"
            value={
              avgHoldDays != null && typeof avgHoldDays === 'number'
                ? `${avgHoldDays.toFixed(1)} 일`
                : '-'
            }
            sub="Average Hold Days"
          />
          <MetricCard
            label="거래 횟수"
            value={formatNumber(tradesCount)}
            sub="Trades Count"
          />
          <MetricCard
            label="초기 자본"
            value={formatCurrency(startCapital, baseCurrency)}
            sub="Initial Capital"
          />
        </div>

        {/* 자산 곡선 차트 */}
        <div className={styles.card}>
          <div className={styles.cardHeader}>
            <div>
              <div className={styles.cardTitle}>자산 곡선</div>
              <div className={styles.cardSubTitle}>
                {assetCurveJson ? '실제 결과' : '자산 곡선 데이터가 없습니다.'}
              </div>
            </div>

            <div>
              <select
                value={yMode}
                onChange={(e) => setYMode(e.target.value)}
                className={styles.yModeSelect}
              >
                <option value="multiple">배율 (초기 자본 대비)</option>
                <option value="equity">자산 값 ({baseCurrency})</option>
              </select>
            </div>
          </div>
          <div className={styles.chartContainer}>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={equitySeries}>
                <XAxis dataKey="day" tickFormatter={(v) => `${v}일`} />
                <YAxis
                  dataKey={yMode === 'multiple' ? 'multiple' : 'equity'}
                  tickFormatter={(v) => {
                    if (v == null) return '';
                    if (yMode === 'multiple') {
                      return `${v.toFixed(2)}x`;
                    }
                    return `${v.toLocaleString()} ${baseCurrency}`;
                  }}
                  domain={['auto', 'auto']}
                />
                <Tooltip
                  formatter={(value, name, props) => {
                    const payload = props?.payload;
                    if (!payload) return value;

                    const equity = payload.equity;
                    const multiple = payload.multiple;

                    if (yMode === 'multiple') {
                      const m = multiple ?? value;
                      const multipleLabel =
                        m != null ? `${formatTwoDecimal(m)}x` : '';
                      const equityLabel =
                        equity != null
                          ? formatCurrencyTwoDecimal(equity, baseCurrency)
                          : '';
                      return [
                        `${multipleLabel}${equityLabel ? ` (${equityLabel})` : ''}`,
                        '자산',
                      ];
                    }

                    const e = equity ?? value;
                    const equityLabel =
                      e != null
                        ? formatCurrencyTwoDecimal(e, baseCurrency)
                        : '';
                    const multipleLabel =
                      multiple != null ? `${formatTwoDecimal(multiple)}x` : '';

                    return [
                      `${equityLabel}${multipleLabel ? ` (${multipleLabel})` : ''}`,
                      '자산',
                    ];
                  }}
                  labelFormatter={(label, payload) => {
                    const first = payload && payload[0] && payload[0].payload;
                    const dateLabel = first?.date;
                    if (dateLabel) return dateLabel;
                    return `${label}일차`;
                  }}
                />

                <Line
                  type="monotone"
                  dataKey={yMode === 'multiple' ? 'multiple' : 'equity'}
                  dot={false}
                  strokeWidth={2}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      </main>

      {/* 템플릿 모달: runId만 내려줌 */}
      {isTemplateSaveModalOpen && (
        <BacktestTemplateSaveModal
          setTemplateSaveModalOpen={setTemplateSaveModalOpen}
          runId={runId}
          runSavePayload={{
            title,
            startDate,
            endDate,
            strategy,
          }}
        />
      )}

      {/* 템플릿 목록에서 저장된 run 불러오는 모달 */}
      {isTemplateListModalOpen && (
        <BacktestTemplateListModal
          onClose={() => setTemplateListModalOpen(false)}
          onOpenRun={(selectedRunId) => {
            onOpenSavedRun?.(selectedRunId);
            setTemplateListModalOpen(false);
          }}
        />
      )}
    </div>
  );
};

export default BacktestRunResults;
