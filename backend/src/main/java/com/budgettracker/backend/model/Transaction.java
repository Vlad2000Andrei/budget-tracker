package com.budgettracker.backend.model;

import com.budgettracker.backend.jooq.enums.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    private Long id;
    private Long userId;
    private Long categoryId;
    private Long accountId;
    private Long recurrenceRuleId;
    private Long linkedTransactionId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal convertedAmount;
    private String convertedCurrency;
    private BigDecimal exchangeRate;
    private CategoryType type;
    private String notes;
    private LocalDateTime date;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
