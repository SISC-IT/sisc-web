import { api } from './axios';

const recentActivities = [
  { id: 1, message: '출석 체크 세션이 생성되었습니다.', time: '10분 전' },
  { id: 2, message: '공지사항이 등록되었습니다.', time: '35분 전' },
  { id: 3, message: '회원 3명이 가입 신청했습니다.', time: '1시간 전' },
  { id: 4, message: '포인트 규칙이 업데이트되었습니다.', time: '어제' },
];

const quickActions = [
  { id: 'upload', label: '엑셀로 회원 등록', to: '/admin/members/upload' },
  { id: 'attendance', label: '출석 체크 설정', to: '/admin/attendance' },
  { id: 'notice', label: '공지사항 작성', to: '/admin/posts' },
  { id: 'points', label: '포인트 규칙 설정', to: '/admin/points' },
];

export const getAdminHomeData = async () => {
  const response = await api.get('/api/admin/users');
  const users = Array.isArray(response.data)
    ? response.data
    : Array.isArray(response.data?.users)
      ? response.data.users
      : Array.isArray(response.data?.payload)
        ? response.data.payload
        : [];

  const pendingApprovals = users.filter((user) => user.role === 'PENDING_MEMBER');
  const members = users.filter((user) => user.role !== 'PENDING_MEMBER');

  return {
    dashboardStats: [
      {
        id: 'members',
        title: '총 회원 수',
        value: String(members.length),
        description: '승인 회원',
      },
      {
        id: 'visitors',
        title: '금일 방문자',
        value: '-',
        description: '집계 준비 중',
      },
      {
        id: 'attendance',
        title: '주간 출석률',
        value: '-',
        description: '집계 준비 중',
      },
      {
        id: 'pending',
        title: '승인 대기',
        value: String(pendingApprovals.length),
        description: '가입 신청',
      },
    ],
    pendingApprovals,
    recentActivities,
    quickActions,
    members,
  };
};
