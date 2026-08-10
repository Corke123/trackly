package org.unibl.etf.pisio.notificationservice.stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.service.ActivityRecorded;

@Component
public class ActivityBroadcaster {

    private final ActivityStreamRegistry registry;

    public ActivityBroadcaster(ActivityStreamRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityRecorded(ActivityRecorded recorded) {
        Activity activity = recorded.activity();
        registry.send(activity.recipientId(), ActivityStreamEvents.notification(activity));
    }
}
