import { useState } from 'react';
import styles from './RoundAddingCard.module.css';
import { attendanceRoundApi } from '../../utils/attendanceApi';

const RoundAddingCard = ({ sessions, selectedSessionId, onRoundAdded }) => {
  const [roundName, setRoundName] = useState('');
  const [hh, setHh] = useState('');
  const [mm, setMm] = useState('');
  const [ss, setSs] = useState('');
  const [availableTimeMm, setAvailableTimeMm] = useState('');
  const [loading, setLoading] = useState(false);

  const selectedSession = sessions.find(
    (session) => session.id === selectedSessionId
  );

  const isFormValid = () => {
    if (!selectedSessionId) {
      alert('세션을 선택해주세요.');
      return false;
    }
    if (!roundName.trim()) {
      alert('라운드 이름을 입력해주세요.');
      return false;
    }
    if (isNaN(hh) || hh < 0 || hh > 23) {
      alert('시간은 0-23 사이의 숫자로 입력해주세요.');
      return false;
    }
    if (isNaN(mm) || mm < 0 || mm > 59) {
      alert('분은 0-59 사이의 숫자로 입력해주세요.');
      return false;
    }
    if (isNaN(ss) || ss < 0 || ss > 59) {
      alert('초는 0-59 사이의 숫자로 입력해주세요.');
      return false;
    }
    if (isNaN(availableTimeMm) || availableTimeMm < 1 || availableTimeMm > 120) {
      alert('출석 가능 시간은 1-120 사이의 분으로 입력해주세요.');
      return false;
    }
    return true;
  };

  const handleAddRound = async () => {
    if (!isFormValid()) return;

    setLoading(true);

    try {
      // 사용자가 입력한 시간 또는 현재 시간 + 1분 사용
      let startHour = hh;
      let startMinute = mm;
      let startSecond = ss;

      // 사용자 입력값이 없으면 현재 시간 + 1분으로 자동 설정
      if (!startHour || !startMinute || !startSecond) {
        const now = new Date();
        startHour = String(now.getHours()).padStart(2, '0');
        startMinute = String((now.getMinutes() + 1) % 60).padStart(2, '0');
        startSecond = String(now.getSeconds()).padStart(2, '0');
      } else {
        // 사용자 입력값도 zero-padding 적용
        startHour = String(startHour).padStart(2, '0');
        startMinute = String(startMinute).padStart(2, '0');
        startSecond = String(startSecond).padStart(2, '0');
      }

      const roundData = {
        roundDate: new Date().toISOString().split('T')[0],
        startTime: `${startHour}:${startMinute}:${startSecond}`,
        allowedMinutes: parseInt(availableTimeMm, 10),
        roundName: roundName,
      };

      console.log('📋 라운드 생성 시작:', {
        sessionId: selectedSessionId,
        roundName: roundName,
        ...roundData,
      });

      const roundResponse = await attendanceRoundApi.createRound(
        selectedSessionId,
        roundData
      );

      console.log('✅ 라운드 생성 성공:', {
        roundId: roundResponse.roundId,
        roundDate: roundResponse.roundDate,
        startTime: roundResponse.startTime,
        allowedMinutes: roundResponse.allowedMinutes,
      });

      onRoundAdded({
        id: roundResponse.roundId,
        date: roundResponse.roundDate,
        startTime: roundResponse.startTime,
        availableMinutes: roundResponse.allowedMinutes,
        status: roundResponse.roundStatus,
        name: roundName,
        participants: [],
      });

      // 입력 초기화
      setRoundName('');
      setHh('');
      setMm('');
      setSs('');
      setAvailableTimeMm('');

      alert('라운드가 추가되었습니다.');
    } catch (err) {
      console.error('라운드 추가 실패:', err);
      alert('라운드 추가에 실패했습니다. 콘솔을 확인해주세요.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.RoundAddingCardContainer}>
      <header style={{
        backgroundColor: '#f0f0f0',
        padding: '15px 20px',
        borderRadius: '8px 8px 0 0',
        borderBottom: '2px solid #e0e0e0',
      }}>
        <h1 style={{ margin: 0, fontSize: '16px', fontWeight: '600' }}>
          라운드 추가
        </h1>
      </header>

      {!selectedSessionId ? (
        <div style={{ padding: '20px', textAlign: 'center', color: '#999' }}>
          세션을 선택하면 라운드를 추가할 수 있습니다.
        </div>
      ) : (
        <div style={{ padding: '20px' }}>
          <div style={{ marginBottom: '15px' }}>
            <strong>선택된 세션:</strong> {selectedSession?.title}
            <span style={{ marginLeft: '10px', fontSize: '12px', color: '#666' }}>
              ({selectedSession?.code})
            </span>
          </div>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: '600' }}>
              라운드 이름 (예: 1주차, 2주차)
            </label>
            <input
              type="text"
              value={roundName}
              onChange={(e) => setRoundName(e.target.value)}
              placeholder="예: 1주차, 2주차, 3월 정기 모임"
              style={{
                width: '100%',
                padding: '8px 10px',
                border: '1px solid #ddd',
                borderRadius: '4px',
                fontSize: '14px',
                boxSizing: 'border-box',
              }}
            />
          </div>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: '600' }}>
              출석 시작 시간
            </label>
            <div style={{ display: 'flex', gap: '5px' }}>
              <input
                type="text"
                value={hh}
                maxLength="2"
                onChange={(e) => setHh(e.target.value)}
                placeholder="시"
                style={{
                  flex: 1,
                  padding: '8px 10px',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  fontSize: '14px',
                }}
              />
              <span style={{ alignSelf: 'center' }}>:</span>
              <input
                type="text"
                value={mm}
                maxLength="2"
                onChange={(e) => setMm(e.target.value)}
                placeholder="분"
                style={{
                  flex: 1,
                  padding: '8px 10px',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  fontSize: '14px',
                }}
              />
              <span style={{ alignSelf: 'center' }}>:</span>
              <input
                type="text"
                value={ss}
                maxLength="2"
                onChange={(e) => setSs(e.target.value)}
                placeholder="초"
                style={{
                  flex: 1,
                  padding: '8px 10px',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  fontSize: '14px',
                }}
              />
            </div>
          </div>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontWeight: '600' }}>
              출석 가능 시간 (분)
            </label>
            <input
              type="text"
              value={availableTimeMm}
              maxLength="3"
              onChange={(e) => setAvailableTimeMm(e.target.value)}
              placeholder="예: 30"
              style={{
                width: '100%',
                padding: '8px 10px',
                border: '1px solid #ddd',
                borderRadius: '4px',
                fontSize: '14px',
                boxSizing: 'border-box',
              }}
            />
          </div>

          <button
            onClick={handleAddRound}
            disabled={loading}
            style={{
              width: '100%',
              padding: '10px',
              backgroundColor: loading ? '#ccc' : '#4CAF50',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              fontSize: '14px',
              fontWeight: '600',
              cursor: loading ? 'not-allowed' : 'pointer',
            }}
          >
            {loading ? '추가 중...' : '라운드 추가'}
          </button>
        </div>
      )}
    </div>
  );
};

export default RoundAddingCard;
