package org.unibl.etf.pisio.notificationservice.stream;

import java.time.Instant;
import org.unibl.etf.pisio.notificationservice.domain.Activity;

public record ActivityNotification(
        Long id,
        Long boardId,
        String type,
        String message,
        String actorId,
        Instant occurredAt
) {

    public static ActivityNotification of(Activity activity) {
        return new ActivityNotification(
                activity.id(),
                activity.boardId(),
                activity.type(),
                activity.recipientMessage(),
                activity.actorId(),
                activity.occurredAt()
        );
    }
}
