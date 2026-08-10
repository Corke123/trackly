package org.unibl.etf.pisio.boardservice.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;
import org.unibl.etf.pisio.boardservice.exception.IncompleteSwimlaneOrderException;
import org.unibl.etf.pisio.boardservice.exception.SwimlaneNotOnBoardException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    public Board rename(String newName) {
        return new Board(this.id, newName, this.swimlanes);
    }

    public Board removeSwimlane(Long swimlaneId) {
        if (hasNotSwimlane(swimlaneId)) {
            throw new SwimlaneNotOnBoardException(this.id, swimlaneId);
        }

        List<Swimlane> remaining = this.swimlanes.stream()
                .filter(swimlane -> !swimlane.id().equals(swimlaneId))
                .toList();

        return new Board(this.id, this.name, remaining);
    }

    /**
     * The swimlane order is the order of this list — Spring Data JDBC writes each element's index
     * into the {@code position} column — so reordering is rebuilding the list in the given order.
     */
    public Board reorderSwimlanes(List<Long> orderedSwimlaneIds) {
        Map<Long, Swimlane> byId = this.swimlanes.stream()
                .collect(Collectors.toMap(Swimlane::id, Function.identity()));

        if (orderedSwimlaneIds.size() != byId.size() || !new HashSet<>(orderedSwimlaneIds).equals(byId.keySet())) {
            throw new IncompleteSwimlaneOrderException(this.id, byId.keySet(), orderedSwimlaneIds);
        }

        List<Swimlane> reordered = orderedSwimlaneIds.stream()
                .map(byId::get)
                .toList();

        return new Board(this.id, this.name, reordered);
    }

    public String swimlaneTitle(Long swimlaneId) {
        return swimlanes.stream()
                .filter(swimlane -> Objects.nonNull(swimlane.id()))
                .filter(swimlane -> swimlane.id().equals(swimlaneId))
                .map(Swimlane::title)
                .findFirst()
                .orElseThrow(() -> new SwimlaneNotOnBoardException(this.id, swimlaneId));
    }

    public boolean hasNotSwimlane(Long swimlaneId) {
        return swimlanes.stream()
                .noneMatch(s -> s.id() != null && s.id().equals(swimlaneId));
    }
}
