package org.unibl.etf.pisio.boardservice.controller.dto;

import org.unibl.etf.pisio.boardservice.domain.Board;

public record BoardSummary(Long id, String name) {

    public static BoardSummary of(Board board) {
        return new BoardSummary(board.id(), board.name());
    }
}
