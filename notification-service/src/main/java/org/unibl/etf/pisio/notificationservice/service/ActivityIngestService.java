package org.unibl.etf.pisio.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.domain.event.BoardEvent;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketAssigned;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketCreated;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketMoved;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class ActivityIngestService {

    private static final Logger log = LoggerFactory.getLogger(ActivityIngestService.class);

    private final ActivityRepository activities;
    private final ObjectMapper objectMapper;

    public ActivityIngestService(ActivityRepository activities, ObjectMapper objectMapper) {
        this.activities = activities;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ingest(String eventId, String eventType, String payload) {
        if (eventId != null && activities.existsByEventId(eventId)) {
            log.debug("Skipping already-recorded event {}", eventId);
            return;
        }

        try {
            BoardEvent event = deserializeEvent(eventType, payload);
            String summary = summarize(event);

            activities.save(new Activity(eventId, event.boardId(), eventType, summary, event.actorId(), event.occurredAt()));

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
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }

    private String summarize(BoardEvent event) {
        return switch (event) {
            case TicketCreated e -> "Ticket #%d created: %s".formatted(e.ticketId(), e.title());
            case TicketMoved e -> "Ticket #%d moved to swimlane %d".formatted(e.ticketId(), e.toSwimlaneId());
            case TicketAssigned e -> "Ticket #%d assigned to %s".formatted(e.ticketId(), e.assigneeId());
        };
    }
}
