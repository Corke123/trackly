package org.unibl.etf.pisio.boardservice.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties(ServiceBusProperties.class)
public class ServiceBusConfig {

    @Bean(name = "boardEventsSender", destroyMethod = "close")
    @Profile("local")
    public ServiceBusSenderClient connectionStringSender(ServiceBusProperties properties) {
        return new ServiceBusClientBuilder()
                .connectionString(properties.connectionString())
                .sender()
                .topicName(properties.topic())
                .buildClient();
    }

    @Bean(name = "boardEventsSender", destroyMethod = "close")
    @Profile("!local")
    public ServiceBusSenderClient managedIdentitySender(ServiceBusProperties properties) {
        return new ServiceBusClientBuilder()
                .fullyQualifiedNamespace(properties.namespace())
                .credential(new DefaultAzureCredentialBuilder().build())
                .sender()
                .topicName(properties.topic())
                .buildClient();
    }
}
