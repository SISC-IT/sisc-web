package org.sejongisc.backend.auth.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.constraints.Email;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.sejongisc.backend.auth.config.EmailProperties;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.dao.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class EmailService {
  private final JavaMailSender mailSender;
  private final RedisTemplate<String, String> redisTemplate;
  private final SpringTemplateEngine templateEngine;
  private final UserRepository userRepository;
  private final EmailProperties emailProperties;

  // 메일 발신자
  @Value("${spring.mail.username}")
  private String from;

  // 메세지 만들기
  private MimeMessage createMessage(String email, String code) throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    message.setFrom(new InternetAddress(from));
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
    message.setSubject("세투연 이메일 인증 메일입니다.");

    Context context = new Context();
    context.setVariable("email", email);
    context.setVariable("code", code);

    String body = templateEngine.process("mail/verificationEmail", context);
    message.setText(body, "UTF-8", "html");

    return message;

  }

  // 메일 발송
  public void sendEmail(@Email String email) {

    // 이미 24시간 내 인증된 이메일인지 확인
    String verifiedKey = emailProperties.getKeyPrefix().getVerified() + email;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(verifiedKey))) {
      throw new CustomException(ErrorCode.EMAIL_ALREADY_VERIFIED);
    }

    // 이메일 형식 검증
    if (!EmailValidator.getInstance().isValid(email)) {
      throw new CustomException(ErrorCode.EMAIL_INVALID_EMAIL);
    }

    // 중복 이메일 검증
    if (userRepository.existsByEmail(email)) {
      throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
    }

    // 인증코드 생성
      String code = generateCode();

    // Redis에 인증 코드 저장 (유효시간: 3분)
    redisTemplate.opsForValue().set(emailProperties.getKeyPrefix().getVerify() + email, code, emailProperties.getCodeExpire());

    // 메일 발송
    try {
      MimeMessage message = createMessage(email, code);
      mailSender.send(message);
    } catch (MessagingException e) {
      log.error("메일전송이 실패하였습니다", e);
      throw new MailSendException("failed to send mail", e);
    }

  }

  // 코드확인
  public void verifyEmail(String email, String code) {
    String key = emailProperties.getKeyPrefix().getVerify()+ email;

    String storedCode = redisTemplate.opsForValue().get(key);
    if (storedCode == null) throw new CustomException(ErrorCode.EMAIL_CODE_NOT_FOUND);
    if (!storedCode.equals(code)) throw new CustomException(ErrorCode.EMAIL_CODE_MISMATCH);


    // 인증 성공 시 Redis에서 코드 삭제
    redisTemplate.delete(key);

    // 인증 완료 상태 저장 (24시간 유효)
    redisTemplate.opsForValue().set(
        emailProperties.getKeyPrefix().getVerified() + email,
        "true",
        emailProperties.getVerifiedExpire()
    );


  }

  // 이메일 인증 코드 생성
  private String generateCode() {
    String charset = emailProperties.getCode().getCharset();
    int len = emailProperties.getCode().getLength();

    SecureRandom rnd = new SecureRandom();
    StringBuilder sb = new StringBuilder(len);

    for (int i = 0; i < len; i++) {
      sb.append(charset.charAt(rnd.nextInt(charset.length())));
    }
    return sb.toString();
  }

  // 비밀번호 인증 관련 메서드
  public void sendResetEmail(String email) {
    if(!userRepository.existsByEmail(email)){
      throw new CustomException(ErrorCode.USER_NOT_FOUND);
    }

    String code = generateCode();
    redisTemplate.opsForValue().set("PASSWORD_RESET_EMAIL:" + email, code, emailProperties.getCodeExpire());

    try {
      MimeMessage message = createResetMessage(email, code);
      mailSender.send(message);
    } catch (MessagingException e) {
      throw new MailSendException("failed to send mail", e);
    }
  }

  public void verifyResetEmail(String email, String code) {
    String stored = redisTemplate.opsForValue().get("PASSWORD_RESET_EMAIL:" + email);
    if (stored == null) throw new CustomException(ErrorCode.EMAIL_CODE_NOT_FOUND);
    if(!stored.equals(code)) throw new CustomException(ErrorCode.EMAIL_CODE_MISMATCH);
    redisTemplate.delete("PASSWORD_RESET_EMAIL:" + email);
  }

  private MimeMessage createResetMessage(String email, String code) throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    message.setFrom(new InternetAddress(from));
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
    message.setSubject("비밀번호 재설정 인증코드");

    Context context = new Context();
    context.setVariable("email", email);
    context.setVariable("code", code);

    String body = templateEngine.process("mail/resetEmail", context);
    message.setText(body, "UTF-8", "html");
    return message;
  }



}
