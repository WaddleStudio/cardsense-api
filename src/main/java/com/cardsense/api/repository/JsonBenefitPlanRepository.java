package com.cardsense.api.repository;

import com.cardsense.api.domain.BenefitPlan;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

@Repository
public class JsonBenefitPlanRepository implements BenefitPlanRepository {

    private final String resourcePath;
    private List<BenefitPlan> plans;

    public JsonBenefitPlanRepository(@Value("${cardsense.benefit-plans.path:benefit-plans.json}") String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @PostConstruct
    public void init() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            plans = mapper.readValue(is, new TypeReference<List<BenefitPlan>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Failed to load benefit plans from " + resourcePath, e);
        }
    }

    @Override
    public List<BenefitPlan> findByCardCode(String cardCode) {
        return plans.stream()
                .filter(p -> cardCode.equalsIgnoreCase(p.getCardCode()))
                .toList();
    }

    @Override
    public BenefitPlan findByPlanId(String planId) {
        return plans.stream()
                .filter(p -> planId.equalsIgnoreCase(p.getPlanId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<BenefitPlan> findActivePlans(LocalDate date) {
        return plans.stream()
                .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()))
                .filter(p -> !date.isBefore(p.getValidFrom()) && !date.isAfter(p.getValidUntil()))
                .toList();
    }
}
