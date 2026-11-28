package org.sejongisc.backend.competition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.sejongisc.backend.competition.dto.CompetitionTeamRankingEntry;
import org.sejongisc.backend.competition.dto.CompetitionTeamRankingResponse;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
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

    private static final String ANONYMOUS_NAME = "익명";

    private static final Comparator<CompetitionRankingEntry> BY_RANK_THEN_NAME =
            Comparator.comparingInt(CompetitionRankingEntry::rank)
                    .thenComparing(CompetitionRankingEntry::userName, Comparator.nullsLast(Comparator.naturalOrder()));

    private final CompetitionRepository competitionRepository;
    private final DailyAccountSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public CompetitionRankingResponse getRankings(UUID competitionId, UUID viewerId, boolean admin) {
        Competition competition = findVisibleCompetition(competitionId, admin);

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
                .map(entry -> admin || entry.userId().equals(viewerId) ? entry : anonymize(entry))
                .toList();
        return new CompetitionRankingResponse(latestDate.get(), rankings);
    }

    public CompetitionTeamRankingResponse getTeamRankings(UUID competitionId, boolean admin) {
        Competition competition = findVisibleCompetition(competitionId, admin);

        Optional<LocalDate> latestDate = snapshotRepository.findLatestSnapshotDate(competition);
        if (latestDate.isEmpty()) {
            return new CompetitionTeamRankingResponse(null, List.of());
        }

        List<DailyAccountSnapshot> snapshots =
                snapshotRepository.findAllByCompetitionAndSnapshotDate(
                        competition, latestDate.get(), ParticipantStatus.ACTIVE);
        return new CompetitionTeamRankingResponse(latestDate.get(), toTeamRankings(snapshots));
    }

    // 종료일 16:00에 최종 순위가 나오므로, 종료일부터 결과 공개 전까지는 관리자만 볼 수 있다
    private Competition findVisibleCompetition(UUID competitionId, boolean admin) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_NOT_FOUND));
        if (!admin && isTallying(competition)) {
            throw new CustomException(ErrorCode.COMPETITION_RESULT_NOT_PUBLIC);
        }
        return competition;
    }

    private boolean isTallying(Competition competition) {
        CompetitionStatus status = competition.getStatus();
        return status == CompetitionStatus.ENDED
                || (status == CompetitionStatus.ACTIVE && !LocalDate.now(clock).isBefore(competition.getEndDate()));
    }

    private static List<CompetitionTeamRankingEntry> toTeamRankings(List<DailyAccountSnapshot> snapshots) {
        Comparator<TeamRankSum> byAverage = TeamRankSum::compareAverage;
        List<TeamRankSum> sorted = snapshots.stream()
                .collect(Collectors.groupingBy(
                        snapshot -> snapshot.getAccount().getParticipant().getTeamName(),
                        Collectors.mapping(DailyAccountSnapshot::getRank, Collectors.toList())))
                .entrySet().stream()
                .map(team -> new TeamRankSum(
                        team.getKey(),
                        team.getValue().stream().mapToInt(Integer::intValue).sum(),
                        team.getValue().size()))
                .sorted(byAverage.thenComparing(TeamRankSum::teamName))
                .toList();

        List<CompetitionTeamRankingEntry> rankings = new ArrayList<>();
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            TeamRankSum team = sorted.get(i);
            if (i == 0 || team.compareAverage(sorted.get(i - 1)) != 0) {
                rank = i + 1;
            }
            rankings.add(new CompetitionTeamRankingEntry(
                    rank, team.teamName(), team.averageRank(), team.rankSum(), team.memberCount()));
        }
        return rankings;
    }

    private record TeamRankSum(String teamName, int rankSum, int memberCount) {

        // 반올림한 평균끼리 비교하면 다른 평균이 같아 보일 수 있어, 분수 그대로 비교한다
        int compareAverage(TeamRankSum other) {
            return Long.compare((long) rankSum * other.memberCount, (long) other.rankSum * memberCount);
        }

        BigDecimal averageRank() {
            return BigDecimal.valueOf(rankSum).divide(BigDecimal.valueOf(memberCount), 2, RoundingMode.HALF_UP);
        }
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
                participant.getParticipantId(),
                participant.getUserId(),
                userNames.get(participant.getUserId()),
                participant.getTeamName(),
                snapshot.getTotalValue(),
                snapshot.getProfitRate());
    }

    // 동아리 안에서는 이름 일부만으로도 누군지 알 수 있어, 다른 참가자는 이름을 모두 가린다
    private static CompetitionRankingEntry anonymize(CompetitionRankingEntry entry) {
        return new CompetitionRankingEntry(
                entry.rank(),
                entry.rankChange(),
                entry.participantId(),
                null,
                ANONYMOUS_NAME,
                entry.teamName(),
                entry.totalValue(),
                entry.profitRate());
    }
}
