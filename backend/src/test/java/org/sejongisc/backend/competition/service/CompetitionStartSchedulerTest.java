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
class CompetitionStartSchedulerTest {

    private static final String CRON = "0 0 * * * *";

    @Mock
    private CompetitionService competitionService;

    @InjectMocks
    private CompetitionStartScheduler scheduler;

    @Test
    @DisplayName("시작할 대회를 여는 서비스를 호출한다")
    void delegatesToService() {
        scheduler.startDueCompetitions();

        verify(competitionService).startDueCompetitions();
    }

    @Test
    @DisplayName("매시 정각에 실행되어 서버가 자정에 꺼져 있었어도 주문 시작(16:30) 전에 대회가 열린다")
    void runsEveryHour() {
        CronExpression cron = CronExpression.parse(CRON);
        ZoneId seoul = ZoneId.of("Asia/Seoul");

        LocalDateTime justAfterMidnight = LocalDateTime.of(2026, 11, 16, 0, 3);
        assertThat(cron.next(justAfterMidnight.atZone(seoul)))
                .isEqualTo(LocalDateTime.of(2026, 11, 16, 1, 0).atZone(seoul));
    }
}
