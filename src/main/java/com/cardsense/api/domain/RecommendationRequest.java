package com.cardsense.api.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationRequest {

    @NotNull(message = "Transaction amount is required")
    @Min(value = 0, message = "Transaction amount must be non-negative")
    @JsonProperty("transaction_amount")
    private Integer transactionAmount;

    @NotNull(message = "Merchant category is required")
    @JsonProperty("merchant_category")
    private String merchantCategory;

    @JsonProperty("merchant_name")
    private String merchantName;

    @JsonProperty("channel")
    private String channel; // "online" or "offline"

    @NotNull(message = "Transaction date is required")
    @JsonProperty("transaction_date")
    private LocalDate transactionDate;
}
