package org.unibl.etf.pisio.boardservice.outbox;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.ListCrudRepository;

public interface OutboxRepository extends ListCrudRepository<OutboxEntry, Long> {

    List<OutboxEntry> findByPublishedFalseOrderByIdAsc(Limit limit);

}
