package org.unibl.etf.pisio.notificationservice.service;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.domain.event.BoardEvent;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketAssigned;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketCommented;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketCreated;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketDeleted;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketMoved;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class ActivityIngestService {

    private static final Logger log = LoggerFactory.getLogger(ActivityIngestService.class);

    private final ActivityRepository activities;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public ActivityIngestService(ActivityRepository activities, ObjectMapper objectMapper,
                                 ApplicationEventPublisher events) {
        this.activities = activities;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    @Transactional
    public void ingest(String eventId, String eventType, String payload) {
        if (eventId != null && activities.existsByEventId(eventId)) {
            log.debug("Skipping already-recorded event {}", eventId);
            return;
        }

        try {
            BoardEvent event = deserializeEvent(eventType, payload);
            Address address = addressOf(event);

            Activity saved = activities.save(new Activity(
                    eventId,
                    event.boardId(),
                    eventType,
                    summarize(event),
                    event.actorId(),
                    address.recipientId(),
                    address.message(),
                    event.occurredAt()
            ));

            events.publishEvent(BoardChanged.of(saved));

            if (saved.isAddressed()) {
                events.publishEvent(new ActivityRecorded(saved));
            }

            log.info("Recorded activity {} for board {}", eventType, event.boardId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to ingest event " + eventType, e);
        }
    }

    private BoardEvent deserializeEvent(String eventType, String payload) {
        return switch (eventType) {
            case TicketCreated.TYPE -> objectMapper.readValue(payload, TicketCreated.class);
            case TicketMoved.TYPE -> objectMapper.readValue(payload, TicketMoved.class);
            case TicketAssigned.TYPE -> objectMapper.readValue(payload, TicketAssigned.class);
            case TicketCommented.TYPE -> objectMapper.readValue(payload, TicketCommented.class);
            case TicketDeleted.TYPE -> objectMapper.readValue(payload, TicketDeleted.class);
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }

    private record Address(String recipientId, String message) {

        static final Address NOBODY = new Address(null, null);
    }

    private Address addressOf(BoardEvent event) {
        return switch (event) {
            case TicketCreated _ -> Address.NOBODY;
            case TicketMoved e -> to(e.assigneeId(), e, "%s moved your ticket %s to %s".formatted(
                    e.actorId(), ticketName(e.ticketId(), e.title()),
                    swimlaneName(e.toSwimlaneId(), e.toSwimlaneTitle())));
            case TicketAssigned e -> to(e.assigneeId(), e, "%s assigned %s to you".formatted(
                    e.actorId(), ticketName(e.ticketId(), e.title())));
            case TicketCommented e -> to(e.assigneeId(), e, "%s commented on your ticket %s".formatted(
                    e.actorId(), ticketName(e.ticketId(), e.title())));
            case TicketDeleted e -> to(e.assigneeId(), e, "%s deleted your ticket %s".formatted(
                    e.actorId(), ticketName(e.ticketId(), e.title())));
        };
    }

    private Address to(String recipientId, BoardEvent event, String message) {
        return recipientId == null || Objects.equals(recipientId, event.actorId())
                ? Address.NOBODY
                : new Address(recipientId, message);
    }

    private String summarize(BoardEvent event) {
        return switch (event) {
            case TicketCreated e -> "Ticket #%d created: %s".formatted(e.ticketId(), e.title());
            case TicketMoved e -> "Ticket %s moved to %s".formatted(ticketName(e.ticketId(), e.title()),
                    swimlaneName(e.toSwimlaneId(), e.toSwimlaneTitle()));
            case TicketAssigned e -> "Ticket %s assigned to %s".formatted(ticketName(e.ticketId(), e.title()),
                    e.assigneeId());
            case TicketCommented e -> "Ticket %s commented on by %s".formatted(
                    ticketName(e.ticketId(), e.title()), e.actorId());
            case TicketDeleted e -> "Ticket %s deleted from %s".formatted(ticketName(e.ticketId(), e.title()),
                    swimlaneName(e.swimlaneId(), e.swimlaneTitle()));
        };
    }

    private String ticketName(Long ticketId, String title) {
        return title == null || title.isBlank() ? "#" + ticketId : "\"%s\"".formatted(title);
    }

    private String swimlaneName(Long swimlaneId, String title) {
        return title == null || title.isBlank() ? "swimlane " + swimlaneId : title;
    }
}
