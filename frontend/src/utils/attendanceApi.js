const API_BASE_URL = 'http://localhost:8080/api/attendance';

// 세션 관련 API
export const attendanceSessionApi = {
  // 세션 생성
  createSession: async (sessionData) => {
    const response = await fetch(`${API_BASE_URL}/sessions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
      body: JSON.stringify(sessionData),
    });
    if (!response.ok) throw new Error('세션 생성 실패');
    return response.json();
  },

  // 모든 세션 조회
  getAllSessions: async () => {
    const response = await fetch(`${API_BASE_URL}/sessions`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
    });
    if (!response.ok) throw new Error('세션 조회 실패');
    return response.json();
  },

  // 세션 상세 조회
  getSession: async (sessionId) => {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
    });
    if (!response.ok) throw new Error('세션 상세 조회 실패');
    return response.json();
  },

  // 세션 수정
  updateSession: async (sessionId, sessionData) => {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
      body: JSON.stringify(sessionData),
    });
    if (!response.ok) throw new Error('세션 수정 실패');
    return response.json();
  },

  // 세션 위치 재설정
  updateSessionLocation: async (sessionId, location) => {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}/location`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
      body: JSON.stringify(location),
    });
    if (!response.ok) throw new Error('세션 위치 재설정 실패');
    return response.json();
  },

  // 세션 삭제
  deleteSession: async (sessionId) => {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}`, {
      method: 'DELETE',
      headers: {
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
    });
    if (!response.ok) throw new Error('세션 삭제 실패');
  },

  // 공개 세션 목록 조회
  getPublicSessions: async () => {
    const token = localStorage.getItem('accessToken');
    const headers = {
      'Content-Type': 'application/json',
    };

    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(`${API_BASE_URL}/sessions/public`, {
      headers,
    });
    if (!response.ok) throw new Error('공개 세션 조회 실패');
    return response.json();
  },
};

// 라운드 관련 API
export const attendanceRoundApi = {
  // 라운드 생성
  createRound: async (sessionId, roundData) => {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}/rounds`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
      body: JSON.stringify(roundData),
    });
    if (!response.ok) throw new Error('라운드 생성 실패');
    return response.json();
  },

  // 라운드 조회
  getRound: async (roundId) => {
    const response = await fetch(`${API_BASE_URL}/rounds/${roundId}`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
    });
    if (!response.ok) throw new Error('라운드 조회 실패');
    return response.json();
  },

  // 세션별 라운드 목록 조회
  getRoundsBySession: async (sessionId) => {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}/rounds`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
    });
    if (!response.ok) throw new Error('라운드 목록 조회 실패');
    return response.json();
  },

  // 라운드 수정
  updateRound: async (roundId, roundData) => {
    const response = await fetch(`${API_BASE_URL}/rounds/${roundId}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
      body: JSON.stringify(roundData),
    });
    if (!response.ok) throw new Error('라운드 수정 실패');
    return response.json();
  },

  // 라운드 삭제
  deleteRound: async (roundId) => {
    const response = await fetch(`${API_BASE_URL}/rounds/${roundId}`, {
      method: 'DELETE',
      headers: {
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
    });
    if (!response.ok) throw new Error('라운드 삭제 실패');
  },

  // 날짜별 라운드 조회
  getRoundByDate: async (sessionId, date) => {
    const response = await fetch(
      `${API_BASE_URL}/sessions/${sessionId}/rounds/by-date?date=${date}`,
      {
        headers: {
          Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
        },
      }
    );
    if (!response.ok) throw new Error('날짜별 라운드 조회 실패');
    return response.json();
  },

  // 라운드별 출석 명단 조회
  getAttendancesByRound: async (roundId) => {
    const response = await fetch(
      `${API_BASE_URL}/rounds/${roundId}/attendances`,
      {
        headers: {
          Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
        },
      }
    );
    if (!response.ok) throw new Error('라운드별 출석 명단 조회 실패');
    return response.json();
  },
};

// 출석 체크인 API
export const attendanceCheckInApi = {
  // 라운드 기반 출석 체크인
  checkInByRound: async (checkInData) => {
    const response = await fetch(`${API_BASE_URL}/rounds/check-in`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
      body: JSON.stringify(checkInData),
    });
    if (!response.ok) throw new Error('출석 체크인 실패');
    return response.json();
  },

  // 세션별 출석 조회
  getAttendancesBySession: async (sessionId) => {
    const response = await fetch(`${API_BASE_URL}/attendances/sessions/${sessionId}`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
    });
    if (!response.ok) throw new Error('세션 출석 조회 실패');
    return response.json();
  },

  // 사용자별 출석 조회
  getAttendancesByUser: async (userId) => {
    const response = await fetch(`${API_BASE_URL}/attendances/users/${userId}`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
      },
    });
    if (!response.ok) throw new Error('사용자 출석 조회 실패');
    return response.json();
  },

  // 출석 상태 수정 (관리자용)
  updateAttendanceStatus: async (sessionId, userId, status) => {
    const response = await fetch(
      `${API_BASE_URL}/attendances/sessions/${sessionId}/users/${userId}`,
      {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
        },
        body: JSON.stringify({ status }),
      }
    );
    if (!response.ok) throw new Error('출석 상태 수정 실패');
    return response.json();
  },
};
