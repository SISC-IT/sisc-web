export const onlyDigits = (s) => s.replace(/\D/g, '');

export const STATUSES = ['출석', '지각', '결석'];

export const uuid = () =>
  globalThis.crypto && globalThis.crypto.randomUUID
    ? globalThis.crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

export const randomStatus = () => {
  const r = Math.random();
  if (r < 0.7) return '출석';
  if (r < 0.9) return '지각';
  return '결석';
};

export const makeInitialMockSessions = () => {
  const now = Date.now();
  return [
    {
      id: uuid(),
      title: '전체 출석',
      code: '1234',
      createdAt: now - 30 * 60 * 1000,
      expiresAt: now + 15 * 60 * 1000,
    },
    {
      id: uuid(),
      title: '금융 it 출석',
      code: '5678',
      createdAt: now - 120 * 60 * 1000,
      expiresAt: now - 60 * 1000,
    },
  ];
};

export const makeMockRoster = () => {
  const names = ['안강준', '김동은', '황순영'];
  return names.map((name) => ({
    id: uuid(),
    name,
    status: randomStatus(),
  }));
};

export const makeLargeMockRoster = () => {
  const names = [
    '안강준',
    '김동은',
    '황순영',
    '이민준',
    '박서준',
    '김도윤',
    '최지호',
    '윤하준',
    '강시우',
    '조은우',
    '신유준',
    '한이안',
    '정지훈',
    '송주원',
    '오건우',
    '임도현',
    '장선우',
    '서예준',
    '황지후',
    '문준서',
  ];
  return names.map((name) => ({
    id: uuid(),
    name,
    status: randomStatus(),
  }));
};
