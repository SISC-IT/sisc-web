package org.sejongisc.backend.betting.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.betting.dto.BetRoundResponse;
import org.sejongisc.backend.betting.dto.PriceResponse;
import org.sejongisc.backend.betting.dto.UserBetRequest;
import org.sejongisc.backend.betting.entity.*;
import org.sejongisc.backend.betting.repository.BetRoundRepository;
import org.sejongisc.backend.betting.repository.UserBetRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.point.entity.PointOrigin;
import org.sejongisc.backend.point.entity.PointReason;
import org.sejongisc.backend.point.service.PointHistoryService;
import org.sejongisc.backend.stock.entity.PriceData;
import org.sejongisc.backend.stock.repository.PriceDataRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BettingService {

    private final BetRoundRepository betRoundRepository;
    private final UserBetRepository userBetRepository;
    private final PointHistoryService pointHistoryService;
    private final PriceDataRepository priceDataRepository;

    private final Random random = new Random();

    /**
     * 현재 활성화된 베팅 라운드 조회
     */
    public Optional<BetRound> getActiveRound(Scope type) {
        return betRoundRepository.findTopByStatusTrueAndScopeOrderByOpenAtDesc(type);
    }

    /**
     * 전체 베팅 라운드 목록 조회
     */
    public List<BetRound> getAllBetRounds() {
        return betRoundRepository.findAllByOrderBySettleAtDesc();
    }

    /**
     * PriceData 기반 무작위 종목 선택 (기존 Stock 대체)
     */
    public PriceResponse getPriceData() {
        List<PriceData> allData = priceDataRepository.findAll();
        if (allData.isEmpty()) {
            throw new CustomException(ErrorCode.STOCK_NOT_FOUND);
        }

        List<String> tickers = allData.stream()
                .map(PriceData::getTicker)
                .distinct()
                .toList();

        String randomTicker = tickers.get(random.nextInt(tickers.size()));

        PriceData latest = priceDataRepository.findTopByTickerOrderByDateDesc(randomTicker)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));

        return PriceResponse.builder()
                .name(latest.getTicker())
                .symbol(latest.getTicker())
                .market(MarketType.US)
                .previousClosePrice(latest.getClosePrice())
                .settleClosePrice(latest.getAdjustedClose())
                .build();
    }


    /**
     * 무료 베팅 가능 여부 (20% 확률)
     */
    public boolean setAllowFree() {
        return random.nextDouble() < 0.2;
    }

    /**
     * 사용자의 전체 베팅 내역 조회
     */
    public List<UserBet> getAllMyBets(UUID userId) {
        return userBetRepository.findAllByUserIdOrderByRound_SettleAtDesc(userId);
    }

    /**
     * 새로운 베팅 라운드 생성
     */
    public void createBetRound(Scope scope) {
        LocalDateTime now = LocalDateTime.now();

        PriceResponse price = getPriceData();

        BetRound betRound = BetRound.builder()
                .scope(scope)
                .title(now.toLocalDate() + " " + price.getName() + " " + scope.name() + " 라운드")
                .symbol(price.getSymbol())
                .allowFree(setAllowFree())
                .openAt(scope.getOpenAt(now))
                .lockAt(scope.getLockAt(now))
                .market(price.getMarket())
                .previousClosePrice(price.getPreviousClosePrice())
                .build();

        betRound.open();
        betRoundRepository.save(betRound);
    }

    /**
     * 종료 조건을 만족한 라운드 종료
     */
    public void closeBetRound() {
        LocalDateTime now = LocalDateTime.now();
        List<BetRound> toClose = betRoundRepository.findByStatusTrueAndLockAtLessThanEqual(now);
        if (toClose.isEmpty()) return;

        toClose.forEach(BetRound::close);
        betRoundRepository.saveAll(toClose);
    }

    /**
     * 사용자 베팅 생성
     */
    @Transactional
    public UserBet postUserBet(UUID userId, UserBetRequest userBetRequest) {
        BetRound betRound = betRoundRepository.findById(userBetRequest.getRoundId())
                .orElseThrow(() -> new CustomException(ErrorCode.BET_ROUND_NOT_FOUND));

        if (userBetRepository.existsByRoundAndUserId(betRound, userId)) {
            throw new CustomException(ErrorCode.BET_DUPLICATE);
        }

        betRound.validate();

        // [수정] 유료 베팅인 경우 베팅 포인트 설정
        int stake = userBetRequest.isFree() ? 0 : userBetRequest.getStakePoints();

        // [삭제] 기존 엔티티 메서드 호출 방식 (동시성 문제 발생)
        //betRound.addBetStats(userBetRequest.getOption(), stake);

        if (userBetRequest.getOption() == BetOption.RISE) {
            betRoundRepository.incrementUpStats(betRound.getBetRoundID(), stake);
        } else {
            betRoundRepository.incrementDownStats(betRound.getBetRoundID(), stake);
        }

        if (!userBetRequest.isFree()) {
            if (!userBetRequest.isStakePointsValid()) {
                throw new CustomException(ErrorCode.BET_POINT_TOO_LOW);
            }

            pointHistoryService.createPointHistory(
                    userId,
                    -userBetRequest.getStakePoints(),
                    PointReason.BETTING,
                    PointOrigin.BETTING,
                    userBetRequest.getRoundId()
            );
            stake = userBetRequest.getStakePoints();
        }

        UserBet userBet = UserBet.builder()
                .round(betRound)
                .userId(userId)
                .option(userBetRequest.getOption())
                .isFree(userBetRequest.isFree())
                .stakePoints(stake)
                .betStatus(BetStatus.ACTIVE)
                .build();

        try {
            return userBetRepository.save(userBet);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.BET_DUPLICATE);
        }
    }

    // [추가] getActiveRound 반환 타입 변경 대응 메서드 (Controller에서 사용)
    public Optional<BetRoundResponse> getActiveRoundResponse(Scope type) {
        return betRoundRepository.findTopByStatusTrueAndScopeOrderByOpenAtDesc(type)
                .map(BetRoundResponse::from);
    }

    /**
     * 사용자 베팅 취소
     */
    @Transactional
    public void cancelUserBet(UUID userId, UUID userBetId) {
        UserBet userBet = userBetRepository.findByUserBetIdAndUserId(userBetId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.BET_NOT_FOUND));

        BetRound betRound = userBet.getRound();
        betRound.validate();

        if (!userBet.isFree() && userBet.getStakePoints() > 0) {
            pointHistoryService.createPointHistory(
                    userId,
                    userBet.getStakePoints(),
                    PointReason.BETTING,
                    PointOrigin.BETTING,
                    userBet.getUserBetId()
            );
        }

        userBetRepository.delete(userBet);
    }

    /**
     * 베팅 결과 정산
     */
    @Transactional
    public void settleUserBets() {
        LocalDateTime now = LocalDateTime.now();

        List<BetRound> activeRounds =
                betRoundRepository.findByStatusFalseAndSettleAtIsNullAndLockAtLessThanEqual(now);

        for (BetRound round : activeRounds) {
            // PriceData를 이용해 시세 조회
            Optional<PriceData> priceOpt = priceDataRepository.findTopByTickerOrderByDateDesc(round.getSymbol());
            if (priceOpt.isEmpty()) continue;

            PriceData price = priceOpt.get();
            BigDecimal finalPrice = price.getAdjustedClose();

            if (finalPrice == null) continue;

            round.settle(finalPrice);
            betRoundRepository.save(round);

            List<UserBet> userBets = userBetRepository.findAllByRound(round);

            for (UserBet bet : userBets) {
                if (bet.getBetStatus() != BetStatus.ACTIVE) continue;

                if (bet.getOption() == round.getResultOption()) {
                    int reward = calculateReward(bet);
                    bet.win(reward);
                    pointHistoryService.createPointHistory(
                            bet.getUserId(),
                            reward,
                            PointReason.BETTING_WIN,
                            PointOrigin.BETTING,
                            round.getBetRoundID()
                    );
                } else {
                    bet.lose();
                }
            }
            userBetRepository.saveAll(userBets);
        }
    }

    /**
     * TODO: 향후 배당 비율에 따른 보상 계산 로직
     */
    private int calculateReward(UserBet bet) {
        return 10;
    }
}
