package org.sejongisc.backend.competition.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitionEndSchedulerTest {

    @Mock
    private CompetitionService competitionService;

    @InjectMocks
    private CompetitionEndScheduler scheduler;

    @Test
    @DisplayName("종료할 대회를 닫는 서비스를 호출한다")
    void delegatesToService() {
        scheduler.endDueCompetitions();

        verify(competitionService).endDueCompetitions();
    }
}
