// 주문·입출금 가능 시간. 서버의 국내 주문 시간과 같다.
export const ORDER_HOURS = '16:30 – 21:00';

// 서버는 금액을 소수 4자리까지 준다. 화면에는 원 미만을 버려서(0 쪽으로) 보여준다.
export const won = (value) => {
  if (value === null || value === undefined) return '-';
  return Math.trunc(Number(value)).toLocaleString('ko-KR');
};

// 비율(0.0523)을 부호 있는 퍼센트(+5.23%)로
export const signedPercent = (rate) => {
  if (rate === null || rate === undefined) return '-';
  const percent = Number(rate) * 100;
  const sign = percent > 0 ? '+' : percent < 0 ? '−' : '';
  return `${sign}${Math.abs(percent).toFixed(2)}%`;
};

// 수수료율·이율(0.0001)을 퍼센트(0.01%)로. 끝자리 0은 지운다.
export const ratePercent = (rate) =>
  `${Number((Number(rate) * 100).toFixed(4))}%`;

// 오르면 파랑, 내리면 빨강 (디자인 기준)
export const changeColor = (rate) => {
  if (rate === null || rate === undefined || Number(rate) === 0)
    return '#4b5563';
  return Number(rate) > 0 ? '#2563eb' : '#dc2626';
};

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

// '2026-11-30' → '2026.11.30(월)'
export const fullDate = (isoDate) => {
  const [y, m, d] = isoDate.split('-').map(Number);
  const weekday = WEEKDAYS[new Date(y, m - 1, d).getDay()];
  return `${y}.${String(m).padStart(2, '0')}.${String(d).padStart(2, '0')}(${weekday})`;
};

// '2026-12-09' → '12.09'
export const shortDate = (isoDate) =>
  isoDate ? isoDate.slice(5).replace('-', '.') : '';

// 오늘(브라우저 날짜)부터 그 날짜까지 남은 일수
export const daysUntil = (isoDate) => {
  const [y, m, d] = isoDate.split('-').map(Number);
  const target = new Date(y, m - 1, d);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((target - today) / 86400000);
};
