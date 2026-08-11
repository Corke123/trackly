package org.unibl.etf.pisio.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trackly.servicebus")
public record ServiceBusProperties(
        boolean enabled, String namespace, String connectionString, String topic, String subscription) {
}
