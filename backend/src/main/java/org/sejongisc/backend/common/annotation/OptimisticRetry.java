package org.sejongisc.backend.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OptimisticRetry {
  int maxAttempts() default 3; // 최대 재시도 횟수
  long backoff() default 100L; // 재시도 사이 지연 시간 (ms)
}
