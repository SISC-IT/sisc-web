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
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.entity.UserStatus;
import org.sejongisc.backend.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceTest {

  @Mock CompetitionRepository competitionRepository;
  @Mock CompetitionParticipantRepository participantRepository;
  @Mock CompetitionAccountRepository accountRepository;
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
        competitionRepository, participantRepository, accountRepository, userRepository, clock);
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
  @DisplayName("시작 전이거나 진행 중인 대회가 있으면 만들지 않는다")
  void rejectWhenOpenCompetitionExists() {
    given(competitionRepository.existsByStatusIn(List.of(CompetitionStatus.READY, CompetitionStatus.ACTIVE)))
        .willReturn(true);

    assertError(ErrorCode.COMPETITION_ALREADY_OPEN);
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
  @DisplayName("종료일이 시작일보다 빠르면 만들지 않는다")
  void rejectInvalidPeriod() {
    CompetitionCreateRequest reversed =
        new CompetitionCreateRequest("대회", LocalDate.of(2026, 12, 4), LocalDate.of(2026, 11, 23));

    assertThatThrownBy(() -> competitionService.create(reversed))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_COMPETITION_PERIOD);
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
  @DisplayName("동시 요청으로 인덱스에 막히면 열린 대회 에러로 바꾸고 참가자를 만들지 않는다")
  void translateUniqueViolation() {
    given(userRepository.findAllByStatusAndRoleNot(UserStatus.ACTIVE, Role.PENDING_MEMBER))
        .willReturn(List.of(user("매크로팀")));
    given(competitionRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk_competition_single_open"));

    assertThatThrownBy(() -> competitionService.create(request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.COMPETITION_ALREADY_OPEN);
    verify(participantRepository, never()).saveAll(anyList());
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
}
