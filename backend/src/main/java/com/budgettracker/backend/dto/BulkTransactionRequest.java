package com.budgettracker.backend.dto;

import com.budgettracker.backend.jooq.enums.SavingsTransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkTransactionRequest {

    @NotEmpty(message = "Transactions list cannot be empty")
    @Valid
    private List<BulkTransactionItem> transactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkTransactionItem {

        @NotNull(message = "Amount is required")
        private BigDecimal amount;

        @NotNull(message = "Currency is required")
        private String currency;

        @NotNull(message = "Date is required")
        private LocalDateTime date;

        private String notes;

        @NotNull(message = "Import transaction type is required")
        private ImportTransactionType importType;

        private Long categoryId;

        private Long accountId;

        private Long transferToAccountId;

        private SavingsTransactionType savingsType;

        private Long savingsToAccountId;

        private CreateRecurrenceRuleRequest recurrenceRule;

        private Long existingRecurrenceRuleId;
    }

    public enum ImportTransactionType {
        INCOME,
        EXPENSE,
        TRANSFER,
        SAVINGS
    }
}
