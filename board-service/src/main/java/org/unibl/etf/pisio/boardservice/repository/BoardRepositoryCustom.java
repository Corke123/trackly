package org.unibl.etf.pisio.boardservice.repository;

import java.util.List;

public interface BoardRepositoryCustom {

    void updateSwimlaneOrder(Long boardId, List<Long> orderedSwimlaneIds);
}
