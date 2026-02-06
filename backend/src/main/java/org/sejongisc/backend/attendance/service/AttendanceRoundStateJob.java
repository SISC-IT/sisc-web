package org.sejongisc.backend.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sejongisc.backend.attendance.service.AttendanceRoundService;
import org.springframework.stereotype.Component;

/**
 * 라운드 상태 자동 전환(UPCOMING->ACTIVE, ACTIVE->CLOSED)
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class AttendanceRoundStateJob implements Job {

  private final AttendanceRoundService attendanceRoundService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    try {
      attendanceRoundService.runRoundStatusMaintenance();
    } catch (Exception e) {
      log.error("AttendanceRoundStateJob failed", e);
    }
  }
}

