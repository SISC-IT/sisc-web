package org.sejongisc.backend.attendance.entity;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class LocationTest {

    @Test
    void calculateDistance_거리계산() {
        //given
        Location loc1 = Location.builder()
                .lat(37.5665)
                .lng(126.9780)
                .radiusMeters(100)
                .build();

        Location loc2 = Location.builder()
                .lat(35.1796)
                .lng(129.0756)
                .radiusMeters(100)
                .build();

        //when
        double distance = loc1.calculateDistance(loc2);

        //then
        Assertions.assertThat(distance).isBetween(300000.0, 400000.0);
    }
}
