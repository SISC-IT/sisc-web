import { useState, useMemo } from 'react';
import styles from './BacktestResultsWithTemplates.module.css';
import {
  LineChart,
  Line,
  ResponsiveContainer,
  XAxis,
  YAxis,
  Tooltip,
} from 'recharts';

function formatCurrency(value, currency) {
  if (value == null) return '-';
  // 필요하면 Intl.NumberFormat으로 바꿔도 됨
  return `${value.toLocaleString()} ${currency || ''}`.trim();
}

function formatPercent(value) {
  if (value == null) return '-';
  return `${value.toFixed(2)}%`;
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
    baseCurrency = '$',
    startCapital,
    metrics = {},
    templates = [],
    onClickTemplate,
    onClickSaveTemplate,
    onClickEditTemplate,
    onClickDeleteTemplate,
  } = props;

  const [yMode, setYMode] = useState('multiple');

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
        return { day, equity, multiple };
      });
    } catch (e) {
      console.error('Failed to parse assetCurveJson', e);
      return [];
    }
  }, [assetCurveJson, startCapital]);

  return (
    <div className={styles.wrapper}>
      {/* 상단 헤더/요약 */}
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>{title || '백테스트 결과'}</h1>
          {rangeLabel && (
            <p className={styles.rangeLabel}>{rangeLabel} • 기준통화 USD</p>
          )}
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
            <MetricCard
              label="초기 자본"
              value={formatCurrency(startCapital, baseCurrency)}
            />
          </div>

          {/* 자산 곡선 차트 */}
          <div className={styles.card}>
            <div className={styles.cardHeader}>
              <div>
                <div className={styles.cardTitle}>자산 곡선</div>
                <div className={styles.cardSubTitle}>
                  {assetCurveJson
                    ? '실제 결과'
                    : '자산 곡선 데이터가 없습니다.'}
                </div>
              </div>
              {/* Y축 단위 선택 */}
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
                    formatter={(value, props) => {
                      const payload = props?.payload;
                      if (!payload) return value;

                      const equity = payload.equity;
                      const multiple = payload.multiple;

                      if (yMode === 'multiple') {
                        const multipleLabel = `${Number(multiple).toFixed(2)}x`;
                        const equityLabel =
                          equity != null
                            ? `${equity.toLocaleString()} ${baseCurrency}`
                            : '';
                        return [`${multipleLabel} (${equityLabel})`, '자산'];
                      }

                      const equityLabel =
                        equity != null
                          ? `${equity.toLocaleString()} ${baseCurrency}`
                          : '';
                      const multipleLabel =
                        multiple != null ? `${multiple.toFixed(2)}x` : '';
                      return [
                        `${equityLabel}${multipleLabel ? ` (${multipleLabel})` : ''}`,
                        '자산',
                      ];
                    }}
                    labelFormatter={(label) => `${label}일차`}
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
