package org.unibl.etf.pisio.boardservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.unibl.etf.pisio.boardservice.controller.dto.CommentView;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.PostComment;
import org.unibl.etf.pisio.boardservice.domain.Comment;
import org.unibl.etf.pisio.boardservice.service.BoardService;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    private static final Instant WHEN = Instant.parse("2026-08-28T09:15:00Z");

    @Mock
    private BoardService boardService;

    @InjectMocks
    private CommentController commentController;

    @Test
    @DisplayName("Given a ticket with a thread, when listComments is called, then every comment is returned as a view")
    void listComments() {
        List<Comment> thread = List.of(
                new Comment(500L, 100L, "demo", "First", WHEN),
                new Comment(501L, 100L, "admin", "Second", WHEN.plusSeconds(60)));
        when(boardService.getComments(100L)).thenReturn(thread);

        List<CommentView> result = commentController.listComments(100L);

        assertThat(result).containsExactly(CommentView.of(thread.get(0)), CommentView.of(thread.get(1)));
    }

    @Test
    @DisplayName(
            """
            Given a body, \
            when postComment is called, \
            then the comment is attributed to the token subject and returned as 201 with its location\
            """)
    void postComment() {
        Comment saved = new Comment(500L, 100L, "demo", "Blocked on the gateway route", WHEN);
        when(boardService.postComment(100L, "Blocked on the gateway route", "demo")).thenReturn(saved);

        ResponseEntity<CommentView> result =
                commentController.postComment(100L, new PostComment("Blocked on the gateway route"), jwtFor("demo"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(CommentView.of(saved));
        assertThat(result.getHeaders().getLocation()).hasToString("/tickets/100/comments/500");
    }

    @Test
    @DisplayName("Given a plain user, when deleteComment is called, then the service is told the actor is not an admin")
    void deleteCommentAsPlainUser() {
        ResponseEntity<Void> result =
                commentController.deleteComment(100L, 500L, jwtFor("demo"), authenticationFor("ROLE_USER"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(boardService).deleteComment(100L, 500L, "demo", false);
    }

    @Test
    @DisplayName("Given an admin, when deleteComment is called, then the service is told the actor is an admin")
    void deleteCommentAsAdmin() {
        ResponseEntity<Void> result =
                commentController.deleteComment(100L, 500L, jwtFor("admin"), authenticationFor("ROLE_ADMIN"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(boardService).deleteComment(100L, 500L, "admin", true);
    }

    private static Jwt jwtFor(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .build();
    }

    private static Authentication authenticationFor(String role) {
        return new UsernamePasswordAuthenticationToken("principal", "credentials",
                List.of(new SimpleGrantedAuthority(role)));
    }
}
