package com.cardsense.api.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationRequest {

    @NotNull(message = "Transaction amount is required")
    @Min(value = 0, message = "Transaction amount must be non-negative")
    @JsonAlias("transaction_amount")
    private Integer amount;

    @NotBlank(message = "Category is required")
    @JsonAlias("merchant_category")
    private String category;

    private List<String> cardCodes;

    private String location;

    @JsonAlias("transaction_date")
    private LocalDate date;
}
