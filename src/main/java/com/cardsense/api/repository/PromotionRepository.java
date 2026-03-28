package com.cardsense.api.repository;

import com.cardsense.api.domain.Promotion;

import java.time.LocalDate;
import java.util.List;

public interface PromotionRepository {
    List<Promotion> findActivePromotions(LocalDate date);

    List<Promotion> findAllPromotions();

    List<Promotion> findPromotionsByCardCode(String cardCode, LocalDate date);
}
