package org.unibl.etf.pisio.boardservice.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.util.ArrayList;
import java.util.List;

@Table("board")
public record Board(
        @Id Long id,
        String name,
        @MappedCollection(idColumn = "board_id", keyColumn = "position") List<Swimlane> swimlanes
) {

    public Board(String name) {
        this(null, name, List.of());
    }

    public Board addSwimlane(String title) {
        Swimlane newSwimlane = new Swimlane(title);

        List<Swimlane> updatedSwimlanes = new ArrayList<>(this.swimlanes);
        updatedSwimlanes.add(newSwimlane);

        return new Board(this.id, this.name, updatedSwimlanes);
    }

    public boolean hasSwimlane(Long swimlaneId) {
        return swimlanes.stream()
                .anyMatch(s -> s.id() != null && s.id().equals(swimlaneId));
    }
}
