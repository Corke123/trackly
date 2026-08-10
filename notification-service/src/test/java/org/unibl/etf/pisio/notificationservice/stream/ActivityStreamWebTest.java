package org.unibl.etf.pisio.notificationservice.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.unibl.etf.pisio.notificationservice.config.SecurityConfig;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActivityStreamController.class)
@Import({SecurityConfig.class, ActivityStreamRegistry.class, ActivityStreamConfig.class})
class ActivityStreamWebTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-25T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityRepository activities;

    @Test
    @DisplayName("Given no bearer token, when the stream is requested, then a 401 is returned")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/activity/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Given a signed-in user, when the stream is requested, then an event stream opens straight away")
    void opensAnEventStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/activity/stream").with(jwt().jwt(token -> token.subject("user-1"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getContentAsString()).contains(":connected");
    }

    @Test
    @DisplayName("Given a Last-Event-ID from a dropped connection, when the stream is reopened, then what was missed arrives with it")
    void replaysWhatWasMissedOnReconnect() throws Exception {
        when(activities.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq("user-1"), eq(7L), any()))
                .thenReturn(List.of(addressed(8L)));

        MvcResult result = mockMvc.perform(get("/activity/stream")
                        .header("Last-Event-ID", "7")
                        .with(jwt().jwt(token -> token.subject("user-1"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("id:8");
        assertThat(body).contains("event:activity");
        assertThat(body).contains("actor-1 assigned \\\"Fix login\\\" to you");
    }

    @Test
    @DisplayName("Given a client that had to open a brand new stream, when it names its resume point in the query string, then what it missed is replayed")
    void replaysFromTheQueryStringWhenTheHeaderCannotBeSet() throws Exception {
        when(activities.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq("user-1"), eq(12L), any()))
                .thenReturn(List.of(addressed(13L)));

        MvcResult result = mockMvc.perform(get("/activity/stream")
                        .param("lastEventId", "12")
                        .with(jwt().jwt(token -> token.subject("user-1"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("id:13");
    }

    @Test
    @DisplayName("Given both a Last-Event-ID header and a query string, when the stream is opened, then the browser's own header is what counts")
    void prefersTheHeaderOverTheQueryString() throws Exception {
        mockMvc.perform(get("/activity/stream")
                        .header("Last-Event-ID", "20")
                        .param("lastEventId", "12")
                        .with(jwt().jwt(token -> token.subject("user-1"))))
                .andExpect(request().asyncStarted());

        verify(activities).findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq("user-1"), eq(20L), any());
    }

    @Test
    @DisplayName("Given someone else's Last-Event-ID, when a user reopens the stream, then only their own activities are looked up")
    void replaysOnlyTheSignedInUsersActivities() throws Exception {
        mockMvc.perform(get("/activity/stream")
                        .header("Last-Event-ID", "7")
                        .with(jwt().jwt(token -> token.subject("user-2"))))
                .andExpect(request().asyncStarted());

        verify(activities).findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq("user-2"), eq(7L), any());
    }

    private static Activity addressed(Long id) {
        return new Activity(id, "event-" + id, 1L, "TicketAssigned", "Ticket \"Fix login\" assigned to user-1",
                "actor-1", "user-1", "actor-1 assigned \"Fix login\" to you", OCCURRED_AT, OCCURRED_AT);
    }
}
