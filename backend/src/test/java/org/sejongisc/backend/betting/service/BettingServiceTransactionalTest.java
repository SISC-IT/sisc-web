package org.sejongisc.backend.betting.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sejongisc.backend.betting.dto.UserBetRequest;
import org.sejongisc.backend.betting.entity.BetOption;
import org.sejongisc.backend.betting.entity.BetRound;
import org.sejongisc.backend.betting.repository.BetRoundRepository;
import org.sejongisc.backend.betting.repository.StockRepository; // ← 의존하면 반드시 목!
import org.sejongisc.backend.betting.repository.UserBetRepository;
import org.sejongisc.backend.point.entity.PointHistory;
import org.sejongisc.backend.point.repository.PointHistoryRepository;
import org.sejongisc.backend.point.service.PointHistoryService;
import org.sejongisc.backend.user.dao.UserRepository;
import org.sejongisc.backend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = BettingServiceTransactionalTest.TestConfig.class)
class BettingServiceTransactionalTest {

    @Configuration
    @EnableRetry
    @Import({BettingService.class, PointHistoryService.class})
    static class TestConfig {}

    @Autowired
    BettingService bettingService;

    @MockBean UserRepository userRepository;
    @MockBean BetRoundRepository betRoundRepository;
    @MockBean UserBetRepository userBetRepository;
    @MockBean PointHistoryRepository pointHistoryRepository;
    @MockBean StockRepository stockRepository;

    @Test
    void 포인트히스토리가_UserBet_저장_전에_호출되고_외부트랜잭션_롤백시에도_정상동작한다() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().point(1000).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        BetRound round = BetRound.builder()
                .openAt(LocalDateTime.now().minusMinutes(5))
                .lockAt(LocalDateTime.now().plusMinutes(10))
                .build();
        given(betRoundRepository.findById(any())).willReturn(Optional.of(round));

        UserBetRequest req = UserBetRequest.builder()
                .roundId(UUID.randomUUID())
                .option(BetOption.RISE)
                .stakePoints(100)
                .free(false)
                .build();

        given(pointHistoryRepository.save(any(PointHistory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        doThrow(new RuntimeException("강제로 외부 트랜잭션 롤백 발생"))
                .when(userBetRepository).save(any());

        assertThatThrownBy(() -> bettingService.postUserBet(userId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("외부 트랜잭션 롤백");

        verify(pointHistoryRepository, times(1)).save(any(PointHistory.class));
        verify(userBetRepository, times(1)).save(any());
    }
}
