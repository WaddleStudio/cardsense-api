package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Details of how a reward was converted to TWD equivalent.
 * Attached to each PromotionRewardBreakdown and CardRecommendation
 * so the frontend can display the exchange rate used.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardDetail {
    /** Raw reward quantity in native units (miles earned, points earned, or TWD). */
    private BigDecimal rawReward;

    /** Native unit name, e.g. "MILES", "POINTS", "TWD". */
    private String rawUnit;

    /** Exchange rate used: 1 native unit = exchangeRate TWD. */
    private BigDecimal exchangeRate;

    /** Whether the rate came from system defaults or user override. */
    private String exchangeRateSource; // "SYSTEM_DEFAULT" or "USER_CUSTOM"

    /** Final TWD equivalent = rawReward × exchangeRate (for POINTS/MILES) or rawReward (for PERCENT/FIXED). */
    private Integer ntdEquivalent;

    /** Human-readable note, e.g. "航空哩程 × 0.40 TWD/哩". */
    private String note;
}
