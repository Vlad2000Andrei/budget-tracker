package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.RolloverRuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetDto {
    private Long id;
    private Long userId;
    private Long categoryId;
    private BigDecimal amountLimit;
    private LocalDate startDate;
    private LocalDate endDate;
    private RolloverRuleType rolloverRule;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
