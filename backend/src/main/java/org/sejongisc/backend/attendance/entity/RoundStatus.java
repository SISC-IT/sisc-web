package org.sejongisc.backend.attendance.entity;

/**
 * 라운드(주차) 상태
 */
public enum RoundStatus {
    UPCOMING("진행 예정", "upcoming"),
    ACTIVE("진행 중", "active"),
    CLOSED("마감됨", "closed");

    private final String description;
    private final String value;

    RoundStatus(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * API 응답에 사용할 문자열 값 반환
     * toString().toLowerCase()와 달리 명시적이고 안전함
     *
     * @return API 응답용 상태값 (lowercase)
     */
    public String getValue() {
        return value;
    }
}
