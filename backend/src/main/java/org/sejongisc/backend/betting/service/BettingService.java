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
import org.sejongisc.backend.betting.dto.UserBetResponse;

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
     * 사용자의 전체 베팅 내역 조회 (수정됨)
     */
    @Transactional(readOnly = true) // 트랜잭션 유지 필수
    public List<UserBetResponse> getAllMyBets(UUID userId) {
        List<UserBet> userBets = userBetRepository.findAllByUserIdOrderByRound_SettleAtDesc(userId);

        // Entity List -> DTO List 변환
        return userBets.stream()
                .map(UserBetResponse::from)
                .toList();
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
     * - 반환 타입을 UserBetResponse(DTO)로 변경하여 LazyInitializationException 방지
     * - 통계 업데이트 시 Repository의 @Modifying 쿼리를 사용하여 동시성 문제(Lost Update) 해결
     */
    @Transactional
    public UserBetResponse postUserBet(UUID userId, UserBetRequest userBetRequest) {
        // 1. 라운드 조회
        BetRound betRound = betRoundRepository.findById(userBetRequest.getRoundId())
                .orElseThrow(() -> new CustomException(ErrorCode.BET_ROUND_NOT_FOUND));

        // 2. 중복 베팅 검증
        if (userBetRepository.existsByRoundAndUserId(betRound, userId)) {
            throw new CustomException(ErrorCode.BET_DUPLICATE);
        }

        // 3. 라운드 상태 검증 (마감 시간 등)
        betRound.validate();

        // 4. 베팅 포인트(stake) 결정
        int stake = userBetRequest.isFree() ? 0 : userBetRequest.getStakePoints();

        // 5. 라운드 통계 업데이트 (동시성 해결: DB 직접 업데이트)
        if (userBetRequest.getOption() == BetOption.RISE) {
            betRoundRepository.incrementUpStats(betRound.getBetRoundID(), stake);
        } else {
            betRoundRepository.incrementDownStats(betRound.getBetRoundID(), stake);
        }

        // 6. 포인트 차감 및 이력 생성 (유료 베팅인 경우)
        if (!userBetRequest.isFree()) {
            if (!userBetRequest.isStakePointsValid()) {
                throw new CustomException(ErrorCode.BET_POINT_TOO_LOW);
            }

            pointHistoryService.createPointHistory(
                    userId,
                    -stake, // 포인트 차감
                    PointReason.BETTING,
                    PointOrigin.BETTING,
                    userBetRequest.getRoundId()
            );
        }

        // 7. UserBet 엔티티 생성
        UserBet userBet = UserBet.builder()
                .round(betRound)
                .userId(userId)
                .option(userBetRequest.getOption())
                .isFree(userBetRequest.isFree())
                .stakePoints(stake)
                .betStatus(BetStatus.ACTIVE)
                .build();

        // 8. 저장 및 DTO 변환 반환
        try {
            UserBet savedBet = userBetRepository.save(userBet);
            // 여기서 DTO로 변환해야 트랜잭션 내에서 betRound 정보를 안전하게 가져올 수 있음
            return UserBetResponse.from(savedBet);
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
     * 사용자 베팅 취소 (수정됨)
     */
    @Transactional
    public void cancelUserBet(UUID userId, UUID userBetId) {
        // 1. 먼저 베팅 정보를 조회 (검증용)
        UserBet userBet = userBetRepository.findByUserBetIdAndUserId(userBetId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.BET_NOT_FOUND));

        // 2. [핵심] 상태를 ACTIVE -> CANCELED로 변경 시도
        // 이 쿼리는 동시에 여러 요청이 와도 단 하나만 1을 반환합니다. (나머지는 0)
        int updatedCount = userBetRepository.updateStatusToCanceled(
                userBetId,
                userId,
                BetStatus.ACTIVE,
                BetStatus.CANCELED // Enum에 CANCELED 추가
        );

        if (updatedCount == 0) {
            // 이미 취소되었거나 처리된 베팅임 -> 중복 처리 방지
            throw new CustomException(ErrorCode.BET_ALREADY_PROCESSED);
        }

        // 3. 상태 변경에 성공한 딱 1개의 요청만 아래 환불/통계 로직 수행
        BetRound betRound = userBet.getRound();
        betRound.validate();

        // 포인트 환불
        if (!userBet.isFree() && userBet.getStakePoints() > 0) {
            pointHistoryService.createPointHistory(
                    userId,
                    userBet.getStakePoints(),
                    PointReason.BETTING,
                    PointOrigin.BETTING,
                    betRound.getBetRoundID() // 밑에서 설명할 targetId 이슈 확인 필요
            );
        }

        // 통계 차감
        int stake = userBet.getStakePoints();
        if (userBet.getOption() == BetOption.RISE) {
            betRoundRepository.decrementUpStats(betRound.getBetRoundID(), stake);
        } else {
            betRoundRepository.decrementDownStats(betRound.getBetRoundID(), stake);
        }

        // 삭제(delete)는 하지 않음 (이력 관리를 위해)
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
