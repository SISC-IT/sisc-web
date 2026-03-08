import { api } from './axios';

const formatDateTime = (value) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);

  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
};

const quickActions = [
  { id: 'upload', label: '엑셀로 회원 등록', to: '/admin/members/upload' },
  { id: 'attendance', label: '출석 체크 설정', to: '/admin/attendance' },
  { id: 'notice', label: '공지사항 작성', to: '/admin/posts' },
  { id: 'points', label: '포인트 규칙 설정', to: '/admin/points' },
];

export const getAdminHomeData = async () => {
  const [usersResult, activitiesResult] = await Promise.allSettled([
    api.get('/api/admin/users'),
    api.get('/api/admin/dashboard/activities', {
      params: { page: 0, size: 20, sort: 'createdAt,desc' },
    }),
  ]);

  if (usersResult.status !== 'fulfilled') {
    throw usersResult.reason;
  }

  const usersResponse = usersResult.value;
  const activitiesResponse =
    activitiesResult.status === 'fulfilled' ? activitiesResult.value : { data: { content: [] } };

  const users = Array.isArray(usersResponse.data)
    ? usersResponse.data
    : Array.isArray(usersResponse.data?.users)
      ? usersResponse.data.users
      : Array.isArray(usersResponse.data?.payload)
        ? usersResponse.data.payload
        : [];

  const activityItems = Array.isArray(activitiesResponse.data?.content)
    ? activitiesResponse.data.content
    : Array.isArray(activitiesResponse.data)
      ? activitiesResponse.data
      : [];

  const recentActivities = activityItems.map((activity, index) => ({
    id: activity?.id || `${activity?.createdAt || 'unknown'}-${index}`,
    message: `${activity?.username || '시스템'} ${activity?.message || '-'}`,
    time: formatDateTime(activity?.createdAt),
  }));

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
