package com.budgettracker.backend.model;

import com.budgettracker.backend.jooq.enums.RolloverRuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Budget {
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
