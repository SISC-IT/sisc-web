package org.sejongisc.backend.attendance.entity;

public enum AttendanceStatus {
    PRESENT("출석"),         // 정상 출석
    LATE("지각"),             // 지각 출석
    ABSENT("결석"),           // 미출석
    EXCUSED("사유결석");        // 사전 허가된 결석

    private final String description;

    AttendanceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
