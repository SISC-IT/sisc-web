package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
class CompetitionStockSyncSchedulerTest {

    private static final String DEFAULT_CRON = "0 40 15 * * MON-FRI";

    @Mock
    private CompetitionStockSyncService syncService;

    @Mock
    private CompetitionService competitionService;

    @InjectMocks
    private CompetitionStockSyncScheduler scheduler;

    @Test
    @DisplayName("열린 대회가 있으면 종목을 동기화한다")
    void syncsWhenCompetitionIsOpen() {
        when(competitionService.hasOpenCompetition()).thenReturn(true);

        scheduler.syncDomesticStocks();

        verify(syncService).syncDomestic();
    }

    @Test
    @DisplayName("열린 대회가 없으면 동기화하지 않는다")
    void skipsWhenNoOpenCompetition() {
        when(competitionService.hasOpenCompetition()).thenReturn(false);

        scheduler.syncDomesticStocks();

        verifyNoInteractions(syncService);
    }

    @Test
    @DisplayName("기본 cron은 평일 15시 40분에 실행된다")
    void defaultCronRunsAtThreeFortyOnWeekdays() {
        CronExpression cron = CronExpression.parse(DEFAULT_CRON);
        ZoneId seoul = ZoneId.of("Asia/Seoul");

        LocalDateTime wednesday = LocalDateTime.of(2026, 11, 11, 9, 0);
        assertThat(cron.next(wednesday.atZone(seoul)))
                .isEqualTo(LocalDateTime.of(2026, 11, 11, 15, 40).atZone(seoul));
    }
}
