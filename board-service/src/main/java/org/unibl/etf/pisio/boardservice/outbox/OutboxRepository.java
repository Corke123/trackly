package org.unibl.etf.pisio.boardservice.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface OutboxRepository extends ListCrudRepository<OutboxEntry, Long> {

    List<OutboxEntry> findByPublishedFalseOrderByIdAsc(Limit limit);

}
