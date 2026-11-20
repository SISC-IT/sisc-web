package org.sejongisc.backend.common.exception.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("@Valid @RequestBody 검증에 실패하면 400과 필드 오류 메시지를 반환한다")
    void requestBodyValidationFailureReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.message").value("이름은 필수입니다."));
    }

    @Test
    @DisplayName("@Valid @ModelAttribute 검증에 실패해도 400을 반환한다")
    void modelAttributeValidationFailureReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/test/model").param("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.message").value("이름은 필수입니다."));
    }

    @Test
    @DisplayName("검증 실패 로그에 사용자가 입력한 값을 남기지 않는다")
    void validationFailureLogDoesNotContainRejectedValue(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/test/secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"short-pw\"}"))
                .andExpect(status().isBadRequest());

        assertThat(output).doesNotContain("short-pw");
        assertThat(output).contains("password");
    }

    @Test
    @DisplayName("타입 변환 실패는 입력값과 내부 타입 정보 대신 공통 메시지를 반환한다")
    void bindingFailureReturnsCommonMessage() throws Exception {
        mockMvc.perform(get("/test/count").param("count", "abc-input"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
    }

    record CountRequest(Integer count) {
    }

    record NameRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
    }

    record SecretRequest(@Size(min = 20, message = "비밀번호가 너무 짧습니다.") String password) {
    }

    @RestController
    static class ValidationController {

        @PostMapping("/test/body")
        String body(@Valid @RequestBody NameRequest request) {
            return request.name();
        }

        @PostMapping("/test/secret")
        String secret(@Valid @RequestBody SecretRequest request) {
            return "ok";
        }

        @GetMapping("/test/count")
        String count(@Valid @ModelAttribute CountRequest request) {
            return "ok";
        }

        @GetMapping("/test/model")
        String model(@Valid @ModelAttribute NameRequest request) {
            return request.name();
        }
    }
}
