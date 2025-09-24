package org.sejongisc.backend.attendance.entity;

public enum SessionStatus {
    UPCOMING("예정"),     // 아직 시작 전
    OPEN("진행중"),        // 체크인 가능한 상태
    CLOSED("종료");       // 체크인 시간 마감

    private final String description;

    SessionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
