package org.sejongisc.backend.competition.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.sejongisc.backend.common.exception.controller.GlobalExceptionHandler;
import org.sejongisc.backend.competition.dto.DepositTransactionRequest;
import org.sejongisc.backend.competition.dto.DepositTransactionResponse;
import org.sejongisc.backend.competition.dto.DepositTransactionType;
import org.sejongisc.backend.competition.entity.LedgerType;
import org.sejongisc.backend.competition.service.CompetitionDepositService;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CompetitionDepositControllerTest {

    @Mock
    private CompetitionDepositService depositService;

    @InjectMocks
    private CompetitionDepositController depositController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private final UUID competitionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(depositController)
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
    @DisplayName("POST /me/deposit-transactions - 로그인 사용자와 요청을 서비스에 넘기고 이동 후 잔액을 반환한다")
    void transfer() throws Exception {
        DepositTransactionRequest request = new DepositTransactionRequest(DepositTransactionType.DEPOSIT, 400000L);
        when(depositService.transfer(competitionId, userId, request)).thenReturn(new DepositTransactionResponse(
                UUID.randomUUID(), LedgerType.DEPOSIT, new BigDecimal("400000"),
                new BigDecimal("600000"), new BigDecimal("700000"), null, LocalDateTime.of(2026, 11, 23, 17, 0)));

        mockMvc.perform(post("/api/competitions/{competitionId}/me/deposit-transactions", competitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(400000))
                .andExpect(jsonPath("$.cashBalance").value(600000))
                .andExpect(jsonPath("$.depositBalance").value(700000))
                .andExpect(jsonPath("$.executedAt").value("2026-11-23T17:00:00"));
    }

    @Test
    @DisplayName("POST /me/deposit-transactions - 금액이 정수가 아니면 서비스를 부르지 않고 400")
    void rejectsDecimalAmount() throws Exception {
        mockMvc.perform(post("/api/competitions/{competitionId}/me/deposit-transactions", competitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DEPOSIT\",\"amount\":1.5}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(depositService);
    }

    @Test
    @DisplayName("POST /me/deposit-transactions - 금액이 0이면 400")
    void rejectsZeroAmount() throws Exception {
        mockMvc.perform(post("/api/competitions/{competitionId}/me/deposit-transactions", competitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DEPOSIT\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("금액은 1원 이상이어야 합니다."));
        verifyNoInteractions(depositService);
    }

    @Test
    @DisplayName("POST /me/deposit-transactions - 이자(INTEREST)는 요청할 수 없어 400")
    void rejectsInterestType() throws Exception {
        mockMvc.perform(post("/api/competitions/{competitionId}/me/deposit-transactions", competitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INTEREST\",\"amount\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(depositService);
    }

    @Test
    @DisplayName("GET /me/deposit-transactions - page, size를 주지 않으면 0, 20으로 넘기고 이자 날짜를 함께 반환한다")
    void getTransactions() throws Exception {
        DepositTransactionResponse interest = new DepositTransactionResponse(
                UUID.randomUUID(), LedgerType.INTEREST, new BigDecimal("82.1917"),
                new BigDecimal("9000000"), new BigDecimal("1000082.1917"),
                LocalDate.of(2026, 11, 23), LocalDateTime.of(2026, 11, 24, 0, 0));
        when(depositService.getTransactions(competitionId, userId, 0, 20))
                .thenReturn(new PageImpl<>(List.of(interest), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/competitions/{competitionId}/me/deposit-transactions", competitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("INTEREST"))
                .andExpect(jsonPath("$.content[0].amount").value(82.1917))
                .andExpect(jsonPath("$.content[0].interestDate").value("2026-11-23"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
