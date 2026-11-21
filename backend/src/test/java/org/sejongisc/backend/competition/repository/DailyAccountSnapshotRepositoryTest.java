package org.sejongisc.backend.competition.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyAccountSnapshot;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(CompetitionJpaTestConfig.class)
@Transactional
class DailyAccountSnapshotRepositoryTest {

  private static final LocalDate THU = LocalDate.of(2026, 11, 19);
  private static final LocalDate FRI = LocalDate.of(2026, 11, 20);

  @Autowired DailyAccountSnapshotRepository snapshotRepository;
  @Autowired CompetitionRepository competitionRepository;
  @Autowired CompetitionParticipantRepository participantRepository;
  @Autowired CompetitionAccountRepository accountRepository;
  @Autowired CompetitionStockRepository stockRepository;
  @Autowired DailyClosePriceRepository closePriceRepository;
  @Autowired HoldingRepository holdingRepository;
  @Autowired EntityManager entityManager;
  @Autowired DataSource dataSource;

  @Test
  void 대회와_날짜로_스냅샷을_등수_순으로_읽는다() {
    Competition competition = saveCompetition(CompetitionStatus.ACTIVE);
    CompetitionAccount second = saveAccount(competition, ParticipantStatus.ACTIVE);
    CompetitionAccount first = saveAccount(competition, ParticipantStatus.ACTIVE);
    saveSnapshot(second, FRI, "10000000", "0", 2);
    saveSnapshot(first, FRI, "10500000", "0.05", 1);
    saveSnapshot(first, THU, "9900000", "-0.01", 2);
    CompetitionAccount otherCompetitionAccount = saveAccount(saveCompetition(CompetitionStatus.ENDED), ParticipantStatus.ACTIVE);
    saveSnapshot(otherCompetitionAccount, FRI, "12000000", "0.2", 1);
    entityManager.flush();
    entityManager.clear();

    List<DailyAccountSnapshot> found = snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, FRI, ParticipantStatus.ACTIVE);

    assertThat(found).extracting(snapshot -> snapshot.getAccount().getAccountId())
        .containsExactly(first.getAccountId(), second.getAccountId());
    assertThat(found.get(0).getRank()).isEqualTo(1);
    assertThat(found.get(0).getTotalValue()).isEqualByComparingTo("10500000");
    assertThat(found.get(0).getProfitRate()).isEqualByComparingTo("0.05");
    assertThat(found.get(0).getAccount().getParticipant().getTeamName()).isEqualTo("매크로팀");
  }

  @Test
  void 스냅샷을_만든_뒤_제외된_참가자의_스냅샷은_읽지_않는다() {
    Competition competition = saveCompetition(CompetitionStatus.ACTIVE);
    CompetitionAccount active = saveAccount(competition, ParticipantStatus.ACTIVE);
    CompetitionAccount excludedLater = saveAccount(competition, ParticipantStatus.ACTIVE);
    saveSnapshot(excludedLater, FRI, "10500000", "0.05", 1);
    saveSnapshot(active, FRI, "10000000", "0", 2);
    new JdbcTemplate(dataSource).update(
        "update competition_participant set status = 'EXCLUDED' where participant_id = ?",
        excludedLater.getParticipant().getParticipantId());
    entityManager.flush();
    entityManager.clear();

    List<DailyAccountSnapshot> found =
        snapshotRepository.findAllByCompetitionAndSnapshotDate(competition, FRI, ParticipantStatus.ACTIVE);

    assertThat(found).extracting(snapshot -> snapshot.getAccount().getAccountId())
        .containsExactly(active.getAccountId());
  }

  @Test
  void 대회의_가장_최근_스냅샷_날짜와_그_직전_날짜를_읽는다() {
    Competition competition = saveCompetition(CompetitionStatus.ACTIVE);
    CompetitionAccount a = saveAccount(competition, ParticipantStatus.ACTIVE);
    CompetitionAccount b = saveAccount(competition, ParticipantStatus.ACTIVE);
    LocalDate wed = THU.minusDays(1);
    for (LocalDate date : List.of(wed, THU, FRI)) {
      saveSnapshot(a, date, "10000000", "0", 1);
      saveSnapshot(b, date, "10000000", "0", 1);
    }
    CompetitionAccount otherCompetitionAccount = saveAccount(saveCompetition(CompetitionStatus.ENDED), ParticipantStatus.ACTIVE);
    saveSnapshot(otherCompetitionAccount, FRI.plusDays(3), "10000000", "0", 1);

    assertThat(snapshotRepository.findLatestSnapshotDate(competition)).contains(FRI);
    assertThat(snapshotRepository.findLatestSnapshotDateBefore(competition, FRI)).contains(THU);
    assertThat(snapshotRepository.findLatestSnapshotDateBefore(competition, wed)).isEmpty();
  }

  @Test
  void 스냅샷이_없는_대회는_최근_날짜가_없다() {
    Competition competition = saveCompetition(CompetitionStatus.ACTIVE);

    assertThat(snapshotRepository.findLatestSnapshotDate(competition)).isEmpty();
  }

  @Test
  void 대회에_해당_날짜_스냅샷이_있는지_확인한다() {
    Competition competition = saveCompetition(CompetitionStatus.ACTIVE);
    saveSnapshot(saveAccount(competition, ParticipantStatus.ACTIVE), THU, "10000000", "0", 1);
    Competition ended = saveCompetition(CompetitionStatus.ENDED);
    saveSnapshot(saveAccount(ended, ParticipantStatus.ACTIVE), FRI, "10000000", "0", 1);

    assertThat(snapshotRepository.existsByCompetitionAndSnapshotDate(competition, THU)).isTrue();
    assertThat(snapshotRepository.existsByCompetitionAndSnapshotDate(competition, FRI)).isFalse();
  }

  @Test
  void 같은_계좌의_같은_날짜_스냅샷은_한_번만_저장된다() {
    CompetitionAccount account = saveAccount(saveCompetition(CompetitionStatus.ACTIVE), ParticipantStatus.ACTIVE);
    saveSnapshot(account, FRI, "10000000", "0", 1);

    assertThatThrownBy(() -> saveSnapshot(account, FRI, "10000000", "0", 1))
        .isInstanceOf(DataIntegrityViolationException.class)
        .cause()
        .isInstanceOf(ConstraintViolationException.class)
        .extracting("constraintName").isEqualTo("uk_daily_account_snapshot_account_date");
  }

  @Test
  void 등수는_1보다_작을_수_없다() {
    CompetitionAccount account = saveAccount(saveCompetition(CompetitionStatus.ACTIVE), ParticipantStatus.ACTIVE);

    assertThatThrownBy(() -> saveSnapshot(account, FRI, "10000000", "0", 0))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 대회의_계좌_중_제외되지_않은_참가자_것만_읽는다() {
    Competition competition = saveCompetition(CompetitionStatus.ACTIVE);
    CompetitionAccount active = saveAccount(competition, ParticipantStatus.ACTIVE);
    saveAccount(competition, ParticipantStatus.EXCLUDED);
    saveAccount(saveCompetition(CompetitionStatus.ENDED), ParticipantStatus.ACTIVE);
    entityManager.flush();
    entityManager.clear();

    List<CompetitionAccount> found =
        accountRepository.findAllByCompetitionAndParticipantStatus(competition, ParticipantStatus.ACTIVE);

    assertThat(found).extracting(CompetitionAccount::getAccountId).containsExactly(active.getAccountId());
  }

  @Test
  void 여러_계좌의_보유_종목을_한_번에_읽는다() {
    Competition competition = saveCompetition(CompetitionStatus.ACTIVE);
    CompetitionAccount a = saveAccount(competition, ParticipantStatus.ACTIVE);
    CompetitionAccount b = saveAccount(competition, ParticipantStatus.ACTIVE);
    CompetitionAccount notRequested = saveAccount(competition, ParticipantStatus.ACTIVE);
    CompetitionStock samsung = saveStock("005930");
    saveHolding(a, samsung, 10L);
    saveHolding(b, samsung, 20L);
    saveHolding(notRequested, samsung, 30L);

    List<Holding> found = holdingRepository.findAllByAccountIn(List.of(a, b));

    assertThat(found).extracting(Holding::getQuantity).containsExactlyInAnyOrder(10L, 20L);
  }

  @Test
  void 종목별로_기준일까지의_가장_최근_종가를_읽는다() {
    CompetitionStock samsung = saveStock("005930");
    CompetitionStock hynix = saveStock("000660");
    savePrice(samsung, THU, "60000");
    DailyClosePrice samsungFri = savePrice(samsung, FRI, "61000");
    DailyClosePrice hynixThu = savePrice(hynix, THU, "200000");
    savePrice(hynix, FRI.plusDays(3), "210000");

    List<DailyClosePrice> found =
        closePriceRepository.findLatestByStockIds(List.of(samsung.getStockId(), hynix.getStockId()), FRI);

    assertThat(found).extracting(DailyClosePrice::getClosePriceId)
        .containsExactlyInAnyOrder(samsungFri.getClosePriceId(), hynixThu.getClosePriceId());
  }

  @Test
  void 해당_날짜의_종가가_하나라도_있는지_확인한다() {
    savePrice(saveStock("005930"), THU, "60000");

    assertThat(closePriceRepository.existsByPriceDate(THU)).isTrue();
    assertThat(closePriceRepository.existsByPriceDate(FRI)).isFalse();
  }

  private Competition saveCompetition(CompetitionStatus status) {
    return competitionRepository.saveAndFlush(Competition.builder()
        .name("2026 2학기 수익률 대결")
        .status(status)
        .startDate(LocalDate.of(2026, 11, 16))
        .endDate(LocalDate.of(2026, 11, 27))
        .initialCash(new BigDecimal("10000000"))
        .feeRate(new BigDecimal("0.0001"))
        .depositInterestRate(new BigDecimal("0.03"))
        .build());
  }

  private CompetitionAccount saveAccount(Competition competition, ParticipantStatus status) {
    UUID userId = UUID.randomUUID();
    new JdbcTemplate(dataSource).update("insert into users (user_id) values (?)", userId);
    CompetitionParticipant participant = participantRepository.saveAndFlush(CompetitionParticipant.builder()
        .competition(competition)
        .userId(userId)
        .teamName("매크로팀")
        .status(status)
        .build());
    return accountRepository.saveAndFlush(CompetitionAccount.builder()
        .participant(participant)
        .cash(new BigDecimal("10000000"))
        .deposit(BigDecimal.ZERO)
        .build());
  }

  private DailyAccountSnapshot saveSnapshot(
      CompetitionAccount account, LocalDate date, String totalValue, String profitRate, int rank) {
    return snapshotRepository.saveAndFlush(DailyAccountSnapshot.builder()
        .account(account)
        .snapshotDate(date)
        .totalValue(new BigDecimal(totalValue))
        .profitRate(new BigDecimal(profitRate))
        .rank(rank)
        .build());
  }

  private CompetitionStock saveStock(String ticker) {
    return stockRepository.saveAndFlush(CompetitionStock.builder()
        .ticker(ticker).name("종목" + ticker).market(Market.KR).build());
  }

  private DailyClosePrice savePrice(CompetitionStock stock, LocalDate date, String price) {
    return closePriceRepository.saveAndFlush(DailyClosePrice.builder()
        .stock(stock)
        .priceDate(date)
        .availableAt(LocalDateTime.of(date, LocalTime.of(16, 30)))
        .closePrice(new BigDecimal(price))
        .build());
  }

  private void saveHolding(CompetitionAccount account, CompetitionStock stock, long quantity) {
    holdingRepository.saveAndFlush(Holding.builder()
        .account(account).stock(stock).quantity(quantity).avgBuyPrice(new BigDecimal("60000")).build());
  }
}
