package org.sejongisc.backend.betting.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.betting.dto.UserBetRequest;
import org.sejongisc.backend.betting.entity.*;
import org.sejongisc.backend.betting.repository.BetRoundRepository;
import org.sejongisc.backend.betting.repository.StockRepository;
import org.sejongisc.backend.betting.repository.UserBetRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.point.entity.PointOrigin;
import org.sejongisc.backend.point.entity.PointReason;
import org.sejongisc.backend.point.service.PointHistoryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BettingService {

    private final BetRoundRepository betRoundRepository;
    private final StockRepository stockRepository;
    private final UserBetRepository userBetRepository;
    private final PointHistoryService pointHistoryService;

    private final Random random = new Random();

    public Optional<BetRound> getActiveRound(Scope type){
        return betRoundRepository.findByStatusTrueAndScope(type);
    }

    // NULL일 시 빈 리스트 반환
    public List<BetRound> getAllBetRounds() {
        // TODO : 필요 시 필터링, 정렬, 검색 로직 추가
        return betRoundRepository.findAllByOrderBySettleAtDesc();
    }

    public Stock getStock(){
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            throw new CustomException(ErrorCode.STOCK_NOT_FOUND);
        }
        // TODO : 가중치 랜덤설정
        return stocks.get(random.nextInt(stocks.size()));
    }

    public boolean setAllowFree(){
        return random.nextDouble() < 0.2;
    }

    public List<UserBet> getAllMyBets(UUID userId) {
        // TODO : 필요 시 필터링, 정렬, 검색 로직 추가
        return userBetRepository.findAllByUserIdOrderByRound_SettleAtDesc(userId);
    }

    public void createBetRound(Scope scope) {
        Stock stock = getStock();
        LocalDateTime now = LocalDateTime.now();

        BetRound betRound = BetRound.builder()
                .scope(scope)
                .title(now.toLocalDate() + " " + stock.getName() + " " + scope.name() + " 라운드")
                .symbol(stock.getSymbol())
                .allowFree(setAllowFree())
                .status(true)
                .openAt(scope.getOpenAt(now))
                .lockAt(scope.getLockAt(now))
                .market(stock.getMarket())
                .previousClosePrice(stock.getPreviousClosePrice())
                .build();

        betRoundRepository.save(betRound);
    }

    public void closeBetRound(){
        // TODO : status를 false로 바꿔야함, 정산 로직 구현하면서 같이 할 것
    }

    @Transactional
    public UserBet postUserBet(UUID userId, UserBetRequest userBetRequest) {
        BetRound betRound = betRoundRepository.findById(userBetRequest.getRoundId())
                .orElseThrow(() -> new CustomException(ErrorCode.BET_ROUND_NOT_FOUND));

        if (userBetRepository.existsByRoundAndUserId(betRound, userId)) {
            throw new CustomException(ErrorCode.BET_DUPLICATE);
        }

        LocalDateTime now = LocalDateTime.now();

        // 허용 구간: [openAt, lockAt)
        if (now.isBefore(betRound.getOpenAt()) || !now.isBefore(betRound.getLockAt())) {
            throw new CustomException(ErrorCode.BET_TIME_INVALID);
        }

        int stake = 0;

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
            // DB 유니크 제약(중복 베팅) 위반 시
            throw new CustomException(ErrorCode.BET_DUPLICATE);
        }
    }

    @Transactional
    public void cancelUserBet(UUID userId, UUID userBetId) {
        UserBet userBet = userBetRepository.findByUserBetIdAndUserId(userBetId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.BET_NOT_FOUND));

        BetRound betRound = userBet.getRound();

        if (!LocalDateTime.now().isBefore(betRound.getLockAt())){
            throw new CustomException(ErrorCode.BET_ROUND_CLOSED);
        }

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
}
