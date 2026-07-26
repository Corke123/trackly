package org.unibl.etf.pisio.notificationservice.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ServiceBusProperties.class)
@ConditionalOnProperty(name = "trackly.servicebus.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceBusConfig {

    @Bean
    public ServiceBusClientBuilder.ServiceBusProcessorClientBuilder serviceBusProcessorClient(ServiceBusProperties serviceBusProperties) {
        return new ServiceBusClientBuilder()
                .connectionString(serviceBusProperties.connectionString())
                .processor()
                .topicName(serviceBusProperties.topic())
                .subscriptionName(serviceBusProperties.subscription());
    }
}
