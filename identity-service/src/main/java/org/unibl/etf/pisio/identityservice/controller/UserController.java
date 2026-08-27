package org.unibl.etf.pisio.identityservice.controller;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final String ENABLED_USERNAMES =
            """
            select username
            from users
            where enabled = true
            order by username
            """;

    private final JdbcClient jdbcClient;

    public UserController(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @GetMapping
    public List<UserView> listUsers() {
        return jdbcClient.sql(ENABLED_USERNAMES)
                .query(String.class)
                .list()
                .stream()
                .map(UserView::new)
                .toList();
    }

    public record UserView(String username) {

    }
}
