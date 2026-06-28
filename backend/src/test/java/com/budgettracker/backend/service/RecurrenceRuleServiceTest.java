package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.RecurringTransactionDto;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.RecurrenceFrequency;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.RecurrenceRule;
import com.budgettracker.backend.model.Transaction;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.repository.AccountRepository;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.repository.RecurrenceRuleRepository;
import com.budgettracker.backend.repository.TransactionRepository;
import com.budgettracker.backend.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.budgettracker.backend.jooq.Tables.CATEGORIES;
import static com.budgettracker.backend.jooq.Tables.RECURRENCE_RULES;
import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@Transactional
public class RecurrenceRuleServiceTest {

    @Autowired
    private RecurrenceRuleService recurrenceRuleService;

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
    private User otherUser;
    private Category testCategory;
    private Account testAccount;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(TRANSACTIONS).execute();
        dsl.deleteFrom(RECURRENCE_RULES).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(com.budgettracker.backend.jooq.Tables.ACCOUNTS).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("rule-user@example.com")
                .googleSub("rule-sub")
                .build());

        otherUser = userRepository.save(User.builder()
                .email("other-rule-user@example.com")
                .googleSub("other-rule-sub")
                .build());

        testCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .build());

        testAccount = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Checking")
                .type(com.budgettracker.backend.jooq.enums.AccountType.CHECKING)
                .balance(new BigDecimal("1000.00"))
                .currency("EUR")
                .build());
    }

    @Test
    public void testGetRecurringTransactions_Empty() {
        List<RecurringTransactionDto> result = recurrenceRuleService.getRecurringTransactionsForUser(testUser);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRecurringTransactions_MultipleFrequencies() {
        // Create recurrence rules
        RecurrenceRule ruleDaily = recurrenceRuleRepository.save(RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.DAILY)
                .interval(2)
                .startDate(LocalDate.now().minusDays(5))
                .build());

        RecurrenceRule ruleWeekly = recurrenceRuleRepository.save(RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.WEEKLY)
                .interval(1)
                .startDate(LocalDate.now().minusWeeks(2))
                .build());

        RecurrenceRule ruleMonthly = recurrenceRuleRepository.save(RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.MONTHLY)
                .interval(1)
                .startDate(LocalDate.now().minusMonths(1))
                .build());

        RecurrenceRule ruleYearly = recurrenceRuleRepository.save(RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.YEARLY)
                .interval(1)
                .startDate(LocalDate.now().minusYears(1))
                .build());

        // Create transactions referencing rules
        Transaction txDaily = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(testCategory.getId())
                .accountId(testAccount.getId())
                .amount(new BigDecimal("10.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now().minusDays(5))
                .recurrenceRuleId(ruleDaily.getId())
                .build());

        Transaction txDailyLater = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(testCategory.getId())
                .accountId(testAccount.getId())
                .amount(new BigDecimal("10.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now().minusDays(3)) // triggers lastOccurrenceDate update in calculation
                .recurrenceRuleId(ruleDaily.getId())
                .build());

        Transaction txWeekly = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(testCategory.getId())
                .accountId(testAccount.getId())
                .amount(new BigDecimal("20.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("20.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now().minusWeeks(2))
                .recurrenceRuleId(ruleWeekly.getId())
                .build());

        Transaction txMonthly = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(testCategory.getId())
                .accountId(testAccount.getId())
                .amount(new BigDecimal("30.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("30.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now().minusMonths(1))
                .recurrenceRuleId(ruleMonthly.getId())
                .build());

        Transaction txYearly = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(testCategory.getId())
                .accountId(testAccount.getId())
                .amount(new BigDecimal("40.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("40.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now().minusYears(1))
                .recurrenceRuleId(ruleYearly.getId())
                .build());

        List<RecurringTransactionDto> results = recurrenceRuleService.getRecurringTransactionsForUser(testUser);
        assertEquals(4, results.size());

        // Check next occurrences dates
        RecurringTransactionDto dailyDto = results.stream().filter(r -> r.getId().equals(ruleDaily.getId())).findFirst().orElseThrow();
        assertEquals(txDailyLater.getDate().toLocalDate().plusDays(2), dailyDto.getNextDate());

        RecurringTransactionDto weeklyDto = results.stream().filter(r -> r.getId().equals(ruleWeekly.getId())).findFirst().orElseThrow();
        assertEquals(txWeekly.getDate().toLocalDate().plusWeeks(1), weeklyDto.getNextDate());

        RecurringTransactionDto monthlyDto = results.stream().filter(r -> r.getId().equals(ruleMonthly.getId())).findFirst().orElseThrow();
        assertEquals(txMonthly.getDate().toLocalDate().plusMonths(1), monthlyDto.getNextDate());

        RecurringTransactionDto yearlyDto = results.stream().filter(r -> r.getId().equals(ruleYearly.getId())).findFirst().orElseThrow();
        assertEquals(txYearly.getDate().toLocalDate().plusYears(1), yearlyDto.getNextDate());
    }

    @Test
    public void testDeleteRecurrenceRule_Forbidden() {
        RecurrenceRule rule = recurrenceRuleRepository.save(RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.DAILY)
                .interval(1)
                .startDate(LocalDate.now())
                .build());

        // Create transaction belonging to testUser referencing rule
        transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(testCategory.getId())
                .accountId(testAccount.getId())
                .amount(new BigDecimal("10.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .recurrenceRuleId(rule.getId())
                .build());

        // Attempt delete as otherUser
        assertThrows(ForbiddenActionException.class, () -> {
            recurrenceRuleService.deleteRecurrenceRule(rule.getId(), otherUser);
        });
    }

    @Test
    public void testRecurrenceRuleRepository_FindAllByIds_Empty() {
        assertTrue(recurrenceRuleRepository.findAllByIds(null).isEmpty());
        assertTrue(recurrenceRuleRepository.findAllByIds(Collections.emptyList()).isEmpty());
    }
}
