package org.unibl.etf.pisio.notificationservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.domain.Limit;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;

final class NotificationIntegrationTestSupport {

    private static final Limit LOOKUP = Limit.of(10);

    private NotificationIntegrationTestSupport() {
    }

    static Activity awaitActivity(ActivityRepository activities, Long boardId, String type) {
        AtomicReference<Activity> found = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            Optional<Activity> match = activities.findByBoardIdOrderByOccurredAtDesc(boardId, LOOKUP).stream()
                    .filter(activity -> type.equals(activity.type()))
                    .findFirst();
            assertThat(match).isPresent();
            found.set(match.get());
        });
        return found.get();
    }
}
