package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.competition.dto.CompetitionCreateRequest;
import org.sejongisc.backend.competition.dto.CompetitionCreateResponse;
import org.sejongisc.backend.competition.dto.CompetitionResponse;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.DailyAccountSnapshot;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.DailyAccountSnapshotRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.entity.UserStatus;
import org.sejongisc.backend.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

  @Mock CompetitionRepository competitionRepository;
  @Mock CompetitionParticipantRepository participantRepository;
  @Mock CompetitionAccountRepository accountRepository;
  @Mock DailyAccountSnapshotRepository snapshotRepository;
  @Mock UserRepository userRepository;
  CompetitionService competitionService;

  private final CompetitionCreateRequest request =
      new CompetitionCreateRequest("2026 2학기 수익률 대결", LocalDate.of(2026, 11, 23), LocalDate.of(2026, 12, 4));

  @BeforeEach
  void setUp() {
    // 오늘은 2026-11-18 (서울)
    Clock clock = Clock.fixed(ZonedDateTime.of(2026, 11, 18, 10, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant(),
        ZoneId.of("Asia/Seoul"));
    competitionService = new CompetitionService(
        competitionRepository, participantRepository, accountRepository, snapshotRepository, userRepository, clock);
  }

  @Test
  @DisplayName("활동 회원 전원을 참가자로 등록하고 기본 초기 자금으로 계좌를 만든다")
  @SuppressWarnings("unchecked")
  void create() {
    User kim = user("매크로팀");
    User lee = user("리서치팀");
    given(userRepository.findAllByStatusAndRoleNot(UserStatus.ACTIVE, Role.PENDING_MEMBER)).willReturn(List.of(kim, lee));
    // 저장한 엔티티를 그대로 돌려준다
    given(competitionRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
    given(participantRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

    CompetitionCreateResponse response = competitionService.create(request);

    ArgumentCaptor<Competition> competition = ArgumentCaptor.forClass(Competition.class);
    verify(competitionRepository).saveAndFlush(competition.capture());
    assertThat(competition.getValue().getStatus()).isEqualTo(CompetitionStatus.READY);
    assertThat(competition.getValue().getInitialCash()).isEqualByComparingTo("10000000");
    assertThat(competition.getValue().getFeeRate()).isEqualByComparingTo("0.0001");
    assertThat(competition.getValue().getDepositInterestRate()).isEqualByComparingTo("0.03");

    ArgumentCaptor<List<CompetitionParticipant>> participants = ArgumentCaptor.forClass(List.class);
    verify(participantRepository).saveAll(participants.capture());
    assertThat(participants.getValue())
        .extracting(CompetitionParticipant::getUserId, CompetitionParticipant::getTeamName, CompetitionParticipant::getStatus)
        .containsExactly(
            tuple(kim.getUserId(), "매크로팀", ParticipantStatus.ACTIVE),
            tuple(lee.getUserId(), "리서치팀", ParticipantStatus.ACTIVE));
    assertThat(participants.getValue()).allSatisfy(p -> assertThat(p.getCompetition()).isSameAs(competition.getValue()));

    ArgumentCaptor<List<CompetitionAccount>> accounts = ArgumentCaptor.forClass(List.class);
    verify(accountRepository).saveAll(accounts.capture());
    assertThat(accounts.getValue()).hasSize(2).allSatisfy(account -> {
      assertThat(account.getCash()).isEqualByComparingTo("10000000");
      assertThat(account.getDeposit()).isEqualByComparingTo(BigDecimal.ZERO);
    });
    assertThat(accounts.getValue()).extracting(CompetitionAccount::getParticipant)
        .containsExactlyElementsOf(participants.getValue());

    assertThat(response.participantCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("팀이 없는 활동 회원이 한 명이라도 있으면 아무것도 만들지 않는다")
  void rejectWhenTeamUnassigned() {
    given(userRepository.findAllByStatusAndRoleNot(UserStatus.ACTIVE, Role.PENDING_MEMBER))
        .willReturn(List.of(user("매크로팀"), user(null), user(" ")));

    assertError(ErrorCode.COMPETITION_TEAM_UNASSIGNED);
  }

  @Test
  @DisplayName("활동 회원이 없으면 만들지 않는다")
  void rejectWhenNoActiveMembers() {
    given(userRepository.findAllByStatusAndRoleNot(UserStatus.ACTIVE, Role.PENDING_MEMBER)).willReturn(List.of());

    assertError(ErrorCode.COMPETITION_NO_PARTICIPANTS);
  }

  @Test
  @DisplayName("종료일이 시작일보다 늦지 않으면 만들지 않는다")
  void rejectInvalidPeriod() {
    for (CompetitionCreateRequest invalid : List.of(
        new CompetitionCreateRequest("대회", LocalDate.of(2026, 12, 4), LocalDate.of(2026, 11, 23)),
        new CompetitionCreateRequest("하루 대회", LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 23)))) {
      assertThatThrownBy(() -> competitionService.create(invalid))
          .isInstanceOf(CustomException.class)
          .extracting("errorCode").isEqualTo(ErrorCode.INVALID_COMPETITION_PERIOD);
    }
    verify(competitionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("시작일이 오늘보다 과거면 만들지 않는다")
  void rejectPastStartDate() {
    CompetitionCreateRequest past =
        new CompetitionCreateRequest("대회", LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 28));

    assertThatThrownBy(() -> competitionService.create(past))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_START_DATE_PAST);
    verify(competitionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("시작 전·진행 중 대회 인덱스에 막히면 열린 대회 에러로 바꾸고 참가자를 만들지 않는다")
  void translateUniqueViolation() {
    given(userRepository.findAllByStatusAndRoleNot(UserStatus.ACTIVE, Role.PENDING_MEMBER))
        .willReturn(List.of(user("매크로팀")));
    given(competitionRepository.saveAndFlush(any())).willThrow(integrityViolation("uk_competition_single_open"));

    assertThatThrownBy(() -> competitionService.create(request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_ALREADY_OPEN);
    verify(participantRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("열린 대회 인덱스가 아닌 DB 오류는 그대로 올린다")
  void rethrowOtherIntegrityViolation() {
    given(userRepository.findAllByStatusAndRoleNot(UserStatus.ACTIVE, Role.PENDING_MEMBER))
        .willReturn(List.of(user("매크로팀")));
    DataIntegrityViolationException other = integrityViolation("ck_competition_initial_cash");
    given(competitionRepository.saveAndFlush(any())).willThrow(other);

    assertThatThrownBy(() -> competitionService.create(request)).isSameAs(other);
    verify(participantRepository, never()).saveAll(anyList());
  }

  private static DataIntegrityViolationException integrityViolation(String constraintName) {
    return new DataIntegrityViolationException("violation",
        new ConstraintViolationException("violation", null, constraintName));
  }

  private void assertError(ErrorCode expected) {
    assertThatThrownBy(() -> competitionService.create(request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode").isEqualTo(expected);
    verify(competitionRepository, never()).saveAndFlush(any());
    verify(participantRepository, never()).saveAll(anyList());
    verify(accountRepository, never()).saveAll(anyList());
  }

  private static User user(String teamName) {
    return User.builder()
        .userId(UUID.randomUUID())
        .name("회원")
        .teamName(teamName)
        .status(UserStatus.ACTIVE)
        .build();
  }

  @Test
  @DisplayName("시작일이 지난 시작 전 대회를 진행 중으로 바꾼다")
  void startDueCompetitions() {
    Competition ready = Competition.builder()
        .status(CompetitionStatus.READY)
        .startDate(LocalDate.of(2026, 11, 18))
        .build();
    given(competitionRepository.findAllByStatusAndStartDateLessThanEqual(
        CompetitionStatus.READY, LocalDate.of(2026, 11, 18))).willReturn(List.of(ready));

    competitionService.startDueCompetitions();

    assertThat(ready.getStatus()).isEqualTo(CompetitionStatus.ACTIVE);
  }

  @Test
  @DisplayName("종료일 스냅샷이 생기면 그 등수를 최종 등수로 정하고 종료한다")
  void endWithEndDateSnapshot() {
    Competition competition = activeEndingOn(TODAY);
    CompetitionParticipant first = participant(competition);
    CompetitionParticipant second = participant(competition);
    givenDue(competition);
    given(snapshotRepository.findLatestSnapshotDate(competition)).willReturn(Optional.of(TODAY));
    given(snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, TODAY, ParticipantStatus.ACTIVE))
        .willReturn(List.of(snapshot(first, 1), snapshot(second, 2)));

    competitionService.endDueCompetitions();

    assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.ENDED);
    assertThat(first.getFinalRank()).isEqualTo(1);
    assertThat(second.getFinalRank()).isEqualTo(2);
  }

  @Test
  @DisplayName("종료일이어도 그날 스냅샷이 아직 없으면 닫지 않는다")
  void waitForEndDateSnapshot() {
    Competition competition = activeEndingOn(TODAY);
    givenDue(competition);
    given(snapshotRepository.findLatestSnapshotDate(competition)).willReturn(Optional.of(TODAY.minusDays(1)));

    competitionService.endDueCompetitions();

    assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.ACTIVE);
    verify(snapshotRepository, never()).findAllByCompetitionAndSnapshotDate(any(), any(), any());
  }

  @Test
  @DisplayName("종료일이 휴장일이라 그날 스냅샷이 없으면 종료일이 지난 뒤 직전 거래일 스냅샷으로 닫는다")
  void endWithLastTradingDaySnapshotAfterEndDate() {
    LocalDate endDate = TODAY.minusDays(1);
    LocalDate lastTradingDay = TODAY.minusDays(2);
    Competition competition = activeEndingOn(endDate);
    CompetitionParticipant participant = participant(competition);
    givenDue(competition);
    given(snapshotRepository.findLatestSnapshotDate(competition)).willReturn(Optional.of(lastTradingDay));
    given(snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, lastTradingDay, ParticipantStatus.ACTIVE))
        .willReturn(List.of(snapshot(participant, 1)));

    competitionService.endDueCompetitions();

    assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.ENDED);
    assertThat(participant.getFinalRank()).isEqualTo(1);
  }

  @Test
  @DisplayName("스냅샷에 없는 참가자(제외자)의 최종 등수는 null로 남는다")
  void excludedParticipantHasNoFinalRank() {
    Competition competition = activeEndingOn(TODAY);
    CompetitionParticipant active = participant(competition);
    CompetitionParticipant excluded = participant(competition);
    givenDue(competition);
    given(snapshotRepository.findLatestSnapshotDate(competition)).willReturn(Optional.of(TODAY));
    given(snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, TODAY, ParticipantStatus.ACTIVE))
        .willReturn(List.of(snapshot(active, 1)));

    competitionService.endDueCompetitions();

    assertThat(active.getFinalRank()).isEqualTo(1);
    assertThat(excluded.getFinalRank()).isNull();
  }

  @Test
  @DisplayName("스냅샷이 한 번도 없으면 종료일이 지난 뒤 최종 등수 없이 닫는다")
  void endWithoutAnySnapshot() {
    Competition competition = activeEndingOn(TODAY.minusDays(1));
    givenDue(competition);
    given(snapshotRepository.findLatestSnapshotDate(competition)).willReturn(Optional.empty());

    competitionService.endDueCompetitions();

    assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.ENDED);
  }

  @Test
  @DisplayName("종료된 대회의 결과를 공개한다")
  void finalizeResult() {
    Competition ended = Competition.builder().competitionId(UUID.randomUUID()).status(CompetitionStatus.ENDED).build();
    given(competitionRepository.findById(ended.getCompetitionId())).willReturn(Optional.of(ended));

    competitionService.finalizeResult(ended.getCompetitionId());

    assertThat(ended.getStatus()).isEqualTo(CompetitionStatus.FINALIZED);
  }

  @Test
  @DisplayName("종료 상태가 아닌 대회는 결과를 공개할 수 없다")
  void finalizeResultRejectsNotEnded() {
    for (CompetitionStatus status : List.of(
        CompetitionStatus.READY, CompetitionStatus.ACTIVE, CompetitionStatus.FINALIZED)) {
      Competition competition = Competition.builder().competitionId(UUID.randomUUID()).status(status).build();
      given(competitionRepository.findById(competition.getCompetitionId())).willReturn(Optional.of(competition));

      assertThatThrownBy(() -> competitionService.finalizeResult(competition.getCompetitionId()))
          .isInstanceOf(CustomException.class)
          .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_NOT_ENDED);
      assertThat(competition.getStatus()).isEqualTo(status);
    }
  }

  @Test
  @DisplayName("없는 대회의 결과를 공개하면 COMPETITION_NOT_FOUND")
  void finalizeResultNotFound() {
    UUID competitionId = UUID.randomUUID();
    given(competitionRepository.findById(competitionId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> competitionService.finalizeResult(competitionId))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_NOT_FOUND);
  }

  private static Competition activeEndingOn(LocalDate endDate) {
    return Competition.builder()
        .competitionId(UUID.randomUUID())
        .status(CompetitionStatus.ACTIVE)
        .endDate(endDate)
        .build();
  }

  private void givenDue(Competition competition) {
    given(competitionRepository.findAllByStatusAndEndDateLessThanEqual(CompetitionStatus.ACTIVE, TODAY))
        .willReturn(List.of(competition));
  }

  private static CompetitionParticipant participant(Competition competition) {
    return CompetitionParticipant.builder()
        .participantId(UUID.randomUUID())
        .competition(competition)
        .status(ParticipantStatus.ACTIVE)
        .build();
  }

  private static DailyAccountSnapshot snapshot(CompetitionParticipant participant, int rank) {
    return DailyAccountSnapshot.builder()
        .account(CompetitionAccount.builder().participant(participant).build())
        .rank(rank)
        .build();
  }

  @Test
  @DisplayName("시작일이 오늘이면 스케줄러를 기다리지 않고 바로 진행 중으로 만든다")
  @SuppressWarnings("unchecked")
  void createStartingTodayIsActive() {
    given(userRepository.findAllByStatusAndRoleNot(UserStatus.ACTIVE, Role.PENDING_MEMBER))
        .willReturn(List.of(user("매크로팀")));
    given(competitionRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
    given(participantRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

    competitionService.create(new CompetitionCreateRequest(
        "2026 2학기 수익률 대결", LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 27)));

    ArgumentCaptor<Competition> competition = ArgumentCaptor.forClass(Competition.class);
    verify(competitionRepository).saveAndFlush(competition.capture());
    assertThat(competition.getValue().getStatus()).isEqualTo(CompetitionStatus.ACTIVE);
  }

  @Test
  @DisplayName("종료일이 지나지 않은 시작 전이거나 진행 중인 대회가 있으면 열린 대회가 있다고 본다")
  void hasOpenCompetitionChecksStatusAndEndDate() {
    List<CompetitionStatus> open = List.of(CompetitionStatus.READY, CompetitionStatus.ACTIVE);
    LocalDate today = LocalDate.of(2026, 11, 18);

    given(competitionRepository.existsByStatusInAndEndDateGreaterThanEqual(open, today)).willReturn(true);
    assertThat(competitionService.hasOpenCompetition()).isTrue();

    given(competitionRepository.existsByStatusInAndEndDateGreaterThanEqual(open, today)).willReturn(false);
    assertThat(competitionService.hasOpenCompetition()).isFalse();
  }

  @Test
  @DisplayName("현재 대회는 시작일이 가장 늦은 대회이고, 내 참가 상태를 함께 준다")
  void getCurrent() {
    UUID userId = UUID.randomUUID();
    Competition ended = competition(CompetitionStatus.ENDED);
    given(competitionRepository.findFirstByOrderByStartDateDesc()).willReturn(Optional.of(ended));
    given(participantRepository.findByCompetitionAndUserId(ended, userId)).willReturn(Optional.of(
        CompetitionParticipant.builder().competition(ended).userId(userId).status(ParticipantStatus.EXCLUDED).build()));

    CompetitionResponse response = competitionService.getCurrent(userId);

    assertThat(response.competitionId()).isEqualTo(ended.getCompetitionId());
    assertThat(response.status()).isEqualTo(CompetitionStatus.ENDED);
    assertThat(response.initialCash()).isEqualByComparingTo("10000000");
    assertThat(response.myStatus()).isEqualTo(ParticipantStatus.EXCLUDED);
  }

  @Test
  @DisplayName("참가자가 아니면 myStatus는 null이다")
  void getWhenNotParticipant() {
    UUID userId = UUID.randomUUID();
    Competition active = competition(CompetitionStatus.ACTIVE);
    given(competitionRepository.findById(active.getCompetitionId())).willReturn(Optional.of(active));
    given(participantRepository.findByCompetitionAndUserId(active, userId)).willReturn(Optional.empty());

    CompetitionResponse response = competitionService.get(active.getCompetitionId(), userId);

    assertThat(response.status()).isEqualTo(CompetitionStatus.ACTIVE);
    assertThat(response.myStatus()).isNull();
  }

  @Test
  @DisplayName("대회가 한 번도 없으면 COMPETITION_NOT_FOUND")
  void getCurrentWithoutCompetition() {
    given(competitionRepository.findFirstByOrderByStartDateDesc()).willReturn(Optional.empty());

    assertThatThrownBy(() -> competitionService.getCurrent(UUID.randomUUID()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_NOT_FOUND);
  }

  private static Competition competition(CompetitionStatus status) {
    return Competition.builder()
        .competitionId(UUID.randomUUID())
        .name("2026 2학기 수익률 대결")
        .status(status)
        .startDate(LocalDate.of(2026, 11, 16))
        .endDate(LocalDate.of(2026, 12, 11))
        .initialCash(new BigDecimal("10000000"))
        .feeRate(new BigDecimal("0.0001"))
        .depositInterestRate(new BigDecimal("0.03"))
        .build();
  }
}
