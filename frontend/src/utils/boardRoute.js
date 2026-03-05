const BOARD_SEGMENT_MAP = {
  전체: 'root',
  '전체 게시판': 'root',
  증권1팀: 'securities-1',
  '증권1팀 게시판': 'securities-1',
  증권2팀: 'securities-2',
  '증권2팀 게시판': 'securities-2',
  증권3팀: 'securities-3',
  '증권3팀 게시판': 'securities-3',
  자산운용: 'asset-management',
  자산운용팀: 'asset-management',
  '자산운용팀 게시판': 'asset-management',
  금융IT: 'finance-it',
  금융IT팀: 'finance-it',
  '금융IT팀 게시판': 'finance-it',
  매크로: 'macro',
  매크로팀: 'macro',
  '매크로팀 게시판': 'macro',
  트레이딩: 'trading',
  '트레이딩팀 게시판': 'trading',
};

export const isAllBoardName = (boardName = '') => {
  const normalized = String(boardName).trim();
  return normalized === '전체' || normalized === '전체 게시판';
};

export const toBoardRouteSegment = (boardName = '') => {
  const normalized = String(boardName).trim();
  if (!normalized) return '';

  if (BOARD_SEGMENT_MAP[normalized]) {
    return BOARD_SEGMENT_MAP[normalized];
  }

  if (normalized.endsWith('게시판')) {
    const withoutSuffix = normalized.replace(/게시판$/, '').trim();
    if (BOARD_SEGMENT_MAP[withoutSuffix]) {
      return BOARD_SEGMENT_MAP[withoutSuffix];
    }
    return withoutSuffix;
  }

  return normalized;
};

export const toBoardPath = (boardName = '') => {
  if (isAllBoardName(boardName)) {
    return '/board';
  }

  const segment = toBoardRouteSegment(boardName);
  return segment ? `/board/${encodeURIComponent(segment)}` : '/board';
};
