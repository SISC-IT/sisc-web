package org.sejongisc.backend.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class SystemMethodLoggingAspect {

  @Around(
      "(within(org.sejongisc.backend..controller..*) || within(org.sejongisc.backend..service..*))"
          + " && !within(org.sejongisc.backend.common.logging..*)"
  )
  public Object logMethod(ProceedingJoinPoint joinPoint) throws Throwable {
    if (!log.isDebugEnabled()) {
      return joinPoint.proceed();
    }

    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    long startedAt = System.currentTimeMillis();

    log.debug("METHOD start {}", methodName);
    try {
      Object result = joinPoint.proceed();
      log.debug("METHOD end {} durationMs={}", methodName, System.currentTimeMillis() - startedAt);
      return result;
    } catch (Throwable throwable) {
      log.warn(
          "METHOD error {} durationMs={} exception={} message={}",
          methodName,
          System.currentTimeMillis() - startedAt,
          throwable.getClass().getSimpleName(),
          sanitizeExceptionMessage(throwable.getMessage())
      );
      throw throwable;
    }
  }

  private String sanitizeExceptionMessage(String message) {
    if (message == null) {
      return "";
    }
    String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
    if (normalized.length() <= 200) {
      return normalized;
    }
    return normalized.substring(0, 200) + "...";
  }
}
