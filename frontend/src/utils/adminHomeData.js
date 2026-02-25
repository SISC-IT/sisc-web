const mockAdminHomeData = {
  dashboardStats: [
    { id: 'members', title: '총 회원 수', value: '156', description: '활성 회원' },
    { id: 'visitors', title: '금일 방문자', value: '89', description: '전일 대비 +5' },
    { id: 'attendance', title: '주간 출석률', value: '78%', description: '전주 대비 +3%' },
    { id: 'pending', title: '승인 대기', value: '12', description: '가입 신청' },
  ],
  pendingApprovals: [
    { id: 1, name: '김민수', email: 'minsu@sisc.com', requestedAt: '2026-02-24' },
    { id: 2, name: '박서연', email: 'seoyeon@sisc.com', requestedAt: '2026-02-24' },
    { id: 3, name: '이준호', email: 'junho@sisc.com', requestedAt: '2026-02-23' },
  ],
  recentActivities: [
    { id: 1, message: '출석 체크 세션이 생성되었습니다.', time: '10분 전' },
    { id: 2, message: '공지사항이 등록되었습니다.', time: '35분 전' },
    { id: 3, message: '회원 3명이 가입 신청했습니다.', time: '1시간 전' },
    { id: 4, message: '포인트 규칙이 업데이트되었습니다.', time: '어제' },
  ],
  quickActions: [
    { id: 'upload', label: '엑셀로 회원 등록', to: '/admin/members/upload' },
    { id: 'attendance', label: '출석 체크 설정', to: '/admin/attendance' },
    { id: 'notice', label: '공지사항 작성', to: '/admin/posts' },
    { id: 'points', label: '포인트 규칙 설정', to: '/admin/points' },
  ],
  members: [
    { id: 1, name: '김하늘', role: 'TEAM_MEMBER', status: 'ACTIVE' },
    { id: 2, name: '최지훈', role: 'TEAM_LEADER', status: 'ACTIVE' },
    { id: 3, name: '오수빈', role: 'PENDING_MEMBER', status: 'PENDING' },
    { id: 4, name: '정우진', role: 'TEAM_MEMBER', status: 'ACTIVE' },
  ],
};

export const getAdminHomeData = async () => {
  return mockAdminHomeData;
};
