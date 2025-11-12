import { useEffect, useState } from 'react';
import './Attendance.css';
import { attendanceSessionApi, attendanceRoundApi, attendanceCheckInApi } from '../utils/attendanceApi';

const Attendance = () => {
  const [sessions, setSessions] = useState([]);
  const [selectedSession, setSelectedSession] = useState(null);
  const [rounds, setRounds] = useState([]);
  const [selectedRound, setSelectedRound] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [checkingIn, setCheckingIn] = useState(false);
  const [success, setSuccess] = useState(null);
  const [userName, setUserName] = useState('');

  // 공개 세션 목록 로드
  useEffect(() => {
    const loadSessions = async () => {
      try {
        setLoading(true);
        console.log('📋 공개 세션 조회 시작');

        const data = await attendanceSessionApi.getPublicSessions();

        console.log('✅ 공개 세션 조회 성공:', data);
        setSessions(Array.isArray(data) ? data : []);
        setError(null);
      } catch (err) {
        console.error('❌ 공개 세션 조회 실패:', err);
        setError('세션을 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    loadSessions();
  }, []);

  // 세션 선택시 라운드 로드
  const handleSelectSession = async (session) => {
    try {
      setSelectedSession(session);
      setSelectedRound(null);
      setRounds([]);
      setLoading(true);

      console.log('📋 라운드 조회 시작:', session.attendanceSessionId);

      const roundData = await attendanceRoundApi.getRoundsBySession(session.attendanceSessionId);

      console.log('✅ 라운드 조회 성공:', roundData);

      // 각 라운드의 시간 정보 로깅
      if (Array.isArray(roundData)) {
        const now = new Date();
        const currentTime = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
        const currentDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

        console.log(`⏰ 클라이언트 현재 시간: ${currentTime}, 현재 날짜: ${currentDate}`);
        roundData.forEach((round) => {
          console.log(`📅 라운드 "${round.roundName}": 날짜=${round.roundDate}, 시작=${round.startTime}, 허용분=${round.allowedMinutes}, 상태=${round.roundStatus}`);
        });
      }

      setRounds(Array.isArray(roundData) ? roundData : []);
      setError(null);
    } catch (err) {
      console.error('❌ 라운드 조회 실패:', err);
      setError('라운드 정보를 불러오지 못했습니다.');
      setRounds([]);
    } finally {
      setLoading(false);
    }
  };

  // 위치 정보 가져오기 (Promise 기반)
  const getLocation = () => {
    return new Promise((resolve) => {
      const defaultLocation = {
        latitude: 37.4979,  // Default: Seoul
        longitude: 127.0276, // Default: Seoul
      };

      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          (position) => {
            resolve({
              latitude: position.coords.latitude,
              longitude: position.coords.longitude,
            });
          },
          (error) => {
            console.warn('위치 정보 수집 실패, 기본값 사용:', error);
            resolve(defaultLocation);
          },
          {
            enableHighAccuracy: false,
            timeout: 5000,
            maximumAge: 0,
          }
        );
      } else {
        console.warn('Geolocation API 미지원, 기본값 사용');
        resolve(defaultLocation);
      }
    });
  };

  // 라운드 선택시 체크인 실행
  const handleCheckIn = async (round) => {
    if (!selectedSession) {
      alert('세션을 선택해주세요.');
      return;
    }

    // 라운드 상태 확인
    if (round.roundStatus === 'UPCOMING') {
      alert('아직 출석 시간이 아닙니다. 시작 시간을 기다려주세요.');
      return;
    }

    if (round.roundStatus === 'CLOSED') {
      alert('출석 시간이 종료되었습니다.');
      return;
    }

    const confirmCheckIn = window.confirm(
      `'${selectedSession.title}' 세션의 '${round.roundId}' 라운드에 출석하시겠습니까?`
    );

    if (!confirmCheckIn) return;

    try {
      setCheckingIn(true);
      setError(null);
      setSuccess(null);

      // 사용자 위치 정보 가져오기
      const location = await getLocation();

      const checkInData = {
        roundId: round.roundId,
        latitude: location.latitude,
        longitude: location.longitude,
        userName: userName || undefined,  // 이름 입력 시에만 전송
      };

      console.log('🎯 출석 체크인 시작:', checkInData);

      const result = await attendanceCheckInApi.checkInByRound(checkInData);

      console.log('✅ 출석 체크인 성공:', result);
      setSuccess('출석 완료되었습니다!');
      setSelectedRound(null);
      setUserName('');  // 입력한 이름 초기화

      // 2초 후 메시지 자동 사라짐
      setTimeout(() => setSuccess(null), 2000);
    } catch (err) {
      console.error('❌ 출석 체크인 실패:', err);

      // 에러 응답 처리 (failureReason 포함)
      let errorMessage = err.message || '출석 처리에 실패했습니다.';
      if (err.response?.data?.failureReason) {
        errorMessage = err.response.data.failureReason;
      }
      setError(errorMessage);
    } finally {
      setCheckingIn(false);
    }
  };

  return (
    <div style={{ padding: '20px', maxWidth: '600px', margin: '0 auto' }}>
      <div style={{ marginBottom: '30px' }}>
        <h1 style={{ fontSize: '24px', fontWeight: 'bold', marginBottom: '20px' }}>
          출석하기
        </h1>

        {error && (
          <div
            style={{
              padding: '12px',
              backgroundColor: '#ffebee',
              color: '#c62828',
              borderRadius: '4px',
              marginBottom: '15px',
            }}
          >
            {error}
          </div>
        )}

        {success && (
          <div
            style={{
              padding: '12px',
              backgroundColor: '#e8f5e9',
              color: '#2e7d32',
              borderRadius: '4px',
              marginBottom: '15px',
            }}
          >
            {success}
          </div>
        )}

        {loading && (
          <div style={{ padding: '20px', textAlign: 'center', color: '#666' }}>
            로드 중...
          </div>
        )}

        {!loading && sessions.length === 0 ? (
          <div style={{ padding: '20px', textAlign: 'center', color: '#999' }}>
            출석 가능한 세션이 없습니다.
          </div>
        ) : (
          <>
            {/* 세션 목록 */}
            <div style={{ marginBottom: '20px' }}>
              <h2 style={{ fontSize: '16px', fontWeight: '600', marginBottom: '10px' }}>
                세션 선택
              </h2>
              <div style={{ display: 'grid', gap: '10px' }}>
                {sessions.map((session) => (
                  <div
                    key={session.attendanceSessionId}
                    onClick={() => handleSelectSession(session)}
                    style={{
                      padding: '15px',
                      border:
                        selectedSession?.attendanceSessionId === session.attendanceSessionId
                          ? '2px solid #4CAF50'
                          : '1px solid #ddd',
                      borderRadius: '8px',
                      cursor: 'pointer',
                      backgroundColor:
                        selectedSession?.attendanceSessionId === session.attendanceSessionId
                          ? '#f1f8f4'
                          : '#fff',
                      transition: 'all 0.2s',
                    }}
                  >
                    <div style={{ fontWeight: '600', marginBottom: '5px' }}>
                      {session.title}
                    </div>
                    <div style={{ fontSize: '12px', color: '#666' }}>
                      코드: {session.code} | 시간: {session.startsAt}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 이름 입력 (익명 사용자용) */}
            {selectedSession && (
              <div style={{ marginBottom: '20px' }}>
                <h2 style={{ fontSize: '16px', fontWeight: '600', marginBottom: '10px' }}>
                  이름 입력 (선택사항)
                </h2>
                <input
                  type="text"
                  value={userName}
                  onChange={(e) => setUserName(e.target.value)}
                  placeholder="출석자 이름을 입력해주세요 (입력하지 않으면 익명 사용자로 기록됩니다)"
                  style={{
                    width: '100%',
                    padding: '10px',
                    border: '1px solid #ddd',
                    borderRadius: '4px',
                    fontSize: '14px',
                    boxSizing: 'border-box',
                  }}
                />
              </div>
            )}

            {/* 라운드 목록 */}
            {selectedSession && (
              <div>
                <h2 style={{ fontSize: '16px', fontWeight: '600', marginBottom: '10px' }}>
                  라운드 선택
                </h2>
                {rounds.length === 0 ? (
                  <div style={{ padding: '15px', textAlign: 'center', color: '#999' }}>
                    출석 가능한 라운드가 없습니다.
                  </div>
                ) : (
                  <div style={{ display: 'grid', gap: '10px' }}>
                    {rounds.map((round) => {
                      const isDisabled = checkingIn || round.roundStatus === 'UPCOMING' || round.roundStatus === 'CLOSED';
                      let buttonColor = '#4CAF50';
                      let statusMessage = '';

                      if (round.roundStatus === 'UPCOMING') {
                        buttonColor = '#FFC107';
                        statusMessage = ' (시작 전)';
                      } else if (round.roundStatus === 'CLOSED') {
                        buttonColor = '#f44336';
                        statusMessage = ' (종료됨)';
                      } else if (checkingIn) {
                        buttonColor = '#ccc';
                      }

                      return (
                        <button
                          key={round.roundId}
                          onClick={() => handleCheckIn(round)}
                          disabled={isDisabled}
                          style={{
                            padding: '15px',
                            border: 'none',
                            borderRadius: '8px',
                            backgroundColor: buttonColor,
                            color: 'white',
                            fontSize: '14px',
                            fontWeight: '600',
                            cursor: isDisabled ? 'not-allowed' : 'pointer',
                            transition: 'background-color 0.2s',
                            opacity: isDisabled ? 0.6 : 1,
                          }}
                          onMouseOver={(e) => {
                            if (!isDisabled && round.roundStatus === 'ACTIVE') {
                              e.target.style.backgroundColor = '#45a049';
                            }
                          }}
                          onMouseOut={(e) => {
                            if (!isDisabled && round.roundStatus === 'ACTIVE') {
                              e.target.style.backgroundColor = '#4CAF50';
                            }
                          }}
                        >
                          {checkingIn ? (
                            '처리 중...'
                          ) : (
                            <>
                              <div style={{ marginBottom: '5px' }}>
                                {round.roundId}
                                {statusMessage}
                              </div>
                              <div style={{ fontSize: '12px' }}>
                                {round.roundDate} {round.startTime} ({round.allowedMinutes}분)
                              </div>
                              <div style={{ fontSize: '11px', marginTop: '5px', opacity: 0.9 }}>
                                상태: {round.roundStatus}
                              </div>
                            </>
                          )}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default Attendance;
