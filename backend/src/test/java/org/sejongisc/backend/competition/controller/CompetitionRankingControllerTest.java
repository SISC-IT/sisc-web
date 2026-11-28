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
import org.sejongisc.backend.competition.dto.CompetitionRankingEntry;
import org.sejongisc.backend.competition.dto.CompetitionRankingResponse;
import org.sejongisc.backend.competition.dto.CompetitionTeamRankingEntry;
import org.sejongisc.backend.competition.dto.CompetitionTeamRankingResponse;
import org.sejongisc.backend.competition.service.CompetitionRankingService;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CompetitionRankingControllerTest {

    @Mock
    private CompetitionRankingService rankingService;

    @InjectMocks
    private CompetitionRankingController rankingController;

    private MockMvc mockMvc;
    private final UUID competitionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(rankingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /rankings - 스냅샷 날짜와 개인 순위를 반환한다")
    void returnsRankings() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rankingService.getRankings(competitionId, false)).thenReturn(new CompetitionRankingResponse(
                LocalDate.of(2026, 11, 20),
                List.of(
                        new CompetitionRankingEntry(1, 1, userId, "이세종", "퀀트팀",
                                new BigDecimal("10600000.0000"), new BigDecimal("0.060000")),
                        new CompetitionRankingEntry(2, null, UUID.randomUUID(), "김세종", "매크로팀",
                                new BigDecimal("10000000.0000"), new BigDecimal("0.000000")))));

        mockMvc.perform(get("/api/competitions/{competitionId}/rankings", competitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotDate").value("2026-11-20"))
                .andExpect(jsonPath("$.rankings[0].rank").value(1))
                .andExpect(jsonPath("$.rankings[0].rankChange").value(1))
                .andExpect(jsonPath("$.rankings[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.rankings[0].userName").value("이세종"))
                .andExpect(jsonPath("$.rankings[0].teamName").value("퀀트팀"))
                .andExpect(jsonPath("$.rankings[0].totalValue").value(10600000.0))
                .andExpect(jsonPath("$.rankings[0].profitRate").value(0.06))
                .andExpect(jsonPath("$.rankings[1].rankChange").doesNotExist());
    }

    @Test
    @DisplayName("GET /rankings - 없는 대회는 404를 반환한다")
    void returns404ForUnknownCompetition() throws Exception {
        when(rankingService.getRankings(competitionId, false))
                .thenThrow(new CustomException(ErrorCode.COMPETITION_NOT_FOUND));

        mockMvc.perform(get("/api/competitions/{competitionId}/rankings", competitionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMPETITION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /rankings/teams - 스냅샷 날짜와 팀 순위를 반환한다")
    void returnsTeamRankings() throws Exception {
        when(rankingService.getTeamRankings(competitionId, false)).thenReturn(new CompetitionTeamRankingResponse(
                LocalDate.of(2026, 11, 20),
                List.of(new CompetitionTeamRankingEntry(1, "퀀트팀", new BigDecimal("1.50"), 3, 2))));

        mockMvc.perform(get("/api/competitions/{competitionId}/rankings/teams", competitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotDate").value("2026-11-20"))
                .andExpect(jsonPath("$.rankings[0].rank").value(1))
                .andExpect(jsonPath("$.rankings[0].teamName").value("퀀트팀"))
                .andExpect(jsonPath("$.rankings[0].averageRank").value(1.5))
                .andExpect(jsonPath("$.rankings[0].rankSum").value(3))
                .andExpect(jsonPath("$.rankings[0].memberCount").value(2));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /rankings - 결과 공개 전이면 403을 반환한다")
    void returns403BeforeResultIsPublic() throws Exception {
        when(rankingService.getRankings(competitionId, false))
                .thenThrow(new CustomException(ErrorCode.COMPETITION_RESULT_NOT_PUBLIC));

        mockMvc.perform(get("/api/competitions/{competitionId}/rankings", competitionId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMPETITION_RESULT_NOT_PUBLIC"));

        when(rankingService.getTeamRankings(competitionId, false))
                .thenThrow(new CustomException(ErrorCode.COMPETITION_RESULT_NOT_PUBLIC));

        mockMvc.perform(get("/api/competitions/{competitionId}/rankings/teams", competitionId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /rankings - 부회장 이상은 관리자로 조회한다")
    void adminRequestsAsAdmin() throws Exception {
        CustomUserDetails admin = new CustomUserDetails(
                User.builder().userId(UUID.randomUUID()).role(Role.VICE_PRESIDENT).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
        when(rankingService.getRankings(competitionId, true))
                .thenReturn(new CompetitionRankingResponse(null, List.of()));
        when(rankingService.getTeamRankings(competitionId, true))
                .thenReturn(new CompetitionTeamRankingResponse(null, List.of()));

        mockMvc.perform(get("/api/competitions/{competitionId}/rankings", competitionId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/competitions/{competitionId}/rankings/teams", competitionId))
                .andExpect(status().isOk());
    }
}
