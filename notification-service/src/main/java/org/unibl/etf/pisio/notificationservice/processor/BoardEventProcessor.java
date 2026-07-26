package org.unibl.etf.pisio.notificationservice.processor;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.unibl.etf.pisio.notificationservice.config.ServiceBusProperties;
import org.unibl.etf.pisio.notificationservice.service.ActivityIngestService;

@Component
@ConditionalOnProperty(name = "trackly.servicebus.enabled", havingValue = "true", matchIfMissing = true)
public class BoardEventProcessor implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BoardEventProcessor.class);

    private final ServiceBusProcessorClient client;
    private final ServiceBusProperties properties;
    private final ActivityIngestService ingest;
    private volatile boolean running;

    public BoardEventProcessor(
            ServiceBusClientBuilder.ServiceBusProcessorClientBuilder builder,
            ServiceBusProperties properties,
            ActivityIngestService ingest) {

        this.properties = properties;
        this.ingest = ingest;

        this.client = builder
                .processMessage(this::onMessage)
                .processError(this::onError)
                .buildProcessorClient();
    }

    private void onMessage(ServiceBusReceivedMessageContext context) {
        var message = context.getMessage();
        ingest.ingest(message.getMessageId(), message.getSubject(), message.getBody().toString());
    }

    private void onError(ServiceBusErrorContext context) {
        log.error("Service Bus error on {}", context.getEntityPath(), context.getException());
    }

    @Override
    public void start() {
        client.start();
        running = true;
        log.info("Listening on topic '{}' subscription '{}'", properties.topic(), properties.subscription());
    }

    @Override
    public void stop() {
        if (client != null) {
            client.close();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
