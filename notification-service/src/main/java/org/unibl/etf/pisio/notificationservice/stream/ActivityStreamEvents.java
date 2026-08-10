package org.unibl.etf.pisio.notificationservice.stream;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.unibl.etf.pisio.notificationservice.domain.Activity;

final class ActivityStreamEvents {

    static final String NOTIFICATION = "activity";

    private ActivityStreamEvents() {
    }

    static SseEmitter.SseEventBuilder notification(Activity activity) {
        return SseEmitter.event()
                .id(String.valueOf(activity.id()))
                .name(NOTIFICATION)
                .data(ActivityNotification.of(activity), MediaType.APPLICATION_JSON);
    }
}
