package org.sejongisc.backend.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.user.dto.SignupRequest;
import org.sejongisc.backend.user.dto.SignupResponse;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    UserService userService;

    @InjectMocks
    UserController userController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                // Security ArgumentResolver 수동 등록
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /user/signup - 201 Created & 응답 DTO 반환")
    void signup_success() throws Exception {
        SignupRequest req = SignupRequest.builder()
                .name("홍길동")
                .email("hong@example.com")
                .password("Password123!")
                .role(Role.TEAM_MEMBER)
                .phoneNumber("01012345678")
                .build();

        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        User entity = User.builder()
                .userId(userId)
                .name("홍길동")
                .email("hong@example.com")
                .passwordHash("ENCODED")
                .role(Role.TEAM_MEMBER)
                .phoneNumber("01012345678")
                .build();
        entity.setCreatedDate(now);
        entity.setUpdatedDate(now);

        SignupResponse resp = SignupResponse.from(entity);

        when(userService.signUp(any(SignupRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId", is(userId.toString())))
                .andExpect(jsonPath("$.name", is("홍길동")))
                .andExpect(jsonPath("$.email", is("hong@example.com")))
                .andExpect(jsonPath("$.role", is("TEAM_MEMBER")));
    }

    @Test
    @DisplayName("POST /user/signup - 요청 검증 실패 시 400")
    void signup_validation_fail() throws Exception {
        String invalidJson = """
            {
              "email":"hong@example.com",
              "password":"Password123!",
              "role":"TEAM_MEMBER",
              "phoneNumber":"01012345678"
            }
            """;

        mockMvc.perform(post("/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /user/details - 로그인 사용자 정보 반환")
    void getUserDetails_success() throws Exception {
        UUID userId = UUID.randomUUID();
        User userEntity = User.builder()
                .userId(userId)
                .name("홍길동")
                .email("hong@example.com")
                .passwordHash("ENCODED")
                .role(Role.TEAM_MEMBER)
                .phoneNumber("01012345678")
                .point(100)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(userEntity);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        // SecurityContext 직접 주입
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/user/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("hong@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.point").value(100))
                .andExpect(jsonPath("$.role").value("TEAM_MEMBER"));
    }
}
