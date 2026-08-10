package org.unibl.etf.pisio.notificationservice.stream;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ActivityStreamProperties.class)
public class ActivityStreamConfig {
}
