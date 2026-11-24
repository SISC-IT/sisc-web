package org.sejongisc.backend.competition.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitionService {

  // 대회마다 DB에 저장되며, 여기 값은 생성 시 기본값으로만 쓴다.
  private static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("10000000");
  private static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.0001");             // 0.01%
  private static final BigDecimal DEFAULT_DEPOSIT_INTEREST_RATE = new BigDecimal("0.03"); // 연 3%, 매일 ÷365로 지급

  private static final String SINGLE_OPEN_INDEX = "uk_competition_single_open";

  private final CompetitionRepository competitionRepository;
  private final CompetitionParticipantRepository participantRepository;
  private final CompetitionAccountRepository accountRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  // 대회를 READY(시작일이 오늘이면 ACTIVE)로 만들고 활동 회원 전원을 참가자로 등록해 계좌를 만든다.
  @Transactional
  public CompetitionCreateResponse create(CompetitionCreateRequest request) {
    LocalDate today = LocalDate.now(clock);
    if (request.startDate().isBefore(today)) {
      throw new CustomException(ErrorCode.COMPETITION_START_DATE_PAST);
    }
    if (request.endDate().isBefore(request.startDate())) {
      throw new CustomException(ErrorCode.INVALID_COMPETITION_PERIOD);
    }

    // 승인 대기 회원은 status가 ACTIVE여도 서비스를 쓸 수 없어 제외한다.
    List<User> members = userRepository.findAllByStatusAndRoleNot(UserStatus.ACTIVE, Role.PENDING_MEMBER);
    if (members.isEmpty()) {
      throw new CustomException(ErrorCode.COMPETITION_NO_PARTICIPANTS);
    }
    List<UUID> unassigned = members.stream()
        .filter(user -> user.getTeamName() == null || user.getTeamName().isBlank())
        .map(User::getUserId)
        .toList();
    if (!unassigned.isEmpty()) {
      log.warn("팀 미배정 활동 회원 {}명으로 대회 생성 거부: userIds={}", unassigned.size(), unassigned);
      throw new CustomException(ErrorCode.COMPETITION_TEAM_UNASSIGNED);
    }

    Competition competition = saveOpenCompetition(Competition.builder()
        .name(request.name())
        // 오늘 시작하는 대회는 다음 스케줄러 실행(매시 정각)을 기다리지 않고 바로 연다.
        .status(request.startDate().isAfter(today) ? CompetitionStatus.READY : CompetitionStatus.ACTIVE)
        .startDate(request.startDate())
        .endDate(request.endDate())
        .initialCash(DEFAULT_INITIAL_CASH)
        .feeRate(DEFAULT_FEE_RATE)
        .depositInterestRate(DEFAULT_DEPOSIT_INTEREST_RATE)
        .build());

    List<CompetitionParticipant> participants = participantRepository.saveAll(members.stream()
        .map(user -> CompetitionParticipant.builder()
            .competition(competition)
            .userId(user.getUserId())
            .teamName(user.getTeamName())
            .status(ParticipantStatus.ACTIVE)
            .build())
        .toList());

    accountRepository.saveAll(participants.stream()
        .map(participant -> CompetitionAccount.builder()
            .participant(participant)
            .cash(competition.getInitialCash())
            .deposit(BigDecimal.ZERO)
            .build())
        .toList());

    return CompetitionCreateResponse.of(competition, participants.size());
  }

  // 종료 처리가 늦어져 상태가 ACTIVE로 남아 있어도 종료일이 지나면 닫힌 것으로 본다.
  @Transactional(readOnly = true)
  public boolean hasOpenCompetition() {
    return competitionRepository.existsByStatusInAndEndDateGreaterThanEqual(
        List.of(CompetitionStatus.READY, CompetitionStatus.ACTIVE), LocalDate.now(clock));
  }

  // 시작일이 지난 READY 대회를 ACTIVE로 바꾼다. 스케줄러가 주기적으로 호출한다.
  // '시작일 = 오늘'이 아니라 '시작일 <= 오늘'로 찾아서, 실행을 한 번 놓쳐도 다음 실행에서 열린다.
  @Transactional
  public void startDueCompetitions() {
    List<Competition> due = competitionRepository.findAllByStatusAndStartDateLessThanEqual(
        CompetitionStatus.READY, LocalDate.now(clock));
    for (Competition competition : due) {
      competition.start();
      log.info("[대회 시작] competitionId={}, startDate={}", competition.getCompetitionId(), competition.getStartDate());
    }
  }

  // 시작 전·진행 중 대회가 이미 있으면 uk_competition_single_open 인덱스가 막는다(동시 요청 포함).
  // 그냥 save면 커밋 시점에 insert되어 여기서 잡을 수 없으므로 바로 flush한다.
  // 그 인덱스 위반만 409로 바꾸고, 다른 DB 오류는 그대로 올린다.
  private Competition saveOpenCompetition(Competition competition) {
    try {
      return competitionRepository.saveAndFlush(competition);
    } catch (DataIntegrityViolationException e) {
      if (e.getCause() instanceof ConstraintViolationException violation
          && SINGLE_OPEN_INDEX.equals(violation.getConstraintName())) {
        throw new CustomException(ErrorCode.COMPETITION_ALREADY_OPEN);
      }
      throw e;
    }
  }
}
