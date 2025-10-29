export const getTimeAgo = (createdAt) => {
  const now = new Date();
  const created = new Date(createdAt);
  const diffInMs = now - created;

  const seconds = Math.floor(diffInMs / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  const months = Math.floor(days / 30);
  const years = Math.floor(months / 12);

  if (seconds < 60) {
    return '방금전';
  } else if (minutes < 60) {
    return `${minutes}분전`;
  } else if (hours < 24) {
    return `${hours}시간전`;
  } else if (days < 30) {
    return `${days}일전`;
  } else if (months < 12) {
    return `${months}달전`;
  } else {
    return `${years}년전`;
  }
};
