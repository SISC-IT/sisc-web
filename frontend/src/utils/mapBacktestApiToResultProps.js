// 서버에서 내려준 backtest 응답을
// BacktestResultsWithTemplates에 넘길 props 형태로 변환하는 순수 함수

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
  const params = safeParseParamsJson(run.paramsJson);

  // 백엔드가 params.strategy 안에 넣을 수도 있고, 지금처럼 루트에 바로 넣을 수도 있으니 둘 다 대응
  const strategyParams = params.strategy ?? params;

  // 1) 제목: run.title이 있으면 우선 사용, 없으면 템플릿 title 사용
  const title =
    run.title || (run.template && run.template.title) || '백테스트 결과';

  // 2) 기간 라벨: "YYYY-MM-DD ~ YYYY-MM-DD"
  const rangeLabel = `${run.startDate} ~ ${run.endDate}`;

  // 3) 통화 / 초기자본
  const baseCurrency = strategyParams.baseCurrency || '$';

  let startCapital;
  if (strategyParams.initialCapital != null) {
    const n = Number(strategyParams.initialCapital);
    startCapital = Number.isFinite(n) ? n : undefined;
  } else {
    startCapital = undefined;
  }

  // 4) 지표(메트릭) 매핑
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

  // 5) 템플릿 정보 매핑
  const templates = run.template
    ? [
        {
          id: run.template.templateId,
          name: run.template.title,
          updatedAt: (run.template.updatedDate || '').slice(0, 10), // "YYYY-MM-DD"
        },
      ]
    : [];

  return {
    title,
    rangeLabel,
    baseCurrency,
    startCapital,
    metrics,
    templates,
  };
}
