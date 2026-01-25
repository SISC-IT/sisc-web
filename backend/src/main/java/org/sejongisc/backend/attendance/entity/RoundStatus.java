package org.sejongisc.backend.attendance.entity;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RoundStatus {
    UPCOMING("진행 예정", "upcoming"),
    ACTIVE("진행 중", "active"),
    CLOSED("마감됨", "closed");

    private final String description;
    private final String value;
}
