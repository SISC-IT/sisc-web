package org.sejongisc.backend.common.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SseService {
    // 채널 ID(String)별로 구독자 리스트 관리
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String channelId) {
        // timeout 0 = 무제한 (핑으로 유지)
        SseEmitter emitter = new SseEmitter(0L); 

        //
        this.emitters.computeIfAbsent(channelId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // 연결 종료 시 정리
        emitter.onCompletion(() -> removeEmitter(channelId, emitter));
        emitter.onTimeout(() -> removeEmitter(channelId, emitter));
        emitter.onError((ex) -> removeEmitter(channelId, emitter));

        return emitter;
    }

    // 특정 채널에 이벤트 전송
    public void send(String channelId, String eventName, Object data) {
        List<SseEmitter> channelEmitters = emitters.get(channelId);
        if (channelEmitters == null) return;

        for (SseEmitter emitter : channelEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                removeEmitter(channelId, emitter);
            }
        }
    }

    // 해당 채널에 구독자가 있는지 확인 (스케줄러 정지 판단용)
    public boolean hasSubscribers(String channelId) {
        List<SseEmitter> list = emitters.get(channelId);
        return list != null && !list.isEmpty();
    }

    public void removeEmitter(String channelId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(channelId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(channelId);
            }
        }
    }

    public void complete(String channelId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.remove(channelId);
        if (list == null) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("Failed to complete SSE emitter: channelId={}", channelId, e);
            }
        }
    }
}
