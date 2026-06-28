package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.tables.records.RecurrenceRulesRecord;
import com.budgettracker.backend.model.RecurrenceRule;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.budgettracker.backend.jooq.Tables.RECURRENCE_RULES;

@Repository
public class RecurrenceRuleRepository {

    private final DSLContext dsl;

    @Autowired
    public RecurrenceRuleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<RecurrenceRule> findById(Long id) {
        return dsl.selectFrom(RECURRENCE_RULES)
                .where(RECURRENCE_RULES.ID.eq(id))
                .fetchOptional()
                .map(this::mapRecordToRecurrenceRule);
    }

    @Transactional
    public RecurrenceRule save(RecurrenceRule rule) {
        LocalDateTime now = LocalDateTime.now();
        if (rule.getId() == null) {
            RecurrenceRulesRecord record = dsl.insertInto(RECURRENCE_RULES)
                    .set(RECURRENCE_RULES.FREQUENCY, rule.getFrequency())
                    .set(RECURRENCE_RULES.INTERVAL, rule.getInterval())
                    .set(RECURRENCE_RULES.START_DATE, rule.getStartDate())
                    .set(RECURRENCE_RULES.END_DATE, rule.getEndDate())
                    .set(RECURRENCE_RULES.CREATED_AT, now)
                    .set(RECURRENCE_RULES.UPDATED_AT, now)
                    .returning()
                    .fetchOne();
            return mapRecordToRecurrenceRule(record);
        } else {
            dsl.update(RECURRENCE_RULES)
                    .set(RECURRENCE_RULES.FREQUENCY, rule.getFrequency())
                    .set(RECURRENCE_RULES.INTERVAL, rule.getInterval())
                    .set(RECURRENCE_RULES.START_DATE, rule.getStartDate())
                    .set(RECURRENCE_RULES.END_DATE, rule.getEndDate())
                    .set(RECURRENCE_RULES.UPDATED_AT, now)
                    .where(RECURRENCE_RULES.ID.eq(rule.getId()))
                    .execute();
            rule.setUpdatedAt(now);
            return rule;
        }
    }

    @Transactional
    public void deleteById(Long id) {
        dsl.deleteFrom(RECURRENCE_RULES)
                .where(RECURRENCE_RULES.ID.eq(id))
                .execute();
    }

    public List<RecurrenceRule> findActiveRules(LocalDate date) {
        return dsl.selectFrom(RECURRENCE_RULES)
                .where(RECURRENCE_RULES.START_DATE.le(date)
                        .and(RECURRENCE_RULES.END_DATE.isNull()
                                .or(RECURRENCE_RULES.END_DATE.ge(date))))
                .fetch()
                .map(this::mapRecordToRecurrenceRule);
    }

    public List<RecurrenceRule> findAllByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return dsl.selectFrom(RECURRENCE_RULES)
                .where(RECURRENCE_RULES.ID.in(ids))
                .fetch()
                .map(this::mapRecordToRecurrenceRule);
    }

    private RecurrenceRule mapRecordToRecurrenceRule(RecurrenceRulesRecord record) {
        if (record == null) {
            return null;
        }
        return RecurrenceRule.builder()
                .id(record.getId())
                .frequency(record.getFrequency())
                .interval(record.getInterval())
                .startDate(record.getStartDate())
                .endDate(record.getEndDate())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
