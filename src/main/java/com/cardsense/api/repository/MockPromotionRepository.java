package com.cardsense.api.repository;

import com.cardsense.api.domain.Promotion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(name = "cardsense.repository.mode", havingValue = "mock", matchIfMissing = true)
public class MockPromotionRepository implements PromotionRepository {

    private List<Promotion> promotions = new ArrayList<>();
    private final ObjectMapper objectMapper;

    public MockPromotionRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("promotions.json");
            if (resource.exists()) {
                promotions = objectMapper.readValue(resource.getInputStream(), new TypeReference<List<Promotion>>() {});
            } else {
                System.out.println("No promotions.json found in classpath.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load promotions", e);
        }
    }

    @Override
    public List<Promotion> findActivePromotions(LocalDate date) {
        return promotions.stream()
                .filter(p -> (p.getValidFrom() == null || !p.getValidFrom().isAfter(date)))
                .filter(p -> (p.getValidUntil() == null || !p.getValidUntil().isBefore(date)))
                .filter(p -> p.getStatus() == null || "ACTIVE".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Promotion> findAllPromotions() {
        return List.copyOf(promotions);
    }
}
