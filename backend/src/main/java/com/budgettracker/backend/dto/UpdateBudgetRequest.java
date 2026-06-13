package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.RolloverRuleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBudgetRequest {

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotNull(message = "Amount limit is required")
    @DecimalMin(value = "0.01", message = "Amount limit must be greater than or equal to 0.01")
    private BigDecimal amountLimit;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private RolloverRuleType rolloverRule;
}
