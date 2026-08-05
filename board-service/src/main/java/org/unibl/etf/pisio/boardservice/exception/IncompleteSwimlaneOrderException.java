package org.unibl.etf.pisio.boardservice.exception;

import java.util.Collection;
import java.util.List;

public class IncompleteSwimlaneOrderException extends RuntimeException {

    public IncompleteSwimlaneOrderException(Long boardId, Collection<Long> expected, List<Long> given) {
        super("A swimlane order for board " + boardId + " must list every swimlane exactly once; expected "
                + expected + " but got " + given);
    }
}
