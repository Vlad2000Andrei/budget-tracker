package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.enums.SavingsTransactionType;
import com.budgettracker.backend.jooq.tables.records.SavingsTransactionsRecord;
import com.budgettracker.backend.model.SavingsGoalTransaction;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.budgettracker.backend.jooq.Tables.SAVINGS_TRANSACTIONS;

@Repository
public class SavingsGoalTransactionRepository {

    private final DSLContext dsl;

    @Autowired
    public SavingsGoalTransactionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<SavingsGoalTransaction> findByTransactionId(Long transactionId) {
        return dsl.selectFrom(SAVINGS_TRANSACTIONS)
                .where(SAVINGS_TRANSACTIONS.TRANSACTION_ID.eq(transactionId))
                .fetchOptional()
                .map(this::mapRecord);
    }

    @Transactional
    public SavingsGoalTransaction save(SavingsGoalTransaction sgt) {
        LocalDateTime now = LocalDateTime.now();
        SavingsTransactionsRecord record = dsl.insertInto(SAVINGS_TRANSACTIONS)
                .set(SAVINGS_TRANSACTIONS.TRANSACTION_ID, sgt.getTransactionId())
                .set(SAVINGS_TRANSACTIONS.FROM_ACCOUNT_ID, sgt.getFromAccountId())
                .set(SAVINGS_TRANSACTIONS.TO_ACCOUNT_ID, sgt.getToAccountId())
                .set(SAVINGS_TRANSACTIONS.TYPE, sgt.getType())
                .set(SAVINGS_TRANSACTIONS.CREATED_AT, now)
                .returning()
                .fetchOne();
        return mapRecord(record);
    }

    @Transactional
    public void deleteByTransactionId(Long transactionId) {
        dsl.deleteFrom(SAVINGS_TRANSACTIONS)
                .where(SAVINGS_TRANSACTIONS.TRANSACTION_ID.eq(transactionId))
                .execute();
    }

    private SavingsGoalTransaction mapRecord(SavingsTransactionsRecord record) {
        if (record == null) return null;
        return SavingsGoalTransaction.builder()
                .id(record.getId())
                .savingsGoalId(null)
                .transactionId(record.getTransactionId())
                .fromAccountId(record.getFromAccountId())
                .toAccountId(record.getToAccountId())
                .type(record.getType())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
