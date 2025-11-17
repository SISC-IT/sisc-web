package org.sejongisc.backend.attendance.entity;

/**
 * 라운드(주차) 상태
 */
public enum RoundStatus {
    UPCOMING("진행 예정"),
    ACTIVE("진행 중"),
    CLOSED("마감됨");

    private final String description;

    RoundStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
