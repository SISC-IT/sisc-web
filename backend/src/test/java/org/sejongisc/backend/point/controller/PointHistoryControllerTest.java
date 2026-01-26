//package org.sejongisc.backend.point.controller;
//import org.junit.jupiter.api.Test;
//import org.sejongisc.backend.common.auth.config.SecurityConfig;
//import org.sejongisc.backend.common.exception.CustomException;
//import org.sejongisc.backend.common.exception.ErrorCode;
//import org.sejongisc.backend.point.dto.PointHistoryResponse;
//import org.sejongisc.backend.point.service.PointHistoryService;
//import org.sejongisc.backend.user.entity.User;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.Import;
//import org.springframework.data.domain.AuditorAware;
//import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.util.List;
//import java.util.UUID;
//
//import static org.mockito.Mockito.when;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//@WebMvcTest(PointHistoryController.class)
//@Import(SecurityConfig.class)
//@AutoConfigureMockMvc(addFilters = false) // 시큐리티 필터 비활성화
//class PointHistoryControllerTest {
//
//  @Autowired
//  private MockMvc mockMvc;
//
//  // JPA Auditing을 위한 MockBean
//  @MockBean
//  JpaMetamodelMappingContext jpaMetamodelMappingContext;
//
//  @MockBean
//  AuditorAware<String> auditorAware;
//
//  @MockBean
//  private PointHistoryService pointHistoryService;
//
//  @Test
//  void 리더보드_성공_200리턴() throws Exception {
//    User u1 = User.builder()
//        .userId(UUID.randomUUID())
//        .name("a")
//        .email("a@test.com")
//        .point(300)
//        .build();
//    User u2 = User.builder()
//        .userId(UUID.randomUUID())
//        .name("b")
//        .email("b@test.com")
//        .point(100)
//        .build();
//
//    PointHistoryResponse resp = PointHistoryResponse.builder()
//        .leaderboardUsers(List.of(u1, u2))
//        .build();
//
//    when(pointHistoryService.getPointLeaderboard(7)).thenReturn(resp);
//
//    mockMvc.perform(get("/api/points/leaderboard")
//            .param("period", "7"))
//        .andExpect(status().isOk())
//        .andExpect(jsonPath("$.leaderboardUsers[0].point").value(300))
//        .andExpect(jsonPath("$.leaderboardUsers[1].point").value(100));
//  }
//
//  @Test
//  void 리더보드_잘못된_period시_예외() throws Exception {
//    when(pointHistoryService.getPointLeaderboard(5))
//        .thenThrow(new CustomException(ErrorCode.INVALID_PERIOD));
//
//    mockMvc.perform(get("/api/points/leaderboard")
//            .param("period", "5"))
//        .andExpect(status().is4xxClientError());
//  }
//}