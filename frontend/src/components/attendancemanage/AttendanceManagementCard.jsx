import styles from './AttendanceManagementCard.module.css';
import { toast } from 'react-toastify';
import { useAttendance } from '../../contexts/AttendanceContext';
import { useEffect, useRef, useState } from 'react';
import { getUsers } from '../../utils/attendanceManage';
import fileIcon from '../../assets/file-icon.svg';
import addUserIcon from '../../assets/add-user-icon.svg';
import profileIcon from '../../assets/profile-icon.svg';
import binIcon from '../../assets/bin-icon.svg';
import slashProfileIcon from '../../assets/slash-profile-icon.svg';
import ConfirmationToast from './ConfirmationToast';

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
  const cardRef = useRef(null);

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
    const fetchAttendanceSheet = async () => {
      if (!selectedSessionId) {
        setAttendanceData({ sessionTitle: '', rounds: [], userRows: [] });
        setSelectedUserIds(new Set());
        return;
      }

      try {
        const data = await getUsers(selectedSessionId);
        setAttendanceData(data);
      } catch (error) {
        console.error('출석부 조회 실패:', error);
        setAttendanceData({ sessionTitle: '', rounds: [], userRows: [] });
      }
    };
    fetchAttendanceSheet();
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

  const confirmAction = (message, onConfirm) => {
    if (selectedUserIds.size === 0) return alert('대상을 선택해주세요.');
    if (activeToastId) toast.dismiss(activeToastId);

    const handleConfirm = async () => {
      await onConfirm(selectedSessionId, Array.from(selectedUserIds));
      setSelectedUserIds(new Set()); // 성공 후 선택 초기화
      toast.dismiss();
    };

    const toastId = toast(
      <ConfirmationToast
        onConfirm={handleConfirm}
        onCancel={() => toast.dismiss()}
        message={`${selectedUserIds.size}명의 유저에 대해 ${message}`}
      />,
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

    const handleConfirm = async () => {
      await onConfirm(selectedSessionId, Array.from(selectedUserIds));
      setSelectedUserIds(new Set());
      toast.dismiss();
    };

    const toastId = toast(
      <ConfirmationToast
        onConfirm={handleConfirm}
        title={title}
        description={description}
        confirmLabel="권한 변경하기"
        cancelLabel="취소"
        variant="roleChange"
      />,
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
  const onDeleteUsersClick = () =>
    confirmAction('유저를 삭제하시겠습니까?', handleDeleteUsers);

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
        </div>
      </header>

      <div className={styles.tableGroup}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th style={{ width: '40px', textAlign: 'center' }}>
                <input
                  type="checkbox"
                  onChange={toggleAllUsers}
                  checked={
                    attendanceData.userRows.length > 0 &&
                    selectedUserIds.size === attendanceData.userRows.length
                  }
                />
              </th>
              <th style={{ width: '100px' }}>이름</th>
              <th style={{ width: '100px' }}>역할</th>
              <th style={{ width: '140px' }}>학번</th>
              {attendanceData.rounds.map((round, index) => (
                <th
                  key={round.roundId}
                  style={{
                    minWidth: '110px',
                    width:
                      index === attendanceData.rounds.length - 1
                        ? 'auto'
                        : '110px',
                  }}
                >
                  {round.roundNumber}회차
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {attendanceData.userRows.length > 0 ? (
              attendanceData.userRows.map((user) => (
                <tr
                  key={user.userId}
                  className={
                    selectedUserIds.has(user.userId) ? styles.selectedRow : ''
                  }
                >
                  <td style={{ textAlign: 'center' }}>
                    <input
                      type="checkbox"
                      checked={selectedUserIds.has(user.userId)}
                      onChange={() => toggleUserSelection(user.userId)}
                    />
                  </td>
                  <td>{user.userName}</td>
                  <td>{user.role}</td>
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
              ))
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
    </div>
  );
};

export default AttendanceManagementCard;
