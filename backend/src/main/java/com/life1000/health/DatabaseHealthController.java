package com.life1000.health;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("mysql")
@RestController
public class DatabaseHealthController {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health/db")
    public ResponseEntity<Map<String, String>> health() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(result)) {
                return ResponseEntity.ok(Map.of("status", "UP"));
            }
        } catch (DataAccessException exception) {
            // Do not expose connection details or credentials in the HTTP response.
        }
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}
