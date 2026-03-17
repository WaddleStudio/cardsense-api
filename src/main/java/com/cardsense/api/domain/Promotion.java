package com.cardsense.api.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion {

    @JsonProperty("promo_id")
    private String promoId;

    @JsonProperty("promo_version_id")
    private String promoVersionId;

    @JsonProperty("card_id")
    private String cardId;

    private String bank;

    private List<String> categories;

    private String channel;

    @JsonProperty("start_date")
    private LocalDate startDate;

    @JsonProperty("end_date")
    private LocalDate endDate;

    @JsonProperty("min_amount")
    private Integer minAmount;

    @JsonProperty("reward_type")
    private String rewardType;

    @JsonProperty("reward_rate")
    private Double rewardRate;

    @JsonProperty("reward_cap")
    private Integer rewardCap;

    @JsonProperty("frequency_limit")
    private String frequencyLimit;

    @JsonProperty("requires_registration")
    private boolean requiresRegistration;

    @JsonProperty("excluded_conditions")
    private List<String> excludedConditions;

    @JsonProperty("source_url")
    private String sourceUrl;

    private String summary;

    @JsonProperty("raw_text_hash")
    private String rawTextHash;

    @JsonProperty("extractor_version")
    private String extractorVersion;

    @JsonProperty("extracted_at")
    private String extractedAt; // Keeping as string for simplicity in matching schema, or could use OffsetDateTime

    private Double confidence;
}
