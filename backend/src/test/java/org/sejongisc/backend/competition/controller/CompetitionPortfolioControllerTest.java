package org.sejongisc.backend.competition.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.sejongisc.backend.competition.dto.CompetitionHoldingResponse;
import org.sejongisc.backend.competition.dto.CompetitionSummaryResponse;
import org.sejongisc.backend.competition.dto.CompetitionTradeResponse;
import org.sejongisc.backend.competition.entity.TradeSide;
import org.sejongisc.backend.competition.service.CompetitionPortfolioService;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CompetitionPortfolioControllerTest {

    @Mock
    private CompetitionPortfolioService portfolioService;

    @InjectMocks
    private CompetitionPortfolioController portfolioController;

    private MockMvc mockMvc;
    private final UUID competitionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(portfolioController)
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
    @DisplayName("GET /me/trades - page, size를 주지 않으면 0, 20으로 넘기고 페이지를 반환한다")
    void getTradesWithDefaultPaging() throws Exception {
        CompetitionTradeResponse trade = new CompetitionTradeResponse(
                UUID.randomUUID(), "005930", "삼성전자", TradeSide.BUY, 10L,
                new BigDecimal("60000"), new BigDecimal("60"),
                LocalDateTime.of(2026, 11, 20, 17, 0), new BigDecimal("399940"));
        when(portfolioService.getTrades(competitionId, userId, 0, 20))
                .thenReturn(new PageImpl<>(List.of(trade), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/competitions/{competitionId}/me/trades", competitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticker").value("005930"))
                .andExpect(jsonPath("$.content[0].companyName").value("삼성전자"))
                .andExpect(jsonPath("$.content[0].executedAt").value("2026-11-20T17:00:00"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /me/trades - 참가자가 아니면 404")
    void getTradesNotParticipant() throws Exception {
        when(portfolioService.getTrades(competitionId, userId, 2, 50))
                .thenThrow(new CustomException(ErrorCode.COMPETITION_PARTICIPANT_NOT_FOUND));

        mockMvc.perform(get("/api/competitions/{competitionId}/me/trades", competitionId)
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMPETITION_PARTICIPANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /me/holdings - 보유 종목과 종가가 없는 종목의 null 값을 그대로 반환한다")
    void getHoldings() throws Exception {
        when(portfolioService.getHoldings(competitionId, userId)).thenReturn(List.of(
                new CompetitionHoldingResponse("005930", "삼성전자", 10L, new BigDecimal("60000"),
                        new BigDecimal("63000"), LocalDate.of(2026, 11, 20),
                        new BigDecimal("630000"), new BigDecimal("30000"), new BigDecimal("0.050000")),
                new CompetitionHoldingResponse("035420", "NAVER", 1L, new BigDecimal("200000"),
                        null, null, null, null, null)));

        mockMvc.perform(get("/api/competitions/{competitionId}/me/holdings", competitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("005930"))
                .andExpect(jsonPath("$[0].priceDate").value("2026-11-20"))
                .andExpect(jsonPath("$[0].evaluationAmount").value(630000))
                .andExpect(jsonPath("$[0].profitRate").value(0.05))
                .andExpect(jsonPath("$[1].ticker").value("035420"))
                .andExpect(jsonPath("$[1].closePrice").doesNotExist())
                .andExpect(jsonPath("$[1].profitRate").doesNotExist());
    }

    @Test
    @DisplayName("GET /me/summary - date를 넘기고 명세 필드 이름으로 반환한다")
    void getSummary() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 20);
        when(portfolioService.getSummary(competitionId, userId, date)).thenReturn(CompetitionSummaryResponse.builder()
                .date(date)
                .valuationStatus(CompetitionSummaryResponse.ValuationStatus.PROVISIONAL)
                .valuedAt(LocalDateTime.of(2026, 11, 20, 17, 0))
                .initialBalance(new BigDecimal("10000000"))
                .cashBalance(new BigDecimal("6999900"))
                .depositBalance(new BigDecimal("2000000"))
                .stockValue(new BigDecimal("1000000"))
                .totalAssets(new BigDecimal("9999900"))
                .profitLoss(new BigDecimal("-100"))
                .returnRate(new BigDecimal("-0.000010"))
                .totalFees(new BigDecimal("100"))
                .totalInterest(BigDecimal.ZERO)
                .participationStatus(CompetitionSummaryResponse.ParticipationStatus.PARTICIPATED)
                .participatedOnDate(true)
                .firstParticipatedAt(LocalDateTime.of(2026, 11, 20, 16, 45))
                .lastParticipatedAt(LocalDateTime.of(2026, 11, 20, 16, 50))
                .build());

        mockMvc.perform(get("/api/competitions/{competitionId}/me/summary", competitionId).param("date", "2026-11-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-20"))
                .andExpect(jsonPath("$.valuationStatus").value("PROVISIONAL"))
                .andExpect(jsonPath("$.cashBalance").value(6999900))
                .andExpect(jsonPath("$.totalAssets").value(9999900))
                .andExpect(jsonPath("$.returnRate").value(-0.00001))
                .andExpect(jsonPath("$.participationStatus").value("PARTICIPATED"))
                .andExpect(jsonPath("$.participatedOnDate").value(true))
                .andExpect(jsonPath("$.firstParticipatedAt").value("2026-11-20T16:45:00"));
    }

    @Test
    @DisplayName("GET /me/summary - date를 생략하면 null을 넘겨 서비스가 오늘로 정한다")
    void getSummaryWithoutDate() throws Exception {
        when(portfolioService.getSummary(competitionId, userId, null)).thenReturn(CompetitionSummaryResponse.builder()
                .valuationStatus(CompetitionSummaryResponse.ValuationStatus.PENDING_DATA).build());

        mockMvc.perform(get("/api/competitions/{competitionId}/me/summary", competitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuationStatus").value("PENDING_DATA"));
    }

    @Test
    @DisplayName("GET /me/summary - 날짜 형식이 틀리면 400")
    void getSummaryRejectsBadDate() throws Exception {
        mockMvc.perform(get("/api/competitions/{competitionId}/me/summary", competitionId).param("date", "2026/11/20"))
                .andExpect(status().isBadRequest());
    }
}
