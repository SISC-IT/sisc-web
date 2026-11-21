package org.sejongisc.backend.competition.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.competition.dto.CompetitionRankingEntry;
import org.sejongisc.backend.competition.dto.CompetitionRankingResponse;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.DailyAccountSnapshot;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.DailyAccountSnapshotRepository;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetitionRankingService {

    private static final Comparator<CompetitionRankingEntry> BY_RANK_THEN_NAME =
            Comparator.comparingInt(CompetitionRankingEntry::rank)
                    .thenComparing(CompetitionRankingEntry::userName, Comparator.nullsLast(Comparator.naturalOrder()));

    private final CompetitionRepository competitionRepository;
    private final DailyAccountSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;

    public CompetitionRankingResponse getRankings(UUID competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_NOT_FOUND));

        Optional<LocalDate> latestDate = snapshotRepository.findLatestSnapshotDate(competition);
        if (latestDate.isEmpty()) {
            return new CompetitionRankingResponse(null, List.of());
        }

        List<DailyAccountSnapshot> snapshots =
                snapshotRepository.findAllByCompetitionAndSnapshotDate(
                        competition, latestDate.get(), ParticipantStatus.ACTIVE);
        Map<UUID, Integer> previousRanks = previousRanks(competition, latestDate.get());
        Map<UUID, String> userNames = userNames(snapshots);

        List<CompetitionRankingEntry> rankings = snapshots.stream()
                .map(snapshot -> toEntry(snapshot, previousRanks, userNames))
                .sorted(BY_RANK_THEN_NAME)
                .toList();
        return new CompetitionRankingResponse(latestDate.get(), rankings);
    }

    private Map<UUID, Integer> previousRanks(Competition competition, LocalDate latestDate) {
        Optional<LocalDate> previousDate = snapshotRepository.findLatestSnapshotDateBefore(competition, latestDate);
        if (previousDate.isEmpty()) {
            return Map.of();
        }
        return snapshotRepository
                .findAllByCompetitionAndSnapshotDate(competition, previousDate.get(), ParticipantStatus.ACTIVE).stream()
                .collect(Collectors.toMap(
                        snapshot -> snapshot.getAccount().getAccountId(), DailyAccountSnapshot::getRank));
    }

    private Map<UUID, String> userNames(List<DailyAccountSnapshot> snapshots) {
        List<UUID> userIds = snapshots.stream()
                .map(snapshot -> snapshot.getAccount().getParticipant().getUserId())
                .toList();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, User::getName));
    }

    private CompetitionRankingEntry toEntry(
            DailyAccountSnapshot snapshot, Map<UUID, Integer> previousRanks, Map<UUID, String> userNames) {
        CompetitionParticipant participant = snapshot.getAccount().getParticipant();
        Integer previousRank = previousRanks.get(snapshot.getAccount().getAccountId());
        return new CompetitionRankingEntry(
                snapshot.getRank(),
                previousRank == null ? null : previousRank - snapshot.getRank(),
                participant.getUserId(),
                userNames.get(participant.getUserId()),
                participant.getTeamName(),
                snapshot.getTotalValue(),
                snapshot.getProfitRate());
    }
}
