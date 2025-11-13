package org.sejongisc.backend.betting.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeTest {

    @Test
    void getOpenAt_DAILY는_같은날_아침9시() {
        LocalDateTime base = LocalDateTime.now();

        LocalDateTime openAt = Scope.DAILY.getOpenAt(base);

        assertThat(openAt.toLocalDate()).isEqualTo(base.toLocalDate()); // 같은 날
        assertThat(openAt.getHour()).isEqualTo(9);
        assertThat(openAt.getMinute()).isEqualTo(0);
    }

    @Test
    void getLockAt_DAILY는_같은날_오후10시() {
        LocalDateTime base = LocalDateTime.now();

        LocalDateTime lockAt = Scope.DAILY.getLockAt(base);

        assertThat(lockAt.toLocalDate()).isEqualTo(base.toLocalDate()); // 같은 날
        assertThat(lockAt.getHour()).isEqualTo(22);
        assertThat(lockAt.getMinute()).isEqualTo(0);
    }

    @Test
    void getOpenAt_WEEKLY는_월요일_아침9시() {
        LocalDateTime base = LocalDateTime.now();

        LocalDateTime openAt = Scope.WEEKLY.getOpenAt(base);

        assertThat(openAt.getDayOfWeek().name()).isEqualTo("MONDAY");
        assertThat(openAt.getHour()).isEqualTo(9);
    }

    @Test
    void getLockAt_WEEKLY는_금요일_오후10시() {
        LocalDateTime base = LocalDateTime.now();

        LocalDateTime lockAt = Scope.WEEKLY.getLockAt(base);

        assertThat(lockAt.getDayOfWeek().name()).isEqualTo("FRIDAY");
        assertThat(lockAt.getHour()).isEqualTo(22);
    }
}
