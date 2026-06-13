package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.CategoryType;
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
public class TransactionDto {
    private Long id;
    private Long categoryId;
    private Long accountId;
    private Long recurrenceRuleId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal convertedAmount;
    private BigDecimal exchangeRate;
    private CategoryType type;
    private String notes;
    private LocalDateTime date;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private RecurrenceRuleDto recurrenceRule;
}
