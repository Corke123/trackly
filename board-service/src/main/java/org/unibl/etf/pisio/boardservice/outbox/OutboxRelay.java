package org.unibl.etf.pisio.boardservice.outbox;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "trackly.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    public static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    public static final Limit BATCH = Limit.of(100);

    private final OutboxRepository outbox;
    private final ServiceBusSenderClient sender;

    public OutboxRelay(OutboxRepository outbox, ServiceBusSenderClient sender) {
        this.outbox = outbox;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${trackly.outbox.relay-delay-ms:2000}")
    @Transactional
    public void relay() {
        List<OutboxEntry> pending = outbox.findByPublishedFalseOrderByIdAsc(BATCH);

        if (pending.isEmpty()) {
            return;
        }

        List<OutboxEntry> publishedEntries = new ArrayList<>();

        for (var entry : pending) {
            ServiceBusMessage message = new ServiceBusMessage(entry.payload());
            message.setSubject(entry.eventType());
            message.setContentType("application/json");
            message.setMessageId(String.valueOf(entry.id()));
            message.getApplicationProperties().put("aggregateType", entry.aggregateType());
            message.getApplicationProperties().put("aggregateId", entry.aggregateId());

            sender.sendMessage(message);

            publishedEntries.add(entry.markPublished());
        }

        outbox.saveAll(publishedEntries);

        log.debug("Relayed {} outbox entries to Service Bus", publishedEntries.size());
    }
}
