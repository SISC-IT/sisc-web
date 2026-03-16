import styles from './SessionManagementCard.module.css';
import calendarAddIcon from '../../assets/calendar-icon.svg';
import menuIcon from '../../assets/menu-icon.svg';
import xIcon from '../../assets/x-icon.svg';
import fileIcon from '../../assets/file-icon.svg';
import pencilIcon from '../../assets/pencil-icon.svg';

import { useEffect, useState, useRef } from 'react'; // useRef 추가
import { toast } from 'react-toastify';
import ConfirmationToast from './ConfirmationToast';
import SessionModifyModal from './SessionModifyModal';
import { useAttendance } from '../../contexts/AttendanceContext';
import { getRounds } from '../../utils/attendanceManage';

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${month}/${day}`;
};

const SessionManagementCard = ({ styles: commonStyles }) => {
  const {
    sessions,
    roundsVersion,
    openAddRoundsModal,
    selectedSessionId,
    setSelectedSessionId,
    handleDeleteSession, // Context에서 가져온 삭제 함수
    openSessionModifyModal,
    closeSessionModifyModal,
    isSessionModifyModalOpen,
    handleSessionChange,
    handleDeleteRound,
  } = useAttendance();

  const [currentDisplayedRounds, setCurrentDisplayedRounds] = useState([]);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuRef = useRef(null);

  const sessionList = sessions || [];
  const currentSession = sessionList.find(
    (session) => String(session.sessionId) === String(selectedSessionId)
  );

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setIsMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    const fetchRounds = async () => {
      if (!selectedSessionId) {
        setCurrentDisplayedRounds([]);
        return;
      }
      try {
        const rounds = await getRounds(selectedSessionId);
        setCurrentDisplayedRounds(rounds || []);
      } catch (e) {
        toast.error('라운드를 불러오지 못했습니다.');
        setCurrentDisplayedRounds([]);
      }
    };
    fetchRounds();
  }, [selectedSessionId, roundsVersion]);

  // 세션 수정 클릭 핸들러
  const onEditClick = () => {
    if (!currentSession) {
      toast.error('세션을 먼저 선택해주세요.');
      return;
    }
    openSessionModifyModal();
    setIsMenuOpen(false);
  };

  // 세션 삭제 클릭 핸들러
  const onDeleteClick = () => {
    setIsMenuOpen(false);

    toast(
      ({ closeToast }) => (
        <ConfirmationToast
          message={`"${currentSession?.session.title}" 세션을 정말 삭제하시겠습니까?`}
          onConfirm={async () => {
            try {
              if (selectedSessionId) {
                await handleDeleteSession(selectedSessionId);
                toast.success('세션이 삭제되었습니다.');
              }
            } catch (error) {
              toast.error('세션 삭제에 실패했습니다.');
            }
          }}
          closeToast={closeToast}
        />
      ),
      {
        position: 'top-center',
        autoClose: false,
        closeOnClick: false,
        draggable: false,
        closeButton: false,
      }
    );
  };

  return (
    <div className={styles.sessionManagementCardContainer}>
      <div className={commonStyles.header}>
        <div className={styles.titleArea}>
          <img src={fileIcon} alt="세션 아이콘" className={styles.titleIcon} />
          <h1>세션 관리</h1>
        </div>
        <div className={commonStyles.buttonGroup}>
          <div className={styles.selectGroup}>
            <select
              className={styles.sessionSelect}
              value={selectedSessionId || ''}
              onChange={(e) => setSelectedSessionId(e.target.value)}
            >
              <option value="" disabled>
                ------ 세션을 선택하세요 ------
              </option>
              {sessionList.map((session) => (
                <option key={session.sessionId} value={session.sessionId}>
                  {session.session.title}
                </option>
              ))}
            </select>
          </div>

          <button
            className={commonStyles.iconButton}
            onClick={() => {
              if (!currentSession) {
                toast.error('세션을 먼저 선택해주세요.');
                return;
              }
              openAddRoundsModal();
            }}
          >
            <div className={commonStyles.iconGroup}>
              <img src={calendarAddIcon} alt="회차 추가" />
              <div className={commonStyles.text}>출석일자 추가</div>
            </div>
          </button>

          {/* 메뉴 영역 */}
          <div className={styles.menuWrapper} ref={menuRef}>
            <button
              className={styles.menuButton}
              onClick={() => {
                if (!currentSession) {
                  toast.error('세션을 먼저 선택해주세요.');
                  return;
                }
                setIsMenuOpen(!isMenuOpen);
              }}
            >
              <img src={menuIcon} alt="메뉴" />
            </button>

            {isMenuOpen && (
              <div className={styles.dropdownMenu}>
                <button onClick={onEditClick}>
                  <img src={pencilIcon} alt="수정" />
                  <span>세션 수정하기</span>
                </button>
                <button onClick={onDeleteClick} className={styles.deleteBtn}>
                  <img src={xIcon} alt="삭제" />
                  <span>세션 삭제하기</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      <div className={styles.tableGroup}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>일자</th>
              <th>시간</th>
              <th>가능(분)</th>
              <th>회차</th>
              <th>QR 코드</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {currentDisplayedRounds.length > 0 ? (
              currentDisplayedRounds.map((round, index) => {
                const startTime = new Date(round.startAt);
                const closeTime = new Date(round.closeAt);
                const minutes = Math.floor((closeTime - startTime) / 60000);

                return (
                  <tr key={round.roundId}>
                    <td>{formatDate(round.roundDate)}</td>
                    <td>
                      {startTime.toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </td>
                    <td>{minutes}</td>
                    <td>{index + 1}</td>
                    <td>
                      <button
                        className={styles.qrButton}
                        onClick={() =>
                          window.open(
                            `/attendance/admin/qr?roundId=${round.roundId}`,
                            '_blank'
                          )
                        }
                      >
                        생성
                      </button>
                    </td>
                    <td>
                      <button
                        className={styles.deleteRoundButton}
                        onClick={() => {
                          toast(
                            ({ closeToast }) => (
                              <ConfirmationToast
                                message={`${index + 1}회차를 정말 삭제하시겠습니까?`}
                                onConfirm={async () => {
                                  try {
                                    await handleDeleteRound(round.roundId);
                                    toast.success('회차가 삭제되었습니다.');
                                  } catch (error) {
                                    toast.error('회차 삭제에 실패했습니다.');
                                  }
                                }}
                                closeToast={closeToast}
                              />
                            ),
                            {
                              position: 'top-center',
                              autoClose: false,
                              closeOnClick: false,
                              draggable: false,
                              closeButton: false,
                            }
                          );
                        }}
                      >
                        <img src={xIcon} alt="라운드삭제" />
                      </button>
                    </td>
                  </tr>
                );
              })
            ) : (
              <tr>
                <td colSpan="6" className={styles.noData}>
                  회차 정보가 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {isSessionModifyModalOpen && currentSession && (
        <SessionModifyModal
          styles={commonStyles}
          onClose={closeSessionModifyModal}
          session={currentSession}
          onSave={async (sessionId, data) => {
            try {
              await handleSessionChange(sessionId, data);
              toast.success('세션이 수정되었습니다.');
            } catch (error) {
              toast.error('세션 수정에 실패했습니다.');
            }
          }}
        />
      )}
    </div>
  );
};

export default SessionManagementCard;
