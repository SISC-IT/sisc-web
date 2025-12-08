export function formatCurrency(value, currency) {
  if (value == null) return '-';
  const num = Number(value);
  if (!Number.isFinite(num)) return '-';
  return `${num.toLocaleString()} ${currency || ''}`.trim();
}

export function formatPercent(value) {
  if (value == null) return '-';
  const num = Number(value);
  if (!Number.isFinite(num)) return '-';
  return `${num.toFixed(2)}%`;
}

export function formatNumber(value) {
  if (value == null) return '-';
  const num = Number(value);
  if (!Number.isFinite(num)) return '-';
  return num.toLocaleString();
}

export function formatSharpe(value) {
  if (value == null) return '-';
  const num = Number(value);
  if (!Number.isFinite(num)) return '-';
  return num.toFixed(2);
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

// CSV 다운로드 헬퍼
export function downloadEquityCsv(filename, data) {
  if (!data || data.length === 0) return;

  const header = 'day,date,equity,multiple\n';
  const rows = data
    .map((p) => {
      const day = p.day ?? '';
      const date = p.date ?? '';
      const equity =
        p.equity != null && Number.isFinite(Number(p.equity))
          ? Number(p.equity)
          : '';
      const multiple =
        p.multiple != null && Number.isFinite(Number(p.multiple))
          ? Number(p.multiple)
          : '';
      return [day, date, equity, multiple].join(',');
    })
    .join('\n');

  const blob = new Blob([header + rows], {
    type: 'text/csv;charset=utf-8;',
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
