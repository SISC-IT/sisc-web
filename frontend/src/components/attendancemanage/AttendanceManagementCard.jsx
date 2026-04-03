import styles from './AttendanceManagementCard.module.css';
import { toast } from 'react-toastify';
import { useAttendance } from '../../contexts/AttendanceContext';
import { useEffect, useMemo, useRef, useState } from 'react';
import { getUsers } from '../../utils/attendanceManage';
import fileIcon from '../../assets/file-icon.svg';
import addUserIcon from '../../assets/add-user-icon.svg';
import profileIcon from '../../assets/profile-icon.svg';
import binIcon from '../../assets/bin-icon.svg';
import slashProfileIcon from '../../assets/slash-profile-icon.svg';
import ConfirmationToast from './ConfirmationToast';
import AbsenceSummaryModal from './AbsenceSummaryModal';

// 상태별 텍스트 및 스타일 통합 관리
const ATTENDANCE_CONFIG = {
  PRESENT: {
    label: '출석',
    className: 'statusPresent',
  },
  LATE: {
    label: '지각',
    className: 'statusLate',
  },
  ABSENT: {
    label: '결석',
    className: 'statusAbsent',
  },
  EXCUSED: {
    label: '공결',
    className: 'statusExcused',
  },
  PENDING: {
    label: '미정',
    className: 'statusPending',
  },
};

const ATTENDANCE_MENU_ORDER = [
  'PRESENT',
  'LATE',
  'ABSENT',
  'EXCUSED',
  'PENDING',
];

const getRoleDisplayLabel = (role) => {
  const roleText = String(role || '').trim();
  const normalized = roleText.toUpperCase();

  if (normalized.includes('OWNER')) {
    return '소유자';
  }

  if (normalized.includes('MANAGE')) {
    return '관리자';
  }

  if (
    normalized.includes('PARTICIPANT')
  ) {
    return '팀원';
  }

  return roleText || '팀원';
};

const EMPTY_ATTENDANCE_DATA = {
  sessionTitle: '',
  rounds: [],
  userRows: [],
};

const getRoleSortPriority = (role) => {
  const normalized = String(role || '').trim().toUpperCase();

  if (normalized.includes('OWNER') || String(role || '').includes('세션 생성자')) {
    return 0;
  }

  if (normalized.includes('MANAGE')) {
    return 1;
  }

  return 2;
};

const AttendanceStatusDropdown = ({
  value,
  statusClass,
  isOpen,
  onToggle,
  onSelect,
}) => {
  return (
    <div
      className={`${styles.attendanceSelectWrap} ${statusClass} ${
        isOpen ? styles.open : ''
      }`}
    >
      <button
        type="button"
        className={styles.attendanceSelect}
        onClick={onToggle}
      >
        {ATTENDANCE_CONFIG[value]?.label || ATTENDANCE_CONFIG.PENDING.label}
      </button>

      {isOpen && (
        <div className={styles.attendanceDropdownMenu}>
          {ATTENDANCE_MENU_ORDER.map((optionValue) => {
            const label = ATTENDANCE_CONFIG[optionValue]?.label;
            if (!label) return null;

            return (
              <button
                key={optionValue}
                type="button"
                className={`${styles.attendanceDropdownItem} ${
                  optionValue === value ? styles.activeDropdownItem : ''
                }`}
                onClick={() => onSelect(optionValue)}
              >
                {label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
};

const AttendanceManagementCard = ({ styles: commonStyles }) => {
  const USERS_PER_PAGE = 10;
  const {
    selectedSessionId,
    handleAttendanceChange,
    roundAttendanceVersion,
    roundsVersion,
    openAddUsersModal,
    handleAddManager,
    handleRemoveManager,
    handleDeleteUsers,
  } = useAttendance();

  const [attendanceData, setAttendanceData] = useState({
    sessionTitle: '',
    rounds: [],
    userRows: [],
  });
  const [selectedUserIds, setSelectedUserIds] = useState(new Set());
  const [activeToastId, setActiveToastId] = useState(null);
  const [openDropdownKey, setOpenDropdownKey] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [isAbsenceModalOpen, setIsAbsenceModalOpen] = useState(false);
  const cardRef = useRef(null);
  const fetchRequestIdRef = useRef(0);

  const sortedUserRows = useMemo(() => {
    if (!attendanceData.rounds.length) {
      return attendanceData.userRows
        .map((user, index) => ({ user, index }))
        .sort((a, b) => {
          const priorityDiff =
            getRoleSortPriority(a.user.role) - getRoleSortPriority(b.user.role);
          if (priorityDiff !== 0) return priorityDiff;
          return a.index - b.index;
        })
        .map((item) => item.user);
    }

    // 로컬 시간대 기준으로 오늘 날짜(YYYY-MM-DD)와 정렬된 회차 목록 생성
    const sortedRounds = [...attendanceData.rounds].sort(
      (a, b) =>
        a.roundDate.localeCompare(b.roundDate) || a.roundNumber - b.roundNumber
    );
    const todayStr = new Date().toLocaleDateString('sv-SE');

    const targetRound =
      sortedRounds.find((r) => r.roundDate === todayStr) ||
      sortedRounds[sortedRounds.length - 1];

    const targetRoundId = targetRound?.roundId;

    return [...attendanceData.userRows].sort((a, b) => {
      const aAtt = a.attendances.find((att) => att.roundId === targetRoundId);
      const bAtt = b.attendances.find((att) => att.roundId === targetRoundId);

      const aStatus = aAtt?.status || 'PENDING';
      const bStatus = bAtt?.status || 'PENDING';

      const getStatusPriority = (status) => {
        if (status === 'ABSENT') return 0;
        if (status === 'LATE') return 1;
        return 2;
      };

      const statusDiff = getStatusPriority(aStatus) - getStatusPriority(bStatus);
      if (statusDiff !== 0) return statusDiff;

      // Secondary sort by role
      const priorityDiff =
        getRoleSortPriority(a.role) - getRoleSortPriority(b.role);
      if (priorityDiff !== 0) return priorityDiff;

      return a.userName.localeCompare(b.userName, 'ko');
    });
  }, [attendanceData.userRows, attendanceData.rounds]);

  const totalUsers = sortedUserRows.length;
  const totalPages = Math.max(1, Math.ceil(totalUsers / USERS_PER_PAGE));
  const startIndex = (currentPage - 1) * USERS_PER_PAGE;
  const paginatedUserRows = sortedUserRows.slice(
    startIndex,
    startIndex + USERS_PER_PAGE
  );

  const getSelectedUsers = () =>
    attendanceData.userRows.filter((user) => selectedUserIds.has(user.userId));

  const isOwnerRole = (role) => {
    const normalized = String(role || '').trim().toUpperCase();
    return (
      normalized === 'OWNER' ||
      normalized.includes('OWNER') ||
      String(role || '').includes('세션 생성자')
    );
  };

  useEffect(() => {
    const requestId = ++fetchRequestIdRef.current;
    const isStale = () => requestId !== fetchRequestIdRef.current;

    // Dismiss any active confirmation toast when session changes
    if (activeToastId) {
      toast.dismiss(activeToastId);
      setActiveToastId(null);
    }

    setAttendanceData(EMPTY_ATTENDANCE_DATA);
    setSelectedUserIds(new Set());
    setCurrentPage(1);

    const fetchAttendanceSheet = async () => {
      if (!selectedSessionId) {
        return;
      }

      try {
        const data = await getUsers(selectedSessionId);
        if (isStale()) return;
        setAttendanceData(data || EMPTY_ATTENDANCE_DATA);
      } catch (error) {
        if (isStale()) return;
        console.error('출석부 조회 실패:', error);
        setAttendanceData(EMPTY_ATTENDANCE_DATA);
      }
    };

    fetchAttendanceSheet();

    return () => {
      if (fetchRequestIdRef.current === requestId) {
        fetchRequestIdRef.current += 1;
      }
    };
  }, [selectedSessionId, roundAttendanceVersion, roundsVersion]);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (cardRef.current && !cardRef.current.contains(event.target)) {
        setOpenDropdownKey(null);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages);
    }
  }, [currentPage, totalPages]);

  const confirmAction = (message, onConfirm) => {
    if (selectedUserIds.size === 0) return alert('대상을 선택해주세요.');
    if (activeToastId) toast.dismiss(activeToastId);

    const toastId = toast(
      ({ closeToast }) => (
        <ConfirmationToast
          closeToast={closeToast}
          onConfirm={async () => {
            const result = await onConfirm(selectedSessionId, Array.from(selectedUserIds));
            
            // Handle partial failures
            if (result?.failedIds && result.failedIds.length > 0) {
              // Keep only failed IDs selected for retry
              setSelectedUserIds(new Set(result.failedIds));
              // Don't close the toast, keep it open for retry
              return;
            }
            
            // All succeeded, clear selection and close
            setSelectedUserIds(new Set());
            closeToast?.();
          }}
          onCancel={() => closeToast?.()}
          message={`${selectedUserIds.size}명의 유저를 ${message}`}
        />
      ),
      {
        autoClose: false,
        closeOnClick: false,
        draggable: false,
        closeButton: false,
        onClose: () => setActiveToastId(null),
      }
    );
    setActiveToastId(toastId);
  };

  const confirmRoleChangeAction = (onConfirm, description) => {
    const selectedUsers = getSelectedUsers();
    if (selectedUsers.length === 0) {
      alert('대상을 선택해주세요.');
      return;
    }
    if (activeToastId) toast.dismiss(activeToastId);

    const firstName = selectedUsers[0].userName;
    const restCount = selectedUsers.length - 1;
    const title =
      restCount > 0
        ? `${firstName} 외 ${restCount}명의 권한을 변경하시겠습니까?`
        : `${firstName}님의 권한을 변경하시겠습니까?`;

    const toastId = toast(
      ({ closeToast }) => (
        <ConfirmationToast
          closeToast={closeToast}
          onConfirm={async () => {
            const result = await onConfirm(selectedSessionId, Array.from(selectedUserIds));
            
            // Handle partial failures
            if (result?.failedIds && result.failedIds.length > 0) {
              // Keep only failed IDs selected for retry
              setSelectedUserIds(new Set(result.failedIds));
              // Don't close the toast, keep it open for retry
              return;
            }
            
            // All succeeded, clear selection and close
            setSelectedUserIds(new Set());
            closeToast?.();
          }}
          onCancel={() => closeToast?.()}
          title={title}
          description={description}
          confirmLabel="권한 변경하기"
          cancelLabel="취소"
          variant="roleChange"
        />
      ),
      {
        autoClose: false,
        closeOnClick: false,
        draggable: false,
        closeButton: false,
        onClose: () => setActiveToastId(null),
      }
    );

    setActiveToastId(toastId);
  };

  // 1. 매니저로 격상 버튼 핸들러
  const onPromoteClick = () => {
    const selectedUsers = getSelectedUsers();
    const hasOwner = selectedUsers.some((user) => isOwnerRole(user.role));

    if (hasOwner) {
      alert('세션 생성자는 매니저가 될 수 없습니다.');
      return;
    }

    confirmRoleChangeAction(
      handleAddManager,
      '선택한 유저에게 매니저 권한을 부여합니다.'
    );
  };

  // 2. 매니저 권한 제거 버튼 핸들러
  const onDemoteClick = () =>
    confirmRoleChangeAction(
      handleRemoveManager,
      '관리자인 경우 일반 회원으로 변경됩니다.'
    );

  // 3. 유저 삭제(퇴출) 버튼 핸들러
  const onDeleteUsersClick = () => {
    const selectedUsers = getSelectedUsers();
    const hasOwner = selectedUsers.some((user) => isOwnerRole(user.role));

    if (hasOwner) {
      alert('세션 생성자를 삭제할 수 없습니다');
      return;
    }

    confirmAction('삭제하시겠습니까?', handleDeleteUsers);
  };

  const toggleUserSelection = (userId) => {
    const newSelection = new Set(selectedUserIds);
    newSelection.has(userId)
      ? newSelection.delete(userId)
      : newSelection.add(userId);
    setSelectedUserIds(newSelection);
  };

  const toggleAllUsers = () => {
    if (
      selectedUserIds.size === attendanceData.userRows.length &&
      attendanceData.userRows.length > 0
    ) {
      setSelectedUserIds(new Set());
    } else {
      setSelectedUserIds(new Set(attendanceData.userRows.map((u) => u.userId)));
    }
  };

  return (
    <div
      className={`${styles.attendanceManagementCardContainer} ${
        openDropdownKey ? styles.dropdownOpenContainer : ''
      }`}
      ref={cardRef}
    >
      <header className={commonStyles.header}>
        <div className={styles.headerLeft}>
          <div className={styles.titleArea}>
            <img
              src={fileIcon}
              alt="세션 아이콘"
              className={styles.sessionIcon}
            />
            <div>
              <h1>세션별 유저 관리</h1>
              <span className={styles.selectedCount}>
                <span>{selectedUserIds.size}</span>명 선택되었습니다.
              </span>
            </div>
          </div>
        </div>

        <div className={styles.actionButtonGroup}>
          <button
            className={styles.addUserButton}
            onClick={() => {
              openAddUsersModal();
            }}
          >
            <img src={addUserIcon} alt="" />
            세션 유저 추가
          </button>
          <div className={styles.buttonWithIcon}>
            <button
              className={styles.handleRoleButton}
              onClick={onPromoteClick}
              title="매니저 권한부여"
            >
              <img src={profileIcon} alt="매니저 권한부여" />
            </button>
            <button
              className={styles.buttonWithIcon}
              onClick={onDemoteClick}
              title="매니저 권한삭제"
            >
              <img src={slashProfileIcon} alt="매니저 권한삭제" />
            </button>
            <button
              className={styles.buttonWithIcon}
              onClick={onDeleteUsersClick}
              title="유저 삭제"
            >
              <img src={binIcon} alt="유저 삭제" />
            </button>
          </div>
          <button
            className={styles.absenceSummaryButton}
            onClick={() => setIsAbsenceModalOpen(true)}
          >
            결석 인원 모아보기
          </button>
        </div>
      </header>

      <div className={styles.tableGroup}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th className={styles.checkboxHeader}>
                <input
                  type="checkbox"
                  onChange={toggleAllUsers}
                  checked={
                    attendanceData.userRows.length > 0 &&
                    selectedUserIds.size === attendanceData.userRows.length
                  }
                />
              </th>
              <th className={styles.nameHeader}>이름</th>
              <th className={styles.roleHeader}>역할</th>
              <th className={styles.studentIdHeader}>학번</th>
              {attendanceData.rounds.map((round) => (
                <th key={round.roundId} className={styles.roundHeader}>
                  {round.roundNumber}회차
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {paginatedUserRows.length > 0 ? (
              paginatedUserRows.map((user) => {
                return (
                  <tr
                    key={user.userId}
                    className={
                      selectedUserIds.has(user.userId) ? styles.selectedRow : ''
                    }
                  >
                    <td className={styles.checkboxCell}>
                      <input
                        type="checkbox"
                        checked={selectedUserIds.has(user.userId)}
                        onChange={() => toggleUserSelection(user.userId)}
                      />
                    </td>
                    <td>{user.userName}</td>
                    <td>{getRoleDisplayLabel(user.role)}</td>
                    <td>{user.studentId}</td>
                    {user.attendances.map((att) => {
                      const statusClass =
                        styles[
                          ATTENDANCE_CONFIG[att.status]?.className ||
                            ATTENDANCE_CONFIG.PENDING.className
                        ];
                      const dropdownKey = `${user.userId}-${att.roundId}`;
                      const isOpen = openDropdownKey === dropdownKey;

                      return (
                        <td
                          key={att.roundId}
                          className={`${styles.statusCell} ${
                            isOpen ? styles.openStatusCell : ''
                          }`}
                        >
                          <AttendanceStatusDropdown
                            value={att.status}
                            statusClass={statusClass}
                            isOpen={isOpen}
                            onToggle={() =>
                              setOpenDropdownKey((prev) =>
                                prev === dropdownKey ? null : dropdownKey
                              )
                            }
                            onSelect={(nextStatus) => {
                              setOpenDropdownKey(null);
                              if (nextStatus !== att.status) {
                                handleAttendanceChange(
                                  user.userId,
                                  att.roundId,
                                  nextStatus
                                );
                              }
                            }}
                          />
                        </td>
                      );
                    })}
                  </tr>
                );
              })
            ) : (
              <tr>
                <td
                  colSpan={4 + attendanceData.rounds.length}
                  className={styles.noData}
                >
                  데이터가 존재하지 않습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {attendanceData.userRows.length > 0 && totalPages > 1 && (
        <div className={styles.paginationBar}>
          {Array.from({ length: totalPages }, (_, index) => {
            const pageNumber = index + 1;
            return (
              <button
                key={pageNumber}
                type="button"
                className={`${styles.pageButton} ${
                  currentPage === pageNumber ? styles.activePageButton : ''
                }`}
                onClick={() => setCurrentPage(pageNumber)}
              >
                {pageNumber}
              </button>
            );
          })}
        </div>
      )}
      {isAbsenceModalOpen && (
        <AbsenceSummaryModal
          isOpen={isAbsenceModalOpen}
          onClose={() => setIsAbsenceModalOpen(false)}
          userRows={attendanceData.userRows}
        />
      )}
    </div>
  );
};

export default AttendanceManagementCard;
