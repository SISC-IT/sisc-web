package org.sejongisc.backend.common.auth.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.common.auth.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "메일 API",
    description = "메일 관련 API 제공"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/email")
public class EmailController {
  private final EmailService emailService;

  @Operation(
      summary = "메일전송",
      description = """
          ## 인증(JWT): **불필요**
          
          ## 요청 파라미터 (String)
          - **`email`**: 회원 이메일
          
          ## 반환값 (ResponseEntity<String>)
          - **`message`**: 전송완료 메세지

          ## 에러코드
          - **`EMAIL_INVALID_EMAIL`**: 유효하지 않은 이메일입니다
          - **`DUPLICATE_EMAIL`**: 이미 존재하는 이메일입니다
          - **`EMAIL_ALREADY_VERIFIED`**: 24시간 내에 인증된 이메일입니다
          """
  )
  @PostMapping("/send")
  public ResponseEntity<String> sendEmail(@RequestParam String email) {
    emailService.sendEmail(email);
    return ResponseEntity.ok("메일 전송을 요청하였습니다.");
  }


  @Operation(
      summary = "이메일 인증",
      description = """
          ## 인증(JWT): **불필요**
          
          ## 요청 파라미터 (String)
          - **`email`**: 회원 이메일
          - **`code`**: 이메일 인증 코드
          
          ## 반환값 (ResponseEntity<String>)
          - **`message`**: 인증 완료 메시지

          ## 에러코드
          - **`EMAIL_CODE_MISMATCH`**: 이메일 인증 코드가 일치하지 않습니다
          - **`EMAIL_CODE_NOT_FOUND`**: 이메일 인증 코드를 찾을 수 없습니다
          """
  )
  @PostMapping("/verify")
  public ResponseEntity<String> verifyEmail(@RequestParam String email, @RequestParam String code) {
    emailService.verifyEmail(email, code);
    return ResponseEntity.ok("이메일 인증이 완료되었습니다.");
  }



}
