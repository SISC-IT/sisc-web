function safeParseParamsJson(paramsJson) {
  if (!paramsJson) return null;
  try {
    return JSON.parse(paramsJson);
  } catch (error) {
    console.error('Failed to parse paramsJson', error);
    return null;
  }
}

export function mapBacktestApiToResultProps(apiResponse) {
  if (!apiResponse || !apiResponse.backtestRun) {
    return null;
  }

  const run = apiResponse.backtestRun;
  const metricsRaw = apiResponse.backtestRunMetricsResponse || {};
  const params = safeParseParamsJson(run.paramsJson) || {};

  const strategyParams = params.strategy ?? params;

  const title =
    run.title || (run.template && run.template.title) || '백테스트 결과';

  const rangeLabel = `${run.startDate} ~ ${run.endDate}`;

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
    startDate: run.startDate,
    endDate: run.endDate,
    baseCurrency,
    startCapital,
    metrics,
    strategy: strategyParams,
  };
}
