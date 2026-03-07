import { api } from './axios.js';
import { getAttendanceSessions, getRounds } from './attendanceManage';

const getSessionId = (session) =>
  session?.sessionId || session?.attendanceSessionId || session?.id || null;

const getSessionTitle = (session) =>
  session?.session?.title || session?.title || session?.sessionTitle || '';

const buildRoundMetaMap = async () => {
  const sessions = await getAttendanceSessions();
  if (!Array.isArray(sessions) || sessions.length === 0) return new Map();

  const perSessionRoundMeta = await Promise.all(
    sessions.map(async (session) => {
      const sessionId = getSessionId(session);
      if (!sessionId) return [];

      try {
        const rounds = await getRounds(sessionId);
        if (!Array.isArray(rounds)) return [];

        const sessionTitle = getSessionTitle(session);
        return rounds
          .filter((round) => !!round?.roundId)
          .map((round) => [
            round.roundId,
            {
              sessionTitle,
              roundDate: round.roundDate,
              roundStartAt: round.startAt || round.roundStartAt,
            },
          ]);
      } catch {
        return [];
      }
    }),
  );

  return new Map(perSessionRoundMeta.flat());
};

export const attendanceList = async () => {
  try {
    const res = await api.get('/api/attendance/me');

    const records = Array.isArray(res.data) ? res.data : [];
    if (records.length === 0) return records;

    try {
      const roundMetaMap = await buildRoundMetaMap();

      return records.map((record) => {
        const roundMeta = roundMetaMap.get(record?.roundId);
        if (!roundMeta) return record;

        return {
          ...record,
          sessionTitle: record?.sessionTitle || roundMeta.sessionTitle,
          roundDate: record?.roundDate || roundMeta.roundDate,
          roundStartAt: record?.roundStartAt || roundMeta.roundStartAt,
        };
      });
    } catch {
      // Keep attendance view available even if metadata enrichment fails.
      return records;
    }
  } catch (err) {
    console.error('출석 세션 불러오기 중 오류 발생: ', err);
    throw err;
  }
};


// [
//   {
//     "attendanceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
//     "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
//     "userName": "string",
//     "roundId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
//     "attendanceStatus": "string",
//     "checkedAt": "2026-02-24T14:33:43.581Z",
//     "note": "string",
//     "checkInLatitude": 0.1,
//     "checkInLongitude": 0.1,
//     "createdAt": "2026-02-24T14:33:43.581Z",
//     "updatedAt": "2026-02-24T14:33:43.581Z"
//   }
// ]
