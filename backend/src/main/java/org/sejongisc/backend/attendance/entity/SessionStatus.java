package org.sejongisc.backend.attendance.entity;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum SessionStatus {
    OPEN("진행중"),        // 체크인 가능한 상태
    CLOSED("종료");       // 체크인 시간 마감

    private final String description;
}
