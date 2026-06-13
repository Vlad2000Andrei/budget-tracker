package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.RecurrenceFrequency;
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
public class RecurringTransactionDto {
    private Long id; // Recurrence rule ID
    private Long categoryId;
    private String categoryName;
    private String categoryIcon;
    private String categoryColor;
    private CategoryType type;
    private BigDecimal amount;
    private String currency;
    private RecurrenceFrequency frequency;
    private int interval;
    private LocalDate nextDate;
}
