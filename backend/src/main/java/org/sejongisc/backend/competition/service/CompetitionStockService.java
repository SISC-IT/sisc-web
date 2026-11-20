package org.sejongisc.backend.competition.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.competition.dto.CompetitionStockDetailResponse;
import org.sejongisc.backend.competition.dto.CompetitionStockListResponse;
import org.sejongisc.backend.competition.dto.CompetitionStockSummary;
import org.sejongisc.backend.competition.dto.StockQuote;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.CompetitionStockRepository;
import org.sejongisc.backend.competition.repository.DailyClosePriceRepository;
import org.sejongisc.backend.competition.repository.HoldingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetitionStockService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CompetitionRepository competitionRepository;
    private final CompetitionStockRepository stockRepository;
    private final DailyClosePriceRepository closePriceRepository;
    private final CompetitionParticipantRepository participantRepository;
    private final CompetitionAccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final Clock clock;

    public CompetitionStockListResponse search(UUID competitionId, String market, String query, int page, int size) {
        findCompetition(competitionId);
        Market marketFilter = StringUtils.hasText(market) ? Market.from(market) : null;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String keyword = StringUtils.hasText(query) ? query.trim() : "";

        Page<CompetitionStock> stocks = stockRepository.search(
                marketFilter, keyword, PageRequest.of(safePage, safeSize, Sort.by("ticker")));
        Map<UUID, List<DailyClosePrice>> prices = latestTwoPrices(stocks.getContent());

        return new CompetitionStockListResponse(
                safePage,
                safeSize,
                stocks.getTotalElements(),
                null,
                stocks.stream()
                        .map(stock -> CompetitionStockSummary.of(
                                stock, StockQuote.of(prices.getOrDefault(stock.getStockId(), List.of()))))
                        .toList()
        );
    }

    public CompetitionStockDetailResponse getDetail(UUID competitionId, String ticker, UUID userId) {
        Competition competition = findCompetition(competitionId);
        CompetitionStock stock = stockRepository.findByTicker(ticker)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_STOCK_NOT_FOUND));
        CompetitionParticipant participant = participantRepository.findByCompetitionAndUserId(competition, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND));
        CompetitionAccount account = accountRepository.findByParticipant(participant)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND));
        Holding holding = holdingRepository.findByAccountAndStock(account, stock).orElse(null);

        StockQuote quote = StockQuote.of(
                closePriceRepository.findTop2ByStockAndAvailableAtLessThanEqualOrderByPriceDateDesc(
                        stock, LocalDateTime.now(clock)));

        return CompetitionStockDetailResponse.of(competition, stock, quote, account, holding);
    }

    private Competition findCompetition(UUID competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_NOT_FOUND));
    }

    private Map<UUID, List<DailyClosePrice>> latestTwoPrices(List<CompetitionStock> stocks) {
        if (stocks.isEmpty()) {
            return Map.of();
        }
        List<UUID> stockIds = stocks.stream().map(CompetitionStock::getStockId).toList();
        return closePriceRepository.findLatestTwoByStockIds(stockIds, LocalDateTime.now(clock)).stream()
                .collect(Collectors.groupingBy(price -> price.getStock().getStockId()));
    }
}
