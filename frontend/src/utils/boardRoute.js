const normalizeBoardName = (value = '') =>
  String(value).trim().replace(/\s+/g, ' ');

const stripBoardSuffix = (value = '') =>
  normalizeBoardName(value).replace(/\s*게시판$/, '').trim();

const toUrlSafeSegment = (value = '') =>
  String(value)
    .trim()
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[\\/]+/g, '-')
    .replace(/[^a-z0-9\u3131-\u318e\uac00-\ud7a3-]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');

const safeDecodeURIComponent = (value = '') => {
  try {
    return decodeURIComponent(value);
  } catch {
    return String(value || '');
  }
};

export const isAllBoardName = (boardName = '') => {
  const normalized = normalizeBoardName(boardName);
  if (!normalized) return false;
  return normalized === '전체' || stripBoardSuffix(normalized) === '전체';
};

export const normalizeBoardRouteSegment = (segment = '') => {
  const normalized = normalizeBoardName(safeDecodeURIComponent(segment));
  if (!normalized) return null;
  if (normalized === 'root') return 'root';

  const baseName = stripBoardSuffix(normalized);
  if (isAllBoardName(baseName)) return 'root';

  return toUrlSafeSegment(baseName) || null;
};

export const normalizeBoardPath = (path = '') => {
  const raw = String(path || '').trim();
  if (!raw) return '/board';

  const decoded = safeDecodeURIComponent(raw).replace(/\/+$/, '') || '/';
  if (decoded === '/board') return '/board';

  const match = decoded.match(/^\/board\/(.+)$/);
  if (!match) return decoded;

  const segment = normalizeBoardRouteSegment(match[1]);
  if (!segment || segment === 'root') return '/board';

  return `/board/${encodeURIComponent(segment)}`;
};

export const toBoardRouteSegment = (boardName = '') => {
  const normalized = normalizeBoardName(boardName);
  if (!normalized) return null;
  if (isAllBoardName(normalized)) return 'root';
  return normalizeBoardRouteSegment(stripBoardSuffix(normalized));
};

export const toBoardPath = (boardName = '') => {
  if (isAllBoardName(boardName)) {
    return '/board';
  }

  const segment = toBoardRouteSegment(boardName);
  if (!segment || segment === 'root') return '/board';
  return normalizeBoardPath(`/board/${encodeURIComponent(segment)}`);
};
