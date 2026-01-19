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
import org.springframework.orm.ObjectOptimisticLockingFailureException; // import 확인

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
     * - 취소 이력이 있는 베팅도 베팅 가능한 상태에 한하여 재배팅 가능
     */
    @Transactional
    public UserBetResponse postUserBet(UUID userId, UserBetRequest userBetRequest) {
        // 라운드 조회
        BetRound betRound = betRoundRepository.findById(userBetRequest.getRoundId())
                .orElseThrow(() -> new CustomException(ErrorCode.BET_ROUND_NOT_FOUND));

        UserBet existingBet = userBetRepository.findByRoundAndUserId(betRound, userId)
                .orElse(null);

        // 중복 베팅 존재 여부 검증
        if (existingBet != null && existingBet.getBetStatus() != BetStatus.DELETED) {
            throw new CustomException(ErrorCode.BET_DUPLICATE);
        }

        // 베팅 가능한 라운드 상태인지 검증
        betRound.validate();

        // 베팅 포인트 결정
        int stake = userBetRequest.isFree() ? 0 : userBetRequest.getStakePoints();

        // 라운드 통계 업데이트
        if (userBetRequest.getOption() == BetOption.RISE) {
            betRoundRepository.incrementUpStats(betRound.getBetRoundID(), stake);
        } else {
            betRoundRepository.incrementDownStats(betRound.getBetRoundID(), stake);
        }

        // 포인트 차감 및 이력 생성 (유료 베팅인 경우)
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

        // 기존 베팅 존재 시 재베팅, 없으면 생성
        UserBet userBet;
        if (existingBet != null) {
            userBet = existingBet;
            userBet.updateBet(userBetRequest.getOption(), stake, userBetRequest.isFree());
        }
        else {
            userBet = UserBet.builder()
                .round(betRound)
                .userId(userId)
                .option(userBetRequest.getOption())
                .isFree(userBetRequest.isFree())
                .stakePoints(stake)
                .betStatus(BetStatus.ACTIVE)
                .build();
        }

        try {
            return UserBetResponse.from(userBetRepository.save(userBet));
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
        try {
            // 1. 엔티티 조회 (UserBet)
            UserBet userBet = userBetRepository.findByUserBetIdAndUserId(userBetId, userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.BET_NOT_FOUND));

            // 2. 이미 처리된 상태인지 검증 (중복 방지 1차)
            if (userBet.getBetStatus() != BetStatus.ACTIVE) {
                throw new CustomException(ErrorCode.BET_ALREADY_PROCESSED);
            }

            // 3. BetRound 조회 및 검증
            // (Lazy Loading 문제 방지를 위해 ID로 다시 조회하는 기존 로직 유지 권장)
            UUID roundId = userBet.getRound().getBetRoundID();
            BetRound betRound = betRoundRepository.findById(roundId)
                    .orElseThrow(() -> new CustomException(ErrorCode.BET_ROUND_NOT_FOUND));

            betRound.validate(); // 마감 시간 등 체크

            // 4. 상태 변경 (ACTIVE -> CANCELED)
            // 여기서 @Version 필드 덕분에 커밋 시점에 버전 충돌 여부를 체크함
            userBet.cancel(); 
            userBetRepository.saveAndFlush(userBet); // 명시적 flush로 버전 충돌 즉시 감지

            // 5. 포인트 환불
            if (!userBet.isFree() && userBet.getStakePoints() > 0) {
                pointHistoryService.createPointHistory(
                        userId,
                        userBet.getStakePoints(),
                        PointReason.BETTING,
                        PointOrigin.BETTING,
                        betRound.getBetRoundID() // targetId 통일 (리뷰 반영)
                );
            }

            // 6. 통계 차감
            int stake = userBet.getStakePoints();
            if (userBet.getOption() == BetOption.RISE) {
                betRoundRepository.decrementUpStats(betRound.getBetRoundID(), stake);
            } else {
                betRoundRepository.decrementDownStats(betRound.getBetRoundID(), stake);
            }

            // userBetRepository.save(userBet); // Transactional이라 자동 저장되지만 명시해도 됨

        } catch (ObjectOptimisticLockingFailureException e) {
            // 동시에 취소 요청이 들어온 경우 하나만 성공하고 나머지는 여기서 걸러짐
            throw new CustomException(ErrorCode.BET_ALREADY_PROCESSED);
        }
    }       // 삭제(delete)는 하지 않음 (이력 관리를 위해)


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
            BetOption resultOption = round.getResultOption();

            for (UserBet bet : userBets) {
                if (bet.getBetStatus() != BetStatus.ACTIVE) continue;

                if (round.isDraw()) {
                    // 가격 변동이 없을 시 참여자 전원 원금 환불
                    pointHistoryService.createPointHistory(
                        bet.getUserId(),
                        bet.getStakePoints(),
                        PointReason.BETTING,
                        PointOrigin.BETTING,
                        round.getBetRoundID()
                    );
                    bet.draw();
                } else if (bet.getOption() == resultOption) {
                    // 예측 성공 시 보상 포인트 지급
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
                    // 예측 실패 시 포인트 소멸
                    bet.lose();
                }
            }
            userBetRepository.saveAll(userBets);
        }
    }

    /**
     * 배당률에 따른 보상 계산
     * - 무료: 맞추면 10P
     * - 유료: 배당률 적용
     */
    private int calculateReward(UserBet bet) {
        BetRound round = bet.getRound();

        // 무료 베팅: 10 포인트
        if (bet.isFree()) {
            return 10;
        }

        long upPoints = round.getUpTotalPoints();
        long downPoints = round.getDownTotalPoints();
        long total = upPoints + downPoints;

        long winning = (round.getResultOption() == BetOption.RISE) ? upPoints : downPoints;

        // 호출 시점에 정답자 존재가 보장되지만 ArithmeticException 방지용
        if (winning == 0) {
            return 0;
        }

        // 배당률 계산: 내 베팅액 * (전체 포인트 / 정답 측 포인트 합)
        double multiplier = (double) total / winning;

        // 소수점 floor -> TODO: 복식부기 도입 시 남는 포인트는 시스템으로 이동시키기
        return (int) Math.floor(bet.getStakePoints() * multiplier);
    }
}
