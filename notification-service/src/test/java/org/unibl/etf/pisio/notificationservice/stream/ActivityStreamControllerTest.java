package org.unibl.etf.pisio.notificationservice.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityStreamControllerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-25T10:00:00Z");

    @Mock
    private ActivityRepository activities;

    private ActivityStreamRegistry registry;
    private ActivityStreamController controller;

    @BeforeEach
    void setUp() {
        registry = spy(new ActivityStreamRegistry());
        controller = new ActivityStreamController(activities,
                registry, new ActivityStreamProperties(Duration.ofMinutes(5), 20));
    }

    @Test
    @DisplayName("Given a signed-in user, when the stream is opened, then it is registered against the subject of their token and nobody else's")
    void registersTheTokenSubject() {
        SseEmitter emitter = controller.stream(tokenFor("user-1"), null, null);

        assertThat(registry.emittersFor("user-1")).containsExactly(emitter);
        assertThat(registry.emittersFor("user-2")).isEmpty();
    }

    @Test
    @DisplayName("Given no Last-Event-ID, when the stream is opened, then nothing is replayed")
    void replaysNothingOnAFirstConnection() {
        controller.stream(tokenFor("user-1"), null, null);

        verify(activities, never()).findByRecipientIdAndIdGreaterThanOrderByIdAsc(any(), any(), any());
    }

    @Test
    @DisplayName("Given a connection that dies partway through a replay, when the rest is sent, then it stops rather than writing into a dead stream")
    void stopsReplayingIntoADeadConnection() {
        when(activities.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq("user-1"), eq(7L), any()))
                .thenReturn(List.of(addressed(8L, "user-1"), addressed(9L, "user-1")));
        doReturn(true, false).when(registry)
                .sendTo(eq("user-1"), any(SseEmitter.class), any(SseEmitter.SseEventBuilder.class));

        controller.stream(tokenFor("user-1"), "7", null);

        verify(registry, times(2))
                .sendTo(eq("user-1"), any(SseEmitter.class), any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("Given a Last-Event-ID, when the stream is reopened, then only that recipient's later activities are replayed")
    void replaysWhatWasMissed() {
        when(activities.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq("user-1"), eq(7L), any()))
                .thenReturn(List.of(addressed(8L, "user-1"), addressed(9L, "user-1")));

        SseEmitter emitter = spy(controller.stream(tokenFor("user-1"), "7", null));

        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(activities).findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq("user-1"), eq(7L), limit.capture());
        assertThat(limit.getValue().max()).isEqualTo(20);
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("Given a Last-Event-ID that is already current, when the stream is reopened, then only the opening comment is sent")
    void replaysNothingWhenNothingWasMissed() {
        when(activities.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq("user-1"), eq(7L), any()))
                .thenReturn(List.of());

        controller.stream(tokenFor("user-1"), "7", null);

        verify(registry, times(1))
                .sendTo(eq("user-1"), any(SseEmitter.class), any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("Given a malformed Last-Event-ID, when the stream is opened, then it is ignored rather than failing the request")
    void ignoresAnUnparseableLastEventId() {
        SseEmitter emitter = controller.stream(tokenFor("user-1"), "not-a-number", null);

        assertThat(emitter).isNotNull();
        verify(activities, never()).findByRecipientIdAndIdGreaterThanOrderByIdAsc(any(), any(), any());
    }

    @Test
    @DisplayName("Given the configured timeout, when a stream is opened, then the emitter is given it")
    void appliesTheConfiguredTimeout() {
        SseEmitter emitter = controller.stream(tokenFor("user-1"), null, null);

        assertThat(emitter.getTimeout()).isEqualTo(Duration.ofMinutes(5).toMillis());
    }

    private static Jwt tokenFor(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("roles", List.of("ROLE_USER"))
                .build();
    }

    private static Activity addressed(Long id, String recipientId) {
        return new Activity(id, "event-" + id, 1L, "TicketAssigned", "Ticket \"Fix login\" assigned to " + recipientId,
                "actor-1", recipientId, "actor-1 assigned \"Fix login\" to you", OCCURRED_AT, OCCURRED_AT);
    }
}
