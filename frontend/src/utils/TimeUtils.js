export const getTimeAgo = (dateInput) => {
  if (!dateInput) return '방금 전';

  let date;
  if (dateInput instanceof Date) {
    date = dateInput;
  } else if (typeof dateInput === 'string') {
    let dateString = dateInput;
    if (
      /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+)$/.test(dateInput) &&
      !dateInput.endsWith('Z')
    ) {
      dateString += 'Z';
    }
    date = new Date(dateString);
  } else if (typeof dateInput === 'number') {
    date = new Date(dateInput);
  } else {
    return '방금 전';
  }

  if (isNaN(date.getTime())) return '방금 전';

  const now = new Date();
  const diffInSeconds = Math.floor((now - date) / 1000);

  if (diffInSeconds < 0) return '방금 전';

  const minutes = Math.floor(diffInSeconds / 60);
  const hours = Math.floor(diffInSeconds / 3600); 
  const days = Math.floor(diffInSeconds / 86400);  
  const months = Math.floor(days / 30);
  const years = Math.floor(days / 365);

  if (diffInSeconds < 60) return '방금 전';
  else if (minutes < 60) return `${minutes}분 전`;
  else if (hours < 24) return `${hours}시간 전`;
  else if (days < 30) return `${days}일 전`;
  else if (months < 12) return `${months}개월 전`;
  else return `${years}년 전`;
};
