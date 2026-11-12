package org.sejongisc.backend.competition.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(CompetitionJpaTestConfig.class)
@Transactional
class CompetitionAccountRepositoryTest {

  @Autowired CompetitionRepository competitionRepository;
  @Autowired CompetitionParticipantRepository participantRepository;
  @Autowired CompetitionAccountRepository accountRepository;
  @Autowired HoldingRepository holdingRepository;
  @Autowired CompetitionStockRepository stockRepository;
  @Autowired EntityManager entityManager;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired DataSource dataSource;

  @Test
  void 대회_참가자_계좌_보유종목을_저장하고_다시_읽는다() {
    Competition competition = saveCompetition(CompetitionStatus.ACTIVE);
    UUID userId = saveUser();
    CompetitionParticipant participant = saveParticipant(competition, userId, "매크로팀");
    CompetitionAccount account = saveAccount(participant, "10000000", "0");
    CompetitionStock samsung = saveStock("005930");
    saveHolding(account, samsung, 20L, "75000.5");
    entityManager.flush();
    entityManager.clear(); // 1차 캐시가 아니라 DB에서 다시 읽도록

    Competition foundCompetition = competitionRepository.findFirstByStatusOrderByStartDateDesc(CompetitionStatus.ACTIVE).orElseThrow();
    assertThat(foundCompetition.getFeeRate()).isEqualByComparingTo("0.0001");
    assertThat(foundCompetition.getDepositInterestRate()).isEqualByComparingTo("0.01");

    CompetitionParticipant foundParticipant =
        participantRepository.findByCompetitionAndUserId(foundCompetition, userId).orElseThrow();
    assertThat(foundParticipant.getTeamName()).isEqualTo("매크로팀");
    assertThat(foundParticipant.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
    assertThat(foundParticipant.getFinalRank()).isNull();
    assertThat(participantRepository.findAllByCompetition(foundCompetition)).hasSize(1);

    CompetitionAccount foundAccount = accountRepository.findByParticipant(foundParticipant).orElseThrow();
    assertThat(foundAccount.getCash()).isEqualByComparingTo("10000000");
    assertThat(foundAccount.getVersion()).isZero();

    Holding foundHolding = holdingRepository.findByAccountAndStock(foundAccount, samsung).orElseThrow();
    assertThat(foundHolding.getQuantity()).isEqualTo(20L);
    assertThat(foundHolding.getAvgBuyPrice()).isEqualByComparingTo("75000.5");
    assertThat(holdingRepository.findAllByAccount(foundAccount)).hasSize(1);
  }

  @Test
  void 진행_중_대회는_하나만_있을_수_있다() {
    saveCompetition(CompetitionStatus.ENDED);
    saveCompetition(CompetitionStatus.ENDED); // 종료된 대회는 여러 개여도 된다
    saveCompetition(CompetitionStatus.ACTIVE);

    assertThatThrownBy(() -> saveCompetition(CompetitionStatus.ACTIVE))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_상태의_대회가_여럿이면_시작일이_가장_늦은_대회를_가져온다() {
    competitionRepository.saveAndFlush(competitionBuilder(CompetitionStatus.ENDED)
        .startDate(LocalDate.of(2026, 3, 2)).endDate(LocalDate.of(2026, 3, 13)).build());
    Competition latest = saveCompetition(CompetitionStatus.ENDED); // 11/16 시작

    Competition found = competitionRepository.findFirstByStatusOrderByStartDateDesc(CompetitionStatus.ENDED)
        .orElseThrow();

    assertThat(found.getCompetitionId()).isEqualTo(latest.getCompetitionId());
  }

  @Test
  void 팀이_없는_참가자는_저장할_수_없다() {
    Competition competition = saveCompetition(CompetitionStatus.READY);

    assertThatThrownBy(() -> saveParticipant(competition, saveUser(), null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_대회에_같은_사용자는_한_번만_등록된다() {
    Competition competition = saveCompetition(CompetitionStatus.READY);
    UUID userId = saveUser();
    saveParticipant(competition, userId, "매크로팀");

    assertThatThrownBy(() -> saveParticipant(competition, userId, "매크로팀"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 참가자당_계좌는_하나다() {
    CompetitionParticipant participant =
        saveParticipant(saveCompetition(CompetitionStatus.READY), saveUser(), "매크로팀");
    saveAccount(participant, "10000000", "0");

    assertThatThrownBy(() -> saveAccount(participant, "10000000", "0"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_계좌에_같은_종목_보유_행은_하나다() {
    CompetitionAccount account = saveAccount(
        saveParticipant(saveCompetition(CompetitionStatus.READY), saveUser(), "매크로팀"), "10000000", "0");
    CompetitionStock samsung = saveStock("005930");
    saveHolding(account, samsung, 10L, "70000");

    assertThatThrownBy(() -> saveHolding(account, samsung, 10L, "80000"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 현금은_음수가_될_수_없다() {
    CompetitionParticipant participant =
        saveParticipant(saveCompetition(CompetitionStatus.READY), saveUser(), "매크로팀");

    assertThatThrownBy(() -> saveAccount(participant, "-1", "0"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 보유_수량은_0이_될_수_없다() {
    CompetitionAccount account = saveAccount(
        saveParticipant(saveCompetition(CompetitionStatus.READY), saveUser(), "매크로팀"), "10000000", "0");

    assertThatThrownBy(() -> saveHolding(account, saveStock("005930"), 0L, "70000"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * 두 요청이 같은 잔고를 읽고 각자 차감해 저장하는 상황.
   * 테스트 트랜잭션 하나로는 재현할 수 없어서 EntityManager 두 개로 각각 커밋한다.
   */
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void 같은_계좌를_동시에_수정하면_늦게_저장한_쪽이_실패한다() {
    UUID accountId = inNewTransaction(em -> {
      Competition competition = competitionBuilder(CompetitionStatus.READY).build();
      em.persist(competition);
      CompetitionParticipant participant = participantBuilder(competition, saveUser(), "매크로팀").build();
      em.persist(participant);
      CompetitionAccount account = accountBuilder(participant, "1000000", "0").build();
      em.persist(account);
      return account.getAccountId();
    });

    EntityManager requestA = entityManagerFactory.createEntityManager();
    EntityManager requestB = entityManagerFactory.createEntityManager();
    requestA.getTransaction().begin();
    requestB.getTransaction().begin();
    CompetitionAccount readByA = requestA.find(CompetitionAccount.class, accountId); // 100만, version 0
    CompetitionAccount readByB = requestB.find(CompetitionAccount.class, accountId); // 100만, version 0

    ReflectionTestUtils.setField(readByA, "cash", new BigDecimal("200000")); // 80만 매수
    requestA.getTransaction().commit(); // version 0 -> 1

    ReflectionTestUtils.setField(readByB, "cash", new BigDecimal("200000")); // 같은 잔고를 보고 80만 매수
    assertThatThrownBy(() -> requestB.getTransaction().commit())
        .isInstanceOf(RollbackException.class)
        .hasCauseInstanceOf(OptimisticLockException.class);
    requestA.close();
    requestB.close();

    CompetitionAccount result = inNewTransaction(em -> em.find(CompetitionAccount.class, accountId));
    assertThat(result.getCash()).isEqualByComparingTo("200000");
    assertThat(result.getVersion()).isEqualTo(1L);
  }

  private Competition saveCompetition(CompetitionStatus status) {
    return competitionRepository.saveAndFlush(competitionBuilder(status).build());
  }

  private CompetitionParticipant saveParticipant(Competition competition, UUID userId, String teamName) {
    return participantRepository.saveAndFlush(participantBuilder(competition, userId, teamName).build());
  }

  private CompetitionAccount saveAccount(CompetitionParticipant participant, String cash, String deposit) {
    return accountRepository.saveAndFlush(accountBuilder(participant, cash, deposit).build());
  }

  private CompetitionStock saveStock(String ticker) {
    return stockRepository.saveAndFlush(CompetitionStock.builder()
        .ticker(ticker)
        .name("종목" + ticker)
        .market(Market.KR)
        .build());
  }

  private void saveHolding(CompetitionAccount account, CompetitionStock stock, long quantity, String avgBuyPrice) {
    holdingRepository.saveAndFlush(Holding.builder()
        .account(account)
        .stock(stock)
        .quantity(quantity)
        .avgBuyPrice(new BigDecimal(avgBuyPrice))
        .build());
  }

  // users는 테스트용 최소 테이블이라 PK만 넣는다
  private UUID saveUser() {
    UUID userId = UUID.randomUUID();
    new JdbcTemplate(dataSource).update("insert into users (user_id) values (?)", userId);
    return userId;
  }

  private static Competition.CompetitionBuilder competitionBuilder(CompetitionStatus status) {
    return Competition.builder()
        .name("2026 2학기 수익률 대결")
        .status(status)
        .startDate(LocalDate.of(2026, 11, 16))
        .endDate(LocalDate.of(2026, 11, 27))
        .initialCash(new BigDecimal("10000000"))
        .feeRate(new BigDecimal("0.0001"))
        .depositInterestRate(new BigDecimal("0.01"));
  }

  private static CompetitionParticipant.CompetitionParticipantBuilder participantBuilder(
      Competition competition, UUID userId, String teamName) {
    return CompetitionParticipant.builder()
        .competition(competition)
        .userId(userId)
        .teamName(teamName)
        .status(ParticipantStatus.ACTIVE);
  }

  private static CompetitionAccount.CompetitionAccountBuilder accountBuilder(
      CompetitionParticipant participant, String cash, String deposit) {
    return CompetitionAccount.builder()
        .participant(participant)
        .cash(new BigDecimal(cash))
        .deposit(new BigDecimal(deposit));
  }

  private <T> T inNewTransaction(java.util.function.Function<EntityManager, T> work) {
    EntityManager em = entityManagerFactory.createEntityManager();
    try {
      em.getTransaction().begin();
      T result = work.apply(em);
      em.getTransaction().commit();
      return result;
    } finally {
      em.close();
    }
  }
}
