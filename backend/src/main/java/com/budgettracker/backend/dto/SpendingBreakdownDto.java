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
public class SpendingBreakdownDto {
    private String currency;
    private BigDecimal totalExpense;
    private List<CategorySpendingItem> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySpendingItem {
        private Long categoryId;
        private String categoryName;
        private String icon;
        private String color;
        private BigDecimal amount;
        private double percentage;
    }
}
