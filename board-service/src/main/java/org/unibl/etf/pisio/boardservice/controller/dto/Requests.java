package org.unibl.etf.pisio.boardservice.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public final class Requests {

    private Requests() {

    }

    public record CreateBoard(@NotBlank String name) {

    }

    public record CreateSwimlane(@NotBlank String title) {

    }

    public record CreateTicket(@NotNull Long swimlaneId, @NotBlank String title, String description) {

    }

    public record UpdateTicket(Long swimlaneId, @PositiveOrZero Integer position, String assigneeId) {

    }
}
