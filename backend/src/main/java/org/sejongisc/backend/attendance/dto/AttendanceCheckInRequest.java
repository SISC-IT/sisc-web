package org.sejongisc.backend.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 출석 체크인 요청
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCheckInRequest {

    private UUID roundId;              // 라운드 ID

    private Double latitude;            // 현재 위치 위도

    private Double longitude;           // 현재 위치 경도

    private String userName;            // 익명 사용자 이름 (선택사항)
}
