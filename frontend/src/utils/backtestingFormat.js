export function formatCurrency(value, currency) {
  if (value == null) return '-';
  // 필요하면 Intl.NumberFormat으로 바꿔도 됨
  return `${value.toLocaleString()} ${currency || ''}`.trim();
}

export function formatPercent(value) {
  if (value == null) return '-';
  return `${value.toFixed(2)}%`;
}

export function formatNumber(value) {
  if (value == null) return '-';
  return value.toLocaleString();
}

export function formatSharpe(value) {
  if (value == null) return '-';
  return value.toFixed(2);
}

export function addDaysToDate(dateStr, days) {
  if (!dateStr) return null;
  const base = new Date(dateStr);
  if (Number.isNaN(base.getTime())) return null;
  const d = new Date(base);
  d.setDate(d.getDate() + days);
  return d;
}

export function formatDateYYYYMMDD(date) {
  if (!date) return '';
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

export function formatTwoDecimal(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '';
  return n.toFixed(2); // 소수 셋째 자리에서 반올림
}

export function formatCurrencyTwoDecimal(value, currency) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '-';
  return `${n.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })} ${currency || ''}`.trim();
}
