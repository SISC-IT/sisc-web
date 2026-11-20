package org.sejongisc.backend.competition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.competition.dto.CompetitionTradeRequest;
import org.sejongisc.backend.competition.dto.CompetitionTradeResponse;
import org.sejongisc.backend.competition.entity.Competition;
import org.sejongisc.backend.competition.entity.CompetitionAccount;
import org.sejongisc.backend.competition.entity.CompetitionParticipant;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Holding;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.entity.Trade;
import org.sejongisc.backend.competition.entity.TradeSide;
import org.sejongisc.backend.competition.repository.CompetitionAccountRepository;
import org.sejongisc.backend.competition.repository.CompetitionParticipantRepository;
import org.sejongisc.backend.competition.repository.CompetitionRepository;
import org.sejongisc.backend.competition.repository.CompetitionStockRepository;
import org.sejongisc.backend.competition.repository.DailyClosePriceRepository;
import org.sejongisc.backend.competition.repository.HoldingRepository;
import org.sejongisc.backend.competition.repository.TradeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompetitionTradeService {

    // 동시에 들어온 주문끼리 부딪힐 때 나는 유니크 위반. 다시 시도하면 해결되므로 409로 바꾼다.
    private static final Set<String> ORDER_CONFLICT_CONSTRAINTS =
            Set.of("uk_trade_account_idempotency_key", "uk_holding_account_stock");

    private final CompetitionRepository competitionRepository;
    private final CompetitionParticipantRepository participantRepository;
    private final CompetitionAccountRepository accountRepository;
    private final CompetitionStockRepository stockRepository;
    private final DailyClosePriceRepository closePriceRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final Clock clock;

    @Transactional
    public CompetitionTradeResponse order(
            UUID competitionId, UUID userId, UUID idempotencyKey, CompetitionTradeRequest request) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_NOT_FOUND));
        CompetitionParticipant participant = participantRepository.findByCompetitionAndUserId(competition, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND));
        CompetitionAccount account = accountRepository.findByParticipant(participant)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND));

        // 응답만 유실된 재시도는 대회 상태와 관계없이 처음 체결 결과로 응답해야 해서 검증보다 먼저 확인한다.
        Trade existing = tradeRepository.findByAccountAndIdempotencyKey(account, idempotencyKey).orElse(null);
        if (existing != null) {
            if (!isSameOrder(existing, request)) {
                throw new CustomException(ErrorCode.COMPETITION_IDEMPOTENCY_KEY_REUSED);
            }
            return CompetitionTradeResponse.of(existing, account);
        }

        if (competition.getStatus() != CompetitionStatus.ACTIVE) {
            throw new CustomException(ErrorCode.COMPETITION_NOT_ACTIVE);
        }
        if (participant.getStatus() == ParticipantStatus.EXCLUDED) {
            throw new CustomException(ErrorCode.COMPETITION_PARTICIPANT_EXCLUDED);
        }

        CompetitionStock stock = stockRepository.findByTicker(request.ticker())
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_STOCK_NOT_FOUND));
        if (stock.getMarket() != Market.KR) {
            throw new CustomException(ErrorCode.COMPETITION_MARKET_NOT_SUPPORTED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (!stock.getMarket().isOrderOpen(now.toLocalTime())) {
            throw new CustomException(ErrorCode.COMPETITION_ORDER_CLOSED);
        }

        // 종가는 거래일에만 수집되므로, 최신 종가가 오늘 날짜가 아니면 주말·공휴일이다.
        DailyClosePrice closePrice = closePriceRepository
                .findTop2ByStockAndAvailableAtLessThanEqualOrderByPriceDateDesc(stock, now).stream()
                .findFirst()
                .filter(price -> price.getPriceDate().equals(now.toLocalDate()))
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_PRICE_UNAVAILABLE));
        if (!closePrice.getClosePriceId().equals(request.closePriceId())) {
            throw new CustomException(ErrorCode.PRICE_CHANGED);
        }

        BigDecimal amount = closePrice.getClosePrice().multiply(BigDecimal.valueOf(request.quantity()));
        BigDecimal fee = amount.multiply(competition.getFeeRate()).setScale(4, RoundingMode.DOWN);

        if (request.side() == TradeSide.BUY) {
            buy(account, stock, request.quantity(), closePrice.getClosePrice(), amount.add(fee));
        } else {
            sell(account, stock, request.quantity(), amount.subtract(fee));
        }

        Trade trade = saveTrade(Trade.builder()
                .account(account)
                .stock(stock)
                .closePrice(closePrice)
                .side(request.side())
                .quantity(request.quantity())
                .fee(fee)
                .idempotencyKey(idempotencyKey)
                .executedAt(now)
                .build());
        return CompetitionTradeResponse.of(trade, account);
    }

    private void buy(CompetitionAccount account, CompetitionStock stock, long quantity, BigDecimal price,
                     BigDecimal cost) {
        account.decreaseCash(cost);
        holdingRepository.findByAccountAndStock(account, stock).ifPresentOrElse(
                holding -> holding.buy(quantity, price),
                () -> holdingRepository.save(Holding.builder()
                        .account(account)
                        .stock(stock)
                        .quantity(quantity)
                        .avgBuyPrice(price)
                        .build()));
    }

    private void sell(CompetitionAccount account, CompetitionStock stock, long quantity, BigDecimal proceeds) {
        Holding holding = holdingRepository.findByAccountAndStock(account, stock)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPETITION_INSUFFICIENT_HOLDING));
        holding.sell(quantity);
        if (holding.getQuantity() == 0) {
            holdingRepository.delete(holding);
        }
        account.increaseCash(proceeds);
    }

    private static boolean isSameOrder(Trade trade, CompetitionTradeRequest request) {
        return trade.getStock().getTicker().equals(request.ticker())
                && trade.getSide() == request.side()
                && trade.getQuantity().equals(request.quantity())
                && trade.getClosePrice().getClosePriceId().equals(request.closePriceId());
    }

    // 현금·보유 종목 변경까지 함께 flush해서 동시 주문 충돌을 여기서 잡는다.
    // 계좌 @Version 충돌과 동시 주문의 유니크 위반만 409로 바꾸고, 다른 DB 오류는 그대로 올린다.
    private Trade saveTrade(Trade trade) {
        try {
            return tradeRepository.saveAndFlush(trade);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new CustomException(ErrorCode.COMPETITION_ORDER_CONFLICT);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException violation
                    && ORDER_CONFLICT_CONSTRAINTS.contains(violation.getConstraintName())) {
                throw new CustomException(ErrorCode.COMPETITION_ORDER_CONFLICT);
            }
            throw e;
        }
    }
}
