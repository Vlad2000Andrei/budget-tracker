package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.CategoryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTransactionRequest {

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    private Long accountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-character ISO code")
    private String currency;

    @NotNull(message = "Type is required")
    private CategoryType type;

    private String notes;

    @NotNull(message = "Date is required")
    private LocalDateTime date;

    private CreateRecurrenceRuleRequest recurrenceRule;
}
