package org.unibl.etf.pisio.notificationservice.service;

import java.time.Instant;
import org.unibl.etf.pisio.notificationservice.domain.Activity;

public record BoardChanged(Long boardId, String type, String actorId, Instant occurredAt) {

    public static BoardChanged of(Activity activity) {
        return new BoardChanged(activity.boardId(), activity.type(), activity.actorId(), activity.occurredAt());
    }
}
