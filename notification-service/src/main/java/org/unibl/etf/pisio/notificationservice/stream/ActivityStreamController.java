package org.unibl.etf.pisio.notificationservice.stream;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;

@RestController
public class ActivityStreamController {

    private static final Logger log = LoggerFactory.getLogger(ActivityStreamController.class);

    private final ActivityRepository activities;
    private final ActivityStreamRegistry registry;
    private final ActivityStreamProperties properties;

    public ActivityStreamController(ActivityRepository activities, ActivityStreamRegistry registry,
                                    ActivityStreamProperties properties) {
        this.activities = activities;
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping(path = "/activity/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt,
                             @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
                             @RequestParam(name = "lastEventId", required = false) String lastEventIdParam) {
        String recipientId = jwt.getSubject();
        SseEmitter emitter = new SseEmitter(properties.timeout().toMillis());

        registry.register(recipientId, emitter);
        open(emitter, recipientId);
        replayMissed(emitter, recipientId, lastEventId == null ? lastEventIdParam : lastEventId);

        return emitter;
    }

    private void open(SseEmitter emitter, String recipientId) {
        registry.sendTo(recipientId, emitter, SseEmitter.event().comment("connected"));
    }

    private void replayMissed(SseEmitter emitter, String recipientId, String lastEventId) {
        Long lastSeen = parseId(lastEventId);
        if (lastSeen == null) {
            return;
        }

        List<Activity> missed = activities.findByRecipientIdAndIdGreaterThanOrderByIdAsc(
                recipientId, lastSeen, Limit.of(properties.replayLimit()));

        for (Activity activity : missed) {
            if (!registry.sendTo(recipientId, emitter, ActivityStreamEvents.notification(activity))) {
                return;
            }
        }

        if (!missed.isEmpty()) {
            log.debug("Replayed {} missed activities to {}", missed.size(), recipientId);
        }
    }

    private Long parseId(String lastEventId) {
        try {
            return lastEventId == null || lastEventId.isBlank() ? null : Long.valueOf(lastEventId.trim());
        } catch (NumberFormatException _) {
            log.debug("Ignoring unparseable Last-Event-ID '{}'", lastEventId);
            return null;
        }
    }
}
