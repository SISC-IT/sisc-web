package org.sejongisc.backend.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyAccountSnapshot;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.DailyAccountSnapshotRepository;
import org.sejongisc.backend.competition.repository.DailyClosePriceRepository;
import org.sejongisc.backend.competition.repository.HoldingRepository;

@ExtendWith(MockitoExtension.class)
class AccountSnapshotServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate THU = LocalDate.of(2026, 11, 19);
    private static final LocalDate FRI = LocalDate.of(2026, 11, 20);
    private static final LocalDateTime NOW = FRI.atTime(16, 5);

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private CompetitionAccountRepository accountRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private DailyClosePriceRepository closePriceRepository;

    @Mock
    private DailyAccountSnapshotRepository snapshotRepository;

    @Captor
    private ArgumentCaptor<List<DailyAccountSnapshot>> savedCaptor;

    private AccountSnapshotService snapshotService;
    private Competition competition;
    private CompetitionStock samsung;

    @BeforeEach
    void setUp() {
        snapshotService = new AccountSnapshotService(
                competitionRepository, accountRepository, holdingRepository, closePriceRepository,
                snapshotRepository, Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
        competition = Competition.builder()
                .competitionId(UUID.randomUUID())
                .status(CompetitionStatus.ACTIVE)
                .initialCash(new BigDecimal("10000000.0000"))
                .build();
        samsung = CompetitionStock.builder()
                .stockId(UUID.randomUUID()).ticker("005930").name("삼성전자").market(Market.KR).build();
    }

    @Test
    @DisplayName("현금과 보유 종목 평가액을 더해 총자산과 수익률을 저장한다")
    void savesTotalValueAndProfitRate() {
        CompetitionAccount account = account("3000000", "0");
        givenSnapshotTarget(account);
        givenHoldings(holding(account, samsung, 30L));
        givenPrices(price(samsung, FRI, "201500"));

        int saved = snapshotService.createDailySnapshot();

        assertThat(saved).isEqualTo(1);
        DailyAccountSnapshot snapshot = savedSnapshots().get(0);
        assertThat(snapshot.getAccount()).isSameAs(account);
        assertThat(snapshot.getSnapshotDate()).isEqualTo(FRI);
        assertThat(snapshot.getTotalValue()).isEqualByComparingTo("9045000");
        assertThat(snapshot.getProfitRate()).isEqualByComparingTo("-0.0955");
        assertThat(snapshot.getRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("예금도 총자산에 더한다")
    void addsDeposit() {
        givenSnapshotTarget(account("4000000", "6500000"));
        givenHoldings();

        snapshotService.createDailySnapshot();

        assertThat(savedSnapshots().get(0).getTotalValue()).isEqualByComparingTo("10500000");
        assertThat(savedSnapshots().get(0).getProfitRate()).isEqualByComparingTo("0.05");
    }

    @Test
    @DisplayName("거래하지 않은 계좌는 수익률 0으로 저장한다")
    void untradedAccountHasZeroProfitRate() {
        givenSnapshotTarget(account("10000000", "0"));
        givenHoldings();

        snapshotService.createDailySnapshot();

        assertThat(savedSnapshots().get(0).getProfitRate()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("총자산이 큰 순서로 등수를 매기고, 같으면 같은 등수를 준 뒤 다음 등수를 건너뛴다")
    void ranksByTotalValueWithTies() {
        CompetitionAccount fourth = account("9000000", "0");
        CompetitionAccount tiedA = account("10000000", "0");
        CompetitionAccount first = account("11000000", "0");
        CompetitionAccount tiedB = account("10000000", "0");
        givenSnapshotTarget(fourth, tiedA, first, tiedB);
        givenHoldings();

        snapshotService.createDailySnapshot();

        List<DailyAccountSnapshot> snapshots = savedSnapshots();
        assertThat(rankOf(snapshots, first)).isEqualTo(1);
        assertThat(rankOf(snapshots, tiedA)).isEqualTo(2);
        assertThat(rankOf(snapshots, tiedB)).isEqualTo(2);
        assertThat(rankOf(snapshots, fourth)).isEqualTo(4);
    }

    @Test
    @DisplayName("오늘 종가가 빠진 종목은 가장 최근 종가로 평가한다")
    void usesLatestPriceWhenTodayPriceMissing() {
        CompetitionAccount account = account("0", "0");
        givenSnapshotTarget(account);
        givenHoldings(holding(account, samsung, 10L));
        givenPrices(price(samsung, THU, "60000"));

        snapshotService.createDailySnapshot();

        assertThat(savedSnapshots().get(0).getTotalValue()).isEqualByComparingTo("600000");
    }

    @Test
    @DisplayName("진행 중인 대회가 없으면 만들지 않는다")
    void skipsWhenNoActiveCompetition() {
        when(competitionRepository.findFirstByStatusOrderByStartDateDesc(CompetitionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(snapshotService.createDailySnapshot()).isZero();
        verify(snapshotRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("오늘 종가가 하나도 없으면 휴장일로 보고 만들지 않는다")
    void skipsOnHoliday() {
        givenActiveCompetition();
        when(closePriceRepository.existsByPriceDate(FRI)).thenReturn(false);

        assertThat(snapshotService.createDailySnapshot()).isZero();
        verify(snapshotRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("오늘 스냅샷이 이미 있으면 다시 만들지 않는다")
    void skipsWhenAlreadyCreated() {
        givenActiveCompetition();
        when(closePriceRepository.existsByPriceDate(FRI)).thenReturn(true);
        when(snapshotRepository.existsByCompetitionAndSnapshotDate(competition, FRI)).thenReturn(true);

        assertThat(snapshotService.createDailySnapshot()).isZero();
        verify(snapshotRepository, never()).saveAll(any());
    }

    private void givenActiveCompetition() {
        when(competitionRepository.findFirstByStatusOrderByStartDateDesc(CompetitionStatus.ACTIVE))
                .thenReturn(Optional.of(competition));
    }

    private void givenSnapshotTarget(CompetitionAccount... accounts) {
        givenActiveCompetition();
        when(closePriceRepository.existsByPriceDate(FRI)).thenReturn(true);
        when(snapshotRepository.existsByCompetitionAndSnapshotDate(competition, FRI)).thenReturn(false);
        when(accountRepository.findAllByCompetitionAndParticipantStatus(competition, ParticipantStatus.ACTIVE))
                .thenReturn(List.of(accounts));
    }

    private void givenHoldings(Holding... holdings) {
        when(holdingRepository.findAllByAccountIn(anyCollection())).thenReturn(List.of(holdings));
    }

    private void givenPrices(DailyClosePrice... prices) {
        when(closePriceRepository.findLatestByStockIds(anyCollection(), any())).thenReturn(List.of(prices));
    }

    private List<DailyAccountSnapshot> savedSnapshots() {
        verify(snapshotRepository).saveAll(savedCaptor.capture());
        return savedCaptor.getValue();
    }

    private int rankOf(List<DailyAccountSnapshot> snapshots, CompetitionAccount account) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.getAccount() == account)
                .findFirst().orElseThrow()
                .getRank();
    }

    private CompetitionAccount account(String cash, String deposit) {
        return CompetitionAccount.builder()
                .accountId(UUID.randomUUID())
                .cash(new BigDecimal(cash))
                .deposit(new BigDecimal(deposit))
                .build();
    }

    private Holding holding(CompetitionAccount account, CompetitionStock stock, long quantity) {
        return Holding.builder()
                .account(account).stock(stock).quantity(quantity).avgBuyPrice(new BigDecimal("60000"))
                .build();
    }

    private DailyClosePrice price(CompetitionStock stock, LocalDate date, String closePrice) {
        return DailyClosePrice.builder()
                .stock(stock).priceDate(date).closePrice(new BigDecimal(closePrice))
                .build();
    }
}
