//package org.sejongisc.backend.betting.service;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.mockito.ArgumentCaptor;
//import org.sejongisc.backend.betting.dto.UserBetRequest;
//import org.sejongisc.backend.betting.entity.*;
//import org.sejongisc.backend.betting.repository.BetRoundRepository;
//import org.sejongisc.backend.betting.repository.StockRepository;
//import org.sejongisc.backend.betting.repository.UserBetRepository;
//import org.sejongisc.backend.common.exception.CustomException;
//import org.sejongisc.backend.common.exception.ErrorCode;
//import org.sejongisc.backend.point.entity.PointOrigin;
//import org.sejongisc.backend.point.entity.PointReason;
//import org.sejongisc.backend.point.service.PointHistoryService;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.*;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.mockito.Mockito.*;
//
//class BettingServiceTest {
//
//    private BetRoundRepository betRoundRepository;
//    private StockRepository stockRepository;
//    private UserBetRepository userBetRepository;
//    private PointHistoryService pointHistoryService;
//
//    private BettingService bettingService;
//
//    private UUID userId;
//    private UUID roundId;
//
//    @BeforeEach
//    void setUp() {
//        betRoundRepository = mock(BetRoundRepository.class);
//        stockRepository = mock(StockRepository.class);
//        userBetRepository = mock(UserBetRepository.class);
//        pointHistoryService = mock(PointHistoryService.class);
//
//        bettingService = new BettingService(
//                betRoundRepository,
//                stockRepository,
//                userBetRepository,
//                pointHistoryService
//        );
//
//        userId = UUID.randomUUID();
//        roundId = UUID.randomUUID();
//    }
//
//    // ---------------------
//    // Stock / util tests
//    // ---------------------
//    @Test
//    void getStock_빈리스트면_예외발생() {
//        when(stockRepository.findAll()).thenReturn(List.of());
//
//        CustomException ex = assertThrows(CustomException.class,
//                () -> bettingService.getStock());
//
//        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STOCK_NOT_FOUND);
//        verify(stockRepository, times(1)).findAll();
//    }
//
//    @Test
//    void getStock_리스트있으면_하나반환() {
//        Stock s1 = Stock.builder()
//                .name("삼성전자")
//                .symbol("005930")
//                .market(MarketType.KOREA)
//                .previousClosePrice(BigDecimal.valueOf(85400))
//                .build();
//
//        Stock s2 = Stock.builder()
//                .name("애플")
//                .symbol("AAPL")
//                .market(MarketType.US)
//                .previousClosePrice(BigDecimal.valueOf(251.65))
//                .build();
//
//        when(stockRepository.findAll()).thenReturn(List.of(s1, s2));
//
//        Stock chosen = bettingService.getStock();
//
//        assertThat(List.of(s1, s2)).contains(chosen);
//        verify(stockRepository, times(1)).findAll();
//    }
//
//    @Test
//    void setAllowFree_항상_불린값반환() {
//        boolean result = bettingService.setAllowFree();
//        assertThat(result).isInstanceOf(Boolean.class);
//    }
//
//    // ---------------------
//    // 조회 관련 테스트
//    // ---------------------
//    @Test
//    @DisplayName("createBetRound_DAILY_정상저장")
//    void createBetRound_DAILY_정상저장() {
//        // given
//        Stock stock = Stock.builder()
//                .name("삼성전자")
//                .symbol("005930")
//                .market(MarketType.KOREA)
//                .previousClosePrice(BigDecimal.valueOf(85400))
//                .build();
//
//        when(stockRepository.findAll()).thenReturn(List.of(stock));
//
//        // when
//        bettingService.createBetRound(Scope.DAILY);
//
//        // then
//        ArgumentCaptor<BetRound> captor = ArgumentCaptor.forClass(BetRound.class);
//        verify(betRoundRepository, times(1)).save(captor.capture());
//        BetRound saved = captor.getValue();
//
//        assertThat(saved.getScope()).isEqualTo(Scope.DAILY);
//        assertThat(saved.getSymbol()).isEqualTo("005930");
//        assertThat(saved.getTitle()).contains("삼성전자");
//        assertThat(saved.isOpen()).isTrue(); // 스케줄러에서 open() 호출 후 저장이라면 true여야 함
//    }
//
//    @Test
//    @DisplayName("활성화된 DAILY BetRound 조회 성공")
//    void findActiveRound_Success() {
//        BetRound dailyRound = BetRound.builder()
//                .betRoundID(UUID.randomUUID())
//                .scope(Scope.DAILY)
//                .status(true)
//                .title("Daily Round")
//                .openAt(LocalDateTime.now())
//                .build();
//
//        when(betRoundRepository.findByStatusTrueAndScope(Scope.DAILY))
//                .thenReturn(Optional.of(dailyRound));
//
//        Optional<BetRound> result = bettingService.getActiveRound(Scope.DAILY);
//
//        assertThat(result).isPresent();
//        assertThat(result.get().getScope()).isEqualTo(Scope.DAILY);
//        assertThat(result.get().isOpen()).isTrue();
//        verify(betRoundRepository, times(1)).findByStatusTrueAndScope(Scope.DAILY);
//    }
//
//    @Test
//    @DisplayName("활성화된 BetRound가 없을 때 빈 Optional 반환")
//    void findActiveRound_NotFound() {
//        when(betRoundRepository.findByStatusTrueAndScope(Scope.DAILY))
//                .thenReturn(Optional.empty());
//
//        Optional<BetRound> result = bettingService.getActiveRound(Scope.DAILY);
//
//        assertThat(result).isEmpty();
//        verify(betRoundRepository, times(1)).findByStatusTrueAndScope(Scope.DAILY);
//    }
//
//    @Test
//    @DisplayName("모든 BetRound 최신순 조회 성공")
//    void getAllBetRounds_Success() {
//        List<BetRound> betRounds = List.of(
//                BetRound.builder()
//                        .betRoundID(UUID.randomUUID())
//                        .scope(Scope.DAILY)
//                        .status(true)
//                        .title("Daily Round")
//                        .openAt(LocalDateTime.now())
//                        .build(),
//                BetRound.builder()
//                        .betRoundID(UUID.randomUUID())
//                        .scope(Scope.WEEKLY)
//                        .status(false)
//                        .title("Weekly Round")
//                        .openAt(LocalDateTime.now().minusDays(1))
//                        .build()
//        );
//
//        when(betRoundRepository.findAllByOrderBySettleAtDesc())
//                .thenReturn(betRounds);
//
//        List<BetRound> results = bettingService.getAllBetRounds();
//
//        assertThat(results).hasSize(2);
//        assertThat(results.get(0).getScope()).isEqualTo(Scope.DAILY);
//        assertThat(results.get(1).getScope()).isEqualTo(Scope.WEEKLY);
//        verify(betRoundRepository, times(1)).findAllByOrderBySettleAtDesc();
//    }
//
//    @Test
//    @DisplayName("BetRound가 없을 때 빈 리스트 반환")
//    void getAllBetRounds_Empty() {
//        when(betRoundRepository.findAllByOrderBySettleAtDesc())
//                .thenReturn(Collections.emptyList());
//
//        List<BetRound> results = bettingService.getAllBetRounds();
//
//        assertThat(results).isEmpty();
//        verify(betRoundRepository, times(1)).findAllByOrderBySettleAtDesc();
//    }
//
//    @Test
//    @DisplayName("WEEKLY BetRound 조회 성공")
//    void findActiveRound_Weekly_Success() {
//        BetRound weeklyRound = BetRound.builder()
//                .betRoundID(UUID.randomUUID())
//                .scope(Scope.WEEKLY)
//                .status(true)
//                .title("Weekly Round")
//                .openAt(LocalDateTime.now())
//                .build();
//
//        when(betRoundRepository.findByStatusTrueAndScope(Scope.WEEKLY))
//                .thenReturn(Optional.of(weeklyRound));
//
//        Optional<BetRound> result = bettingService.getActiveRound(Scope.WEEKLY);
//
//        assertThat(result).isPresent();
//        assertThat(result.get().getScope()).isEqualTo(Scope.WEEKLY);
//        verify(betRoundRepository, times(1)).findByStatusTrueAndScope(Scope.WEEKLY);
//    }
//
//    @Test
//    @DisplayName("getAllMyBets() - 유저 ID로 조회 시 Repository 호출 및 결과 반환 확인")
//    void getAllMyBets_Success() {
//        UUID u = UUID.randomUUID();
//        BetRound round = BetRound.builder()
//                .title("테스트 라운드")
//                .openAt(LocalDateTime.now().minusHours(2))
//                .lockAt(LocalDateTime.now().plusHours(1))
//                .settleAt(LocalDateTime.now().plusHours(2))
//                .build();
//
//        UserBet bet1 = UserBet.builder()
//                .userBetId(UUID.randomUUID())
//                .round(round)
//                .userId(u)
//                .option(BetOption.RISE)
//                .stakePoints(100)
//                .isFree(false)
//                .build();
//
//        UserBet bet2 = UserBet.builder()
//                .userBetId(UUID.randomUUID())
//                .round(round)
//                .userId(u)
//                .option(BetOption.FALL)
//                .stakePoints(50)
//                .isFree(true)
//                .build();
//
//        List<UserBet> mockResult = List.of(bet1, bet2);
//        when(userBetRepository.findAllByUserIdOrderByRound_SettleAtDesc(u))
//                .thenReturn(mockResult);
//
//        List<UserBet> result = bettingService.getAllMyBets(u);
//
//        verify(userBetRepository, times(1))
//                .findAllByUserIdOrderByRound_SettleAtDesc(u);
//        assertThat(result).hasSize(2);
//        assertThat(result.get(0).getUserId()).isEqualTo(u);
//        assertThat(result.get(1).getRound().getTitle()).isEqualTo("테스트 라운드");
//    }
//
//    // ---------------------
//    // Bet creation / posting tests
//    // ---------------------
//    private BetRound openRoundNow() {
//        LocalDateTime now = LocalDateTime.now();
//        return BetRound.builder()
//                .betRoundID(roundId)
//                .scope(Scope.DAILY)
//                .status(true)
//                .title("OPEN")
//                .openAt(now.minusMinutes(1))
//                .lockAt(now.plusMinutes(10))
//                .build();
//    }
//
//    private BetRound closedRoundNow() {
//        LocalDateTime now = LocalDateTime.now();
//        return BetRound.builder()
//                .betRoundID(roundId)
//                .scope(Scope.DAILY)
//                .status(false)
//                .title("CLOSED")
//                .openAt(now.minusMinutes(10))
//                .lockAt(now.minusMinutes(1))
//                .build();
//    }
//
//    private UserBetRequest paidReq(int stake) {
//        return UserBetRequest.builder()
//                .roundId(roundId)
//                .option(BetOption.RISE)
//                .free(false)
//                .stakePoints(stake)
//                .build();
//    }
//
//    private UserBetRequest freeReq() {
//        return UserBetRequest.builder()
//                .roundId(roundId)
//                .option(BetOption.FALL)
//                .free(true)
//                .stakePoints(999)
//                .build();
//    }
//
//    @Test
//    @DisplayName("postUserBet_paid_success")
//    void postUserBet_paid_success() {
//        BetRound round = openRoundNow();
//
//        when(betRoundRepository.findById(roundId)).thenReturn(Optional.of(round));
//        when(userBetRepository.existsByRoundAndUserId(round, userId)).thenReturn(false);
//        when(userBetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
//
//        UserBetRequest req = paidReq(100);
//
//        UserBet result = bettingService.postUserBet(userId, req);
//
//        assertThat(result.getStakePoints()).isEqualTo(100);
//        assertThat(result.isFree()).isFalse();
//
//        verify(pointHistoryService).createPointHistory(
//                eq(userId), eq(-100),
//                eq(PointReason.BETTING),
//                eq(PointOrigin.BETTING),
//                eq(roundId)
//        );
//        verify(userBetRepository).save(any(UserBet.class));
//    }
//
//    @Test
//    @DisplayName("postUserBet_free_success")
//    void postUserBet_free_success() {
//        BetRound round = openRoundNow();
//
//        when(betRoundRepository.findById(roundId)).thenReturn(Optional.of(round));
//        when(userBetRepository.existsByRoundAndUserId(round, userId)).thenReturn(false);
//        when(userBetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
//
//        UserBetRequest req = freeReq();
//
//        UserBet result = bettingService.postUserBet(userId, req);
//
//        assertThat(result.isFree()).isTrue();
//        assertThat(result.getStakePoints()).isZero();
//
//        verify(pointHistoryService, never()).createPointHistory(any(), anyInt(), any(), any(), any());
//        verify(userBetRepository).save(any(UserBet.class));
//    }
//
//    @Test
//    @DisplayName("postUserBet_round_not_found")
//    void postUserBet_round_not_found() {
//        when(betRoundRepository.findById(roundId)).thenReturn(Optional.empty());
//
//        CustomException ex = assertThrows(CustomException.class,
//                () -> bettingService.postUserBet(userId, paidReq(100)));
//
//        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BET_ROUND_NOT_FOUND);
//        verifyNoInteractions(pointHistoryService);
//    }
//
//    @Test
//    @DisplayName("postUserBet_duplicate")
//    void postUserBet_duplicate() {
//        BetRound round = openRoundNow();
//        when(betRoundRepository.findById(roundId)).thenReturn(Optional.of(round));
//        when(userBetRepository.existsByRoundAndUserId(round, userId)).thenReturn(true);
//
//        CustomException ex = assertThrows(CustomException.class,
//                () -> bettingService.postUserBet(userId, paidReq(100)));
//
//        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BET_DUPLICATE);
//        verifyNoInteractions(pointHistoryService);
//        verify(userBetRepository, never()).save(any());
//    }
//
//    @Test
//    @DisplayName("postUserBet_time_invalid")
//    void postUserBet_time_invalid() {
//        BetRound closed = closedRoundNow();
//        when(betRoundRepository.findById(roundId)).thenReturn(Optional.of(closed));
//        when(userBetRepository.existsByRoundAndUserId(closed, userId)).thenReturn(false);
//
//        CustomException ex = assertThrows(CustomException.class,
//                () -> bettingService.postUserBet(userId, paidReq(100)));
//
//        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BET_ROUND_CLOSED);
//        verifyNoInteractions(pointHistoryService);
//        verify(userBetRepository, never()).save(any());
//    }
//
//    // ---------------------
//    // cancelUserBet tests
//    // ---------------------
//    @Test
//    @DisplayName("cancelUserBet_paid_refund_and_delete")
//    void cancelUserBet_paid_refund_and_delete() {
//        BetRound round = openRoundNow();
//        UUID userBetId = UUID.randomUUID();
//
//        UserBet bet = UserBet.builder()
//                .userBetId(userBetId)
//                .round(round)
//                .userId(userId)
//                .isFree(false)
//                .stakePoints(200)
//                .betStatus(BetStatus.ACTIVE)
//                .build();
//
//        when(userBetRepository.findByUserBetIdAndUserId(userBetId, userId))
//                .thenReturn(Optional.of(bet));
//
//        bettingService.cancelUserBet(userId, userBetId);
//
//        verify(pointHistoryService).createPointHistory(
//                eq(userId), eq(200),
//                eq(PointReason.BETTING),
//                eq(PointOrigin.BETTING),
//                eq(userBetId)
//        );
//        verify(userBetRepository).delete(bet);
//    }
//
//    @Test
//    @DisplayName("cancelUserBet_not_found")
//    void cancelUserBet_not_found() {
//        UUID userBetId = UUID.randomUUID();
//        when(userBetRepository.findByUserBetIdAndUserId(userBetId, userId))
//                .thenReturn(Optional.empty());
//
//        CustomException ex = assertThrows(CustomException.class,
//                () -> bettingService.cancelUserBet(userId, userBetId));
//
//        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BET_NOT_FOUND);
//        verifyNoInteractions(pointHistoryService);
//        verify(userBetRepository, never()).delete(any());
//    }
//
//    @Test
//    @DisplayName("cancelUserBet_after_lock")
//    void cancelUserBet_after_lock() {
//        BetRound closed = closedRoundNow();
//        UUID userBetId = UUID.randomUUID();
//
//        UserBet bet = UserBet.builder()
//                .userBetId(userBetId)
//                .round(closed)
//                .userId(userId)
//                .isFree(false)
//                .stakePoints(200)
//                .betStatus(BetStatus.ACTIVE)
//                .build();
//
//        when(userBetRepository.findByUserBetIdAndUserId(userBetId, userId))
//                .thenReturn(Optional.of(bet));
//
//        CustomException ex = assertThrows(CustomException.class,
//                () -> bettingService.cancelUserBet(userId, userBetId));
//
//        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BET_ROUND_CLOSED);
//        verify(pointHistoryService, never()).createPointHistory(any(), anyInt(), any(), any(), any());
//        verify(userBetRepository, never()).delete(any());
//    }
//}
