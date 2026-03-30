package com.cardsense.api.repository;

import com.cardsense.api.domain.BenefitPlan;

import java.time.LocalDate;
import java.util.List;

public interface BenefitPlanRepository {
    List<BenefitPlan> findByCardCode(String cardCode);
    BenefitPlan findByPlanId(String planId);
    List<BenefitPlan> findActivePlans(LocalDate date);
}
