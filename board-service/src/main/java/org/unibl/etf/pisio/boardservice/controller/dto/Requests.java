package org.unibl.etf.pisio.boardservice.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class Requests {

    private Requests() {

    }

    public record CreateBoard(@NotBlank String name) {

    }

    public record RenameBoard(@NotBlank String name) {

    }

    public record CreateSwimlane(@NotBlank String title) {

    }

    public record ReorderSwimlanes(@NotEmpty List<Long> swimlaneIds) {

    }

    public record CreateTicket(@NotNull Long swimlaneId, @NotBlank String title, String description) {

    }

    public record UpdateTicket(Long swimlaneId, @PositiveOrZero Integer position, String assigneeId) {

    }

    public record PostComment(@NotBlank @Size(max = 2000) String body) {

    }
}
