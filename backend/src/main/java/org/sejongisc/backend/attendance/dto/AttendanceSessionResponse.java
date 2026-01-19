package org.sejongisc.backend.attendance.dto;


import java.util.UUID;
import org.sejongisc.backend.attendance.entity.AttendanceSession;
import org.sejongisc.backend.attendance.entity.SessionRole;

public record AttendanceSessionResponse(
    UUID sessionId,
    AttendanceSessionRequest session,
    SessionRole myRole,
    Permissions permissions
) {
    public record Permissions(
        boolean canUpdateSession,
        boolean canCloseSession,
        boolean canAddManager
    ) {
        public static Permissions from(SessionRole role) {
            if (role == null|| role == SessionRole.PARTICIPANT) {
                return new Permissions(false, false, false);
            }else if(role == SessionRole.MANAGER) {
                return new Permissions(true, true, false);
            }

            return new Permissions(
                true, true, true
            );
        }

    }

    public static AttendanceSessionResponse from(AttendanceSession session, SessionRole myRole) {
        return new AttendanceSessionResponse(
            session.getAttendanceSessionId(),
            AttendanceSessionRequest.from(session),
            myRole,
            Permissions.from(myRole)
        );
    }
    // 목록 조회에서 쓰기 좋은 기본 변환(비로그인/비멤버 기준)
    public static AttendanceSessionResponse from(AttendanceSession session) {
        return from(session, null);
    }

    }
