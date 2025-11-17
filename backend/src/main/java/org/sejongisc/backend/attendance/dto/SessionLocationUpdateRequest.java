package org.sejongisc.backend.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 세션 위치 재설정 요청
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionLocationUpdateRequest {

    private Double latitude;            // 위도

    private Double longitude;           // 경도
}
