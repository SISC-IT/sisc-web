function safeParseParamsJson(paramsJson) {
  if (!paramsJson) return {};
  try {
    const parsed = JSON.parse(paramsJson);
    if (!parsed || typeof parsed !== 'object') return {};
    return parsed;
  } catch (error) {
    console.error('Failed to parse paramsJson', error);
    return {};
  }
}

export function mapBacktestApiToResultProps(apiResponse) {
  if (!apiResponse || !apiResponse.backtestRun) {
    return null;
  }

  const run = apiResponse.backtestRun;
  const metricsRaw = apiResponse.backtestRunMetricsResponse || {};

  const params = safeParseParamsJson(run.paramsJson);

  // strategy 키가 있으면 그걸 쓰고, 없으면 params 자체를 전략 파라미터로 사용
  const strategyParams =
    (params && typeof params === 'object' && params.strategy) || params || {};

  const title =
    run.title || (run.template && run.template.title) || '백테스트 결과';

  const rangeLabel =
    run.startDate && run.endDate ? `${run.startDate} ~ ${run.endDate}` : '';

  const baseCurrency = strategyParams.baseCurrency || '$';

  let startCapital;
  if (strategyParams.initialCapital != null) {
    const n = Number(strategyParams.initialCapital);
    startCapital = Number.isFinite(n) ? n : undefined;
  }

  const metrics = {
    totalReturn:
      typeof metricsRaw.totalReturn === 'number' ? metricsRaw.totalReturn : 0,
    maxDrawdown:
      typeof metricsRaw.maxDrawdown === 'number' ? metricsRaw.maxDrawdown : 0,
    sharpeRatio:
      typeof metricsRaw.sharpeRatio === 'number' ? metricsRaw.sharpeRatio : 0,
    avgHoldDays:
      typeof metricsRaw.avgHoldDays === 'number' ? metricsRaw.avgHoldDays : 0,
    tradesCount:
      typeof metricsRaw.tradesCount === 'number' ? metricsRaw.tradesCount : 0,
    assetCurveJson:
      typeof metricsRaw.assetCurveJson === 'string'
        ? metricsRaw.assetCurveJson
        : null,
  };

  return {
    title,
    rangeLabel,
    baseCurrency,
    startCapital,
    startDate: run.startDate,
    endDate: run.endDate,
    metrics,
    // 필요하면 strategyParams도 같이 넘길 수 있음
    strategy: {
      initialCapital: startCapital,
      ticker: strategyParams.ticker,
      defaultExitDays: strategyParams.defaultExitDays,
      buyConditions: strategyParams.buyConditions || [],
      sellConditions: strategyParams.sellConditions || [],
      note: strategyParams.note || '',
    },
  };
}
