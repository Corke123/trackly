package org.unibl.etf.pisio.boardservice.integration;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.azure.ServiceBusEmulatorContainer;

@TestConfiguration(proxyBeanMethods = false)
public class ServiceBusTestSupportConfig {

    @Bean
    BoardEventTestReceiver boardEventTestReceiver() {
        return new BoardEventTestReceiver();
    }

    @Bean(destroyMethod = "close")
    ServiceBusProcessorClient boardEventsProcessorClient(ServiceBusEmulatorContainer serviceBusEmulatorContainer,
                                                         BoardEventTestReceiver receiver) {
        ServiceBusProcessorClient client = new ServiceBusClientBuilder()
                .connectionString(serviceBusEmulatorContainer.getConnectionString())
                .processor()
                .topicName("board-events")
                .subscriptionName("notification")
                .processMessage(context -> {
                    receiver.onMessage(context.getMessage());
                    context.complete();
                })
                .processError(_ -> {
                })
                .buildProcessorClient();
        client.start();
        return client;
    }

    public static class BoardEventTestReceiver {

        private final List<ServiceBusReceivedMessage> received = new CopyOnWriteArrayList<>();

        void onMessage(ServiceBusReceivedMessage message) {
            received.add(message);
        }

        public List<ServiceBusReceivedMessage> received() {
            return received;
        }
    }
}
