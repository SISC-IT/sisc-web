package org.sejongisc.backend.competition.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompetitionEndScheduler {

    private final CompetitionService competitionService;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void endDueCompetitions() {
        competitionService.endDueCompetitions();
    }
}
