package com.budgettracker.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateCheckResponse {

    private List<DuplicateResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicateResult {
        private boolean isPotentialDuplicate;
        private Long existingTransactionId;
        private Long categoryId;
        private String importType;
        private Long transferToAccountId;
        private String savingsType;
        private Long savingsToAccountId;
    }
}
