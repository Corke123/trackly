package org.unibl.etf.pisio.boardservice.repository;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;
import org.unibl.etf.pisio.boardservice.domain.Comment;

public interface CommentRepository extends ListCrudRepository<Comment, Long> {

    List<Comment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

    void deleteByTicketId(Long ticketId);
}
