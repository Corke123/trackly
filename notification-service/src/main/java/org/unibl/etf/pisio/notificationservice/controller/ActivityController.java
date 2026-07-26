package org.unibl.etf.pisio.notificationservice.controller;

import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;

import java.time.Instant;
import java.util.List;

@RestController
public class ActivityController {

    public static final Limit FEED_SIZE = Limit.of(50);

    private final ActivityRepository activityRepository;

    public ActivityController(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @GetMapping("/activity")
    public List<ActivityView> feed(@RequestParam(required = false) Long boardId) {
        List<Activity> activities = boardId == null
                ? activityRepository.findByOrderByOccurredAtDesc(FEED_SIZE)
                : activityRepository.findByBoardIdOrderByOccurredAtDesc(boardId, FEED_SIZE);

        return activities.stream()
                .map(ActivityView::of)
                .toList();
    }

    public record ActivityView(Long id, Long boardId, String type, String summary, String actorId, Instant occurredAt) {

        static ActivityView of(Activity activity) {
            return new ActivityView(
                    activity.id(),
                    activity.boardId(),
                    activity.type(),
                    activity.summary(),
                    activity.actorId(),
                    activity.occurredAt()
            );
        }
    }
}
