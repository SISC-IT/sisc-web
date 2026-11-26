package org.sejongisc.backend.competition.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import javax.sql.DataSource;
import org.hibernate.Hibernate;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.entity.Trade;
import org.sejongisc.backend.competition.entity.TradeSide;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(CompetitionJpaTestConfig.class)
@Transactional
class TradeRepositoryTest {

  @Autowired TradeRepository tradeRepository;
  @Autowired CompetitionRepository competitionRepository;
  @Autowired CompetitionParticipantRepository participantRepository;
  @Autowired CompetitionAccountRepository accountRepository;
  @Autowired CompetitionStockRepository stockRepository;
  @Autowired DailyClosePriceRepository closePriceRepository;
  @Autowired EntityManager entityManager;
  @Autowired DataSource dataSource;

  private Competition competition;
  private CompetitionStock samsung;
  private DailyClosePrice closePrice;

  @BeforeEach
  void setUp() {
    competition = competitionRepository.saveAndFlush(Competition.builder()
        .name("2026 2학기 수익률 대결")
        .status(CompetitionStatus.ACTIVE)
        .startDate(LocalDate.of(2026, 11, 16))
        .endDate(LocalDate.of(2026, 11, 27))
        .initialCash(new BigDecimal("10000000"))
        .feeRate(new BigDecimal("0.0001"))
        .depositInterestRate(new BigDecimal("0.03"))
        .build());
    samsung = stockRepository.saveAndFlush(CompetitionStock.builder()
        .ticker("005930").name("삼성전자").market(Market.KR).build());
    closePrice = closePriceRepository.saveAndFlush(DailyClosePrice.builder()
        .stock(samsung)
        .priceDate(LocalDate.of(2026, 11, 20))
        .availableAt(LocalDateTime.of(2026, 11, 20, 16, 30))
        .closePrice(new BigDecimal("60000"))
        .build());
  }

  @Test
  void 거래를_저장하고_계좌와_멱등키로_다시_찾는다() {
    CompetitionAccount account = saveAccount();
    UUID key = UUID.randomUUID();
    tradeRepository.saveAndFlush(trade(account, key));
    entityManager.clear(); // 1차 캐시가 아니라 DB에서 다시 읽도록

    Trade found = tradeRepository.findByAccountAndIdempotencyKey(account, key).orElseThrow();

    assertThat(found.getSide()).isEqualTo(TradeSide.BUY);
    assertThat(found.getQuantity()).isEqualTo(10L);
    assertThat(found.getFee()).isEqualByComparingTo("60");
    assertThat(found.getClosePrice().getClosePriceId()).isEqualTo(closePrice.getClosePriceId());
    assertThat(found.getPrice()).isEqualByComparingTo("60000");
    assertThat(found.getCashAfter()).isEqualByComparingTo("9399940");
    assertThat(found.getExecutedAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 17, 0));
    assertThat(tradeRepository.findByAccountAndIdempotencyKey(account, UUID.randomUUID())).isEmpty();
  }

  @Test
  void 같은_계좌에서_같은_멱등키는_한_번만_저장된다() {
    CompetitionAccount account = saveAccount();
    UUID key = UUID.randomUUID();
    tradeRepository.saveAndFlush(trade(account, key));

    assertThatThrownBy(() -> tradeRepository.saveAndFlush(trade(account, key)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .cause()
        .isInstanceOf(ConstraintViolationException.class)
        .extracting("constraintName").isEqualTo("uk_trade_account_idempotency_key");
  }

  @Test
  void 다른_계좌는_같은_멱등키를_써도_된다() {
    UUID key = UUID.randomUUID();
    tradeRepository.saveAndFlush(trade(saveAccount(), key));
    tradeRepository.saveAndFlush(trade(saveAccount(), key));

    assertThat(tradeRepository.count()).isEqualTo(2);
  }

  @Test
  void 거래_수량은_0이_될_수_없다() {
    Trade zero = Trade.builder()
        .account(saveAccount()).stock(samsung).closePrice(closePrice)
        .side(TradeSide.BUY).quantity(0L).price(new BigDecimal("60000")).fee(BigDecimal.ZERO).cashAfter(BigDecimal.ZERO)
        .idempotencyKey(UUID.randomUUID()).executedAt(LocalDateTime.of(2026, 11, 20, 17, 0))
        .build();

    assertThatThrownBy(() -> tradeRepository.saveAndFlush(zero))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 체결가는_0이_될_수_없다() {
    Trade zeroPrice = Trade.builder()
        .account(saveAccount()).stock(samsung).closePrice(closePrice)
        .side(TradeSide.BUY).quantity(1L).price(BigDecimal.ZERO).fee(BigDecimal.ZERO).cashAfter(BigDecimal.ZERO)
        .idempotencyKey(UUID.randomUUID()).executedAt(LocalDateTime.of(2026, 11, 20, 17, 0))
        .build();

    assertThatThrownBy(() -> tradeRepository.saveAndFlush(zeroPrice))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 계좌의_거래를_최근_체결순으로_페이지에_나눠_읽고_종목도_함께_읽는다() {
    CompetitionAccount account = saveAccount();
    tradeRepository.saveAndFlush(trade(account, UUID.randomUUID(), LocalDateTime.of(2026, 11, 18, 17, 0)));
    tradeRepository.saveAndFlush(trade(account, UUID.randomUUID(), LocalDateTime.of(2026, 11, 20, 17, 0)));
    tradeRepository.saveAndFlush(trade(account, UUID.randomUUID(), LocalDateTime.of(2026, 11, 19, 17, 0)));
    tradeRepository.saveAndFlush(trade(saveAccount(), UUID.randomUUID(), LocalDateTime.of(2026, 11, 21, 17, 0)));
    entityManager.clear();

    Page<Trade> first = tradeRepository.findAllByAccountOrderByExecutedAtDesc(account, PageRequest.of(0, 2));
    Page<Trade> second = tradeRepository.findAllByAccountOrderByExecutedAtDesc(account, PageRequest.of(1, 2));

    assertThat(first.getTotalElements()).isEqualTo(3); // 다른 계좌의 거래는 세지 않는다
    assertThat(first.getContent()).extracting(Trade::getExecutedAt).containsExactly(
        LocalDateTime.of(2026, 11, 20, 17, 0), LocalDateTime.of(2026, 11, 19, 17, 0));
    assertThat(second.getContent()).extracting(Trade::getExecutedAt).containsExactly(
        LocalDateTime.of(2026, 11, 18, 17, 0));
    List<Trade> trades = first.getContent();
    assertThat(trades).allMatch(trade -> Hibernate.isInitialized(trade.getStock()));
  }

  @Test
  void 그_시각_전_수수료를_합하고_그날의_첫_거래와_마지막_거래를_찾는다() {
    CompetitionAccount account = saveAccount();
    tradeRepository.saveAndFlush(trade(account, UUID.randomUUID(), LocalDateTime.of(2026, 11, 19, 17, 0))); // 수수료 60
    tradeRepository.saveAndFlush(trade(account, UUID.randomUUID(), LocalDateTime.of(2026, 11, 20, 16, 45)));
    tradeRepository.saveAndFlush(trade(account, UUID.randomUUID(), LocalDateTime.of(2026, 11, 20, 16, 50)));
    tradeRepository.saveAndFlush(trade(account, UUID.randomUUID(), LocalDateTime.of(2026, 11, 21, 17, 0))); // 다음 날
    LocalDateTime dayStart = LocalDateTime.of(2026, 11, 20, 0, 0);
    LocalDateTime dayEnd = LocalDateTime.of(2026, 11, 21, 0, 0);

    assertThat(tradeRepository.sumFeeExecutedBefore(account, dayEnd)).isEqualByComparingTo("180");
    assertThat(tradeRepository.sumFeeExecutedBefore(saveAccount(), dayEnd)).isEqualByComparingTo("0");
    assertThat(tradeRepository.existsByAccountAndExecutedAtLessThan(account, dayStart.minusDays(1))).isFalse();
    assertThat(tradeRepository.existsByAccountAndExecutedAtLessThan(account, dayEnd)).isTrue();
    assertThat(tradeRepository
        .findFirstByAccountAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtAsc(account, dayStart, dayEnd))
        .map(Trade::getExecutedAt).contains(LocalDateTime.of(2026, 11, 20, 16, 45));
    assertThat(tradeRepository
        .findFirstByAccountAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtDesc(account, dayStart, dayEnd))
        .map(Trade::getExecutedAt).contains(LocalDateTime.of(2026, 11, 20, 16, 50));
  }

  @Test
  void 대회의_계좌별_거래_횟수와_마지막_체결_시각을_모은다() {
    CompetitionAccount kim = saveAccount();
    CompetitionAccount lee = saveAccount();
    saveAccount(); // 거래 없는 계좌는 결과에 없다
    tradeRepository.saveAndFlush(trade(kim, UUID.randomUUID(), LocalDateTime.of(2026, 11, 18, 17, 0)));
    tradeRepository.saveAndFlush(trade(kim, UUID.randomUUID(), LocalDateTime.of(2026, 11, 20, 17, 0)));
    tradeRepository.saveAndFlush(trade(lee, UUID.randomUUID(), LocalDateTime.of(2026, 11, 19, 17, 0)));

    List<TradeRepository.TradeActivity> activities = tradeRepository.findTradeActivities(competition);

    assertThat(activities)
        .extracting(TradeRepository.TradeActivity::getAccountId, TradeRepository.TradeActivity::getTradeCount,
            TradeRepository.TradeActivity::getLastTradedAt)
        .containsExactlyInAnyOrder(
            tuple(kim.getAccountId(), 2L, LocalDateTime.of(2026, 11, 20, 17, 0)),
            tuple(lee.getAccountId(), 1L, LocalDateTime.of(2026, 11, 19, 17, 0)));
  }

  private Trade trade(CompetitionAccount account, UUID key) {
    return trade(account, key, LocalDateTime.of(2026, 11, 20, 17, 0));
  }

  private Trade trade(CompetitionAccount account, UUID key, LocalDateTime executedAt) {
    return Trade.builder()
        .account(account)
        .stock(samsung)
        .closePrice(closePrice)
        .side(TradeSide.BUY)
        .quantity(10L)
        .price(new BigDecimal("60000"))
        .fee(new BigDecimal("60"))
        .cashAfter(new BigDecimal("9399940"))
        .idempotencyKey(key)
        .executedAt(executedAt)
        .build();
  }

  private CompetitionAccount saveAccount() {
    UUID userId = UUID.randomUUID();
    new JdbcTemplate(dataSource).update("insert into users (user_id) values (?)", userId);
    CompetitionParticipant participant = participantRepository.saveAndFlush(CompetitionParticipant.builder()
        .competition(competition)
        .userId(userId)
        .teamName("매크로팀")
        .status(ParticipantStatus.ACTIVE)
        .build());
    return accountRepository.saveAndFlush(CompetitionAccount.builder()
        .participant(participant)
        .cash(new BigDecimal("10000000"))
        .deposit(BigDecimal.ZERO)
        .build());
  }
}
