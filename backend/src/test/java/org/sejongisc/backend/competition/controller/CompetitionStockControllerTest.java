package org.sejongisc.backend.competition.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.sejongisc.backend.competition.dto.CompetitionStockDetailResponse;
import org.sejongisc.backend.competition.dto.CompetitionStockListResponse;
import org.sejongisc.backend.competition.dto.CompetitionStockSummary;
import org.sejongisc.backend.competition.dto.UnavailableReason;
import org.sejongisc.backend.competition.entity.Market;
import org.sejongisc.backend.competition.service.CompetitionStockService;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CompetitionStockControllerTest {

    @Mock
    private CompetitionStockService stockService;

    @InjectMocks
    private CompetitionStockController stockController;

    private MockMvc mockMvc;
    private final UUID competitionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(stockController)
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
    @DisplayName("GET /stocks - 파라미터를 생략하면 첫 페이지 20건으로 조회한다")
    void searchUsesDefaults() throws Exception {
        CompetitionStockSummary samsung = new CompetitionStockSummary(
                "005930", "삼성전자", Market.KR, "KRW", UUID.randomUUID(),
                new BigDecimal("60000"), new BigDecimal("60000"), new BigDecimal("59000"), new BigDecimal("0.016949"),
                LocalDate.of(2026, 11, 9), true, null);
        when(stockService.search(competitionId, null, null, 0, 20))
                .thenReturn(new CompetitionStockListResponse(0, 20, 1, null, List.of(samsung)));

        mockMvc.perform(get("/api/competitions/{competitionId}/stocks", competitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.exchangeRate").doesNotExist())
                .andExpect(jsonPath("$.stocks[0].ticker").value("005930"))
                .andExpect(jsonPath("$.stocks[0].market").value("KR"))
                .andExpect(jsonPath("$.stocks[0].closePrice").value(60000))
                .andExpect(jsonPath("$.stocks[0].dailyChangeRate").value(0.016949))
                .andExpect(jsonPath("$.stocks[0].priceDate").value("2026-11-09"))
                .andExpect(jsonPath("$.stocks[0].tradable").value(true));
    }

    @Test
    @DisplayName("GET /stocks - 시장, 검색어, 페이지를 서비스에 그대로 넘긴다")
    void searchPassesParameters() throws Exception {
        when(stockService.search(competitionId, "US", "AAPL", 2, 50))
                .thenReturn(new CompetitionStockListResponse(2, 50, 0, null, List.of()));

        mockMvc.perform(get("/api/competitions/{competitionId}/stocks", competitionId)
                        .param("market", "US")
                        .param("query", "AAPL")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    @DisplayName("GET /stocks - 지원하지 않는 시장 구분은 400을 반환한다")
    void searchReturns400ForUnknownMarket() throws Exception {
        when(stockService.search(competitionId, "JP", null, 0, 20))
                .thenThrow(new CustomException(ErrorCode.INVALID_COMPETITION_MARKET));

        mockMvc.perform(get("/api/competitions/{competitionId}/stocks", competitionId).param("market", "JP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_COMPETITION_MARKET"));
    }

    @Test
    @DisplayName("GET /stocks/{ticker} - 로그인한 사용자의 보유 정보를 반환한다")
    void getDetailUsesLoginUser() throws Exception {
        when(stockService.getDetail(competitionId, "005930", userId))
                .thenReturn(CompetitionStockDetailResponse.builder()
                        .ticker("005930")
                        .market(Market.KR)
                        .holdingQuantity(10)
                        .cashBalance(new BigDecimal("6999900"))
                        .tradable(false)
                        .unavailableReason(UnavailableReason.PRICE_UNAVAILABLE)
                        .build());

        mockMvc.perform(get("/api/competitions/{competitionId}/stocks/{ticker}", competitionId, "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("005930"))
                .andExpect(jsonPath("$.holdingQuantity").value(10))
                .andExpect(jsonPath("$.cashBalance").value(6999900))
                .andExpect(jsonPath("$.tradable").value(false))
                .andExpect(jsonPath("$.unavailableReason").value("PRICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("GET /stocks/{ticker} - 없는 대회는 404를 반환한다")
    void getDetailReturns404ForUnknownCompetition() throws Exception {
        when(stockService.getDetail(competitionId, "005930", userId))
                .thenThrow(new CustomException(ErrorCode.COMPETITION_NOT_FOUND));

        mockMvc.perform(get("/api/competitions/{competitionId}/stocks/{ticker}", competitionId, "005930"))
                .andExpect(status().isNotFound());
    }
}
