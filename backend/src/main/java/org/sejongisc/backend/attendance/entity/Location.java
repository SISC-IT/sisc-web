package org.sejongisc.backend.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    @Column(name = "latitude")
    private Double lat;

    @Column(name = "longitude")
    private Double lng;

    @Column(name = "radius_meters")
    private Integer radiusMeters;

    // 좌표거리 계산 / 위치 허용 범위 계산
    // 거리계산 메서드
    public double calculateDistance(Location other) {
        if (other == null || other.lat == null || other.lng == null) {
            return Double.MAX_VALUE;
        }

        final int R = 6371000; // 지구 반지름 (미터)

        double lat1Rad = Math.toRadians(this.lat);
        double lat2Rad = Math.toRadians(other.lat);
        double deltaLatRad = Math.toRadians(other.lat - this.lat);
        double deltaLngRad = Math.toRadians(other.lng - this.lng);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLngRad / 2) * Math.sin(deltaLngRad / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // 미터 단위 거리
    }

    public boolean isWithRange(Location userLocation) {
        if (userLocation == null || radiusMeters == null) {
            return true; // 위치 검증 비활성화
        }

        double distance = calculateDistance(userLocation);
        return distance <= radiusMeters;
    }
}
