package org.unibl.etf.pisio.notificationservice.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties(ServiceBusProperties.class)
@ConditionalOnProperty(name = "trackly.servicebus.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceBusConfig {

    @Bean(name = "serviceBusProcessorClient")
    @Profile("local")
    public ServiceBusClientBuilder.ServiceBusProcessorClientBuilder connectionStringProcessorClient(
            ServiceBusProperties properties) {
        return new ServiceBusClientBuilder()
                .connectionString(properties.connectionString())
                .processor()
                .topicName(properties.topic())
                .subscriptionName(properties.subscription());
    }

    @Bean(name = "serviceBusProcessorClient")
    @Profile("!local")
    public ServiceBusClientBuilder.ServiceBusProcessorClientBuilder managedIdentityProcessorClient(
            ServiceBusProperties properties) {
        return new ServiceBusClientBuilder()
                .fullyQualifiedNamespace(properties.namespace())
                .credential(new DefaultAzureCredentialBuilder().build())
                .processor()
                .topicName(properties.topic())
                .subscriptionName(properties.subscription());
    }
}
