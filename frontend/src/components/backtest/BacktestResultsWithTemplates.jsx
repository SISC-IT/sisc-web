import { useMemo } from 'react';
import {
  LineChart,
  Line,
  ResponsiveContainer,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from 'recharts';
import styles from './BacktestResultsWithTemplates.module.css';

// 간단한 mock series (서버에서 series 안 내려오는 경우 대비용)
function buildMockSeries() {
  const points = [];
  let equity = 1000000;
  for (let i = 0; i < 40; i += 1) {
    const change = (Math.random() - 0.4) * 15000;
    equity = Math.max(800000, equity + change);
    points.push({
      date: `D+${i + 1}`,
      equity: Math.round(equity),
    });
  }
  return points;
}

function formatCurrency(value, currency) {
  if (value == null) return '-';
  // 필요하면 Intl.NumberFormat으로 바꿔도 됨
  return `${value.toLocaleString()} ${currency || ''}`.trim();
}

function formatPercent(value) {
  if (value == null) return '-';
  return `${(value * 100).toFixed(2)}%`;
}

function formatNumber(value) {
  if (value == null) return '-';
  return value.toLocaleString();
}

function formatSharpe(value) {
  if (value == null) return '-';
  return value.toFixed(2);
}

function MetricCard({ label, value, sub }) {
  return (
    <div className={styles.metricCard}>
      <div className={styles.metricLabel}>{label}</div>
      <div className={styles.metricValue}>{value}</div>
      {sub ? <div className={styles.metricSub}>{sub}</div> : null}
    </div>
  );
}

function TemplateList({
  templates,
  onClickTemplate,
  onClickSaveTemplate,
  onClickEditTemplate,
  onClickDeleteTemplate,
}) {
  if (!templates || templates.length === 0) {
    return (
      <div className={styles.emptyTemplateBox}>
        아직 저장된 템플릿이 없습니다.
      </div>
    );
  }

  return (
    <ul className={styles.templateList}>
      {templates.map((tpl) => (
        <li key={tpl.id} className={styles.templateItem}>
          <button
            type="button"
            className={styles.templateMain}
            onClick={() => onClickTemplate && onClickTemplate(tpl)}
          >
            <div className={styles.templateName}>{tpl.name}</div>
            {tpl.updatedAt ? (
              <div className={styles.templateUpdatedAt}>
                최근 수정: {tpl.updatedAt}
              </div>
            ) : null}
          </button>

          <div className={styles.templateActions}>
            {onClickSaveTemplate && (
              <button
                type="button"
                className={styles.templateActionBtn}
                onClick={() => onClickSaveTemplate(tpl)}
              >
                저장
              </button>
            )}
            {onClickEditTemplate && (
              <button
                type="button"
                className={styles.templateActionBtn}
                onClick={() => onClickEditTemplate(tpl)}
              >
                수정
              </button>
            )}
            {onClickDeleteTemplate && (
              <button
                type="button"
                className={styles.templateActionBtnDanger}
                onClick={() => onClickDeleteTemplate(tpl)}
              >
                삭제
              </button>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}

export default function BacktestResultsWithTemplates(props) {
  const {
    title,
    rangeLabel,
    baseCurrency = 'KRW',
    startCapital,
    metrics = {},
    series,
    templates = [],
    onClickTemplate,
    onClickSaveTemplate,
    onClickEditTemplate,
    onClickDeleteTemplate,
  } = props;

  const hasSeries = Array.isArray(series) && series.length > 0;

  const equitySeries = useMemo(
    () => (hasSeries ? series : buildMockSeries()),
    [hasSeries, series]
  );

  const { totalReturn, maxDrawdown, sharpeRatio, avgHoldDays, tradesCount } =
    metrics;

  return (
    <div className={styles.wrapper}>
      {/* 상단 헤더/요약 */}
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>{title || '백테스트 결과'}</h1>
          {rangeLabel && <p className={styles.rangeLabel}>{rangeLabel}</p>}
        </div>
        <div className={styles.headerRight}>
          <div className={styles.capitalBox}>
            <div className={styles.capitalLabel}>초기 자본</div>
            <div className={styles.capitalValue}>
              {startCapital != null
                ? formatCurrency(startCapital, baseCurrency)
                : '-'}
            </div>
          </div>
        </div>
      </header>

      <main className={styles.main}>
        {/* 좌측: 지표 + 차트 */}
        <section className={styles.leftColumn}>
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
              value={avgHoldDays != null ? `${avgHoldDays.toFixed(1)} 일` : '-'}
            />
            <MetricCard label="거래 횟수" value={formatNumber(tradesCount)} />
          </div>

          {/* 자산 곡선 차트 */}
          <div className={styles.card}>
            <div className={styles.cardHeader}>
              <div className={styles.cardTitle}>자산 곡선</div>
              <div className={styles.cardSubTitle}>
                {hasSeries ? '실제 결과' : '샘플 데이터'}
              </div>
            </div>
            <div className={styles.chartContainer}>
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={equitySeries}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis
                    dataKey="equity"
                    tickFormatter={(v) => `${(v / 10000).toFixed(0)}만`}
                  />
                  <Tooltip
                    formatter={(value) => `${Number(value).toLocaleString()}원`}
                  />
                  <Line
                    type="monotone"
                    dataKey="equity"
                    dot={false}
                    strokeWidth={2}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        </section>

        {/* 우측: 템플릿 리스트 */}
        <aside className={styles.rightColumn}>
          <div className={styles.card}>
            <div className={styles.cardHeader}>
              <div className={styles.cardTitle}>템플릿</div>
              <div className={styles.cardSubTitle}>
                전략을 저장하고 다시 실행할 수 있습니다.
              </div>
            </div>

            <TemplateList
              templates={templates}
              onClickTemplate={onClickTemplate}
              onClickSaveTemplate={onClickSaveTemplate}
              onClickEditTemplate={onClickEditTemplate}
              onClickDeleteTemplate={onClickDeleteTemplate}
            />
          </div>
        </aside>
      </main>
    </div>
  );
}
