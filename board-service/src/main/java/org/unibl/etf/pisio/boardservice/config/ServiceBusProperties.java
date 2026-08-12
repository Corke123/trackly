package org.unibl.etf.pisio.boardservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trackly.servicebus")
public record ServiceBusProperties(String namespace, String connectionString, String topic) {
}
