package org.unibl.etf.pisio.notificationservice.stream;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ActivityStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActivityStreamRegistry.class);

    private final Map<String, Set<SseEmitter>> listeners = new ConcurrentHashMap<>();

    public void register(String recipientId, SseEmitter emitter) {
        listeners.computeIfAbsent(recipientId, _ -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> remove(recipientId, emitter));
        emitter.onTimeout(() -> remove(recipientId, emitter));
        emitter.onError(_ -> remove(recipientId, emitter));

        log.debug("Registered activity stream for {}", recipientId);
    }

    public void send(String recipientId, SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : emittersFor(recipientId)) {
            sendTo(recipientId, emitter, event);
        }
    }

    public boolean sendTo(String recipientId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping closed activity stream for {}", recipientId);
            remove(recipientId, emitter);
            emitter.completeWithError(e);
            return false;
        }
    }

    public Collection<SseEmitter> emittersFor(String recipientId) {
        return List.copyOf(listeners.getOrDefault(recipientId, Set.of()));
    }

    @Scheduled(fixedDelayString = "${trackly.activity-stream.heartbeat-delay-ms:25000}")
    public void heartbeat() {
        listeners.keySet().forEach(recipientId -> send(recipientId, SseEmitter.event().comment("ping")));
    }

    private void remove(String recipientId, SseEmitter emitter) {
        listeners.computeIfPresent(recipientId, (_, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
