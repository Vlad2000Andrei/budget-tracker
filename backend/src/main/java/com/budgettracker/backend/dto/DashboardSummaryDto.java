package com.budgettracker.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {
    private BigDecimal totalBalance;
    private String balanceCurrency;
    private BigDecimal monthIncome;
    private BigDecimal monthExpenses;
    private List<BudgetSummaryDto> budgets;
    private List<SavingsGoalSummaryDto> savingsGoals;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetSummaryDto {
        private Long id;
        private Long categoryId;
        private String categoryName;
        private String categoryIcon;
        private String categoryColor;
        private BigDecimal spent;
        private BigDecimal limit;
        private Integer pct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavingsGoalSummaryDto {
        private Long id;
        private Long categoryId;
        private String categoryName;
        private String categoryIcon;
        private String categoryColor;
        private BigDecimal current;
        private BigDecimal target;
        private Integer pct;
    }
}
