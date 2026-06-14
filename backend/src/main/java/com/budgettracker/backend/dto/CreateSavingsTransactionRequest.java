package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.SavingsTransactionType;
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
public class CreateSavingsTransactionRequest {

    /** Account the money is taken FROM (e.g. Checking for a deposit, Savings for a withdrawal). */
    @NotNull(message = "fromAccountId is required")
    private Long fromAccountId;

    /** Account the money is sent TO (e.g. Savings for a deposit, Checking for a withdrawal). */
    @NotNull(message = "toAccountId is required")
    private Long toAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than or equal to 0.01")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-character ISO code")
    private String currency;

    @NotNull(message = "Transaction type is required (DEPOSIT or WITHDRAWAL)")
    private SavingsTransactionType type;

    @NotNull(message = "Date is required")
    private LocalDateTime date;

    private String notes;

    private Long categoryId;
}
