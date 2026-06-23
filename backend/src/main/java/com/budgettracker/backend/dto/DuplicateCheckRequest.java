package com.budgettracker.backend.dto;

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
public class DuplicateCheckRequest {

    @NotNull(message = "Account ID is required")
    private Long accountId;

    @NotEmpty(message = "Transactions list cannot be empty")
    @Valid
    private List<CandidateTransaction> transactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateTransaction {
        @NotNull(message = "Date is required")
        private LocalDateTime date;

        @NotNull(message = "Amount is required")
        private BigDecimal amount;
    }
}
