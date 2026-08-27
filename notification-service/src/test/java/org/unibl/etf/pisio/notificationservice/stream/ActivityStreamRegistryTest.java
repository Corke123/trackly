package org.unibl.etf.pisio.notificationservice.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ActivityStreamRegistryTest {

    private ActivityStreamRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ActivityStreamRegistry();
    }

    @Test
    @DisplayName(
            """
            Given a registered listener, \
            when an event is sent to their recipient id, \
            then it reaches their emitter\
            """)
    void sendsToRegisteredListener() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register("user-1", emitter);

        SseEmitter.SseEventBuilder event = SseEmitter.event().data("hello");
        registry.send("user-1", event);

        verify(emitter).send(event);
    }

    @Test
    @DisplayName("Given a user connected from two tabs, when an event is sent, then both connections receive it")
    void sendsToEveryConnectionOfRecipient() throws IOException {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        registry.register("user-1", first);
        registry.register("user-1", second);

        registry.send("user-1", SseEmitter.event().data("hello"));

        verify(first).send(any(SseEmitter.SseEventBuilder.class));
        verify(second).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("Given listeners of another recipient, when an event is sent, then their emitters are left alone")
    void doesNotCrossRecipients() throws IOException {
        SseEmitter mine = mock(SseEmitter.class);
        SseEmitter theirs = mock(SseEmitter.class);
        registry.register("user-1", mine);
        registry.register("user-2", theirs);

        registry.send("user-1", SseEmitter.event().data("hello"));

        verify(mine).send(any(SseEmitter.SseEventBuilder.class));
        verify(theirs, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("Given nobody listening for a recipient, when an event is sent, then nothing happens")
    void sendingToNobodyIsHarmless() {
        registry.send("user-nobody", SseEmitter.event().data("hello"));

        assertThat(registry.emittersFor("user-nobody")).isEmpty();
    }

    @Test
    @DisplayName(
            """
            Given a connection that has gone away, \
            when an event is sent, \
            then it is dropped rather than retried forever\
            """)
    void dropsBrokenConnections() throws IOException {
        SseEmitter broken = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(broken).send(any(SseEmitter.SseEventBuilder.class));
        registry.register("user-1", broken);

        registry.send("user-1", SseEmitter.event().data("hello"));

        verify(broken).completeWithError(any(IOException.class));
        assertThat(registry.emittersFor("user-1")).isEmpty();
    }

    @Test
    @DisplayName("Given a completed connection, when the completion callback fires, then it is no longer registered")
    void forgetsCompletedConnections() {
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register("user-1", emitter);

        completionCallbackOf(emitter).run();

        assertThat(registry.emittersFor("user-1")).isEmpty();
    }

    @Test
    @DisplayName("Given a user with two tabs open, when one is closed, then the other keeps receiving")
    void keepsTheRemainingConnectionsOfRecipient() throws IOException {
        SseEmitter closed = mock(SseEmitter.class);
        SseEmitter open = mock(SseEmitter.class);
        registry.register("user-1", closed);
        registry.register("user-1", open);

        completionCallbackOf(closed).run();
        registry.send("user-1", SseEmitter.event().data("hello"));

        assertThat(registry.emittersFor("user-1")).containsExactly(open);
        verify(open).send(any(SseEmitter.SseEventBuilder.class));
        verify(closed, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName(
            """
            Given listeners of several recipients, \
            when a change is broadcast, \
            then every one of them receives it\
            """)
    void broadcastsToEveryRecipient() throws IOException {
        SseEmitter mine = mock(SseEmitter.class);
        SseEmitter theirs = mock(SseEmitter.class);
        registry.register("user-1", mine);
        registry.register("user-2", theirs);

        registry.broadcast(SseEmitter.event().data("the board changed"));

        verify(mine).send(any(SseEmitter.SseEventBuilder.class));
        verify(theirs).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("Given nobody connected, when a change is broadcast, then nothing happens")
    void broadcastingToNobodyIsHarmless() {
        registry.broadcast(SseEmitter.event().data("the board changed"));

        assertThat(registry.emittersFor("user-nobody")).isEmpty();
    }

    @Test
    @DisplayName(
            """
            Given a connection that has gone away, \
            when a change is broadcast, \
            then the live ones still receive it\
            """)
    void broadcastSurvivesBrokenConnections() throws IOException {
        SseEmitter broken = mock(SseEmitter.class);
        SseEmitter healthy = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(broken).send(any(SseEmitter.SseEventBuilder.class));
        registry.register("user-1", broken);
        registry.register("user-2", healthy);

        registry.broadcast(SseEmitter.event().data("the board changed"));

        verify(healthy).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(registry.emittersFor("user-1")).isEmpty();
    }

    @Test
    @DisplayName("Given open connections, when the heartbeat runs, then each one is written to")
    void heartbeatKeepsConnectionsAlive() throws IOException {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        registry.register("user-1", first);
        registry.register("user-2", second);

        registry.heartbeat();

        verify(first).send(any(SseEmitter.SseEventBuilder.class));
        verify(second).send(any(SseEmitter.SseEventBuilder.class));
    }

    private Runnable completionCallbackOf(SseEmitter emitter) {
        var captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onCompletion(captor.capture());
        return captor.getValue();
    }
}
