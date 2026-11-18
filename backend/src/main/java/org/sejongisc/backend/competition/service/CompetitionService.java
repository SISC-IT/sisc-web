package org.sejongisc.backend.competition.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  static final BigDecimal DEFAULT_INITIAL_CASH = new BigDecimal("10000000");
  static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.0001");             // 0.01%
  static final BigDecimal DEFAULT_DEPOSIT_INTEREST_RATE = new BigDecimal("0.03"); // 연 3%, 매일 ÷365로 지급

  private final CompetitionRepository competitionRepository;
  private final CompetitionParticipantRepository participantRepository;
  private final CompetitionAccountRepository accountRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  // 대회를 READY로 만들고 활동 회원 전원을 참가자로 등록해 계좌를 만든다.
  @Transactional
  public CompetitionCreateResponse create(CompetitionCreateRequest request) {
    if (request.startDate().isBefore(LocalDate.now(clock))) {
      throw new CustomException(ErrorCode.COMPETITION_START_DATE_PAST);
    }
    if (request.endDate().isBefore(request.startDate())) {
      throw new CustomException(ErrorCode.INVALID_COMPETITION_PERIOD);
    }
    // 평소엔 여기서 막고, 동시 요청은 saveOpenCompetition에서 막는다.
    if (competitionRepository.existsByStatusIn(List.of(CompetitionStatus.READY, CompetitionStatus.ACTIVE))) {
      throw new CustomException(ErrorCode.COMPETITION_ALREADY_OPEN);
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
        .status(CompetitionStatus.READY)
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

  // 동시 요청이 위 확인을 함께 통과하면 uk_competition_single_open 인덱스가 막는다.
  // 그냥 save면 커밋 시점에 insert되어 여기서 잡을 수 없으므로 바로 flush한다.
  private Competition saveOpenCompetition(Competition competition) {
    try {
      return competitionRepository.saveAndFlush(competition);
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.COMPETITION_ALREADY_OPEN);
    }
  }
}
