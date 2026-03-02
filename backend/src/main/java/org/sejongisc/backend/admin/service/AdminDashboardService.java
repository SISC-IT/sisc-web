package org.sejongisc.backend.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.activity.entity.ActivityLog;
import org.sejongisc.backend.activity.entity.ActivityType;
import org.sejongisc.backend.activity.repository.ActivityLogRepository;
import org.sejongisc.backend.admin.dto.dashboard.BoardActivityResponse;
import org.sejongisc.backend.admin.dto.dashboard.SummaryResponse;
import org.sejongisc.backend.admin.dto.dashboard.VisitorTrendResponse;
import org.sejongisc.backend.common.sse.SseService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

  private final ActivityLogRepository activityLogRepository;
  private final SseService sseService;

  public static final String ADMIN_CHANNEL = "ADMIN_DASHBOARD";
  // 공통 시간 계산용 Record
  private record WeekRange(LocalDateTime start, LocalDateTime now, LocalDateTime lastStart, LocalDateTime lastEnd) {}

  // 이번 주 일요일부터 현재까지의 범위
  private WeekRange calculateWeekRange() {
    LocalDateTime startOfThisWeek = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        .atStartOfDay();
    LocalDateTime now = LocalDateTime.now();

    return new WeekRange(startOfThisWeek, now, startOfThisWeek.minusWeeks(1), now.minusWeeks(1));
  }

  // 금주 활동량 요약 (일~토 기준)
  @Transactional(readOnly = true)
  public SummaryResponse getWeeklyBoardSummary() {
    WeekRange range = calculateWeekRange();
    List<ActivityType> boardTypes = List.of(ActivityType.BOARD_POST, ActivityType.BOARD_COMMENT, ActivityType.BOARD_LIKE);

    long thisWeek = activityLogRepository.countActivitiesByTypeAndPeriod(boardTypes, range.start(), range.now());
    long lastWeek = activityLogRepository.countActivitiesByTypeAndPeriod(boardTypes, range.lastStart(), range.lastEnd());

    return new SummaryResponse(thisWeek, calculatePercentage(thisWeek, lastWeek));
  }

  // 금주 누적 방문자 요약 (일~토 기준 전주 대비)
  @Transactional(readOnly = true)
  public SummaryResponse getWeeklyVisitorSummary() {
    WeekRange range = calculateWeekRange();

    long thisWeekVisitors = activityLogRepository.countDailyUniqueVisitors(range.start(), range.now());
    long lastWeekVisitors = activityLogRepository.countDailyUniqueVisitors(range.lastStart(), range.lastEnd());

    return new SummaryResponse(thisWeekVisitors, calculatePercentage(thisWeekVisitors, lastWeekVisitors));
  }

  // N 일 방문자 추이 (차트용)
  @Transactional(readOnly = true)
  public List<VisitorTrendResponse> getVisitorTrend(int days) {
    LocalDateTime startDate = LocalDate.now().minusDays(days - 1).atStartOfDay();
    List<Object[]> results = activityLogRepository.getDailyVisitorTrendNative(startDate);

    return results.stream()
        .map(result -> new VisitorTrendResponse(
            ((Date) result[0]).toString(), // java.sql.Date -> String
            ((Number) result[1]).longValue()
        ))
        .collect(Collectors.toList());
  }

  // 게시판별 활동량 집계 (차트용)
  @Transactional(readOnly = true)
  public List<BoardActivityResponse> getBoardActivityStats(int days) {
    LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
    LocalDateTime end = LocalDateTime.now();

    List<Object[]> results = activityLogRepository.countActivityByBoard(start, end);

    return results.stream()
        .map(result -> new BoardActivityResponse(
            (String) result[0],
            ((Number) result[1]).longValue()
        ))
        .collect(Collectors.toList());
  }

  // 실시간 로그 스트림 구독
  public SseEmitter subscribeActivityStream() {
    SseEmitter emitter = sseService.subscribe(ADMIN_CHANNEL);
    try {
      // 연결 시 503 에러 방지를 위한 더미 이벤트 발송
      emitter.send(SseEmitter.event().name("CONNECT").data("Connected to Admin SSE Stream"));
    } catch (Exception e) {
      sseService.removeEmitter(ADMIN_CHANNEL, emitter);
    }
    return emitter;
  }

  // 최근 로그 20개 조회 (최초 렌더링용)
  @Transactional(readOnly = true)
  public Slice<ActivityLog> getRecentActivities(Pageable pageable) {
    return activityLogRepository.findAllByOrderByCreatedAtDesc(pageable);
  }

  // 증감률 계산 공통 메서드
  private double calculatePercentage(long current, long previous) {
    if (previous == 0) return current > 0 ? 100.0 : 0.0;
    double percentage = ((double) (current - previous) / previous) * 100;
    return Math.round(percentage * 100.0) / 100.0;
  }
}