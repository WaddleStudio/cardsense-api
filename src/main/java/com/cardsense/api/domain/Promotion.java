package com.cardsense.api.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion {

    @JsonAlias("promo_id")
    private String promoId;

    @JsonAlias("promo_version_id")
    private String promoVersionId;

    private String cardCode;

    private String cardName;

    private String cardStatus;

    private Integer annualFee;

    private String applyUrl;

    private String bankCode;

    @JsonAlias("bank")
    private String bankName;

    private String category;

    private String channel;

    @JsonAlias("start_date")
    private LocalDate validFrom;

    @JsonAlias("end_date")
    private LocalDate validUntil;

    @JsonAlias("min_amount")
    private Integer minAmount;

    private String cashbackType;

    private BigDecimal cashbackValue;

    private Integer maxCashback;

    @JsonAlias("frequency_limit")
    private String frequencyLimit;

    @JsonAlias("requires_registration")
    private boolean requiresRegistration;

    private List<String> conditions;

    @JsonAlias("excluded_conditions")
    private List<String> excludedConditions;

    private String status;
}
