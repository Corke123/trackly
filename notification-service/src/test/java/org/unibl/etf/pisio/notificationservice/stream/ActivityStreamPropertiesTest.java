package org.unibl.etf.pisio.notificationservice.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActivityStreamPropertiesTest {

    @Test
    @DisplayName("Given configured values, when the properties are built, then they are kept")
    void keepsConfiguredValues() {
        ActivityStreamProperties properties = new ActivityStreamProperties(Duration.ofMinutes(2), 5);

        assertThat(properties.timeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.replayLimit()).isEqualTo(5);
    }

    @Test
    @DisplayName(
            """
            Given nothing configured, \
            when the properties are built, \
            then a stream still has a timeout and a bounded replay\
            """)
    void fallsBackToWorkableDefaults() {
        ActivityStreamProperties properties = new ActivityStreamProperties(null, 0);

        assertThat(properties.timeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.replayLimit()).isEqualTo(20);
    }
}
