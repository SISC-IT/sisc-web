package org.sejongisc.backend.common.exception.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

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

    @Test
    @DisplayName("처리하지 않은 예외는 500과 공통 메시지만 반환하고 예외 메시지는 응답에 담지 않는다")
    void unhandledExceptionHidesExceptionMessage() throws Exception {
        mockMvc.perform(get("/test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버에 문제가 발생했습니다."));
    }

    @Test
    @DisplayName("권한이 없으면 403을 반환한다")
    void accessDeniedReturnsForbidden() throws Exception {
        mockMvc.perform(get("/test/admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("없는 주소는 404를 반환한다")
    void unknownPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/test/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405와 지원하는 메서드(Allow 헤더)를 반환한다")
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/test/admin"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("필수 헤더가 없거나 형식이 틀리면 400을 반환한다")
    void badHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/test/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
        mockMvc.perform(post("/test/header").header("Idempotency-Key", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("필수 파라미터가 없거나 타입이 틀리면 400을 반환하고 입력값을 로그에 남기지 않는다")
    void badParameterReturnsBadRequest(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
        mockMvc.perform(get("/test/param").param("page", "abc-input"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));

        assertThat(output).doesNotContain("abc-input");
    }

    @Test
    @DisplayName("깨진 JSON이나 없는 enum 값은 400을 반환하고 본문을 로그에 남기지 않는다")
    void unreadableBodyReturnsBadRequest(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"secret-pw\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
        mockMvc.perform(post("/test/enum").contentType(MediaType.APPLICATION_JSON).content("{\"side\": \"buy-secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));

        assertThat(output).doesNotContain("secret-pw").doesNotContain("buy-secret");
    }

    @Test
    @DisplayName("서비스 메서드 검증(@Validated)에 실패하면 400을 반환하고 로그에는 입력값 대신 필드 경로를 남긴다")
    void methodValidationFailureReturnsBadRequest(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/test/method-validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));

        assertThat(output).contains("EmailSender.send.email")
                .doesNotContain("abc-email").doesNotContain("secret-key");
    }

    @Test
    @DisplayName("엔티티 검증에 실패하면 서버 버그로 보고 500을 반환하고, 로그에는 입력값 대신 필드 경로를 남긴다")
    void entityValidationFailureReturnsServerError(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/test/entity-validation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"));

        assertThat(output).contains("Member.email").doesNotContain("abc-email");
    }

    @Test
    @DisplayName("업로드 요청에 파일이 없으면 400을 반환한다")
    void missingPartReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/test/part"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("업로드 주소에 multipart가 아닌 요청을 보내면 400을 반환한다")
    void nonMultipartRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/test/part").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("업로드 처리 자체가 실패하면(임시 파일 저장 실패 등) 서버 문제로 보고 500을 반환한다")
    void multipartParseFailureReturnsServerError() throws Exception {
        mockMvc.perform(post("/test/multipart-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    @DisplayName("업로드 크기 제한을 넘으면 413을 반환한다")
    void maxUploadSizeExceededReturns413() throws Exception {
        mockMvc.perform(post("/test/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("UPLOAD_SIZE_EXCEEDED"));
    }

    @Test
    @DisplayName("PATCH 요청의 Content-Type이 틀리면 415와 Accept-Patch 헤더를 반환한다")
    void unsupportedMediaTypeOnPatchReturnsAcceptPatch() throws Exception {
        mockMvc.perform(patch("/test/json-only").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Accept-Patch", "application/json"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415와 지원하는 형식(Accept 헤더)을 반환한다")
    void unsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post("/test/json-only").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Accept", "application/json"))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    enum Side {
        BUY, SELL
    }

    record SideRequest(Side side) {
    }

    record CountRequest(Integer count) {
    }

    record NameRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
    }

    record Member(@Email String email) {
    }

    static class EmailSender {
        void send(@Email String email, Map<String, @Email String> aliases) {
        }
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

        private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        @PostMapping("/test/method-validation")
        String methodValidation() throws NoSuchMethodException {
            Method send = EmailSender.class.getDeclaredMethod("send", String.class, Map.class);
            throw new ConstraintViolationException(validator.forExecutables().validateParameters(
                    new EmailSender(), send, new Object[] {"abc-email", Map.of("secret-key", "abc-email")}));
        }

        @PostMapping("/test/entity-validation")
        String entityValidation() {
            throw new ConstraintViolationException(validator.validate(new Member("abc-email")));
        }

        @PostMapping("/test/multipart-failure")
        String multipartFailure() {
            throw new MultipartException("Failed to parse multipart servlet request", new IOException("disk full"));
        }

        @PostMapping("/test/too-large")
        String tooLarge() {
            throw new MaxUploadSizeExceededException(100L * 1024 * 1024);
        }

        @PostMapping("/test/part")
        String part(@RequestPart("file") MultipartFile file) {
            return "ok";
        }

        @PostMapping(value = "/test/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        String jsonOnly(@RequestBody NameRequest request) {
            return "ok";
        }

        @PatchMapping(value = "/test/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        String jsonOnlyPatch(@RequestBody NameRequest request) {
            return "ok";
        }

        @GetMapping("/test/admin")
        String admin() {
            throw new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false));
        }

        @PostMapping("/test/header")
        String header(@RequestHeader("Idempotency-Key") UUID key) {
            return "ok";
        }

        @GetMapping("/test/param")
        String param(@RequestParam int page) {
            return "ok";
        }

        @PostMapping("/test/enum")
        String side(@RequestBody SideRequest request) {
            return "ok";
        }

        @GetMapping("/test/error")
        String error() {
            throw new IllegalStateException("Key (email)=(member@example.com) already exists");
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
