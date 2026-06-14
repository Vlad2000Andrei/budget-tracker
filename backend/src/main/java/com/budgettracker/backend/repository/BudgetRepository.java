package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.enums.RolloverRuleType;
import com.budgettracker.backend.jooq.tables.records.BudgetsRecord;
import com.budgettracker.backend.model.Budget;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.budgettracker.backend.jooq.Tables.BUDGETS;

@Repository
public class BudgetRepository {

    private final DSLContext dsl;

    @Autowired
    public BudgetRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<Budget> findById(Long id) {
        return dsl.selectFrom(BUDGETS)
                .where(BUDGETS.ID.eq(id))
                .fetchOptional()
                .map(this::mapRecordToBudget);
    }

    public boolean existsById(Long id) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(BUDGETS)
                        .where(BUDGETS.ID.eq(id))
        );
    }

    public List<Budget> findByUserId(Long userId) {
        return dsl.selectFrom(BUDGETS)
                .where(BUDGETS.USER_ID.eq(userId))
                .fetch()
                .map(this::mapRecordToBudget);
    }

    @Transactional
    public Budget save(Budget budget) {
        LocalDateTime now = LocalDateTime.now();
        if (budget.getId() == null) {
            BudgetsRecord record = dsl.insertInto(BUDGETS)
                    .set(BUDGETS.USER_ID, budget.getUserId())
                    .set(BUDGETS.CATEGORY_ID, budget.getCategoryId())
                    .set(BUDGETS.AMOUNT_LIMIT, budget.getAmountLimit())
                    .set(BUDGETS.START_DATE, budget.getStartDate())
                    .set(BUDGETS.END_DATE, budget.getEndDate())
                    .set(BUDGETS.ROLLOVER_RULE, budget.getRolloverRule() != null ? budget.getRolloverRule() : RolloverRuleType.NONE)
                    .set(BUDGETS.CREATED_AT, now)
                    .set(BUDGETS.UPDATED_AT, now)
                    .returning()
                    .fetchOne();
            return mapRecordToBudget(record);
        } else {
            dsl.update(BUDGETS)
                    .set(BUDGETS.CATEGORY_ID, budget.getCategoryId())
                    .set(BUDGETS.AMOUNT_LIMIT, budget.getAmountLimit())
                    .set(BUDGETS.START_DATE, budget.getStartDate())
                    .set(BUDGETS.END_DATE, budget.getEndDate())
                    .set(BUDGETS.ROLLOVER_RULE, budget.getRolloverRule() != null ? budget.getRolloverRule() : RolloverRuleType.NONE)
                    .set(BUDGETS.UPDATED_AT, now)
                    .where(BUDGETS.ID.eq(budget.getId()))
                    .execute();
            budget.setUpdatedAt(now);
            return budget;
        }
    }

    @Transactional
    public void deleteById(Long id) {
        dsl.deleteFrom(BUDGETS)
                .where(BUDGETS.ID.eq(id))
                .execute();
    }

    public boolean hasOverlappingBudget(Long userId, Long categoryId, LocalDate startDate, LocalDate endDate, Long excludeId) {
        var categoryCondition = categoryId == null ? BUDGETS.CATEGORY_ID.isNull() : BUDGETS.CATEGORY_ID.eq(categoryId);
        var condition = BUDGETS.USER_ID.eq(userId)
                .and(categoryCondition)
                .and(BUDGETS.END_DATE.isNull().or(BUDGETS.END_DATE.ge(startDate)));
        if (endDate != null) {
            condition = condition.and(BUDGETS.START_DATE.le(endDate));
        }
        if (excludeId != null) {
            condition = condition.and(BUDGETS.ID.ne(excludeId));
        }
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(BUDGETS)
                        .where(condition)
        );
    }

    private Budget mapRecordToBudget(BudgetsRecord record) {
        if (record == null) {
            return null;
        }
        return Budget.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .categoryId(record.getCategoryId())
                .amountLimit(record.getAmountLimit())
                .startDate(record.getStartDate())
                .endDate(record.getEndDate())
                .rolloverRule(record.getRolloverRule())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
