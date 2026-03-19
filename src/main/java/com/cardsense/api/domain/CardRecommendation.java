package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardRecommendation {
    private String cardName;
    private String bankName;
    private String cashbackType;
    private BigDecimal cashbackValue;
    private Integer estimatedReturn;
    private String reason;
    private String promotionId;
    private String promoVersionId;
    private LocalDate validUntil;
    private List<String> conditions;
    private String applyUrl;
}