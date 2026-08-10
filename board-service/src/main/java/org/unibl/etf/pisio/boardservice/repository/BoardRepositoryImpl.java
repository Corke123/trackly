package org.unibl.etf.pisio.boardservice.repository;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

class BoardRepositoryImpl implements BoardRepositoryCustom {

    private static final String UPDATE_POSITION = """
            update swimlane
            set position = :position
            where id = :swimlaneId
              and board_id = :boardId
            """;

    private final JdbcClient jdbcClient;

    BoardRepositoryImpl(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void updateSwimlaneOrder(Long boardId, List<Long> orderedSwimlaneIds) {
        for (int position = 0; position < orderedSwimlaneIds.size(); position++) {
            jdbcClient.sql(UPDATE_POSITION)
                    .param("position", position)
                    .param("swimlaneId", orderedSwimlaneIds.get(position))
                    .param("boardId", boardId)
                    .update();
        }
    }
}
