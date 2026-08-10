package org.unibl.etf.pisio.notificationservice.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("trackly.activity-stream")
public record ActivityStreamProperties(Duration timeout, int replayLimit) {

    public ActivityStreamProperties {
        timeout = timeout == null ? Duration.ofMinutes(30) : timeout;
        replayLimit = replayLimit <= 0 ? 20 : replayLimit;
    }
}
