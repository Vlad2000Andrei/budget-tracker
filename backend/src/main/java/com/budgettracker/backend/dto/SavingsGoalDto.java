package com.budgettracker.backend.dto;

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
public class SavingsGoalDto {
    private Long id;
    private Long userId;
    private Long categoryId;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private LocalDate targetDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
