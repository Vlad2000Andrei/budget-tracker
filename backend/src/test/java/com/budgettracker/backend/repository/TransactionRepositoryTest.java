package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.Transaction;
import com.budgettracker.backend.model.User;
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

import static com.budgettracker.backend.jooq.Tables.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@Transactional
public class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;
    private Category groceryCategory;
    private Category salaryCategory;
    private Account checkingAccount;
    private Account savingsAccount;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(TRANSACTIONS).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(ACCOUNTS).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("repo-user@example.com")
                .googleSub("repo-sub")
                .defaultCurrency("EUR")
                .build());

        groceryCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .build());

        salaryCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Salary")
                .type(CategoryType.INCOME)
                .build());

        checkingAccount = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Checking")
                .type(com.budgettracker.backend.jooq.enums.AccountType.CHECKING)
                .balance(new BigDecimal("1000.00"))
                .currency("EUR")
                .build());

        savingsAccount = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Savings")
                .type(com.budgettracker.backend.jooq.enums.AccountType.SAVINGS)
                .balance(new BigDecimal("2000.00"))
                .currency("EUR")
                .build());
    }

    @Test
    public void testFindAll_WithMultipleFilters() {
        LocalDateTime date = LocalDateTime.of(2026, 6, 20, 10, 0, 0);

        Transaction tx1 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(groceryCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("45.50"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("45.50"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(date)
                .build());

        Transaction tx2 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(salaryCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("1500.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("1500.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.INCOME)
                .date(date.plusDays(1))
                .build());

        // 1. Filter by Account
        List<Transaction> filtered = transactionRepository.findAll(testUser.getId(), checkingAccount.getId(), null, null, null, null);
        assertEquals(2, filtered.size());

        filtered = transactionRepository.findAll(testUser.getId(), savingsAccount.getId(), null, null, null, null);
        assertEquals(0, filtered.size());

        // 2. Filter by Category
        filtered = transactionRepository.findAll(testUser.getId(), null, groceryCategory.getId(), null, null, null);
        assertEquals(1, filtered.size());
        assertEquals(tx1.getId(), filtered.get(0).getId());

        // 3. Filter by Date range
        filtered = transactionRepository.findAll(testUser.getId(), null, null, LocalDate.of(2026, 6, 21), LocalDate.of(2026, 6, 22), null);
        assertEquals(1, filtered.size());
        assertEquals(tx2.getId(), filtered.get(0).getId());

        // 4. Filter by Type standard
        filtered = transactionRepository.findAll(testUser.getId(), null, null, null, null, "INCOME");
        assertEquals(1, filtered.size());
        assertEquals(tx2.getId(), filtered.get(0).getId());

        // 5. Filter by invalid Type string (ignores catch block)
        filtered = transactionRepository.findAll(testUser.getId(), null, null, null, null, "INVALID_TYPE_STRING");
        assertEquals(2, filtered.size()); // ignores and returns all
    }

    @Test
    public void testFindAll_WithMoveType() {
        LocalDateTime date = LocalDateTime.of(2026, 6, 20, 10, 0, 0);

        Transaction tx1 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(groceryCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("-50.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("-50.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(date)
                .build());

        Transaction tx2 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(groceryCategory.getId())
                .accountId(savingsAccount.getId())
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("50.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.INCOME)
                .date(date)
                .linkedTransactionId(tx1.getId())
                .build());

        // Filter by MOVE type (linkedTransactionId != null)
        List<Transaction> moves = transactionRepository.findAll(testUser.getId(), null, null, null, null, "MOVE");
        assertEquals(1, moves.size());
        assertEquals(tx2.getId(), moves.get(0).getId());
    }

    @Test
    public void testSave_DefaultConvertedCurrencyHandling() {
        Transaction tx = Transaction.builder()
                .userId(testUser.getId())
                .categoryId(groceryCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("20.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("20.00"))
                .convertedCurrency(null) // Should fallback to User default currency
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build();

        Transaction saved = transactionRepository.save(tx);
        assertEquals("EUR", saved.getConvertedCurrency()); // Fallback to User's defaultCurrency ("EUR")

        // Test fallback to USD when user default currency is null
        User userNoCurrency = userRepository.save(User.builder()
                .email("no-currency@example.com")
                .googleSub("no-currency-sub")
                .defaultCurrency(null)
                .build());

        Transaction txNoCurrency = Transaction.builder()
                .userId(userNoCurrency.getId())
                .categoryId(groceryCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("20.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("20.00"))
                .convertedCurrency(null)
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build();

        Transaction savedNoCurrency = transactionRepository.save(txNoCurrency);
        assertEquals("USD", savedNoCurrency.getConvertedCurrency()); // Fallback to "USD"
    }

    @Test
    public void testFindAll_WithBlankAndNullType() {
        List<Transaction> filteredNull = transactionRepository.findAll(testUser.getId(), null, null, null, null, null);
        List<Transaction> filteredBlank = transactionRepository.findAll(testUser.getId(), null, null, null, null, "   ");
        assertNotNull(filteredNull);
        assertNotNull(filteredBlank);
    }

    @Test
    public void testSave_WithPreSetConvertedCurrency() {
        Transaction tx = Transaction.builder()
                .userId(testUser.getId())
                .categoryId(groceryCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("20.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("20.00"))
                .convertedCurrency("RON")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build();

        Transaction saved = transactionRepository.save(tx);
        assertEquals("RON", saved.getConvertedCurrency());
    }

    @Test
    public void testFindAll_SingleFilters() {
        LocalDate today = LocalDate.now();
        // 1. Account filter only
        assertNotNull(transactionRepository.findAll(testUser.getId(), checkingAccount.getId(), null, null, null, null));
        // 2. Category filter only
        assertNotNull(transactionRepository.findAll(testUser.getId(), null, groceryCategory.getId(), null, null, null));
        // 3. Start date filter only
        assertNotNull(transactionRepository.findAll(testUser.getId(), null, null, today, null, null));
        // 4. End date filter only
        assertNotNull(transactionRepository.findAll(testUser.getId(), null, null, null, today, null));
        // 5. Move type filter only
        assertNotNull(transactionRepository.findAll(testUser.getId(), null, null, null, null, "MOVE"));
        // 6. Valid CategoryType filter
        assertNotNull(transactionRepository.findAll(testUser.getId(), null, null, null, null, "EXPENSE"));
        
        // 7. Save transaction with null date (should default to now)
        Transaction txNullDate = Transaction.builder()
                .userId(testUser.getId())
                .categoryId(groceryCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("20.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("20.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(null) // null date defaults to now
                .build();
        Transaction saved = transactionRepository.save(txNullDate);
        assertNotNull(saved.getDate());

        // 8. FindByCategoryIds with null / empty
        assertTrue(transactionRepository.findByCategoryIds(testUser.getId(), null).isEmpty());
        assertTrue(transactionRepository.findByCategoryIds(testUser.getId(), java.util.Collections.emptyList()).isEmpty());

        // 9. FindDuplicateTransactionId with null values
        assertFalse(transactionRepository.findDuplicateTransactionId(testUser.getId(), null, null, null).isPresent());
    }
}
