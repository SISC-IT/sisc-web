package org.sejongisc.backend.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.common.annotation.OptimisticRetry;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
@Slf4j
@Order(-1) // @Transactional보다 먼저 실행되어야 함
public class OptimisticRetryAspect {

  @Around("@annotation(optimisticRetry)")
  public Object doRetry(ProceedingJoinPoint joinPoint, OptimisticRetry optimisticRetry) throws Throwable {
    // Retry 설정 생성
    RetryTemplate retryTemplate = createRetryTemplate(optimisticRetry);

    return retryTemplate.execute(context -> {
      if (context.getRetryCount() > 0) {
        log.warn("데이터 동시 수정 충돌 발생 ({}회차 재시도): {}", context.getRetryCount(), joinPoint.getSignature().getName());
      }

      try {
        return joinPoint.proceed();
      } catch (OptimisticLockingFailureException | CustomException e) {
        // 낙관적 락 예외: RetryTemplate이 잡아 재시도
        // 비즈니스 예외: 즉시 종료
        throw e;
      } catch (Throwable e) {
        // 그 외 미처리 예외: 로깅 후 종료
        log.error("재시도 로직 실행 중 미처리 예외 발생: ", e);
        throw e;
      }
    }, context -> {
      Throwable lastThrowable = context.getLastThrowable();

      // 비즈니스 예외라면 그대로 throw
      if (lastThrowable instanceof CustomException) {
        throw (CustomException) lastThrowable;
      }

      // RuntimeException인 경우에도 throw
      if (lastThrowable instanceof RuntimeException) {
        throw (RuntimeException) lastThrowable;
      }

      // 재시도 횟수 소진 후 최종 실패 시
      log.error("재시도 횟수 초과로 업데이트 최종 실패: {}", joinPoint.getSignature().getName());
      throw new CustomException(ErrorCode.CONCURRENT_UPDATE);
    });
  }

  /**
   * @OptimisticRetry 어노테이션 설정 값을 기반으로 RetryTemplate 생성
   */
  private RetryTemplate createRetryTemplate(OptimisticRetry optimisticRetry) {
    RetryTemplate template = new RetryTemplate();

    // 낙관적 락 예외에 대해서만 재시도
    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
      optimisticRetry.maxAttempts(),
      Map.of(OptimisticLockingFailureException.class, true)
    );

    // 재시도 사이의 대기 시간 설정
    FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
    backOffPolicy.setBackOffPeriod(optimisticRetry.backoff());

    template.setRetryPolicy(retryPolicy);
    template.setBackOffPolicy(backOffPolicy);
    return template;
  }
}
