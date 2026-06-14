package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.SavingsGoalType;
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
public class UpdateSavingsGoalRequest {

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotNull(message = "Goal type is required")
    private SavingsGoalType goalType;

    @NotNull(message = "Target amount is required")
    @DecimalMin(value = "0.01", message = "Target amount must be greater than or equal to 0.01")
    private BigDecimal targetAmount;

    private LocalDate targetDate;
}
