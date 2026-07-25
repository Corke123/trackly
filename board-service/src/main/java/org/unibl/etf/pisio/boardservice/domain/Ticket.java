package org.unibl.etf.pisio.boardservice.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("ticket")
public record Ticket(
        @Id Long id,
        Long boardId,
        Long swimlaneId,
        String title,
        String description,
        String assigneeId,
        int position,
        Instant createdAt
) {

    public Ticket(Long boardId, Long swimlaneId, String title, String description, int position) {
        this(null, boardId, swimlaneId, title, description, null, position, Instant.now());
    }

    public Ticket moveTo(Long newSwimlaneId, int newPosition) {
        return new Ticket(this.id, this.boardId, newSwimlaneId, this.title, this.description, this.assigneeId, newPosition, this.createdAt);
    }

    public Ticket assignTo(String newAssigneeId) {
        return new Ticket(this.id, this.boardId, this.swimlaneId, this.title, this.description, newAssigneeId, this.position, this.createdAt);
    }
}
