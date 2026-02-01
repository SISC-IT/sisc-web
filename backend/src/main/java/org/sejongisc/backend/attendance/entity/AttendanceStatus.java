package org.sejongisc.backend.attendance.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AttendanceStatus {
    PENDING("미정"),         // 라운드 예정 중 - 아직 체크인 안 됨
    PRESENT("출석"),         // 정상 출석
    LATE("지각"),             // 지각 출석
    ABSENT("결석"),           // 미출석
    EXCUSED("사유결석");        // 사전 허가된 결석

    private final String description;
}
