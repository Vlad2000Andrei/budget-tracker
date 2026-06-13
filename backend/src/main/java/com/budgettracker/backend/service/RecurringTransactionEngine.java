package com.budgettracker.backend.service;

import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.RecurrenceFrequency;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.RecurrenceRule;
import com.budgettracker.backend.model.Transaction;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.AccountRepository;
import com.budgettracker.backend.repository.RecurrenceRuleRepository;
import com.budgettracker.backend.repository.TransactionRepository;
import com.budgettracker.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class RecurringTransactionEngine {

    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final CurrencyExchangeService currencyExchangeService;
    private final SavingsGoalService savingsGoalService;

    @Autowired
    public RecurringTransactionEngine(RecurrenceRuleRepository recurrenceRuleRepository,
                                      TransactionRepository transactionRepository,
                                      AccountRepository accountRepository,
                                      UserRepository userRepository,
                                      CurrencyExchangeService currencyExchangeService,
                                      SavingsGoalService savingsGoalService) {
        this.recurrenceRuleRepository = recurrenceRuleRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.currencyExchangeService = currencyExchangeService;
        this.savingsGoalService = savingsGoalService;
    }

    @Scheduled(cron = "0 0 0 * * *") // Run daily at midnight
    @Transactional
    public void processRecurringTransactions() {
        LocalDate today = LocalDate.now();
        log.info("Starting recurring transaction processing for date: {}", today);
        List<RecurrenceRule> activeRules = recurrenceRuleRepository.findActiveRules(today);
        for (RecurrenceRule rule : activeRules) {
            try {
                processRule(rule, today);
            } catch (Exception e) {
                log.error("Error processing recurrence rule ID: {}", rule.getId(), e);
            }
        }
        log.info("Finished recurring transaction processing.");
    }

    @Transactional
    public void processRule(RecurrenceRule rule, LocalDate today) {
        List<Transaction> spawned = transactionRepository.findByRecurrenceRuleId(rule.getId());
        if (spawned.isEmpty()) {
            log.warn("No template transaction found for recurrence rule ID: {}", rule.getId());
            return;
        }

        // The first transaction is the template
        Transaction template = spawned.get(0);
        
        // Find the latest processed occurrence date
        LocalDate lastOccurrenceDate = rule.getStartDate();
        for (Transaction tx : spawned) {
            LocalDate txDate = tx.getDate().toLocalDate();
            if (txDate.isAfter(lastOccurrenceDate)) {
                lastOccurrenceDate = txDate;
            }
        }

        // Calculate next occurrences from lastOccurrenceDate up to today
        LocalDate nextDate = getNextOccurrence(lastOccurrenceDate, rule.getFrequency(), rule.getInterval());
        while (!nextDate.isAfter(today)) {
            // Check if rule has ended
            if (rule.getEndDate() != null && nextDate.isAfter(rule.getEndDate())) {
                break;
            }

            spawnTransaction(template, rule, nextDate);
            nextDate = getNextOccurrence(nextDate, rule.getFrequency(), rule.getInterval());
        }
    }

    private void spawnTransaction(Transaction template, RecurrenceRule rule, LocalDate occurrenceDate) {
        log.info("Spawning recurring transaction for rule ID: {} on date: {}", rule.getId(), occurrenceDate);

        User user = userRepository.findById(template.getUserId()).orElse(null);
        if (user == null) {
            log.error("User not found for ID: {}", template.getUserId());
            return;
        }

        // Currency conversions
        BigDecimal exchangeRate = currencyExchangeService.getExchangeRate(template.getCurrency(), user.getDefaultCurrency());
        BigDecimal convertedAmount = template.getAmount().multiply(exchangeRate).setScale(4, java.math.RoundingMode.HALF_UP);

        LocalDateTime occurrenceDateTime = occurrenceDate.atTime(template.getDate().toLocalTime());

        Transaction newTx = Transaction.builder()
                .userId(template.getUserId())
                .categoryId(template.getCategoryId())
                .accountId(template.getAccountId())
                .recurrenceRuleId(rule.getId())
                .amount(template.getAmount())
                .currency(template.getCurrency().toUpperCase())
                .convertedAmount(convertedAmount)
                .exchangeRate(exchangeRate)
                .type(template.getType())
                .notes(template.getNotes())
                .date(occurrenceDateTime)
                .build();

        transactionRepository.save(newTx);

        // Adjust Account Balance if applicable
        if (template.getAccountId() != null) {
            Account account = accountRepository.findById(template.getAccountId()).orElse(null);
            if (account != null) {
                BigDecimal accountAmount = currencyExchangeService.convert(template.getAmount(), template.getCurrency(), account.getCurrency());
                adjustAccountBalance(account, accountAmount, template.getType());
            }
        }

        // Reconcile savings goals if applicable
        if (template.getType() == CategoryType.SAVINGS) {
            savingsGoalService.reconcileAllGoalsForCategory(user.getId(), template.getCategoryId());
        }
    }

    private void adjustAccountBalance(Account account, BigDecimal amount, CategoryType type) {
        if (type == CategoryType.INCOME) {
            account.setBalance(account.getBalance().add(amount));
        } else { // EXPENSE or SAVINGS
            account.setBalance(account.getBalance().subtract(amount));
        }
        accountRepository.save(account);
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
}
