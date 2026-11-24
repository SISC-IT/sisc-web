package org.sejongisc.backend.competition.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.sejongisc.backend.competition.dto.CompetitionResponse;
import org.sejongisc.backend.competition.entity.CompetitionStatus;
import org.sejongisc.backend.competition.entity.ParticipantStatus;
import org.sejongisc.backend.competition.service.CompetitionService;
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
class CompetitionControllerTest {

    @Mock
    private CompetitionService competitionService;

    @InjectMocks
    private CompetitionController competitionController;

    private MockMvc mockMvc;
    private final UUID competitionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(competitionController)
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
    @DisplayName("GET /current - 로그인 사용자를 넘기고 대회 정보와 내 참가 상태를 반환한다")
    void getCurrent() throws Exception {
        when(competitionService.getCurrent(userId)).thenReturn(response(ParticipantStatus.ACTIVE));

        mockMvc.perform(get("/api/competitions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competitionId").value(competitionId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.startDate").value("2026-11-16"))
                .andExpect(jsonPath("$.initialCash").value(10000000))
                .andExpect(jsonPath("$.myStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /current - 대회가 한 번도 없으면 404")
    void getCurrentNotFound() throws Exception {
        when(competitionService.getCurrent(userId)).thenThrow(new CustomException(ErrorCode.COMPETITION_NOT_FOUND));

        mockMvc.perform(get("/api/competitions/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMPETITION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /{competitionId} - 참가자가 아니면 myStatus는 null로 나간다")
    void getNotParticipant() throws Exception {
        when(competitionService.get(competitionId, userId)).thenReturn(response(null));

        mockMvc.perform(get("/api/competitions/{competitionId}", competitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competitionId").value(competitionId.toString()))
                .andExpect(jsonPath("$.myStatus").doesNotExist());
    }

    private CompetitionResponse response(ParticipantStatus myStatus) {
        return new CompetitionResponse(
                competitionId, "2026 2학기 수익률 대결", CompetitionStatus.ACTIVE,
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 11),
                new BigDecimal("10000000"), new BigDecimal("0.0001"), new BigDecimal("0.03"), myStatus);
    }
}
