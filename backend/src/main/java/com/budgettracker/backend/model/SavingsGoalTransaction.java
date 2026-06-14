package com.budgettracker.backend.model;

import com.budgettracker.backend.jooq.enums.SavingsTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingsGoalTransaction {
    private Long id;
    private Long savingsGoalId;
    private Long transactionId;
    private Long fromAccountId;
    private Long toAccountId;
    private SavingsTransactionType type;
    private LocalDateTime createdAt;
}
