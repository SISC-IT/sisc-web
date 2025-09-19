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

    @Column(name = "latitude", precision = 10, scale = 7)
    private Double lat;

    @Column(name = "longitude", precision = 10, scale = 7)
    private Double lng;

    @Column(name = "radius_meters")
    private Integer radiusMeters;

    // 좌표거리 계산 / 위치 허용 범위 계산
}
