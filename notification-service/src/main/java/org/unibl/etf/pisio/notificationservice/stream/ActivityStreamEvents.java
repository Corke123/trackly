package org.unibl.etf.pisio.notificationservice.stream;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.service.BoardChanged;

final class ActivityStreamEvents {

    static final String NOTIFICATION = "activity";

    static final String BOARD_CHANGED = "board-changed";

    private ActivityStreamEvents() {
    }

    static SseEmitter.SseEventBuilder notification(Activity activity) {
        return SseEmitter.event()
                .id(String.valueOf(activity.id()))
                .name(NOTIFICATION)
                .data(ActivityNotification.of(activity), MediaType.APPLICATION_JSON);
    }

    static SseEmitter.SseEventBuilder boardChanged(BoardChanged change) {
        return SseEmitter.event()
                .name(BOARD_CHANGED)
                .data(change, MediaType.APPLICATION_JSON);
    }
}
