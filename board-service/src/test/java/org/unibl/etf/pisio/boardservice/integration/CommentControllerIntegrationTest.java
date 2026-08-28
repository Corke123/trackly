package org.unibl.etf.pisio.boardservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.unibl.etf.pisio.boardservice.integration.BoardIntegrationTestSupport.addSwimlane;
import static org.unibl.etf.pisio.boardservice.integration.BoardIntegrationTestSupport.createBoard;
import static org.unibl.etf.pisio.boardservice.integration.BoardIntegrationTestSupport.createTicket;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.unibl.etf.pisio.boardservice.controller.dto.CommentView;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.PostComment;
import org.unibl.etf.pisio.boardservice.repository.CommentRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, ServiceBusTestSupportConfig.class})
@ActiveProfiles("local")
class CommentControllerIntegrationTest {

    private static final ParameterizedTypeReference<List<CommentView>> THREAD =
            new ParameterizedTypeReference<>() {};

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    @Qualifier("userRestTestClient")
    private RestTestClient userRestTestClient;

    @Autowired
    private CommentRepository commentRepository;

    @Test
    @DisplayName(
            """
            Given an existing ticket, \
            when comments are posted by two users, \
            then each is attributed to its own token subject and the thread reads oldest first\
            """)
    void postAndListComments() {
        Long ticketId = createTicketOnNewBoard();

        postComment(restTestClient, ticketId, "Picking this up");
        postComment(userRestTestClient, ticketId, "Blocked on the gateway route");

        List<CommentView> thread = restTestClient.get()
                .uri("/tickets/{ticketId}/comments", ticketId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(THREAD)
                .returnResult()
                .getResponseBody();

        assertThat(thread)
                .extracting(CommentView::authorId, CommentView::body)
                .containsExactly(
                        tuple("admin", "Picking this up"),
                        tuple("demo", "Blocked on the gateway route"));
        assertThat(commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)).hasSize(2);
    }

    @Test
    @DisplayName("Given a blank body, when a comment is posted, then the request is rejected as a bad request")
    void postBlankComment() {
        Long ticketId = createTicketOnNewBoard();

        restTestClient.post()
                .uri("/tickets/{ticketId}/comments", ticketId)
                .body(new PostComment("   "))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)).isEmpty();
    }

    @Test
    @DisplayName("Given a missing ticket, when a comment is posted, then the request is refused as not found")
    void postCommentOnMissingTicket() {
        restTestClient.post()
                .uri("/tickets/{ticketId}/comments", 999_999L)
                .body(new PostComment("Into the void"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName(
            """
            Given a comment written by somebody else, \
            when a plain user deletes it, \
            then the service refuses and the comment survives\
            """)
    void plainUsersMayNotDeleteSomebodyElsesComment() {
        Long ticketId = createTicketOnNewBoard();
        Long commentId = postComment(restTestClient, ticketId, "The admin wrote this");

        userRestTestClient.delete()
                .uri("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
                .exchange()
                .expectStatus().isForbidden();

        assertThat(commentRepository.findById(commentId)).isPresent();
    }

    @Test
    @DisplayName("Given a comment written by somebody else, when an admin deletes it, then it is removed")
    void adminsMayDeleteAnyComment() {
        Long ticketId = createTicketOnNewBoard();
        Long commentId = postComment(userRestTestClient, ticketId, "A plain user wrote this");

        restTestClient.delete()
                .uri("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
                .exchange()
                .expectStatus().isNoContent();

        assertThat(commentRepository.findById(commentId)).isEmpty();
    }

    @Test
    @DisplayName("Given a commented ticket, when the ticket is deleted, then its comments are deleted with it")
    void deletingTicketDeletesItsComments() {
        Long ticketId = createTicketOnNewBoard();
        postComment(restTestClient, ticketId, "Short-lived");

        restTestClient.delete()
                .uri("/tickets/{ticketId}", ticketId)
                .exchange()
                .expectStatus().isNoContent();

        assertThat(commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)).isEmpty();
    }

    private Long createTicketOnNewBoard() {
        Long boardId = createBoard(restTestClient, "Board");
        Long swimlaneId = addSwimlane(restTestClient, boardId, "To Do");
        return createTicket(restTestClient, boardId, swimlaneId, "Write tests", "Cover the happy paths");
    }

    private static Long postComment(RestTestClient client, Long ticketId, String body) {
        CommentView created = client.post()
                .uri("/tickets/{ticketId}/comments", ticketId)
                .body(new PostComment(body))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CommentView.class)
                .returnResult()
                .getResponseBody();
        assertThat(created).isNotNull();
        return created.id();
    }
}
