package org.unibl.etf.pisio.notificationservice.integration;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.azure.ServiceBusEmulatorContainer;
import org.unibl.etf.pisio.notificationservice.config.ServiceBusProperties;
import org.unibl.etf.pisio.notificationservice.domain.event.BoardEvent;
import tools.jackson.databind.ObjectMapper;

@TestConfiguration(proxyBeanMethods = false)
public class ServiceBusTestSupportConfig {

    @Bean(destroyMethod = "close")
    ServiceBusSenderClient boardEventsSenderClient(ServiceBusEmulatorContainer serviceBusEmulatorContainer,
                                                   ServiceBusProperties properties) {
        return new ServiceBusClientBuilder()
                .connectionString(serviceBusEmulatorContainer.getConnectionString())
                .sender()
                .topicName(properties.topic())
                .buildClient();
    }

    @Bean
    BoardEventTestPublisher boardEventTestPublisher(ServiceBusSenderClient sender, ObjectMapper objectMapper) {
        return new BoardEventTestPublisher(sender, objectMapper);
    }

    public static class BoardEventTestPublisher {

        private final ServiceBusSenderClient sender;
        private final ObjectMapper objectMapper;

        BoardEventTestPublisher(ServiceBusSenderClient sender, ObjectMapper objectMapper) {
            this.sender = sender;
            this.objectMapper = objectMapper;
        }

        public void publish(String messageId, BoardEvent event) {
            ServiceBusMessage message = new ServiceBusMessage(objectMapper.writeValueAsString(event));
            message.setSubject(event.eventType());
            message.setContentType("application/json");
            message.setMessageId(messageId);
            message.getApplicationProperties().put("aggregateType", "Ticket");
            message.getApplicationProperties().put("aggregateId", String.valueOf(event.ticketId()));

            sender.sendMessage(message);
        }
    }
}
