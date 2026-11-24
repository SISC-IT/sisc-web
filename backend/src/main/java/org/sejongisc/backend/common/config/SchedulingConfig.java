package org.sejongisc.backend.common.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

/**
 * 반환 타입을 TaskScheduler로 둔다. 구현 타입(ThreadPoolTaskScheduler)으로 선언하면
 * 부트가 이를 Executor 빈으로 보고 @Async용 기본 실행기를 만들지 않는다.
 */
@Configuration
public class SchedulingConfig {

  // 스케줄러 빈을 직접 만들면 부트가 기본 스케줄러를 만들지 않는다.
  // 이름을 지정하지 않은 @Scheduled가 계속 쓰도록 같은 이름으로 선언한다.
  @Bean
  public TaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
    return builder.build();
  }

  // 종가 수집은 종목 수만큼 키움을 호출해 몇 분씩 걸린다. 다른 스케줄 작업을 막지 않도록 분리한다.
  @Bean
  public TaskScheduler closePriceScheduler(ThreadPoolTaskSchedulerBuilder builder) {
    return builder.threadNamePrefix("close-price-").build();
  }
}
