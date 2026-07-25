package org.unibl.etf.pisio.boardservice.repository;

import org.springframework.data.repository.ListCrudRepository;
import org.unibl.etf.pisio.boardservice.domain.Board;

public interface BoardRepository extends ListCrudRepository<Board, Long> {
}
