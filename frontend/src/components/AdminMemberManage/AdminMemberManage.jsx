import { useEffect, useMemo, useState } from 'react';
import { Search, Star } from 'lucide-react';
import styles from './AdminMemberManage.module.css';
import {
  changeAdminMemberRole,
  changeAdminMemberStatus,
  deleteAdminMember,
  getAdminMembersData,
  promoteAdminMemberSenior,
} from '../../utils/adminMembersData';

const ROLE_LABELS = {
  SYSTEM_ADMIN: '시스템관리자',
  PRESIDENT: '회장',
  VICE_PRESIDENT: '부회장',
  TEAM_LEADER: '팀장',
  TEAM_MEMBER: '일반',
  PENDING_MEMBER: '대기회원',
};

const STATUS_LABELS = {
  ACTIVE: '활성',
  INACTIVE: '비활성',
  GRADUATED: '졸업',
  OUT: '탈퇴',
};

const getRoleClassName = (role) => {
  if (role === '회장') return styles.rolePresident;
  if (role === '시스템관리자') return styles.rolePresident;
  if (role === '부회장') return styles.roleManager;
  if (role === '운영부') return styles.roleManager;
  if (role === '팀장') return styles.roleLeader;
  if (role === '대기회원') return styles.roleLeader;
  return styles.roleNormal;
};

const getStatusClassName = (status) => {
  if (status === '활성') return styles.statusActive;
  if (status === '비활성' || status === '졸업' || status === '탈퇴') return styles.statusInactive;
  return styles.statusPending;
};

const AdminMemberManage = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');
  const [statusFilter, setStatusFilter] = useState('all');
  const [members, setMembers] = useState([]);

  const loadMembers = async ({ keyword, role, status } = {}) => {
    try {
      const data = await getAdminMembersData({ keyword, role, status });
      setMembers(data.members || []);
    } catch (error) {
      window.alert(error?.message || '회원 목록을 불러오지 못했습니다.');
      setMembers([]);
    }
  };

  useEffect(() => {
    loadMembers();
  }, []);

  useEffect(() => {
    const backendRole = roleFilter === 'all' ? undefined : roleFilter;
    const backendStatus = statusFilter === 'all' ? undefined : statusFilter;
    loadMembers({
      keyword: searchQuery.trim() || undefined,
      role: backendRole,
      status: backendStatus,
    });
  }, [roleFilter, searchQuery, statusFilter]);

  const filteredMembers = useMemo(() => {
    return members.map((member) => ({
      ...member,
      displayRole: ROLE_LABELS[member.role] || member.role,
      displayStatus: STATUS_LABELS[member.status] || member.status,
    }));
  }, [members]);

  const handleRoleChange = async (member) => {
    const roleInput = window.prompt(
      '변경할 권한을 입력하세요.\n예: TEAM_MEMBER, TEAM_LEADER, VICE_PRESIDENT, PRESIDENT, SYSTEM_ADMIN, PENDING_MEMBER',
      member.role
    );

    if (!roleInput) return;

    try {
      await changeAdminMemberRole({ userId: member.id, role: roleInput });
      await loadMembers({
        keyword: searchQuery.trim() || undefined,
        role: roleFilter === 'all' ? undefined : roleFilter,
        status: statusFilter === 'all' ? undefined : statusFilter,
      });
    } catch (error) {
      window.alert(error?.message || '권한 변경에 실패했습니다.');
    }
  };

  const handleStatusChange = async (member) => {
    const statusInput = window.prompt(
      '변경할 상태를 입력하세요.\n예: ACTIVE, INACTIVE, GRADUATED, OUT',
      member.status
    );

    if (!statusInput) return;

    try {
      await changeAdminMemberStatus({ userId: member.id, status: statusInput });
      await loadMembers({
        keyword: searchQuery.trim() || undefined,
        role: roleFilter === 'all' ? undefined : roleFilter,
        status: statusFilter === 'all' ? undefined : statusFilter,
      });
    } catch (error) {
      window.alert(error?.message || '상태 변경에 실패했습니다.');
    }
  };

  const handlePromoteSenior = async (member) => {
    if (!window.confirm(`${member.name}님을 선배(SENIOR)로 전환하시겠습니까?`)) {
      return;
    }

    try {
      await promoteAdminMemberSenior({ userId: member.id });
      await loadMembers({
        keyword: searchQuery.trim() || undefined,
        role: roleFilter === 'all' ? undefined : roleFilter,
        status: statusFilter === 'all' ? undefined : statusFilter,
      });
    } catch (error) {
      window.alert(error?.message || '선배 전환에 실패했습니다.');
    }
  };

  const handleDelete = async (member) => {
    if (!window.confirm(`${member.name}님을 강제 탈퇴 처리하시겠습니까?`)) {
      return;
    }

    try {
      await deleteAdminMember({ userId: member.id });
      await loadMembers({
        keyword: searchQuery.trim() || undefined,
        role: roleFilter === 'all' ? undefined : roleFilter,
        status: statusFilter === 'all' ? undefined : statusFilter,
      });
    } catch (error) {
      window.alert(error?.message || '회원 삭제에 실패했습니다.');
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
          <option value="PRESIDENT">회장</option>
          <option value="VICE_PRESIDENT">부회장</option>
          <option value="TEAM_LEADER">팀장</option>
          <option value="TEAM_MEMBER">일반</option>
          <option value="PENDING_MEMBER">대기회원</option>
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
          <option value="OUT">탈퇴</option>
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
              <th>가입일</th>
              <th className={styles.rightAlign}>작업</th>
            </tr>
          </thead>
          <tbody>
            {filteredMembers.map((member) => (
              <tr key={member.id}>
                <td>
                  <div className={styles.memberInfo}>
                    <div className={styles.avatar}>{member.name[0]}</div>
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
                    className={`${styles.badge} ${getRoleClassName(member.displayRole)}`}
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
                <td>{member.generation ? `${member.generation}기` : '-'}</td>
                <td className={styles.rightAlign}>
                  <div className={styles.rowActions}>
                    <button
                      type="button"
                      className={styles.actionButton}
                      onClick={() => handleRoleChange(member)}
                    >
                      권한 변경
                    </button>
                    <button
                      type="button"
                      className={styles.actionButton}
                      onClick={() => handleStatusChange(member)}
                    >
                      상태 변경
                    </button>
                    <button
                      type="button"
                      className={styles.actionButton}
                      onClick={() => handlePromoteSenior(member)}
                    >
                      선배 전환
                    </button>
                    <button
                      type="button"
                      className={styles.actionButton}
                      onClick={() => handleDelete(member)}
                    >
                      회원 삭제
                    </button>
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
    </div>
  );
};

export default AdminMemberManage;
