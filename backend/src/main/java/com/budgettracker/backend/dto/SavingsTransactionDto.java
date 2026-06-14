package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.SavingsTransactionType;
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
public class SavingsTransactionDto {
    private Long id;
    private Long savingsGoalId;
    private Long transactionId;
    private Long fromAccountId;
    private String fromAccountName;
    private Long toAccountId;
    private String toAccountName;
    private SavingsTransactionType type;
    private BigDecimal amount;
    private String currency;
    private BigDecimal convertedAmount;
    private String convertedCurrency;
    private LocalDateTime date;
    private String notes;
    private LocalDateTime createdAt;
}
