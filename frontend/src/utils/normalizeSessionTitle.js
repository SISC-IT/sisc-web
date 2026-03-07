const SESSION_TITLE_FALLBACK = '기타';

export const normalizeSessionTitle = (sessionTitle) => {
  if (typeof sessionTitle !== 'string') return SESSION_TITLE_FALLBACK;

  const normalizedTitle = sessionTitle.trim();
  return normalizedTitle !== '' ? normalizedTitle : SESSION_TITLE_FALLBACK;
};

export const getSessionTitleFallback = () => SESSION_TITLE_FALLBACK;
