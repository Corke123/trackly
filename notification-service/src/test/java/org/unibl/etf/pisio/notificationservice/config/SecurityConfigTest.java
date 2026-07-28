package org.unibl.etf.pisio.notificationservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.unibl.etf.pisio.notificationservice.controller.ActivityController;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActivityController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityRepository activityRepository;

    @Test
    @DisplayName("Given no bearer token, when the activity feed is requested, then a 401 is returned")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/activity")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Given a valid bearer token with a roles claim, when the activity feed is requested, then the request is authenticated and reaches the controller")
    void requestWithBearerTokenIsAuthenticated() throws Exception {
        when(activityRepository.findByOrderByOccurredAtDesc(ActivityController.FEED_SIZE)).thenReturn(List.of());

        mockMvc.perform(get("/activity").with(jwt().jwt(jwt -> jwt.claim("roles", List.of("ROLE_USER")))))
                .andExpect(status().isOk());
    }
}
