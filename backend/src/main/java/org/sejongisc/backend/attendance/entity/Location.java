package org.sejongisc.backend.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    @Column(name = "latitude")
    @NotNull(message = "위도는 필수입니다")
    @DecimalMin(value = "-90.0", message = "위도는 -90도 이상이어야 합니다")
    @DecimalMax(value = "90.0", message = "위도는 90도 이하여야 합니다")
    private Double lat;

    @Column(name = "longitude")
    @NotNull(message = "경도는 필수입니다")
    @DecimalMin(value = "-180.0", message = "경도는 -180도 이상이어야 합니다")
    @DecimalMax(value = "180.0", message = "경도는 180도 이하여야 합니다")
    private Double lng;

    @Column(name = "radius_meters")
    @Positive(message = "반경은 양수여야 합니다")
    private Integer radiusMeters;

    /**
     * Compute the haversine distance in meters between this location and another location.
     *
     * @param other the other Location to measure to; if null or if its latitude or longitude is null, the method treats it as invalid
     * @return the distance in meters between the two locations, or Double.MAX_VALUE if the other location is null or has null coordinates
     */
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

    /**
     * Checks whether the provided user location is within this location's allowed radius.
     *
     * If the provided location is null or this location's `radiusMeters` is null, location validation is considered disabled and the method returns `true`.
     *
     * @param userLocation the location to check
     * @return `true` if the distance from this location to `userLocation` is less than or equal to `radiusMeters`, `false` otherwise
     */
    public boolean isWithRange(Location userLocation) {
        if (userLocation == null || radiusMeters == null) {
            return true; // 위치 검증 비활성화
        }

        double distance = calculateDistance(userLocation);
        return distance <= radiusMeters;
    }
}