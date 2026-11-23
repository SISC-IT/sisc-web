package org.sejongisc.backend.competition.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.competition.dto.CompetitionHoldingResponse;
import org.sejongisc.backend.competition.dto.CompetitionTradeResponse;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.DailyClosePriceRepository;
import org.sejongisc.backend.competition.repository.HoldingRepository;
import org.sejongisc.backend.competition.repository.TradeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetitionPortfolioService {

    private static final int MAX_PAGE_SIZE = 100;

    // 평가액이 큰 순서. 종가가 없어 평가액이 null인 종목은 뒤로 보낸다.
    private static final Comparator<CompetitionHoldingResponse> BY_EVALUATION_DESC = Comparator.comparing(
            CompetitionHoldingResponse::evaluationAmount, Comparator.nullsLast(Comparator.reverseOrder()));

    private final CompetitionRepository competitionRepository;
    private final CompetitionParticipantRepository participantRepository;
    private final CompetitionAccountRepository accountRepository;
    private final TradeRepository tradeRepository;
    private final HoldingRepository holdingRepository;
    private final DailyClosePriceRepository closePriceRepository;
    private final Clock clock;

    public Page<CompetitionTradeResponse> getTrades(UUID competitionId, UUID userId, int page, int size) {
        CompetitionAccount account = findAccount(competitionId, userId);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return tradeRepository.findAllByAccountOrderByExecutedAtDesc(account, pageRequest)
                .map(CompetitionTradeResponse::of);
    }

    public List<CompetitionHoldingResponse> getHoldings(UUID competitionId, UUID userId) {
        List<Holding> holdings = holdingRepository.findAllByAccount(findAccount(competitionId, userId));
        Map<UUID, DailyClosePrice> latestPrices = latestPrices(holdings);
        return holdings.stream()
                .map(holding -> CompetitionHoldingResponse.of(
                        holding, latestPrices.get(holding.getStock().getStockId())))
                .sorted(BY_EVALUATION_DESC)
                .toList();
    }

    private CompetitionAccount findAccount(UUID competitionId, UUID userId) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_NOT_FOUND));
        CompetitionParticipant participant = participantRepository.findByCompetitionAndUserId(competition, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND));
        return accountRepository.findByParticipant(participant)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND));
    }

    // 종목 목록 화면과 같은 기준(지금 공개된 종가)으로 종목별 최신 종가를 한 번에 읽는다.
    private Map<UUID, DailyClosePrice> latestPrices(List<Holding> holdings) {
        if (holdings.isEmpty()) {
            return Map.of();
        }
        List<UUID> stockIds = holdings.stream().map(holding -> holding.getStock().getStockId()).toList();
        // 쿼리는 종목별로 최신순 2건을 준다. 먼저 나온 최신 종가만 남긴다.
        return closePriceRepository.findLatestTwoByStockIds(stockIds, LocalDateTime.now(clock)).stream()
                .collect(Collectors.toMap(
                        price -> price.getStock().getStockId(), price -> price, (latest, older) -> latest));
    }
}
