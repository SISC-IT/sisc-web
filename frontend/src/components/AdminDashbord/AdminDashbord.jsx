import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  BarChart3,
  Calendar,
  Eye,
  FileText,
  RefreshCw,
  Users,
} from 'lucide-react';
import {
  getBoardsDistribution,
  getDashboardActivities,
  getUsersDistribution,
  getVisitorsTrend,
  getDashboardActivitiesStreamUrl,
} from '../../utils/adminDashboardApi';
import styles from './AdminDashbord.module.css';

const PERIOD_OPTIONS = [
  { label: '최근 7일', value: 7 },
  { label: '최근 30일', value: 30 },
  { label: '최근 90일', value: 90 },
];

const PIE_COLORS = ['#2563eb', '#16a34a', '#d97706', '#7c3aed', '#475569', '#dc2626'];
const ACTIVITY_FETCH_SIZE = 100;
const MAX_ACTIVITY_LOGS = 300;

const toNumber = (value) => Number(value || 0);

const formatDateLabel = (value) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);

  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${month}/${day}`;
};

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

const normalizeActivity = (activity) => ({
  id: activity?.id || `${activity?.createdAt || 'unknown'}-${activity?.message || ''}`,
  username: activity?.username || '시스템',
  message: activity?.message || '-',
  boardName: activity?.boardName || '',
  createdAt: activity?.createdAt || null,
});

const mergeActivityLists = (current = [], incoming = []) => {
  const map = new Map();

  [...incoming, ...current].forEach((item) => {
    const normalized = normalizeActivity(item);
    if (!map.has(normalized.id)) {
      map.set(normalized.id, normalized);
    }
  });

  return Array.from(map.values())
    .sort((a, b) => {
      const aTime = new Date(a.createdAt || 0).getTime();
      const bTime = new Date(b.createdAt || 0).getTime();
      return bTime - aTime;
    })
    .slice(0, MAX_ACTIVITY_LOGS);
};

const AdminDashbord = () => {
  const [periodDays, setPeriodDays] = useState(7);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [visitorsTrend, setVisitorsTrend] = useState([]);
  const [usersDistribution, setUsersDistribution] = useState([]);
  const [boardsDistribution, setBoardsDistribution] = useState([]);

  const [activities, setActivities] = useState([]);

  const totalUserCount = useMemo(
    () => usersDistribution.reduce((acc, item) => acc + toNumber(item?.count), 0),
    [usersDistribution]
  );

  const pieChartData = useMemo(
    () =>
      usersDistribution.map((item, index) => ({
        name: item?.roleName || 'UNKNOWN',
        value: toNumber(item?.count),
        color: PIE_COLORS[index % PIE_COLORS.length],
      })),
    [usersDistribution]
  );

  const chartVisitorsData = useMemo(
    () =>
      visitorsTrend.map((item) => ({
        date: formatDateLabel(item?.date),
        visitors: toNumber(item?.visitorCount),
      })),
    [visitorsTrend]
  );

  const chartBoardsData = useMemo(
    () =>
      boardsDistribution
        .map((item) => ({
          boardName: item?.boardName || '기타',
          activityCount: toNumber(item?.activityCount),
        }))
        .sort((a, b) => b.activityCount - a.activityCount)
        .slice(0, 8),
    [boardsDistribution]
  );

  const visitorTotalInPeriod = useMemo(
    () => visitorsTrend.reduce((acc, item) => acc + toNumber(item?.visitorCount), 0),
    [visitorsTrend]
  );

  const boardTotalInPeriod = useMemo(
    () => boardsDistribution.reduce((acc, item) => acc + toNumber(item?.activityCount), 0),
    [boardsDistribution]
  );

  const loadActivities = useCallback(async () => {
    const response = await getDashboardActivities(0, ACTIVITY_FETCH_SIZE);
    const content = Array.isArray(response?.content) ? response.content : [];

    setActivities(content.map(normalizeActivity).slice(0, MAX_ACTIVITY_LOGS));
  }, []);

  const loadDashboard = useCallback(async () => {
    try {
      setLoading(true);
      setError('');

      const [
        visitorsTrendResult,
        usersDistributionResult,
        boardsDistributionResult,
      ] = await Promise.allSettled([
        getVisitorsTrend(periodDays),
        getUsersDistribution(),
        getBoardsDistribution(periodDays),
      ]);

      const partialFailures = [];

      if (visitorsTrendResult.status === 'fulfilled') {
        setVisitorsTrend(Array.isArray(visitorsTrendResult.value) ? visitorsTrendResult.value : []);
      } else {
        setVisitorsTrend([]);
        partialFailures.push('방문자 추이');
      }

      if (usersDistributionResult.status === 'fulfilled') {
        setUsersDistribution(Array.isArray(usersDistributionResult.value) ? usersDistributionResult.value : []);
      } else {
        setUsersDistribution([]);
        partialFailures.push('회원 권한 분포');
      }

      if (boardsDistributionResult.status === 'fulfilled') {
        setBoardsDistribution(Array.isArray(boardsDistributionResult.value) ? boardsDistributionResult.value : []);
      } else {
        setBoardsDistribution([]);
        partialFailures.push('게시판 활동 분포');
      }

      if (partialFailures.length > 0) {
        setError(`일부 지표를 불러오지 못했습니다: ${partialFailures.join(', ')}`);
      }

      try {
        await loadActivities();
      } catch (activitiesError) {
        console.error('활동 로그 조회 실패:', activitiesError);
        setActivities([]);
      }
    } catch (loadError) {
      console.error('관리자 대시보드 로딩 실패:', loadError);
      setError('대시보드 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  }, [loadActivities, periodDays]);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  useEffect(() => {
    let eventSource;

    try {
      eventSource = new EventSource(getDashboardActivitiesStreamUrl(), {
        withCredentials: true,
      });

      eventSource.onmessage = (event) => {
        if (!event?.data) return;

        try {
          const payload = JSON.parse(event.data);
          setActivities((prev) => mergeActivityLists(prev, [payload]));
        } catch {
          // Ignore heartbeat/non-JSON messages.
        }
      };
    } catch (streamError) {
      console.error('활동 로그 스트림 연결 실패:', streamError);
    }

    return () => {
      if (eventSource) {
        eventSource.close();
      }
    };
  }, []);

  return (
    <div className={styles.container}>
      <section className={styles.toolbar}>
        <p className={styles.description}>실시간 통계 데이터를 확인하세요.</p>
        <div className={styles.toolbarActions}>
          <select
            className={styles.periodSelect}
            value={periodDays}
            onChange={(e) => setPeriodDays(Number(e.target.value))}
            disabled={loading}
          >
            {PERIOD_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <button
            type="button"
            className={styles.refreshButton}
            onClick={loadDashboard}
            disabled={loading}
          >
            <RefreshCw size={14} className={loading ? styles.spinning : ''} />
            새로고침
          </button>
        </div>
      </section>

      {error && <p className={styles.errorText}>{error}</p>}

      <section className={styles.statsGrid}>
        <article className={styles.statCard}>
          <div className={styles.statHeader}>
            <span>총 회원 수</span>
            <Users size={16} />
          </div>
          <strong className={styles.statValue}>{totalUserCount.toLocaleString()}명</strong>
          <span className={styles.statSubText}>권한 분포 기준</span>
        </article>

        <article className={styles.statCard}>
          <div className={styles.statHeader}>
            <span>최근 {periodDays}일 방문자</span>
            <Eye size={16} />
          </div>
          <strong className={styles.statValue}>{visitorTotalInPeriod.toLocaleString()}명</strong>
          <span className={styles.statSubText}>방문자 추이 합계</span>
        </article>

        <article className={styles.statCard}>
          <div className={styles.statHeader}>
            <span>최근 {periodDays}일 게시판 활동</span>
            <FileText size={16} />
          </div>
          <strong className={styles.statValue}>{boardTotalInPeriod.toLocaleString()}건</strong>
          <span className={styles.statSubText}>게시판별 활동 집계 합</span>
        </article>

        <article className={styles.statCard}>
          <div className={styles.statHeader}>
            <span>활동 로그</span>
            <Calendar size={16} />
          </div>
          <strong className={styles.statValue}>{activities.length.toLocaleString()}개</strong>
          <span className={styles.statSubText}>최신 순 표시</span>
        </article>
      </section>

      <section className={styles.chartGrid}>
        <article className={styles.panel}>
          <header className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>방문자 추이</h2>
          </header>
          <div className={styles.chartBox}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartVisitorsData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="visitorsGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#2563eb" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#2563eb" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                <XAxis dataKey="date" stroke="#6b7280" fontSize={12} />
                <YAxis stroke="#6b7280" fontSize={12} />
                <Tooltip />
                <Area
                  type="monotone"
                  dataKey="visitors"
                  stroke="#2563eb"
                  fill="url(#visitorsGradient)"
                  strokeWidth={2}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </article>

        <article className={styles.panel}>
          <header className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>게시판별 활동 분포</h2>
          </header>
          <div className={styles.chartBox}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartBoardsData} layout="vertical" margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" horizontal={false} />
                <XAxis type="number" stroke="#6b7280" fontSize={12} />
                <YAxis type="category" dataKey="boardName" stroke="#6b7280" fontSize={12} width={90} />
                <Tooltip />
                <Bar dataKey="activityCount" fill="#16a34a" radius={[0, 6, 6, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </article>

        <article className={styles.panel}>
          <header className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>회원 권한 분포</h2>
          </header>
          <div className={styles.pieWrap}>
            <div className={styles.pieChartBox}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={pieChartData}
                    dataKey="value"
                    nameKey="name"
                    innerRadius={50}
                    outerRadius={80}
                    paddingAngle={2}
                  >
                    {pieChartData.map((entry, index) => (
                      <Cell key={`${entry.name}-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            </div>

            <ul className={styles.legendList}>
              {pieChartData.map((item) => (
                <li key={item.name} className={styles.legendItem}>
                  <span className={styles.legendLabel}>
                    <span className={styles.legendDot} style={{ backgroundColor: item.color }} />
                    {item.name}
                  </span>
                  <strong>{item.value.toLocaleString()}명</strong>
                </li>
              ))}
            </ul>
          </div>
        </article>

        <article className={styles.panel}>
          <header className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>요약 지표</h2>
            <BarChart3 size={16} />
          </header>
          <ul className={styles.summaryList}>
            <li className={styles.summaryItem}>
              <span>조회 기간</span>
              <strong>{periodDays}일</strong>
            </li>
            <li className={styles.summaryItem}>
              <span>방문자 데이터 포인트</span>
              <strong>{chartVisitorsData.length}개</strong>
            </li>
            <li className={styles.summaryItem}>
              <span>활동 집계 게시판 수</span>
              <strong>{boardsDistribution.length}개</strong>
            </li>
            <li className={styles.summaryItem}>
              <span>권한 종류 수</span>
              <strong>{usersDistribution.length}개</strong>
            </li>
          </ul>
        </article>
      </section>

      <section className={styles.panel}>
        <header className={styles.panelHeader}>
          <h2 className={styles.panelTitle}>실시간 활동 로그</h2>
        </header>

        <div className={styles.logScrollArea}>
          <ul className={styles.logList}>
            {activities.length === 0 ? (
              <li className={styles.emptyLog}>활동 로그가 없습니다.</li>
            ) : (
              activities.map((activity) => (
                <li key={activity.id} className={styles.logItem}>
                  <span className={styles.logTime}>{formatDateTime(activity.createdAt)}</span>
                  <span className={styles.logMessage}>
                    <strong>{activity.username}</strong> {activity.message}
                    {activity.boardName ? ` (${activity.boardName})` : ''}
                  </span>
                </li>
              ))
            )}
          </ul>
        </div>
      </section>
    </div>
  );
};

export default AdminDashbord;
