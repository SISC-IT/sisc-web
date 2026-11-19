package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.support.CronExpression;

@ExtendWith(MockitoExtension.class)
class DailyClosePriceSchedulerTest {

    private static final String DEFAULT_CRON = "0 0 16 * * MON-FRI";

    @Mock
    private DailyClosePriceCollector collector;

    @InjectMocks
    private DailyClosePriceScheduler scheduler;

    @Test
    @DisplayName("수집기를 호출한다")
    void delegatesToCollector() {
        scheduler.collectDomesticClosePrices();

        verify(collector).collectDomestic();
    }

    @Test
    @DisplayName("기본 cron은 평일 16시에 실행된다")
    void defaultCronRunsAtFourPmOnWeekdays() {
        CronExpression cron = CronExpression.parse(DEFAULT_CRON);
        ZoneId seoul = ZoneId.of("Asia/Seoul");

        // 수요일 09:00 기준 다음 실행은 같은 날 16:00
        LocalDateTime wednesday = LocalDateTime.of(2026, 11, 11, 9, 0);
        assertThat(cron.next(wednesday.atZone(seoul)))
                .isEqualTo(LocalDateTime.of(2026, 11, 11, 16, 0).atZone(seoul));
    }

    @Test
    @DisplayName("주말에는 실행하지 않는다")
    void skipsWeekend() {
        CronExpression cron = CronExpression.parse(DEFAULT_CRON);
        ZoneId seoul = ZoneId.of("Asia/Seoul");

        // 금요일 17:00 다음 실행은 토·일을 건너뛰고 월요일 16:00
        LocalDateTime fridayEvening = LocalDateTime.of(2026, 11, 13, 17, 0);
        assertThat(cron.next(fridayEvening.atZone(seoul)))
                .isEqualTo(LocalDateTime.of(2026, 11, 16, 16, 0).atZone(seoul));
    }
}
