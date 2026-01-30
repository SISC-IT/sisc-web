package org.sejongisc.backend.auth.service;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.auth.config.EmailProperties;
import org.sejongisc.backend.user.repository.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

  @Mock JavaMailSender mailSender;
  @Mock
  RedisTemplate<String, String> redisTemplate;
  @Mock
  ValueOperations<String, String> valueOps;
  @Mock
  SpringTemplateEngine templateEngine;
  @Mock
  UserRepository userRepository;
  @Mock EmailProperties props;



  @InjectMocks
  EmailService emailService;

  @BeforeEach
  void setUp() {
    // value 객체 필드 세팅
    ReflectionTestUtils.setField(emailService, "from", "noreply@test.com");
    // EmailProperties 더미 값 세팅
    var keyPrefix = new EmailProperties.KeyPrefix();
    keyPrefix.setVerify("verify:");
    keyPrefix.setVerified("verified:");
    var codeConf = new EmailProperties.Code();
    codeConf.setCharset("0123456789");
    codeConf.setLength(6);

    given(props.getKeyPrefix()).willReturn(keyPrefix);
    given(props.getCode()).willReturn(codeConf);
    given(props.getCodeExpire()).willReturn(Duration.ofMinutes(3));
//    given(props.getVerifiedExpire()).willReturn(Duration.ofHours(24));
    given(redisTemplate.opsForValue()).willReturn(valueOps);
  }

  @Test
  void sendEmail_success_savesCodeAndSendsMail() throws Exception {
    // Given
    var email = "user@example.com";
    given(redisTemplate.hasKey("verified:" + email)).willReturn(false);
    given(userRepository.existsByEmail(email)).willReturn(false);
    var mime = Mockito.mock(MimeMessage.class);
    given(mailSender.createMimeMessage()).willReturn(mime);
    given(templateEngine.process(any(String.class), any(org.thymeleaf.context.IContext.class)))
        .willReturn("<html>ok</html>");

    // When
    emailService.sendEmail(email);

    // Then
    then(valueOps).should().set(startsWith("verify:"), anyString(), eq(Duration.ofMinutes(3)));
    then(mailSender).should().send(mime);
  }

  @Test
  void verifyEmail() {
  }
}