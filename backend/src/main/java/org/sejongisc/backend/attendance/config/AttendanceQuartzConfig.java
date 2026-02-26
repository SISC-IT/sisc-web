package org.sejongisc.backend.attendance.config;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.sejongisc.backend.attendance.service.AttendanceRoundStateJob;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
public class AttendanceQuartzConfig {

  public static final String ROUND_STATE_CRON = "0 * * * * ?";

  @Bean
  public JobDetail attendanceRoundStateJobDetail() {
    return JobBuilder.newJob(AttendanceRoundStateJob.class)
        .withIdentity("attendanceRoundStateJob")
        .storeDurably()
        .build();
  }

  @Bean
  public Trigger attendanceRoundStateTrigger(JobDetail attendanceRoundStateJobDetail) {
    return TriggerBuilder.newTrigger()
        .forJob(attendanceRoundStateJobDetail)
        .withIdentity("attendanceRoundStateTrigger")
        .withSchedule(CronScheduleBuilder.cronSchedule(ROUND_STATE_CRON))
        .build();
  }

  @Bean
  public SpringBeanJobFactory springBeanJobFactory(ApplicationContext applicationContext) {
    return new AutowiringSpringBeanJobFactory(applicationContext.getAutowireCapableBeanFactory());
  }

  @Bean
  public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer(SpringBeanJobFactory jobFactory) {
    return factory -> factory.setJobFactory(jobFactory);
  }

  private static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {
    private final AutowireCapableBeanFactory beanFactory;

    private AutowiringSpringBeanJobFactory(AutowireCapableBeanFactory beanFactory) {
      this.beanFactory = beanFactory;
    }

    @Override
    protected Object createJobInstance(org.quartz.spi.TriggerFiredBundle bundle) {
      Class<?> jobClass = bundle.getJobDetail().getJobClass();
      return beanFactory.createBean(jobClass);
    }
  }
}
