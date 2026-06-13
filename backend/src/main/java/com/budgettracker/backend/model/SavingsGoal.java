package com.budgettracker.backend.model;

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
public class SavingsGoal {
    private Long id;
    private Long userId;
    private Long categoryId;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private LocalDate targetDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
