package org.sejongisc.backend.competition.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
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

    public CompetitionStockListResponse search(UUID competitionId, Market market, String query, int page, int size) {
        findCompetition(competitionId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String pattern = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";

        Page<CompetitionStock> stocks = stockRepository.search(
                market, pattern, PageRequest.of(safePage - 1, safeSize, Sort.by("ticker")));
        LocalDateTime now = LocalDateTime.now(clock);

        return new CompetitionStockListResponse(
                safePage,
                safeSize,
                stocks.getTotalElements(),
                null,
                stocks.stream()
                        .map(stock -> CompetitionStockSummary.of(stock, quote(stock, now)))
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

        return CompetitionStockDetailResponse.of(
                competition, stock, quote(stock, LocalDateTime.now(clock)), account, holding);
    }

    private Competition findCompetition(UUID competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_NOT_FOUND));
    }

    private StockQuote quote(CompetitionStock stock, LocalDateTime now) {
        return StockQuote.of(
                closePriceRepository.findTop2ByStockAndAvailableAtLessThanEqualOrderByPriceDateDesc(stock, now));
    }
}
