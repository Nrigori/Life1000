package com.life1000.health;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseHealthControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DatabaseHealthController controller = new DatabaseHealthController(jdbc);

    @Test
    void reportsSuccessfulProbe() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        var response = controller.health();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(Map.of("status", "UP"));
    }

    @Test
    void failedConnectionReturnsUnavailableWithoutExposingDetails() {
        when(jdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("private connection details"));
        var response = controller.health();
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isEqualTo(Map.of("status", "DOWN"));
    }
}
