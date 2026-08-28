package org.unibl.etf.pisio.boardservice.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unibl.etf.pisio.boardservice.controller.dto.CommentView;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.PostComment;
import org.unibl.etf.pisio.boardservice.domain.Comment;
import org.unibl.etf.pisio.boardservice.service.BoardService;

@RestController
@RequestMapping("/tickets/{ticketId}/comments")
public class CommentController {

    private static final String ADMIN = "ROLE_ADMIN";

    private final BoardService boardService;

    public CommentController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping
    public List<CommentView> listComments(@PathVariable Long ticketId) {
        return boardService.getComments(ticketId).stream().map(CommentView::of).toList();
    }

    @PostMapping
    public ResponseEntity<CommentView> postComment(@PathVariable Long ticketId,
                                                   @Valid @RequestBody PostComment request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        Comment comment = boardService.postComment(ticketId, request.body(), jwt.getSubject());
        return ResponseEntity
                .created(URI.create("/tickets/" + ticketId + "/comments/" + comment.id()))
                .body(CommentView.of(comment));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long ticketId,
                                              @PathVariable Long commentId,
                                              @AuthenticationPrincipal Jwt jwt,
                                              Authentication authentication) {
        boardService.deleteComment(ticketId, commentId, jwt.getSubject(), isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN::equals);
    }
}
