package org.unibl.etf.pisio.boardservice.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ServiceBusProperties.class)
public class ServiceBusConfig {

    @Bean(destroyMethod = "close")
    public ServiceBusSenderClient boardEventsSender(ServiceBusProperties properties) {
        return new ServiceBusClientBuilder()
                .connectionString(properties.connectionString())
                .sender()
                .topicName(properties.topic())
                .buildClient();
    }
}
