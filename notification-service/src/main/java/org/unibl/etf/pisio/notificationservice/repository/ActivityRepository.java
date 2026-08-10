package org.unibl.etf.pisio.notificationservice.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.repository.ListCrudRepository;
import org.unibl.etf.pisio.notificationservice.domain.Activity;

import java.util.List;

public interface ActivityRepository extends ListCrudRepository<Activity, Long> {

    boolean existsByEventId(String eventId);

    List<Activity> findByBoardIdOrderByOccurredAtDesc(Long boardId, Limit limit);

    List<Activity> findByOrderByOccurredAtDesc(Limit limit);

    List<Activity> findByRecipientIdAndIdGreaterThanOrderByIdAsc(String recipientId, Long id, Limit limit);

}
