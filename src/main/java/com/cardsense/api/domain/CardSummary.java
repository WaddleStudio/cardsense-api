package com.cardsense.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardSummary {
    private String cardCode;
    private String cardName;
    private String cardStatus;
    private Integer annualFee;
    private String applyUrl;
    private String bankCode;
    private String bankName;
}