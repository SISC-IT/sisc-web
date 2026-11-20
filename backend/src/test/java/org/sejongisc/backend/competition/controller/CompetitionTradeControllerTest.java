package org.sejongisc.backend.competition.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.common.auth.dto.CustomUserDetails;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.exception.controller.GlobalExceptionHandler;
import org.sejongisc.backend.competition.dto.CompetitionTradeRequest;
import org.sejongisc.backend.competition.dto.CompetitionTradeResponse;
import org.sejongisc.backend.competition.entity.TradeSide;
import org.sejongisc.backend.competition.service.CompetitionTradeService;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CompetitionTradeControllerTest {

    @Mock
    private CompetitionTradeService tradeService;

    @InjectMocks
    private CompetitionTradeController tradeController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private final UUID competitionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID key = UUID.randomUUID();
    private final UUID closePriceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(tradeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(
                User.builder().userId(userId).role(Role.TEAM_MEMBER).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /me/trades - 로그인 사용자, 주문 키, 주문 내용을 서비스에 넘기고 체결 결과를 반환한다")
    void orderPassesLoginUserAndKey() throws Exception {
        CompetitionTradeRequest request = new CompetitionTradeRequest("005930", TradeSide.BUY, 10L, closePriceId);
        when(tradeService.order(competitionId, userId, key, request))
                .thenReturn(new CompetitionTradeResponse(
                        UUID.randomUUID(), "005930", TradeSide.BUY, 10L,
                        new BigDecimal("60000"), new BigDecimal("60"),
                        LocalDateTime.of(2026, 11, 20, 17, 0), new BigDecimal("399940")));

        mockMvc.perform(post("/api/competitions/{competitionId}/me/trades", competitionId)
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("005930"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.price").value(60000))
                .andExpect(jsonPath("$.fee").value(60))
                .andExpect(jsonPath("$.executedAt").value("2026-11-20T17:00:00"))
                .andExpect(jsonPath("$.cashBalance").value(399940));
    }

    @Test
    @DisplayName("POST /me/trades - 수량이 0이면 서비스를 부르지 않고 400을 반환한다")
    void orderRejectsZeroQuantity() throws Exception {
        CompetitionTradeRequest request = new CompetitionTradeRequest("005930", TradeSide.BUY, 0L, closePriceId);

        mockMvc.perform(post("/api/competitions/{competitionId}/me/trades", competitionId)
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.message").value("수량은 1주 이상이어야 합니다."));
        verifyNoInteractions(tradeService);
    }

    @Test
    @DisplayName("POST /me/trades - 종가가 바뀌었으면 409와 PRICE_CHANGED를 반환한다")
    void orderReturns409WhenPriceChanged() throws Exception {
        CompetitionTradeRequest request = new CompetitionTradeRequest("005930", TradeSide.BUY, 10L, closePriceId);
        when(tradeService.order(any(), any(), any(), any()))
                .thenThrow(new CustomException(ErrorCode.PRICE_CHANGED));

        mockMvc.perform(post("/api/competitions/{competitionId}/me/trades", competitionId)
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRICE_CHANGED"));
    }
}
