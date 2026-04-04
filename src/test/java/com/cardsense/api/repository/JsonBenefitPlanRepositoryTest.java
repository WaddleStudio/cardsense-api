package com.cardsense.api.repository;

import com.cardsense.api.domain.BenefitPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonBenefitPlanRepositoryTest {

    private BenefitPlanRepository repository;

    @BeforeEach
    public void setup() {
        JsonBenefitPlanRepository repo = new JsonBenefitPlanRepository("benefit-plans.json");
        repo.init();
        repository = repo;
    }

    @Test
    public void testFindByCardCodeReturnsCubePlans() {
        List<BenefitPlan> plans = repository.findByCardCode("CATHAY_CUBE");
        assertEquals(7, plans.size());
        assertTrue(plans.stream().allMatch(p -> "CATHAY_CUBE".equals(p.getCardCode())));
        assertTrue(plans.stream().allMatch(p -> "CATHAY_CUBE_PLANS".equals(p.getExclusiveGroup())));
    }

    @Test
    public void testFindByCardCodeReturnsRichartPlans() {
        List<BenefitPlan> plans = repository.findByCardCode("TAISHIN_RICHART");
        assertEquals(7, plans.size());
    }

    @Test
    public void testFindByCardCodeReturnsUnicardPlans() {
        List<BenefitPlan> plans = repository.findByCardCode("ESUN_UNICARD");
        assertEquals(3, plans.size());
        assertTrue(plans.stream().allMatch(p -> "MONTHLY".equals(p.getSwitchFrequency())));
    }

    @Test
    public void testFindByCardCodeReturnsEmptyForUnknownCard() {
        List<BenefitPlan> plans = repository.findByCardCode("UNKNOWN_CARD");
        assertTrue(plans.isEmpty());
    }

    @Test
    public void testFindByPlanIdReturnsPlan() {
        BenefitPlan plan = repository.findByPlanId("CATHAY_CUBE_DIGITAL");
        assertEquals("CATHAY_CUBE_DIGITAL", plan.getPlanId());
        assertEquals("玩數位", plan.getPlanName());
        assertEquals("DAILY", plan.getSwitchFrequency());
    }

    @Test
    public void testFindByPlanIdReturnsNullForUnknown() {
        BenefitPlan plan = repository.findByPlanId("NONEXISTENT");
        assertNull(plan);
    }

    @Test
    public void testFindActivePlansFiltersByDate() {
        LocalDate testDate = LocalDate.of(2026, 3, 15);
        List<BenefitPlan> plans = repository.findActivePlans(testDate);
        assertEquals(17, plans.size());
        assertTrue(plans.stream().allMatch(p ->
                !testDate.isBefore(p.getValidFrom()) && !testDate.isAfter(p.getValidUntil())
        ));
    }

    @Test
    public void testFindActivePlansExcludesOutOfRange() {
        LocalDate beforeStart = LocalDate.of(2025, 12, 31);
        List<BenefitPlan> plans = repository.findActivePlans(beforeStart);
        assertTrue(plans.isEmpty());
    }

    @Test
    public void testUnicardUpRequiresSubscription() {
        BenefitPlan upPlan = repository.findByPlanId("ESUN_UNICARD_UP");
        assertTrue(upPlan.isRequiresSubscription());
        assertEquals("149 e point", upPlan.getSubscriptionCost());
    }
}
