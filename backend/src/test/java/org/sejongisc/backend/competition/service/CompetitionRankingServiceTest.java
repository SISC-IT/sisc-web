package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.competition.dto.CompetitionRankingEntry;
import org.sejongisc.backend.competition.dto.CompetitionRankingResponse;
import org.sejongisc.backend.competition.dto.CompetitionTeamRankingEntry;
import org.sejongisc.backend.competition.dto.CompetitionTeamRankingResponse;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.DailyAccountSnapshot;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.DailyAccountSnapshotRepository;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CompetitionRankingServiceTest {

    private static final LocalDate THU = LocalDate.of(2026, 11, 19);
    private static final LocalDate FRI = LocalDate.of(2026, 11, 20);

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private DailyAccountSnapshotRepository snapshotRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CompetitionRankingService rankingService;

    private final UUID competitionId = UUID.randomUUID();
    private Competition competition;
    private CompetitionAccount kim;
    private CompetitionAccount lee;

    @BeforeEach
    void setUp() {
        competition = Competition.builder().competitionId(competitionId).build();
        kim = account("매크로팀");
        lee = account("퀀트팀");
    }

    @Test
    @DisplayName("가장 최근 스냅샷을 등수 순으로 반환하고 직전 스냅샷 대비 등수 변동을 담는다")
    void returnsLatestRankingWithRankChange() {
        givenCompetition();
        givenLatestDate(FRI);
        givenPreviousDate(FRI, THU);
        when(snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, FRI, ParticipantStatus.ACTIVE)).thenReturn(List.of(
                snapshot(lee, FRI, "10600000", "0.06", 1),
                snapshot(kim, FRI, "10480000", "0.048", 2)));
        when(snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, THU, ParticipantStatus.ACTIVE)).thenReturn(List.of(
                snapshot(kim, THU, "10520000", "0.052", 1),
                snapshot(lee, THU, "10310000", "0.031", 2)));
        givenUsers(user(kim, "김세종"), user(lee, "이세종"));

        CompetitionRankingResponse response = rankingService.getRankings(competitionId);

        assertThat(response.snapshotDate()).isEqualTo(FRI);
        assertThat(response.rankings()).hasSize(2);
        CompetitionRankingEntry first = response.rankings().get(0);
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.rankChange()).isEqualTo(1);
        assertThat(first.userId()).isEqualTo(lee.getParticipant().getUserId());
        assertThat(first.userName()).isEqualTo("이세종");
        assertThat(first.teamName()).isEqualTo("퀀트팀");
        assertThat(first.totalValue()).isEqualByComparingTo("10600000");
        assertThat(first.profitRate()).isEqualByComparingTo("0.06");
        CompetitionRankingEntry second = response.rankings().get(1);
        assertThat(second.rank()).isEqualTo(2);
        assertThat(second.rankChange()).isEqualTo(-1);
    }

    @Test
    @DisplayName("첫 스냅샷이라 비교할 날짜가 없으면 등수 변동은 null이다")
    void rankChangeIsNullOnFirstSnapshot() {
        givenCompetition();
        givenLatestDate(FRI);
        givenPreviousDate(FRI, null);
        when(snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, FRI, ParticipantStatus.ACTIVE))
                .thenReturn(List.of(snapshot(kim, FRI, "10000000", "0", 1)));
        givenUsers(user(kim, "김세종"));

        CompetitionRankingResponse response = rankingService.getRankings(competitionId);

        assertThat(response.rankings().get(0).rankChange()).isNull();
    }

    @Test
    @DisplayName("직전 스냅샷에 없던 참가자의 등수 변동은 null이다")
    void rankChangeIsNullForAccountMissingInPreviousSnapshot() {
        givenCompetition();
        givenLatestDate(FRI);
        givenPreviousDate(FRI, THU);
        when(snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, FRI, ParticipantStatus.ACTIVE)).thenReturn(List.of(
                snapshot(kim, FRI, "10100000", "0.01", 1),
                snapshot(lee, FRI, "10000000", "0", 2)));
        when(snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, THU, ParticipantStatus.ACTIVE))
                .thenReturn(List.of(snapshot(kim, THU, "10000000", "0", 1)));
        givenUsers(user(kim, "김세종"), user(lee, "이세종"));

        CompetitionRankingResponse response = rankingService.getRankings(competitionId);

        assertThat(response.rankings().get(0).rankChange()).isZero();
        assertThat(response.rankings().get(1).rankChange()).isNull();
    }

    @Test
    @DisplayName("같은 등수는 이름순으로 정렬한다")
    void sortsTiesByName() {
        givenCompetition();
        givenLatestDate(FRI);
        givenPreviousDate(FRI, null);
        when(snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, FRI, ParticipantStatus.ACTIVE)).thenReturn(List.of(
                snapshot(lee, FRI, "10000000", "0", 1),
                snapshot(kim, FRI, "10000000", "0", 1)));
        givenUsers(user(kim, "김세종"), user(lee, "이세종"));

        CompetitionRankingResponse response = rankingService.getRankings(competitionId);

        assertThat(response.rankings()).extracting(CompetitionRankingEntry::userName)
                .containsExactly("김세종", "이세종");
    }

    @Test
    @DisplayName("스냅샷이 아직 없으면 빈 순위를 반환한다")
    void returnsEmptyWhenNoSnapshot() {
        givenCompetition();
        when(snapshotRepository.findLatestSnapshotDate(competition)).thenReturn(Optional.empty());

        CompetitionRankingResponse response = rankingService.getRankings(competitionId);

        assertThat(response.snapshotDate()).isNull();
        assertThat(response.rankings()).isEmpty();
    }

    @Test
    @DisplayName("없는 대회는 COMPETITION_NOT_FOUND를 던진다")
    void throwsWhenCompetitionNotFound() {
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rankingService.getRankings(competitionId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_NOT_FOUND);
    }

    @Test
    @DisplayName("팀 순위는 팀원 등수의 평균이 낮은 순이고 인원이 적은 팀이 유리하지 않다")
    void teamRankingsByAverageRank() {
        givenCompetition();
        givenLatestDate(FRI);
        CompetitionAccount park = account("매크로팀");
        CompetitionAccount choi = account("퀀트팀");
        CompetitionAccount jung = account("가치팀");
        // 매크로팀 1, 4등(평균 2.5), 퀀트팀 2, 3등(평균 2.5), 가치팀 5등. 합으로 보면 가치팀이 앞선다.
        when(snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, FRI, ParticipantStatus.ACTIVE)).thenReturn(List.of(
                snapshot(kim, FRI, "10500000", "0.05", 1),
                snapshot(lee, FRI, "10400000", "0.04", 2),
                snapshot(choi, FRI, "10300000", "0.03", 3),
                snapshot(park, FRI, "10200000", "0.02", 4),
                snapshot(jung, FRI, "10100000", "0.01", 5)));

        CompetitionTeamRankingResponse response = rankingService.getTeamRankings(competitionId);

        assertThat(response.snapshotDate()).isEqualTo(FRI);
        assertThat(response.rankings())
                .extracting(CompetitionTeamRankingEntry::rank, CompetitionTeamRankingEntry::teamName,
                        CompetitionTeamRankingEntry::rankSum, CompetitionTeamRankingEntry::memberCount)
                .containsExactly(
                        tuple(1, "매크로팀", 5, 2),
                        tuple(1, "퀀트팀", 5, 2),
                        tuple(3, "가치팀", 5, 1));
        assertThat(response.rankings().get(0).averageRank()).isEqualByComparingTo("2.5");
    }

    @Test
    @DisplayName("반올림하면 같아 보이는 평균 등수도 실제로 다르면 다른 등수다")
    void teamRankingsCompareExactAverage() {
        givenCompetition();
        givenLatestDate(FRI);
        // 매크로팀 5/3 = 1.666..., 퀀트팀 167/100 = 1.67. 둘 다 반올림하면 1.67이다.
        List<DailyAccountSnapshot> snapshots = new ArrayList<>();
        snapshots.addAll(teamSnapshots("매크로팀", 1, 1));
        snapshots.addAll(teamSnapshots("매크로팀", 2, 2));
        snapshots.addAll(teamSnapshots("퀀트팀", 33, 1));
        snapshots.addAll(teamSnapshots("퀀트팀", 67, 2));
        when(snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, FRI, ParticipantStatus.ACTIVE))
                .thenReturn(snapshots);

        CompetitionTeamRankingResponse response = rankingService.getTeamRankings(competitionId);

        assertThat(response.rankings())
                .extracting(CompetitionTeamRankingEntry::rank, CompetitionTeamRankingEntry::teamName,
                        CompetitionTeamRankingEntry::averageRank)
                .containsExactly(
                        tuple(1, "매크로팀", new BigDecimal("1.67")),
                        tuple(2, "퀀트팀", new BigDecimal("1.67")));
    }

    @Test
    @DisplayName("스냅샷이 아직 없으면 빈 팀 순위를 반환한다")
    void teamRankingsEmptyWhenNoSnapshot() {
        givenCompetition();
        when(snapshotRepository.findLatestSnapshotDate(competition)).thenReturn(Optional.empty());

        CompetitionTeamRankingResponse response = rankingService.getTeamRankings(competitionId);

        assertThat(response.snapshotDate()).isNull();
        assertThat(response.rankings()).isEmpty();
    }

    private void givenCompetition() {
        when(competitionRepository.findById(competitionId)).thenReturn(Optional.of(competition));
    }

    private void givenLatestDate(LocalDate latest) {
        when(snapshotRepository.findLatestSnapshotDate(competition)).thenReturn(Optional.of(latest));
    }

    private void givenPreviousDate(LocalDate latest, LocalDate previous) {
        when(snapshotRepository.findLatestSnapshotDateBefore(competition, latest))
                .thenReturn(Optional.ofNullable(previous));
    }

    private List<DailyAccountSnapshot> teamSnapshots(String teamName, int count, int rank) {
        List<DailyAccountSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            snapshots.add(snapshot(account(teamName), FRI, "10000000", "0", rank));
        }
        return snapshots;
    }

    private void givenUsers(User... users) {
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(users));
    }

    private CompetitionAccount account(String teamName) {
        return CompetitionAccount.builder()
                .accountId(UUID.randomUUID())
                .participant(CompetitionParticipant.builder()
                        .participantId(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .teamName(teamName)
                        .build())
                .build();
    }

    private User user(CompetitionAccount account, String name) {
        return User.builder().userId(account.getParticipant().getUserId()).name(name).build();
    }

    private DailyAccountSnapshot snapshot(
            CompetitionAccount account, LocalDate date, String totalValue, String profitRate, int rank) {
        return DailyAccountSnapshot.builder()
                .account(account)
                .snapshotDate(date)
                .totalValue(new BigDecimal(totalValue))
                .profitRate(new BigDecimal(profitRate))
                .rank(rank)
                .build();
    }
}
