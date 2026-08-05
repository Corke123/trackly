package org.unibl.etf.pisio.boardservice.repository;

import org.springframework.data.repository.ListCrudRepository;
import org.unibl.etf.pisio.boardservice.domain.Ticket;

import java.util.List;

public interface TicketRepository extends ListCrudRepository<Ticket, Long> {

    List<Ticket> findByBoardIdOrderBySwimlaneIdAscPositionAsc(Long boardId);

    List<Ticket> findBySwimlaneIdOrderByPositionAsc(Long swimlaneId);

    int countBySwimlaneId(Long swimlaneId);

}
