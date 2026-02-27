package org.sejongisc.backend.activity.listener;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.activity.entity.ActivityLog;
import org.sejongisc.backend.activity.event.ActivityEvent;
import org.sejongisc.backend.activity.repository.ActivityLogRepository;
import org.sejongisc.backend.common.sse.SseService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ActivityEventListener {

    private final ActivityLogRepository activityLogRepository;
    private final SseService sseService; // 실시간 전송용 서비스

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleActivityEvent(ActivityEvent event) {
        // DB 저장 (마이페이지 및 관리자 통계용)
        ActivityLog log = activityLogRepository.save(ActivityLog.builder()
                .userId(event.userId())
                .username(event.username())
                .type(event.type())
                .message(event.message())
                .targetId(event.targetId())
                .boardName(event.boardName())
                .build());

        // 관리자 채널에 실시간 SSE 전송 (메인 대시보드 피드용)
        sseService.send("ADMIN_DASHBOARD", "newLog", log);
    }
}