package org.sejongisc.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.betting.service.BettingScheduler;
import org.sejongisc.backend.competition.service.DailyClosePriceScheduler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulingConfigTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          TaskExecutionAutoConfiguration.class, TaskSchedulingAutoConfiguration.class))
      .withUserConfiguration(SchedulingConfig.class);

  @Test
  @DisplayName("종가 수집 전용 스케줄러와 기본 스케줄러가 따로 만들어진다")
  void registersSeparateSchedulers() {
    contextRunner.run(context -> {
      ThreadPoolTaskScheduler closePrice =
          context.getBean("closePriceScheduler", ThreadPoolTaskScheduler.class);
      ThreadPoolTaskScheduler fallback =
          context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);

      assertThat(closePrice).isNotSameAs(fallback);
      assertThat(closePrice.getThreadNamePrefix()).isEqualTo("close-price-");
      assertThat(fallback.getThreadNamePrefix()).isEqualTo("scheduling-");
    });
  }

  @Test
  @DisplayName("스케줄러 빈을 추가해도 @Async용 기본 실행기는 그대로 있다")
  void keepsDefaultAsyncExecutor() {
    contextRunner.run(context ->
        assertThat(context.getBean("applicationTaskExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class));
  }

  @Test
  @DisplayName("종가 수집만 전용 스케줄러를 쓴다")
  void onlyClosePriceCollectionUsesDedicatedScheduler() throws NoSuchMethodException {
    Scheduled closePrice = DailyClosePriceScheduler.class
        .getMethod("collectDomesticClosePrices").getAnnotation(Scheduled.class);
    Scheduled betting = BettingScheduler.class
        .getMethod("openScheduler").getAnnotation(Scheduled.class);

    assertThat(closePrice.scheduler()).isEqualTo("closePriceScheduler");
    assertThat(betting.scheduler()).isEmpty();
  }
}
