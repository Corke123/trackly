package org.unibl.etf.pisio.boardservice.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("swimlane")
public record Swimlane(
        @Id Long id,
        String title
) {

    public Swimlane(String title) {
        this(null, title);
    }
}
