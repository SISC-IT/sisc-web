package org.sejongisc.backend.competition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.DailyAccountSnapshot;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.DailyAccountSnapshotRepository;
import org.sejongisc.backend.competition.repository.DailyClosePriceRepository;
import org.sejongisc.backend.competition.repository.HoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSnapshotService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionAccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final DailyClosePriceRepository closePriceRepository;
    private final DailyAccountSnapshotRepository snapshotRepository;
    private final Clock clock;

    // 현금과 보유 종목을 같은 시점 기준으로 읽는다
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int createDailySnapshot() {
        LocalDate today = LocalDate.now(clock);
        Optional<Competition> active =
                competitionRepository.findFirstByStatusOrderByStartDateDesc(CompetitionStatus.ACTIVE);
        if (active.isEmpty()) {
            return 0;
        }
        Competition competition = active.get();
        // 오늘 종가가 하나도 없으면 휴장일
        if (!closePriceRepository.existsByPriceDate(today)) {
            return 0;
        }
        if (snapshotRepository.existsByCompetitionAndSnapshotDate(competition, today)) {
            return 0;
        }
        return saveRankedSnapshots(competition, today);
    }

    // 종료일 스냅샷이 없으면(휴장일, 수집 실패) 종료일이 지난 뒤 가장 최근 종가로 만든다.
    // 직전 거래일 스냅샷 뒤의 저녁 거래 수수료와 밤 이자까지 최종 평가에 들어간다.
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int createFinalSnapshot() {
        LocalDate today = LocalDate.now(clock);
        Optional<Competition> active =
                competitionRepository.findFirstByStatusOrderByStartDateDesc(CompetitionStatus.ACTIVE);
        if (active.isEmpty()) {
            return 0;
        }
        Competition competition = active.get();
        LocalDate endDate = competition.getEndDate();
        if (!endDate.isBefore(today)) {
            return 0;
        }
        if (snapshotRepository.existsByCompetitionAndSnapshotDate(competition, endDate)) {
            return 0;
        }
        return saveRankedSnapshots(competition, endDate);
    }

    private int saveRankedSnapshots(Competition competition, LocalDate snapshotDate) {
        List<CompetitionAccount> accounts =
                accountRepository.findAllByCompetitionAndParticipantStatus(competition, ParticipantStatus.ACTIVE);
        List<Holding> holdings = holdingRepository.findAllByAccountIn(accounts);
        Map<UUID, DailyClosePrice> prices = latestPricesByStock(holdings, snapshotDate);
        Map<UUID, BigDecimal> totalValues = totalValuesByAccount(accounts, holdings, prices);
        List<DailyAccountSnapshot> snapshots = toRankedSnapshots(competition, accounts, totalValues, snapshotDate);

        snapshotRepository.saveAll(snapshots);
        log.info("[스냅샷] 완료: competitionId={}, date={}, 계좌={}",
                competition.getCompetitionId(), snapshotDate, snapshots.size());
        return snapshots.size();
    }

    private Map<UUID, DailyClosePrice> latestPricesByStock(List<Holding> holdings, LocalDate snapshotDate) {
        if (holdings.isEmpty()) {
            return Map.of();
        }
        List<UUID> stockIds = holdings.stream()
                .map(holding -> holding.getStock().getStockId())
                .distinct()
                .toList();
        Map<UUID, DailyClosePrice> prices = closePriceRepository.findLatestByStockIds(stockIds, snapshotDate).stream()
                .collect(Collectors.toMap(price -> price.getStock().getStockId(), price -> price));

        long stale = prices.values().stream().filter(price -> !snapshotDate.equals(price.getPriceDate())).count();
        if (stale > 0) {
            log.warn("[스냅샷] 그날 종가가 없어 직전 종가로 평가한 종목이 있습니다: date={}, 종목={}", snapshotDate, stale);
        }
        return prices;
    }

    // 현금 + 예금 + 보유 수량 × 종가
    private Map<UUID, BigDecimal> totalValuesByAccount(
            List<CompetitionAccount> accounts, List<Holding> holdings, Map<UUID, DailyClosePrice> prices) {
        Map<UUID, BigDecimal> totalValues = new HashMap<>();
        for (CompetitionAccount account : accounts) {
            totalValues.put(account.getAccountId(), account.getCash().add(account.getDeposit()));
        }
        for (Holding holding : holdings) {
            DailyClosePrice price = prices.get(holding.getStock().getStockId());
            if (price == null) {
                throw new IllegalStateException(
                        "보유 종목의 종가가 없습니다: ticker=" + holding.getStock().getTicker());
            }
            totalValues.merge(
                    holding.getAccount().getAccountId(),
                    price.getClosePrice().multiply(BigDecimal.valueOf(holding.getQuantity())),
                    BigDecimal::add);
        }
        return totalValues;
    }

    // 총자산이 같으면 같은 등수, 다음 등수는 건너뛴다(1, 2, 2, 4)
    private List<DailyAccountSnapshot> toRankedSnapshots(
            Competition competition, List<CompetitionAccount> accounts,
            Map<UUID, BigDecimal> totalValues, LocalDate snapshotDate) {
        List<CompetitionAccount> sorted = accounts.stream()
                .sorted(Comparator.comparing(
                        (CompetitionAccount account) -> totalValues.get(account.getAccountId())).reversed())
                .toList();

        List<DailyAccountSnapshot> snapshots = new ArrayList<>();
        int rank = 0;
        BigDecimal previous = null;
        for (int i = 0; i < sorted.size(); i++) {
            CompetitionAccount account = sorted.get(i);
            BigDecimal totalValue = totalValues.get(account.getAccountId());
            if (previous == null || totalValue.compareTo(previous) != 0) {
                rank = i + 1;
                previous = totalValue;
            }
            snapshots.add(DailyAccountSnapshot.builder()
                    .account(account)
                    .snapshotDate(snapshotDate)
                    .totalValue(totalValue)
                    .profitRate(totalValue
                            .divide(competition.getInitialCash(), 6, RoundingMode.HALF_UP)
                            .subtract(BigDecimal.ONE))
                    .rank(rank)
                    .build());
        }
        return snapshots;
    }
}
