package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.enums.SavingsGoalType;
import com.budgettracker.backend.jooq.tables.records.SavingsGoalsRecord;
import com.budgettracker.backend.model.SavingsGoal;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.budgettracker.backend.jooq.Tables.SAVINGS_GOALS;
import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.sum;

@Repository
public class SavingsGoalRepository {

    private final DSLContext dsl;

    @Autowired
    public SavingsGoalRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<SavingsGoal> findById(Long id) {
        return dsl.selectFrom(SAVINGS_GOALS)
                .where(SAVINGS_GOALS.ID.eq(id))
                .fetchOptional()
                .map(this::mapRecordToSavingsGoal);
    }

    public boolean existsById(Long id) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(SAVINGS_GOALS)
                        .where(SAVINGS_GOALS.ID.eq(id))
        );
    }

    public List<SavingsGoal> findByUserId(Long userId) {
        return dsl.selectFrom(SAVINGS_GOALS)
                .where(SAVINGS_GOALS.USER_ID.eq(userId))
                .fetch()
                .map(this::mapRecordToSavingsGoal);
    }

    public List<SavingsGoal> findByCategoryId(Long categoryId) {
        return dsl.selectFrom(SAVINGS_GOALS)
                .where(SAVINGS_GOALS.CATEGORY_ID.eq(categoryId))
                .fetch()
                .map(this::mapRecordToSavingsGoal);
    }

    public List<SavingsGoal> findByCategoryIdIn(List<Long> categoryIds) {
        return dsl.selectFrom(SAVINGS_GOALS)
                .where(SAVINGS_GOALS.CATEGORY_ID.in(categoryIds))
                .fetch()
                .map(this::mapRecordToSavingsGoal);
    }

    @Transactional
    public SavingsGoal save(SavingsGoal goal) {
        LocalDateTime now = LocalDateTime.now();
        SavingsGoalType goalType = goal.getGoalType() != null ? goal.getGoalType() : SavingsGoalType.ONE_OFF;
        if (goal.getId() == null) {
            SavingsGoalsRecord record = dsl.insertInto(SAVINGS_GOALS)
                    .set(SAVINGS_GOALS.USER_ID, goal.getUserId())
                    .set(SAVINGS_GOALS.CATEGORY_ID, goal.getCategoryId())
                    .set(SAVINGS_GOALS.GOAL_TYPE, goalType)
                    .set(SAVINGS_GOALS.TARGET_AMOUNT, goal.getTargetAmount())
                    .set(SAVINGS_GOALS.CURRENT_AMOUNT, goal.getCurrentAmount() != null ? goal.getCurrentAmount() : BigDecimal.ZERO)
                    .set(SAVINGS_GOALS.TARGET_DATE, goal.getTargetDate())
                    .set(SAVINGS_GOALS.CREATED_AT, now)
                    .set(SAVINGS_GOALS.UPDATED_AT, now)
                    .returning()
                    .fetchOne();
            return mapRecordToSavingsGoal(record);
        } else {
            dsl.update(SAVINGS_GOALS)
                    .set(SAVINGS_GOALS.CATEGORY_ID, goal.getCategoryId())
                    .set(SAVINGS_GOALS.GOAL_TYPE, goalType)
                    .set(SAVINGS_GOALS.TARGET_AMOUNT, goal.getTargetAmount())
                    .set(SAVINGS_GOALS.CURRENT_AMOUNT, goal.getCurrentAmount() != null ? goal.getCurrentAmount() : BigDecimal.ZERO)
                    .set(SAVINGS_GOALS.TARGET_DATE, goal.getTargetDate())
                    .set(SAVINGS_GOALS.UPDATED_AT, now)
                    .where(SAVINGS_GOALS.ID.eq(goal.getId()))
                    .execute();
            goal.setGoalType(goalType);
            goal.setUpdatedAt(now);
            return goal;
        }
    }

    @Transactional
    public void deleteById(Long id) {
        dsl.deleteFrom(SAVINGS_GOALS)
                .where(SAVINGS_GOALS.ID.eq(id))
                .execute();
    }

    public List<org.jooq.Record2<BigDecimal, String>> findSavingsTransactions(Long userId, List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return dsl.select(TRANSACTIONS.CONVERTED_AMOUNT, TRANSACTIONS.CONVERTED_CURRENCY)
                .from(TRANSACTIONS)
                .where(TRANSACTIONS.USER_ID.eq(userId)
                        .and(TRANSACTIONS.CATEGORY_ID.in(categoryIds)))
                .fetch();
    }

    public List<org.jooq.Record2<BigDecimal, String>> findSavingsTransactionsInPeriod(Long userId, List<Long> categoryIds, LocalDateTime start, LocalDateTime end) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return dsl.select(TRANSACTIONS.CONVERTED_AMOUNT, TRANSACTIONS.CONVERTED_CURRENCY)
                .from(TRANSACTIONS)
                .where(TRANSACTIONS.USER_ID.eq(userId)
                        .and(TRANSACTIONS.CATEGORY_ID.in(categoryIds))
                        .and(TRANSACTIONS.DATE.ge(start))
                        .and(TRANSACTIONS.DATE.lt(end)))
                .fetch();
    }

    private SavingsGoal mapRecordToSavingsGoal(SavingsGoalsRecord record) {
        if (record == null) {
            return null;
        }
        return SavingsGoal.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .categoryId(record.getCategoryId())
                .goalType(record.getGoalType())
                .targetAmount(record.getTargetAmount())
                .currentAmount(record.getCurrentAmount())
                .targetDate(record.getTargetDate())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
