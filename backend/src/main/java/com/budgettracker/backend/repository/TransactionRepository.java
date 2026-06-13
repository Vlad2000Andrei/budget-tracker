package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.tables.records.TransactionsRecord;
import com.budgettracker.backend.model.Transaction;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;

@Repository
public class TransactionRepository {

    private final DSLContext dsl;

    @Autowired
    public TransactionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<Transaction> findById(Long id) {
        return dsl.selectFrom(TRANSACTIONS)
                .where(TRANSACTIONS.ID.eq(id))
                .fetchOptional()
                .map(this::mapRecordToTransaction);
    }

    public List<Transaction> findAll(Long userId, Long accountId, Long categoryId, LocalDate startDate, LocalDate endDate, CategoryType type) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(TRANSACTIONS.USER_ID.eq(userId));

        if (accountId != null) {
            conditions.add(TRANSACTIONS.ACCOUNT_ID.eq(accountId));
        }
        if (categoryId != null) {
            conditions.add(TRANSACTIONS.CATEGORY_ID.eq(categoryId));
        }
        if (startDate != null) {
            conditions.add(TRANSACTIONS.DATE.ge(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            conditions.add(TRANSACTIONS.DATE.lt(endDate.plusDays(1).atStartOfDay()));
        }
        if (type != null) {
            conditions.add(TRANSACTIONS.TYPE.eq(type));
        }

        return dsl.selectFrom(TRANSACTIONS)
                .where(conditions)
                .orderBy(TRANSACTIONS.DATE.desc())
                .fetch()
                .map(this::mapRecordToTransaction);
    }

    @Transactional
    public Transaction save(Transaction transaction) {
        LocalDateTime now = LocalDateTime.now();
        if (transaction.getId() == null) {
            TransactionsRecord record = dsl.insertInto(TRANSACTIONS)
                    .set(TRANSACTIONS.USER_ID, transaction.getUserId())
                    .set(TRANSACTIONS.CATEGORY_ID, transaction.getCategoryId())
                    .set(TRANSACTIONS.ACCOUNT_ID, transaction.getAccountId())
                    .set(TRANSACTIONS.RECURRENCE_RULE_ID, transaction.getRecurrenceRuleId())
                    .set(TRANSACTIONS.AMOUNT, transaction.getAmount())
                    .set(TRANSACTIONS.CURRENCY, transaction.getCurrency())
                    .set(TRANSACTIONS.CONVERTED_AMOUNT, transaction.getConvertedAmount())
                    .set(TRANSACTIONS.EXCHANGE_RATE, transaction.getExchangeRate())
                    .set(TRANSACTIONS.TYPE, transaction.getType())
                    .set(TRANSACTIONS.NOTES, transaction.getNotes())
                    .set(TRANSACTIONS.DATE, transaction.getDate() != null ? transaction.getDate() : now)
                    .set(TRANSACTIONS.CREATED_AT, now)
                    .set(TRANSACTIONS.UPDATED_AT, now)
                    .returning()
                    .fetchOne();
            return mapRecordToTransaction(record);
        } else {
            dsl.update(TRANSACTIONS)
                    .set(TRANSACTIONS.CATEGORY_ID, transaction.getCategoryId())
                    .set(TRANSACTIONS.ACCOUNT_ID, transaction.getAccountId())
                    .set(TRANSACTIONS.RECURRENCE_RULE_ID, transaction.getRecurrenceRuleId())
                    .set(TRANSACTIONS.AMOUNT, transaction.getAmount())
                    .set(TRANSACTIONS.CURRENCY, transaction.getCurrency())
                    .set(TRANSACTIONS.CONVERTED_AMOUNT, transaction.getConvertedAmount())
                    .set(TRANSACTIONS.EXCHANGE_RATE, transaction.getExchangeRate())
                    .set(TRANSACTIONS.TYPE, transaction.getType())
                    .set(TRANSACTIONS.NOTES, transaction.getNotes())
                    .set(TRANSACTIONS.DATE, transaction.getDate())
                    .set(TRANSACTIONS.UPDATED_AT, now)
                    .where(TRANSACTIONS.ID.eq(transaction.getId()))
                    .execute();
            transaction.setUpdatedAt(now);
            return transaction;
        }
    }

    @Transactional
    public void deleteById(Long id) {
        dsl.deleteFrom(TRANSACTIONS)
                .where(TRANSACTIONS.ID.eq(id))
                .execute();
    }

    public List<Transaction> findByRecurrenceRuleId(Long recurrenceRuleId) {
        return dsl.selectFrom(TRANSACTIONS)
                .where(TRANSACTIONS.RECURRENCE_RULE_ID.eq(recurrenceRuleId))
                .orderBy(TRANSACTIONS.DATE.asc())
                .fetch()
                .map(this::mapRecordToTransaction);
    }

    private Transaction mapRecordToTransaction(TransactionsRecord record) {
        if (record == null) {
            return null;
        }
        return Transaction.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .categoryId(record.getCategoryId())
                .accountId(record.getAccountId())
                .recurrenceRuleId(record.getRecurrenceRuleId())
                .amount(record.getAmount())
                .currency(record.getCurrency())
                .convertedAmount(record.getConvertedAmount())
                .exchangeRate(record.getExchangeRate())
                .type(record.getType())
                .notes(record.getNotes())
                .date(record.getDate())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
