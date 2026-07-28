package org.unibl.etf.pisio.boardservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.unibl.etf.pisio.boardservice.controller.BoardController;
import org.unibl.etf.pisio.boardservice.domain.Board;
import org.unibl.etf.pisio.boardservice.service.BoardService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BoardController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BoardService boardService;

    @Test
    @DisplayName("Given no bearer token, when a board endpoint is called, then a 401 is returned")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/boards/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Given a valid bearer token with a roles claim, when a board endpoint is called, then the request is authenticated and reaches the controller")
    void requestWithBearerTokenIsAuthenticated() throws Exception {
        when(boardService.getBoard(1L)).thenReturn(new Board(1L, "Board", List.of()));
        when(boardService.getBoardTickets(1L)).thenReturn(List.of());

        mockMvc.perform(get("/boards/1").with(jwt().jwt(jwt -> jwt.claim("roles", List.of("ROLE_USER")))))
                .andExpect(status().isOk());
    }
}
