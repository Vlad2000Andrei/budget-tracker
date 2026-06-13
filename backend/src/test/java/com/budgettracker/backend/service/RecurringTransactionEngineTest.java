package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.CreateRecurrenceRuleRequest;
import com.budgettracker.backend.dto.CreateTransactionRequest;
import com.budgettracker.backend.dto.TransactionDto;
import com.budgettracker.backend.dto.UpdateTransactionRequest;
import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.RecurrenceFrequency;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.AccountRepository;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.repository.RecurrenceRuleRepository;
import com.budgettracker.backend.repository.UserRepository;
import com.budgettracker.backend.repository.TransactionRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.budgettracker.backend.jooq.Tables.ACCOUNTS;
import static com.budgettracker.backend.jooq.Tables.CATEGORIES;
import static com.budgettracker.backend.jooq.Tables.RECURRENCE_RULES;
import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@Transactional
public class RecurringTransactionEngineTest {

    @Autowired
    private RecurringTransactionEngine recurringTransactionEngine;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RecurrenceRuleRepository recurrenceRuleRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;
    private Category expenseCategory;
    private Account account;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(TRANSACTIONS).execute();
        dsl.deleteFrom(RECURRENCE_RULES).execute();
        dsl.deleteFrom(ACCOUNTS).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("test-user@example.com")
                .googleSub("test-google-sub-999")
                .defaultCurrency("USD")
                .build());

        expenseCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Subscriptions")
                .type(CategoryType.EXPENSE)
                .build());

        account = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Checking")
                .type(AccountType.CHECKING)
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .build());
    }

    @Test
    public void testCreateTransactionWithRecurrence_Success() {
        CreateRecurrenceRuleRequest ruleRequest = CreateRecurrenceRuleRequest.builder()
                .frequency(RecurrenceFrequency.MONTHLY)
                .interval(1)
                .startDate(LocalDate.of(2026, 6, 1))
                .build();

        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .categoryId(expenseCategory.getId())
                .accountId(account.getId())
                .amount(new BigDecimal("15.00"))
                .currency("USD")
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 1, 10, 0))
                .notes("Netflix")
                .recurrenceRule(ruleRequest)
                .build();

        TransactionDto created = transactionService.createTransaction(txRequest, testUser);

        assertNotNull(created.getRecurrenceRuleId());
        assertNotNull(created.getRecurrenceRule());
        assertEquals(RecurrenceFrequency.MONTHLY, created.getRecurrenceRule().getFrequency());
        assertEquals(1, created.getRecurrenceRule().getInterval());
        assertEquals(LocalDate.of(2026, 6, 1), created.getRecurrenceRule().getStartDate());

        // Account balance should have been reduced by $15
        Account updatedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(new BigDecimal("985.0000"), updatedAccount.getBalance());
    }

    @Test
    public void testEngineSpawnsTransactions_Success() {
        // 1. Create a daily recurrence rule starting 3 days ago
        LocalDate startDate = LocalDate.now().minusDays(3);
        
        CreateRecurrenceRuleRequest ruleRequest = CreateRecurrenceRuleRequest.builder()
                .frequency(RecurrenceFrequency.DAILY)
                .interval(1)
                .startDate(startDate)
                .build();

        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .categoryId(expenseCategory.getId())
                .accountId(account.getId())
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .type(CategoryType.EXPENSE)
                .date(startDate.atTime(10, 0))
                .notes("Daily Coffee")
                .recurrenceRule(ruleRequest)
                .build();

        // Spawns the template transaction (day -3)
        TransactionDto template = transactionService.createTransaction(txRequest, testUser);

        // 2. Run the engine process
        recurringTransactionEngine.processRecurringTransactions();

        // 3. Verify spawned transactions
        // We expect transactions for: day -3 (template), day -2, day -1, today (day 0)
        // Total of 4 transactions
        List<com.budgettracker.backend.model.Transaction> allTx = transactionRepository.findByRecurrenceRuleId(template.getRecurrenceRuleId());
        assertEquals(4, allTx.size());

        // Verify dates of the transactions
        assertEquals(startDate, allTx.get(0).getDate().toLocalDate());
        assertEquals(startDate.plusDays(1), allTx.get(1).getDate().toLocalDate());
        assertEquals(startDate.plusDays(2), allTx.get(2).getDate().toLocalDate());
        assertEquals(LocalDate.now(), allTx.get(3).getDate().toLocalDate());

        // Verify account balance
        // Account balance should have been reduced by $40 ($10 * 4 transactions)
        Account updatedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(new BigDecimal("960.0000"), updatedAccount.getBalance());
    }

    @Test
    public void testUpdateTransaction_RecurrenceRuleChange() {
        // Create initially recurring transaction
        CreateRecurrenceRuleRequest ruleRequest = CreateRecurrenceRuleRequest.builder()
                .frequency(RecurrenceFrequency.WEEKLY)
                .interval(1)
                .startDate(LocalDate.of(2026, 6, 1))
                .build();

        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .categoryId(expenseCategory.getId())
                .accountId(account.getId())
                .amount(new BigDecimal("20.00"))
                .currency("USD")
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 1, 10, 0))
                .notes("Weekly Sub")
                .recurrenceRule(ruleRequest)
                .build();

        TransactionDto created = transactionService.createTransaction(txRequest, testUser);
        Long ruleId = created.getRecurrenceRuleId();
        assertNotNull(ruleId);

        // 1. Update the recurrence details (e.g. change frequency to DAILY)
        CreateRecurrenceRuleRequest updatedRuleRequest = CreateRecurrenceRuleRequest.builder()
                .frequency(RecurrenceFrequency.DAILY)
                .interval(2)
                .startDate(LocalDate.of(2026, 6, 2))
                .build();

        UpdateTransactionRequest updateRequest = UpdateTransactionRequest.builder()
                .categoryId(expenseCategory.getId())
                .accountId(account.getId())
                .amount(new BigDecimal("20.00"))
                .currency("USD")
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 1, 10, 0))
                .notes("Daily Sub")
                .recurrenceRule(updatedRuleRequest)
                .build();

        TransactionDto updated = transactionService.updateTransaction(created.getId(), updateRequest, testUser);
        assertEquals(ruleId, updated.getRecurrenceRuleId());
        assertNotNull(updated.getRecurrenceRule());
        assertEquals(RecurrenceFrequency.DAILY, updated.getRecurrenceRule().getFrequency());
        assertEquals(2, updated.getRecurrenceRule().getInterval());
        assertEquals(LocalDate.of(2026, 6, 2), updated.getRecurrenceRule().getStartDate());

        // 2. Remove the recurrence rule by setting it to null
        UpdateTransactionRequest updateRequestRemoveRecurrence = UpdateTransactionRequest.builder()
                .categoryId(expenseCategory.getId())
                .accountId(account.getId())
                .amount(new BigDecimal("20.00"))
                .currency("USD")
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 1, 10, 0))
                .notes("One-off Sub")
                .recurrenceRule(null)
                .build();

        TransactionDto nonRecurring = transactionService.updateTransaction(created.getId(), updateRequestRemoveRecurrence, testUser);
        assertNull(nonRecurring.getRecurrenceRuleId());
        assertNull(nonRecurring.getRecurrenceRule());

        // Verify the recurrence rule was deleted from the database
        assertFalse(recurrenceRuleRepository.findById(ruleId).isPresent());
    }

    @Test
    public void testDeleteTransaction_CleanUpRecurrenceRule() {
        CreateRecurrenceRuleRequest ruleRequest = CreateRecurrenceRuleRequest.builder()
                .frequency(RecurrenceFrequency.MONTHLY)
                .interval(1)
                .startDate(LocalDate.of(2026, 6, 1))
                .build();

        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .categoryId(expenseCategory.getId())
                .accountId(account.getId())
                .amount(new BigDecimal("15.00"))
                .currency("USD")
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 1, 10, 0))
                .notes("Netflix")
                .recurrenceRule(ruleRequest)
                .build();

        TransactionDto created = transactionService.createTransaction(txRequest, testUser);
        Long ruleId = created.getRecurrenceRuleId();
        assertNotNull(ruleId);

        // Delete the template transaction
        transactionService.deleteTransaction(created.getId(), testUser);

        // Verify the recurrence rule is cleaned up since no sibling transactions reference it
        assertFalse(recurrenceRuleRepository.findById(ruleId).isPresent());
    }
}
