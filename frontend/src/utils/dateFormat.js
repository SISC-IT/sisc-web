export function formatKoreanDateTime(isoString) {
  if (!isoString) return '-';

  const date = new Date(isoString);

  if (isNaN(date.getTime())) return '-';

  const year = date.getFullYear();
  const month = date.getMonth() + 1;
  const day = date.getDate();

  let hours = date.getHours();
  const minutes = date.getMinutes();

  const isPM = hours >= 12;
  const period = isPM ? '오후' : '오전';

  hours = hours % 12;
  if (hours === 0) hours = 12;

  const pad = (n) => String(n).padStart(2, '0');

  return `${year}년 ${month}월 ${day}일 ${period} ${pad(hours)}시 ${pad(minutes)}분`;
}
