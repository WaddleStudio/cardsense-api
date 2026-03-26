package com.cardsense.api.controller;

import com.cardsense.api.repository.MockPromotionRepository;
import com.cardsense.api.repository.PromotionRepository;
import com.cardsense.api.repository.SqlitePromotionRepository;
import com.cardsense.api.repository.SupabasePromotionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void reportsSupabaseRepositoryMode() {
        HealthController controller = new HealthController(
                mock(SupabasePromotionRepository.class),
                healthyJdbc()
        );

        assertEquals("supabase", controller.health().getBody().get("repository"));
    }

    @Test
    void reportsSqliteRepositoryMode() {
        HealthController controller = new HealthController(
                mock(SqlitePromotionRepository.class),
                healthyJdbc()
        );

        assertEquals("sqlite", controller.health().getBody().get("repository"));
    }

    @Test
    void reportsMockRepositoryMode() {
        HealthController controller = new HealthController(
                mock(MockPromotionRepository.class),
                healthyJdbc()
        );

        assertEquals("mock", controller.health().getBody().get("repository"));
    }

    private JdbcTemplate healthyJdbc() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        return jdbc;
    }
}
