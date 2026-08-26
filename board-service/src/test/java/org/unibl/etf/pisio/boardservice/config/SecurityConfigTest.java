package org.unibl.etf.pisio.boardservice.config;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.unibl.etf.pisio.boardservice.controller.BoardController;
import org.unibl.etf.pisio.boardservice.domain.Board;
import org.unibl.etf.pisio.boardservice.service.BoardService;

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
    @DisplayName(
            """
            Given a valid bearer token with a roles claim, \
            when a board endpoint is called, \
            then the request is authenticated and reaches the controller\
            """)
    void requestWithBearerTokenIsAuthenticated() throws Exception {
        when(boardService.getBoard(1L)).thenReturn(new Board(1L, "Board", List.of()));
        when(boardService.getBoardTickets(1L)).thenReturn(List.of());

        mockMvc.perform(get("/boards/1").with(jwt().jwt(jwt -> jwt.claim("roles", List.of("ROLE_USER")))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName(
            """
            Given a token without the admin role, \
            when an admin-only board endpoint is called, \
            then a 403 is returned and the service is never reached\
            """)
    void adminOnlyEndpointRejectsPlainUser() throws Exception {
        mockMvc.perform(patch("/boards/1")
                        .with(user())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/boards/1/swimlanes/10").with(user()).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/boards/1/swimlanes/order")
                        .with(user())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"swimlaneIds\":[10,20]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/boards/1/swimlanes")
                        .with(user())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"To Do\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(boardService);
    }

    @Test
    @DisplayName(
            """
            Given a token with the admin role, \
            when an admin-only board endpoint is called, \
            then the request reaches the controller\
            """)
    void adminOnlyEndpointAcceptsAdmin() throws Exception {
        when(boardService.renameBoard(1L, "Renamed")).thenReturn(new Board(1L, "Renamed", List.of()));
        when(boardService.getBoardTickets(1L)).thenReturn(List.of());

        mockMvc.perform(patch("/boards/1")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk());
    }

    private static JwtRequestPostProcessor user() {
        return jwt().jwt(token -> token.subject("demo").claim("roles", List.of("ROLE_USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin").claim("roles", List.of("ROLE_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
