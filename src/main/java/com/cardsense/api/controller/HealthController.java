package com.cardsense.api.controller;

import com.cardsense.api.repository.PromotionRepository;
import com.cardsense.api.repository.SupabasePromotionRepository;
import com.cardsense.api.repository.SqlitePromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final PromotionRepository promotionRepository;
    @Qualifier("supabaseJdbcTemplate")
    private final JdbcTemplate supabaseJdbcTemplate;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("repository", repositoryMode());

        try {
            supabaseJdbcTemplate.queryForObject("SELECT 1", Integer.class);
            result.put("supabase", "UP");
        } catch (Exception e) {
            result.put("supabase", "DOWN: " + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    private String repositoryMode() {
        if (promotionRepository instanceof SupabasePromotionRepository) {
            return "supabase";
        }
        if (promotionRepository instanceof SqlitePromotionRepository) {
            return "sqlite";
        }
        return "mock";
    }
}
