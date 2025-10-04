package org.sejongisc.backend.betting.entity;

import java.time.LocalDateTime;

public enum Scope {
    DAILY {
        @Override
        public LocalDateTime getOpenAt(LocalDateTime base) {
            return base.withHour(9).withMinute(0).withSecond(0).withNano(0);
        }
        @Override
        public LocalDateTime getLockAt(LocalDateTime base) {
            return base.withHour(22).withMinute(0).withSecond(0).withNano(0);
        }
    },
    WEEKLY {
        @Override
        public LocalDateTime getOpenAt(LocalDateTime base) {
            return base.with(java.time.DayOfWeek.MONDAY)
                    .withHour(9).withMinute(0).withSecond(0).withNano(0);
        }
        @Override
        public LocalDateTime getLockAt(LocalDateTime base) {
            return base.with(java.time.DayOfWeek.FRIDAY)
                    .withHour(22).withMinute(0).withSecond(0).withNano(0);
        }
    };

    public abstract LocalDateTime getOpenAt(LocalDateTime base);
    public abstract LocalDateTime getLockAt(LocalDateTime base);
}
