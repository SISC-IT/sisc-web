export async function fetchDictionary() {
  // 실제 API 교체 시, 여기만 바꾸면 됩니다.
  // 아래 데이터는 질문의 "예시 JSON응답 형식"을 그대로 옮긴 것입니다.
  return Promise.resolve({
    version: '2025-10-05',
    dimensions: [
      { id: 'price', name: '가격', unit: null, range: null },
      { id: 'score_0_100', name: '스코어(0~100)', unit: null, range: [0, 100] },
      { id: 'osc_zero', name: '0선진동', unit: null, range: [-100, 100] },
      { id: 'volatility', name: '변동성', unit: null, range: null },
      { id: 'volume', name: '거래량', unit: null, range: null },
    ],
    priceFields: [
      { code: 'Close', name: '종가', dimension: 'price' },
      { code: 'Open', name: '시가', dimension: 'price' },
      { code: 'High', name: '고가', dimension: 'price' },
      { code: 'Low', name: '저가', dimension: 'price' },
      { code: 'Volume', name: '거래량', dimension: 'volume' },
    ],
    operators: [
      { code: 'GT', label: '>', name: '초과' },
      { code: 'GTE', label: '>=', name: '이상' },
      { code: 'LT', label: '<', name: '미만' },
      { code: 'LTE', label: '<=', name: '이하' },
      { code: 'EQ', label: '==', name: '같음' },
      { code: 'NEQ', label: '!=', name: '다름' },
      { code: 'CROSSES_ABOVE', label: '↗', name: '상향돌파' },
      { code: 'CROSSES_BELOW', label: '↘', name: '하향돌파' },
    ],
    indicators: [
      {
        code: 'SMA',
        name: '단순이동평균',
        category: 'Trend',
        dimension: 'price',
        params: [
          {
            name: 'length',
            type: 'int',
            min: 2,
            max: 400,
            step: 1,
            default: 20,
          },
        ],
        outputs: [{ name: 'value', label: '값' }],
        transforms: [],
      },
      {
        code: 'RSI',
        name: 'RSI',
        category: 'Momentum',
        dimension: 'score_0_100',
        params: [
          {
            name: 'length',
            type: 'int',
            min: 2,
            max: 200,
            step: 1,
            default: 14,
          },
        ],
        outputs: [{ name: 'value', label: '값(0~100)' }],
        transforms: [],
      },
      {
        code: 'MACD',
        name: 'MACD',
        category: 'Trend',
        dimension: 'osc_zero',
        params: [
          { name: 'fast', type: 'int', default: 12 },
          { name: 'slow', type: 'int', default: 26 },
          { name: 'signal', type: 'int', default: 9 },
        ],
        outputs: [
          { name: 'macd', label: 'MACD' },
          { name: 'signal', label: 'Signal' },
          { name: 'hist', label: 'Histogram' },
        ],
        transforms: [],
      },
    ],
    dimensionCompatibility: [
      {
        leftDimension: 'price',
        allowRightTypes: ['const', 'price', 'indicator'],
        allowIndicatorDimensions: ['price'],
      },
      {
        leftDimension: 'score_0_100',
        allowRightTypes: ['const', 'indicator'],
        allowIndicatorDimensions: ['score_0_100'],
      },
      {
        leftDimension: 'osc_zero',
        allowRightTypes: ['const', 'indicator'],
        allowIndicatorDimensions: ['osc_zero'],
      },
      {
        leftDimension: 'volatility',
        allowRightTypes: ['const'],
        allowIndicatorDimensions: [],
      },
      {
        leftDimension: 'volume',
        allowRightTypes: ['const'],
        allowIndicatorDimensions: [],
      },
    ],
    dimensionAllowedOperators: [
      {
        dimension: 'price',
        operators: [
          'GT',
          'GTE',
          'LT',
          'LTE',
          'EQ',
          'NEQ',
          'CROSSES_ABOVE',
          'CROSSES_BELOW',
        ],
      },
      {
        dimension: 'score_0_100',
        operators: [
          'GT',
          'GTE',
          'LT',
          'LTE',
          'EQ',
          'NEQ',
          'CROSSES_ABOVE',
          'CROSSES_BELOW',
        ],
      },
      {
        dimension: 'osc_zero',
        operators: [
          'GT',
          'GTE',
          'LT',
          'LTE',
          'EQ',
          'NEQ',
          'CROSSES_ABOVE',
          'CROSSES_BELOW',
        ],
      },
      {
        dimension: 'volatility',
        operators: ['GT', 'GTE', 'LT', 'LTE', 'EQ', 'NEQ'],
      },
      {
        dimension: 'volume',
        operators: ['GT', 'GTE', 'LT', 'LTE', 'EQ', 'NEQ'],
      },
    ],
  });
}
