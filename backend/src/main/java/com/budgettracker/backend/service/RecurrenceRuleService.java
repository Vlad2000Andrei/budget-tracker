package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.RecurringTransactionDto;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.jooq.enums.RecurrenceFrequency;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.RecurrenceRule;
import com.budgettracker.backend.model.Transaction;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.repository.RecurrenceRuleRepository;
import com.budgettracker.backend.repository.TransactionRepository;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.budgettracker.backend.jooq.Tables.RECURRENCE_RULES;
import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;

@Service
public class RecurrenceRuleService {

    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final DSLContext dsl;

    @Autowired
    public RecurrenceRuleService(RecurrenceRuleRepository recurrenceRuleRepository,
                                 TransactionRepository transactionRepository,
                                 CategoryRepository categoryRepository,
                                 DSLContext dsl) {
        this.recurrenceRuleRepository = recurrenceRuleRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.dsl = dsl;
    }

    public List<RecurringTransactionDto> getRecurringTransactionsForUser(User user) {
        // Query all transactions for user that are recurring (recurrence_rule_id is not null)
        // Group by recurrence_rule_id and pick the oldest one as the template
        List<Transaction> txs = dsl.selectFrom(TRANSACTIONS)
                .where(TRANSACTIONS.USER_ID.eq(user.getId()).and(TRANSACTIONS.RECURRENCE_RULE_ID.isNotNull()))
                .orderBy(TRANSACTIONS.DATE.asc())
                .fetch()
                .map(this::mapRecordToTransaction);

        if (txs.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Transaction>> groupedByRule = txs.stream()
                .collect(Collectors.groupingBy(Transaction::getRecurrenceRuleId));

        List<Long> ruleIds = new ArrayList<>(groupedByRule.keySet());
        List<RecurrenceRule> rules = dsl.selectFrom(RECURRENCE_RULES)
                .where(RECURRENCE_RULES.ID.in(ruleIds))
                .fetch()
                .map(this::mapRecordToRecurrenceRule);

        Map<Long, RecurrenceRule> ruleMap = rules.stream()
                .collect(Collectors.toMap(RecurrenceRule::getId, r -> r));

        return ruleIds.stream()
                .map(ruleId -> {
                    RecurrenceRule rule = ruleMap.get(ruleId);
                    List<Transaction> spawned = groupedByRule.get(ruleId);
                    Transaction template = spawned.get(0); // oldest is the template
                    Category category = categoryRepository.findById(template.getCategoryId()).orElse(null);

                    LocalDate nextDate = calculateNextOccurrence(rule, spawned);

                    return RecurringTransactionDto.builder()
                            .id(ruleId)
                            .categoryId(template.getCategoryId())
                            .categoryName(category != null ? category.getName() : "Unknown")
                            .categoryIcon(category != null ? category.getIcon() : "")
                            .categoryColor(category != null ? category.getColor() : "")
                            .type(template.getType())
                            .amount(template.getAmount())
                            .currency(template.getCurrency())
                            .frequency(rule.getFrequency())
                            .interval(rule.getInterval())
                            .nextDate(nextDate)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteRecurrenceRule(Long ruleId, User user) {
        // Ownership check: verify if there is at least one transaction belonging to the user that references this recurrenceRuleId
        boolean isOwned = dsl.fetchExists(
                dsl.selectOne()
                        .from(TRANSACTIONS)
                        .where(TRANSACTIONS.RECURRENCE_RULE_ID.eq(ruleId)
                                .and(TRANSACTIONS.USER_ID.eq(user.getId())))
        );

        if (!isOwned) {
            throw new ForbiddenActionException("You do not have access to this recurrence rule");
        }

        recurrenceRuleRepository.deleteById(ruleId);
    }

    private LocalDate calculateNextOccurrence(RecurrenceRule rule, List<Transaction> spawned) {
        LocalDate lastOccurrenceDate = rule.getStartDate();
        for (Transaction tx : spawned) {
            LocalDate txDate = tx.getDate().toLocalDate();
            if (txDate.isAfter(lastOccurrenceDate)) {
                lastOccurrenceDate = txDate;
            }
        }
        return getNextOccurrence(lastOccurrenceDate, rule.getFrequency(), rule.getInterval());
    }

    private LocalDate getNextOccurrence(LocalDate current, RecurrenceFrequency frequency, int interval) {
        switch (frequency) {
            case DAILY:
                return current.plusDays(interval);
            case WEEKLY:
                return current.plusWeeks(interval);
            case MONTHLY:
                return current.plusMonths(interval);
            case YEARLY:
                return current.plusYears(interval);
            default:
                throw new IllegalArgumentException("Unknown frequency: " + frequency);
        }
    }

    private Transaction mapRecordToTransaction(com.budgettracker.backend.jooq.tables.records.TransactionsRecord record) {
        if (record == null) return null;
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

    private RecurrenceRule mapRecordToRecurrenceRule(com.budgettracker.backend.jooq.tables.records.RecurrenceRulesRecord record) {
        if (record == null) return null;
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
