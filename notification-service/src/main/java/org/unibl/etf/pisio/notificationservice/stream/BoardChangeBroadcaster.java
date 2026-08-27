package org.unibl.etf.pisio.notificationservice.stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.unibl.etf.pisio.notificationservice.service.BoardChanged;

@Component
public class BoardChangeBroadcaster {

    private final ActivityStreamRegistry registry;

    public BoardChangeBroadcaster(ActivityStreamRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBoardChanged(BoardChanged change) {
        registry.broadcast(ActivityStreamEvents.boardChanged(change));
    }
}
