import { useEffect, useMemo, useRef, useState } from 'react';
import { MoreVertical, Search, Star } from 'lucide-react';
import styles from './AdminMemberManage.module.css';
import {
  changeAdminMemberGrade,
  changeAdminMemberRole,
  changeAdminMemberStatus,
  deleteAdminMember,
  getAdminMembersData,
} from '../../utils/adminMembersData';

const ROLE_LABELS = {
  SYSTEM_ADMIN: '관리자',
  PRESIDENT: '회장',
  VICE_PRESIDENT: '부회장',
  TEAM_LEADER: '팀장',
  TEAM_MEMBER: '일반',
  PENDING_MEMBER: '대기회원',
};

const ROLE_OPTIONS = [
  'SYSTEM_ADMIN',
  'PRESIDENT',
  'VICE_PRESIDENT',
  'TEAM_LEADER',
  'TEAM_MEMBER',
  'PENDING_MEMBER',
];

const STATUS_LABELS = {
  ACTIVE: '활성',
  INACTIVE: '비활성',
  GRADUATED: '졸업',
};

const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE', 'GRADUATED'];

const GRADE_LABELS = {
  NEW_MEMBER: '신입부원',
  ASSOCIATE_MEMBER: '준회원',
  REGULAR_MEMBER: '정회원',
};

const GRADE_OPTIONS = ['NEW_MEMBER', 'ASSOCIATE_MEMBER', 'REGULAR_MEMBER'];

const getRoleClassName = (role) => {
  if (role === 'PRESIDENT') return styles.rolePresident;
  if (role === 'SYSTEM_ADMIN' || role === 'VICE_PRESIDENT') return styles.roleManager;
  if (role === 'TEAM_LEADER' || role === 'PENDING_MEMBER') return styles.roleLeader;
  return styles.roleNormal;
};

const getStatusClassName = (status) => {
  if (status === '활성') return styles.statusActive;
  if (status === '비활성' || status === '졸업') return styles.statusInactive;
  return styles.statusPending;
};

const AdminMemberManage = () => {
  // 필터/검색 상태
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');
  const [statusFilter, setStatusFilter] = useState('all');

  // 회원 목록 데이터 상태
  const [members, setMembers] = useState([]);
  const [isDeletingById, setIsDeletingById] = useState({});
  const [isChangingById, setIsChangingById] = useState({});
  const [openActionMenuId, setOpenActionMenuId] = useState(null);
  const [changeDialog, setChangeDialog] = useState({
    open: false,
    type: 'role',
    member: null,
    value: '',
  });
  const latestRequestIdRef = useRef(0);

  // 회원 목록 조회 (필요한 필터만 백엔드로 전달)
  const loadMembers = async ({ keyword, role, status, requestId } = {}) => {
    try {
      const data = await getAdminMembersData({ keyword, role, status });

      if (requestId != null && requestId !== latestRequestIdRef.current) {
        return;
      }

      const nextMembers = data.members || [];
      setMembers(nextMembers);
    } catch (error) {
      if (requestId != null && requestId !== latestRequestIdRef.current) {
        return;
      }

      window.alert(error?.message || '회원 목록을 불러오지 못했습니다.');
      setMembers([]);
    }
  };

  // 필터/검색 조건 변경 시 목록 재조회
  useEffect(() => {
    const requestId = ++latestRequestIdRef.current;
    const backendRole = roleFilter === 'all' ? undefined : roleFilter;
    const backendStatus = statusFilter === 'all' ? undefined : statusFilter;
    loadMembers({
      keyword: searchQuery.trim() || undefined,
      role: backendRole,
      status: backendStatus,
      requestId,
    });

    return () => {
      if (latestRequestIdRef.current === requestId) {
        latestRequestIdRef.current += 1;
      }
    };
  }, [roleFilter, searchQuery, statusFilter]);

  useEffect(() => {
    const closeActionMenuOnOutsideClick = (event) => {
      if (!event.target.closest('[data-member-action-menu]')) {
        setOpenActionMenuId(null);
      }
    };

    document.addEventListener('mousedown', closeActionMenuOnOutsideClick);
    return () => {
      document.removeEventListener('mousedown', closeActionMenuOnOutsideClick);
    };
  }, []);

  const filteredMembers = useMemo(() => {
    return members.map((member) => ({
      ...member,
      displayRole: ROLE_LABELS[member.role] || member.role,
      displayStatus: STATUS_LABELS[member.status] || member.status,
      displayGrade: GRADE_LABELS[member.grade] || member.grade || '-',
    }));
  }, [members]);

  const openRoleDialog = (member) => {
    setChangeDialog({
      open: true,
      type: 'role',
      member,
      value: member.role,
    });
  };

  const openStatusDialog = (member) => {
    setChangeDialog({
      open: true,
      type: 'status',
      member,
      value: member.status,
    });
  };

  const openGradeDialog = (member) => {
    setChangeDialog({
      open: true,
      type: 'grade',
      member,
      value: member.grade || GRADE_OPTIONS[0],
    });
  };

  const closeChangeDialog = () => {
    setChangeDialog({ open: false, type: 'role', member: null, value: '' });
  };

  const confirmChangeDialog = async () => {
    const { type, member, value } = changeDialog;
    if (!member) return;

    if (isChangingById[member.id]) {
      return;
    }

    setIsChangingById((prev) => ({
      ...prev,
      [member.id]: true,
    }));

    try {
      if (type === 'role') {
        if (!ROLE_OPTIONS.includes(value)) {
          window.alert('유효하지 않은 권한입니다.');
          return;
        }
        await changeAdminMemberRole({ userId: member.id, role: value });
      } else if (type === 'status') {
        if (!STATUS_OPTIONS.includes(value)) {
          window.alert('유효하지 않은 상태입니다.');
          return;
        }
        await changeAdminMemberStatus({ userId: member.id, status: value });
      } else {
        if (!GRADE_OPTIONS.includes(value)) {
          window.alert('유효하지 않은 신분입니다.');
          return;
        }
        await changeAdminMemberGrade({ userId: member.id, grade: value });
      }

      closeChangeDialog();
      await loadMembers({
        keyword: searchQuery.trim() || undefined,
        role: roleFilter === 'all' ? undefined : roleFilter,
        status: statusFilter === 'all' ? undefined : statusFilter,
      });
    } catch (error) {
      window.alert(
        error?.message ||
          (type === 'role'
            ? '권한 변경에 실패했습니다.'
            : type === 'status'
              ? '상태 변경에 실패했습니다.'
              : '신분 변경에 실패했습니다.')
      );
    } finally {
      setIsChangingById((prev) => ({
        ...prev,
        [member.id]: false,
      }));
    }
  };

  // 단일 회원 삭제
  const handleDelete = async (member) => {
    if (isDeletingById[member.id]) {
      return;
    }

    if (!window.confirm(`${member.name}님을 강제 탈퇴 처리하시겠습니까?`)) {
      return;
    }

    setIsDeletingById((prev) => ({
      ...prev,
      [member.id]: true,
    }));

    try {
      await deleteAdminMember({ userId: member.id });
      await loadMembers({
        keyword: searchQuery.trim() || undefined,
        role: roleFilter === 'all' ? undefined : roleFilter,
        status: statusFilter === 'all' ? undefined : statusFilter,
      });
    } catch (error) {
      window.alert(error?.message || '회원 삭제에 실패했습니다.');
    } finally {
      setIsDeletingById((prev) => ({
        ...prev,
        [member.id]: false,
      }));
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.filterRow}>
        <div className={styles.searchWrap}>
          <Search size={16} className={styles.searchIcon} />
          <input
            className={styles.searchInput}
            placeholder="이름, 이메일, 학번으로 검색..."
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
          />
        </div>

        <select
          className={styles.filterSelect}
          value={roleFilter}
          onChange={(event) => setRoleFilter(event.target.value)}
        >
          <option value="all">모든 권한</option>
          {ROLE_OPTIONS.map((role) => (
            <option key={role} value={role}>
              {ROLE_LABELS[role] || role}
            </option>
          ))}
        </select>

        <select
          className={styles.filterSelect}
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value)}
        >
          <option value="all">모든 상태</option>
          <option value="ACTIVE">활성</option>
          <option value="INACTIVE">비활성</option>
          <option value="GRADUATED">졸업</option>
        </select>
      </div>

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>회원</th>
              <th>학번</th>
              <th>소속팀</th>
              <th>권한</th>
              <th>포인트</th>
              <th>상태</th>
              <th>신분</th>
              <th>기수</th>
              <th className={styles.rightAlign}>작업</th>
            </tr>
          </thead>
          <tbody>
            {filteredMembers.map((member) => (
              <tr key={member.id}>
                <td>
                  <div className={styles.memberInfo}>
                    <div className={styles.avatar}>{member.name?.[0] ?? '?'}</div>
                    <div>
                      <p className={styles.memberName}>{member.name}</p>
                      <p className={styles.memberEmail}>{member.email}</p>
                    </div>
                  </div>
                </td>
                <td>{member.studentId}</td>
                <td>{member.teamName || '-'}</td>
                <td>
                  <span
                    className={`${styles.badge} ${getRoleClassName(member.role)}`}
                  >
                    {member.displayRole}
                  </span>
                </td>
                <td>
                  <span className={styles.points}>
                    <Star size={12} />
                    {(member.point || 0).toLocaleString()}
                  </span>
                </td>
                <td>
                  <span
                    className={`${styles.badge} ${getStatusClassName(member.displayStatus)}`}
                  >
                    {member.displayStatus}
                  </span>
                </td>
                <td>{member.displayGrade}</td>
                <td>{member.generation ? `${member.generation}기` : '-'}</td>
                <td className={styles.rightAlign}>
                  <div className={styles.rowActionMenu} data-member-action-menu>
                    <button
                      type="button"
                      className={styles.kebabButton}
                      onClick={() =>
                        setOpenActionMenuId((prev) =>
                          prev === member.id ? null : member.id
                        )
                      }
                      aria-label="회원 관리 메뉴"
                      aria-expanded={openActionMenuId === member.id}
                    >
                      <MoreVertical size={16} />
                    </button>
                    {openActionMenuId === member.id && (
                      <div className={styles.actionMenu}>
                        <button
                          type="button"
                          className={styles.actionMenuItem}
                          onClick={() => {
                            openRoleDialog(member);
                            setOpenActionMenuId(null);
                          }}
                        >
                          권한 변경
                        </button>
                        <button
                          type="button"
                          className={styles.actionMenuItem}
                          onClick={() => {
                            openStatusDialog(member);
                            setOpenActionMenuId(null);
                          }}
                        >
                          상태 변경
                        </button>
                        <button
                          type="button"
                          className={styles.actionMenuItem}
                          onClick={() => {
                            openGradeDialog(member);
                            setOpenActionMenuId(null);
                          }}
                        >
                          신분 변경
                        </button>
                        <button
                          type="button"
                          className={styles.actionMenuItem}
                          onClick={() => {
                            handleDelete(member);
                            setOpenActionMenuId(null);
                          }}
                          disabled={Boolean(isDeletingById[member.id])}
                        >
                          {isDeletingById[member.id] ? '처리 중...' : '회원 삭제'}
                        </button>
                      </div>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className={styles.footerRow}>
        <p>총 {filteredMembers.length}명의 회원</p>
        <div className={styles.paging}>
          <button type="button" className={styles.actionButton} disabled>
            이전
          </button>
          <button type="button" className={styles.actionButton} disabled>
            다음
          </button>
        </div>
      </div>

      {changeDialog.open && changeDialog.member && (
        <div className={styles.modalOverlay}>
          <div className={styles.modalCard}>
            <h3>
              {changeDialog.type === 'role'
                ? '권한 변경'
                : changeDialog.type === 'status'
                  ? '상태 변경'
                  : '신분 변경'}
            </h3>
            <p>
              {changeDialog.member.name}님의
              {changeDialog.type === 'role'
                ? ' 권한'
                : changeDialog.type === 'status'
                  ? ' 상태'
                  : ' 신분'}을 선택하세요.
            </p>
            <select
              className={styles.filterSelect}
              value={changeDialog.value}
              onChange={(event) =>
                setChangeDialog((prev) => ({
                  ...prev,
                  value: event.target.value,
                }))
              }
            >
              {(changeDialog.type === 'role'
                ? ROLE_OPTIONS
                : changeDialog.type === 'status'
                  ? STATUS_OPTIONS
                  : GRADE_OPTIONS
              ).map((option) => (
                <option key={option} value={option}>
                  {(changeDialog.type === 'role'
                    ? ROLE_LABELS[option]
                    : changeDialog.type === 'status'
                      ? STATUS_LABELS[option]
                      : GRADE_LABELS[option]) || option}
                </option>
              ))}
            </select>
            <div className={styles.modalActions}>
              <button
                type="button"
                className={styles.actionButton}
                onClick={closeChangeDialog}
              >
                취소
              </button>
              <button
                type="button"
                className={styles.actionButton}
                onClick={confirmChangeDialog}
                disabled={Boolean(isChangingById[changeDialog.member.id])}
              >
                {isChangingById[changeDialog.member.id] ? '처리 중...' : '변경'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminMemberManage;
