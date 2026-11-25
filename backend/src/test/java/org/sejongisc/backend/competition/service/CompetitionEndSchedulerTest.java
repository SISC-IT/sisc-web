package org.sejongisc.backend.competition.service;

import static org.mockito.Mockito.inOrder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitionEndSchedulerTest {

    @Mock
    private DepositInterestService interestService;

    @Mock
    private AccountSnapshotService snapshotService;

    @Mock
    private CompetitionService competitionService;

    @InjectMocks
    private CompetitionEndScheduler scheduler;

    @Test
    @DisplayName("밀린 이자를 지급하고 종료일 스냅샷을 만든 뒤 대회를 닫는다")
    void paysInterestAndCreatesFinalSnapshotBeforeEnding() {
        scheduler.endDueCompetitions();

        InOrder order = inOrder(interestService, snapshotService, competitionService);
        order.verify(interestService).payDailyInterest();
        order.verify(snapshotService).createFinalSnapshot();
        order.verify(competitionService).endDueCompetitions();
    }
}
