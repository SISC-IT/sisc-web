package org.sejongisc.backend.competition.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.assetmanagement.model.ListedStock;
import org.sejongisc.backend.assetmanagement.service.MarketDataClient;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.repository.CompetitionStockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 목록에서 사라진 종목은 종가나 보유가 걸려 있을 수 있어 지우지 않는다. */
@Slf4j
@Service
public class CompetitionStockSyncService {

    private static final int MAX_TICKER_LENGTH = 20;
    private static final int MAX_NAME_LENGTH = 100;

    private final MarketDataClient marketDataClient;
    private final CompetitionStockRepository stockRepository;
    private final List<String> marketTypes;

    public CompetitionStockSyncService(
            MarketDataClient marketDataClient,
            CompetitionStockRepository stockRepository,
            @Value("${competition.stock-sync.market-types:0,10}") List<String> marketTypes
    ) {
        this.marketDataClient = marketDataClient;
        this.stockRepository = stockRepository;
        this.marketTypes = marketTypes;
    }

    public SyncResult syncDomestic() {
        Collection<ListedStock> listedStocks = fetchListedStocks();
        Map<String, CompetitionStock> existingStocks = stockRepository.findByMarket(Market.KR).stream()
                .collect(Collectors.toMap(CompetitionStock::getTicker, Function.identity()));

        List<CompetitionStock> added = createNewStocks(listedStocks, existingStocks);
        List<CompetitionStock> renamed = renameChangedStocks(listedStocks, existingStocks);
        saveAll(added, renamed);

        SyncResult result = new SyncResult(
                added.size(), renamed.size(), listedStocks.size() - added.size() - renamed.size());
        log.info("[종목동기화] 완료: {}", result);
        return result;
    }

    private Collection<ListedStock> fetchListedStocks() {
        return marketTypes.stream()
                .flatMap(marketType -> fetchMarket(marketType).stream())
                .filter(this::isValid)
                .map(stock -> new ListedStock(stock.code().trim(), stock.name().trim()))
                .collect(Collectors.toMap(
                        ListedStock::code, Function.identity(), (first, second) -> first, LinkedHashMap::new))
                .values();
    }

    private List<ListedStock> fetchMarket(String marketType) {
        try {
            return marketDataClient.fetchDomesticListedStocks(marketType);
        } catch (RuntimeException e) {
            log.warn("[종목동기화] 시장 조회 실패: marketType={}, message={}", marketType, e.getMessage());
            return List.of();
        }
    }

    private boolean isValid(ListedStock stock) {
        boolean valid = StringUtils.hasText(stock.code())
                && StringUtils.hasText(stock.name())
                && stock.code().trim().length() <= MAX_TICKER_LENGTH
                && stock.name().trim().length() <= MAX_NAME_LENGTH;
        if (!valid) {
            log.warn("[종목동기화] 잘못된 항목을 건너뜁니다: code={}, name={}", stock.code(), stock.name());
        }
        return valid;
    }

    private List<CompetitionStock> createNewStocks(
            Collection<ListedStock> listedStocks, Map<String, CompetitionStock> existingStocks) {
        return listedStocks.stream()
                .filter(listed -> !existingStocks.containsKey(listed.code()))
                .map(listed -> CompetitionStock.builder()
                        .ticker(listed.code())
                        .name(listed.name())
                        .market(Market.KR)
                        .build())
                .toList();
    }

    private List<CompetitionStock> renameChangedStocks(
            Collection<ListedStock> listedStocks, Map<String, CompetitionStock> existingStocks) {
        List<CompetitionStock> renamed = new ArrayList<>();
        for (ListedStock listed : listedStocks) {
            CompetitionStock existing = existingStocks.get(listed.code());
            if (existing != null && !existing.getName().equals(listed.name())) {
                existing.rename(listed.name());
                renamed.add(existing);
            }
        }
        return renamed;
    }

    private void saveAll(List<CompetitionStock> added, List<CompetitionStock> renamed) {
        List<CompetitionStock> changed = new ArrayList<>(added);
        changed.addAll(renamed);
        if (!changed.isEmpty()) {
            stockRepository.saveAll(changed);
        }
    }

    public record SyncResult(int added, int renamed, int unchanged) {}
}
