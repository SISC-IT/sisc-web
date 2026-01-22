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
import org.sejongisc.backend.common.annotation.OptimisticRetry;
import org.sejongisc.backend.point.dto.AccountEntry;
import org.sejongisc.backend.point.entity.Account;
import org.sejongisc.backend.point.entity.AccountName;
import org.sejongisc.backend.point.entity.TransactionReason;
import org.sejongisc.backend.point.service.AccountService;
import org.sejongisc.backend.point.service.PointLedgerService;
import org.sejongisc.backend.stock.entity.PriceData;
import org.sejongisc.backend.stock.repository.PriceDataRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sejongisc.backend.betting.dto.UserBetResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BettingService {

    private final BetRoundRepository betRoundRepository;
    private final UserBetRepository userBetRepository;
    private final AccountService accountService;
    private final PointLedgerService pointLedgerService;
    private final PriceDataRepository priceDataRepository;

    private final Random random = new Random();


    /**
     * 전체 베팅 라운드 목록 조회
     */
    @Transactional(readOnly = true)
    public List<BetRound> getAllBetRounds() {
        return betRoundRepository.findAllByOrderBySettleAtDesc();
    }

    /**
     * PriceData 기반 무작위 종목 선택 (기존 Stock 대체)
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional
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
    @Transactional
    public void closeBetRound() {
        LocalDateTime now = LocalDateTime.now();
        List<BetRound> toClose = betRoundRepository.findByStatusTrueAndLockAtLessThanEqual(now);
        if (toClose.isEmpty()) return;

        toClose.forEach(BetRound::close);
    }

    /**
     * 사용자 베팅 생성
     * - 반환 타입을 UserBetResponse(DTO)로 변경하여 LazyInitializationException 방지
     * - 통계 업데이트 시 Repository의 @Modifying 쿼리를 사용하여 동시성 문제(Lost Update) 해결
     * - 취소 이력이 있는 베팅도 베팅 가능한 상태에 한하여 재배팅 가능
     */
    @Transactional
    @OptimisticRetry
    public UserBetResponse postUserBet(UUID userId, UserBetRequest userBetRequest) {
        // 베팅 포인트 검증
        if (!userBetRequest.isFree() && !userBetRequest.isStakePointsValid()) {
            throw new CustomException(ErrorCode.BET_POINT_TOO_LOW);
        }

        // 라운드 조회
        BetRound betRound = betRoundRepository.findById(userBetRequest.getRoundId())
                .orElseThrow(() -> new CustomException(ErrorCode.BET_ROUND_NOT_FOUND));

        // 베팅 가능한 라운드 상태인지 검증
        betRound.validate();

        UserBet existingBet = userBetRepository.findByRoundAndUserId(betRound, userId)
                .orElse(null);

        // 중복 베팅 존재 여부 검증
        if (existingBet != null && existingBet.getBetStatus() != BetStatus.DELETED) {
            throw new CustomException(ErrorCode.BET_DUPLICATE);
        }

        // 베팅 포인트 결정
        int stake = userBetRequest.isFree() ? 0 : userBetRequest.getStakePoints();

        // 라운드 통계 업데이트
        if (userBetRequest.getOption() == BetOption.RISE) {
            betRoundRepository.incrementUpStats(betRound.getBetRoundID(), stake);
        } else {
            betRoundRepository.incrementDownStats(betRound.getBetRoundID(), stake);
        }

        // 사용자 포인트 차감 및 이력 생성 (유료 베팅인 경우)
        if (!userBetRequest.isFree() && stake > 0) {
            pointLedgerService.processTransaction(
                TransactionReason.BETTING_STAKE,
                userBetRequest.getRoundId(),
                AccountEntry.credit(accountService.getUserAccount(userId), (long) stake),
                AccountEntry.debit(accountService.getAccountByName(AccountName.BETTING_POOL), (long) stake)
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

    /**
     * 현재 활성화된 베팅 라운드 조회
     */
    @Transactional(readOnly = true)
    public Optional<BetRoundResponse> getActiveRoundResponse(Scope type) {
        return betRoundRepository.findTopByStatusTrueAndScopeOrderByOpenAtDesc(type)
                .map(BetRoundResponse::from);
    }

    /**
     * 사용자 베팅 취소
     */
    @Transactional
    @OptimisticRetry
    public void cancelUserBet(UUID userId, UUID userBetId) {
        // fetch join으로 UserBet 및 BetRound 조회
        UserBet userBet = userBetRepository.findByUserBetIdAndUserIdWithRound(userBetId, userId)
            .orElseThrow(() -> new CustomException(ErrorCode.BET_NOT_FOUND));

        // 이미 처리된 상태인지 검증
        if (userBet.getBetStatus() != BetStatus.ACTIVE) {
            throw new CustomException(ErrorCode.BET_ALREADY_PROCESSED);
        }

        BetRound betRound = userBet.getRound();
        // 베팅 가능한 라운드 상태인지 검증
        betRound.validate();

        int stake = userBet.getStakePoints();
        UUID roundId = betRound.getBetRoundID();

        // 상태 변경 (ACTIVE -> DELETED)
        userBet.cancel();

        // 사용자 포인트 환불
        if (!userBet.isFree() && stake > 0) {
            pointLedgerService.processTransaction(
                TransactionReason.BETTING_CANCEL,
                roundId,
                AccountEntry.credit(accountService.getAccountByName(AccountName.BETTING_POOL), (long) stake),
                AccountEntry.debit(accountService.getUserAccount(userId), (long) stake)
            );
        }

        // 통계 차감
        if (userBet.getOption() == BetOption.RISE) {
            betRoundRepository.decrementUpStats(roundId, stake);
        } else {
            betRoundRepository.decrementDownStats(roundId, stake);
        }
    }


    /**
     * 베팅 결과 정산
     */
    @Transactional
    @OptimisticRetry
    public void settleUserBets() {
        LocalDateTime now = LocalDateTime.now();
        Account poolAccount = accountService.getAccountByName(AccountName.BETTING_POOL);
        Account systemAccount = accountService.getAccountByName(AccountName.SYSTEM_ISSUANCE);

        // 정산 대상 활성 라운드 조회
        List<BetRound> activeRounds =
                betRoundRepository.findByStatusFalseAndSettleAtIsNullAndLockAtLessThanEqual(now);

        // 활성 라운드의 전체 베팅 한 번에 조회
        List<UserBet> allUserBets = userBetRepository.findAllByRoundIn(activeRounds);

        // 라운드별 그룹화
        Map<UUID, List<UserBet>> betMap = allUserBets.stream()
            .collect(Collectors.groupingBy(bet -> bet.getRound().getBetRoundID()));

        for (BetRound round : activeRounds) {
            // PriceData를 이용해 시세 조회
            Optional<PriceData> priceOpt = priceDataRepository.findTopByTickerOrderByDateDesc(round.getSymbol());
            if (priceOpt.isEmpty()) continue;

            PriceData price = priceOpt.get();
            BigDecimal finalPrice = price.getAdjustedClose();

            if (finalPrice == null) continue;

            // 라운드 정산
            round.settle(finalPrice);

            // 현재 라운드의 베팅 리스트
            List<UserBet> userBets = betMap.getOrDefault(round.getBetRoundID(), Collections.emptyList());
            BetOption resultOption = round.getResultOption();
            // 해당 라운드에서 나갈 포인트 합
            long totalStake = 0;

            for (UserBet bet : userBets) {
                if (bet.getBetStatus() != BetStatus.ACTIVE) continue;

                Account userAccount = accountService.getUserAccount(bet.getUserId());

                if (round.isDraw()) {
                    // 가격 변동이 없을 시 참여자 전원 원금 환불
                    if (!bet.isFree() && bet.getStakePoints() > 0) {
                        pointLedgerService.processTransaction(
                            TransactionReason.BETTING_REFUND,
                            round.getBetRoundID(),
                            AccountEntry.credit(poolAccount, (long) bet.getStakePoints()),
                            AccountEntry.debit(userAccount, (long) bet.getStakePoints())
                        );
                        totalStake += bet.getStakePoints();
                    }
                    bet.draw();
                } else if (bet.getOption() == resultOption) {
                    // 예측 성공 시 보상 포인트 지급
                    int reward = calculateReward(bet);
                    bet.win(reward);

                    if (bet.isFree()) {
                        // 무료 베팅: 시스템 -> 사용자 보상 지급
                        pointLedgerService.processTransaction(
                            TransactionReason.BETTING_REWARD,
                            round.getBetRoundID(),
                            AccountEntry.credit(systemAccount, (long) reward),
                            AccountEntry.debit(userAccount, (long) reward)
                        );
                    }
                    else {
                        pointLedgerService.processTransaction(
                            TransactionReason.BETTING_REWARD,
                            round.getBetRoundID(),
                            AccountEntry.credit(poolAccount, (long) reward),
                            AccountEntry.credit(userAccount, (long) reward)
                        );
                        totalStake += reward;
                    }
                } else {
                    // 예측 실패 시 포인트 소멸
                    bet.lose();
                }
            }

            // 보상 소수점 처리 후 잔여금: 베팅 풀 -> 시스템 게정으로 이동
            long residual = round.getUpTotalPoints() + round.getDownTotalPoints() - totalStake;

            if (residual > 0) {
                pointLedgerService.processTransaction(
                    TransactionReason.BETTING_RESIDUAL,
                    round.getBetRoundID(),
                    AccountEntry.credit(accountService.getAccountByName(AccountName.BETTING_POOL), residual),
                    AccountEntry.debit(accountService.getAccountByName(AccountName.SYSTEM_ISSUANCE), residual)
                );
            }
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
