package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.BulkTransactionRequest;
import com.budgettracker.backend.dto.DuplicateCheckRequest;
import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.SavingsTransactionType;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.AccountRepository;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.repository.TransactionRepository;
import com.budgettracker.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.budgettracker.backend.jooq.Tables.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
@Transactional
public class ImportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;
    private Account checkingAccount;
    private Account savingsAccount;
    private Category salaryCategory;
    private Category groceryCategory;
    private Category savingsCategory;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(TRANSACTIONS).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(ACCOUNTS).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("import-user@example.com")
                .googleSub("import-sub-999")
                .defaultCurrency("USD")
                .build());

        checkingAccount = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Checking Account")
                .type(AccountType.CHECKING)
                .balance(new BigDecimal("1000.0000"))
                .currency("USD")
                .build());

        savingsAccount = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Savings Account")
                .type(AccountType.SAVINGS)
                .balance(new BigDecimal("500.0000"))
                .currency("USD")
                .build());

        salaryCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Salary")
                .type(CategoryType.INCOME)
                .build());

        groceryCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .build());

        savingsCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("General Savings")
                .type(CategoryType.SAVINGS)
                .build());
    }

    @Test
    public void testDetectDuplicates_ReturnsFlagsCorrectly() throws Exception {
        LocalDateTime date = LocalDateTime.of(2026, 6, 23, 12, 0, 0);

        // Pre-insert an existing transaction
        transactionRepository.save(com.budgettracker.backend.model.Transaction.builder()
                .userId(testUser.getId())
                .categoryId(groceryCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("45.50"))
                .currency("USD")
                .convertedAmount(new BigDecimal("45.50"))
                .convertedCurrency("USD")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .notes("Supermarket")
                .date(date)
                .build());

        // Prepare request
        DuplicateCheckRequest.CandidateTransaction cand1 = DuplicateCheckRequest.CandidateTransaction.builder()
                .date(LocalDateTime.of(2026, 6, 23, 0, 0, 0)) // boundary date check: same day
                .amount(new BigDecimal("-45.50")) // absolute values should match
                .build();

        DuplicateCheckRequest.CandidateTransaction cand2 = DuplicateCheckRequest.CandidateTransaction.builder()
                .date(LocalDateTime.of(2026, 6, 24, 0, 0, 0)) // different day
                .amount(new BigDecimal("45.50"))
                .build();

        DuplicateCheckRequest.CandidateTransaction cand3 = DuplicateCheckRequest.CandidateTransaction.builder()
                .date(LocalDateTime.of(2026, 6, 23, 0, 0, 0))
                .amount(new BigDecimal("99.99")) // different amount
                .build();

        DuplicateCheckRequest request = DuplicateCheckRequest.builder()
                .accountId(checkingAccount.getId())
                .transactions(List.of(cand1, cand2, cand3))
                .build();

        mockMvc.perform(post("/v1/imports/detect-duplicates")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[0].potentialDuplicate", is(true)))
                .andExpect(jsonPath("$.results[0].existingTransactionId", notNullValue()))
                .andExpect(jsonPath("$.results[1].potentialDuplicate", is(false)))
                .andExpect(jsonPath("$.results[1].existingTransactionId", nullValue()))
                .andExpect(jsonPath("$.results[2].potentialDuplicate", is(false)))
                .andExpect(jsonPath("$.results[2].existingTransactionId", nullValue()));
    }

    @Test
    public void testCreateTransactionsBulk_Success() throws Exception {
        LocalDateTime date = LocalDateTime.of(2026, 6, 23, 10, 0, 0);

        Account checkingAccount2 = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Checking Account 2")
                .type(AccountType.CHECKING)
                .balance(new BigDecimal("500.0000"))
                .currency("USD")
                .build());

        BulkTransactionRequest.BulkTransactionItem incomeItem = BulkTransactionRequest.BulkTransactionItem.builder()
                .amount(new BigDecimal("1500.00"))
                .currency("USD")
                .date(date)
                .notes("Monthly Salary")
                .importType(BulkTransactionRequest.ImportTransactionType.INCOME)
                .categoryId(salaryCategory.getId())
                .accountId(checkingAccount.getId())
                .build();

        BulkTransactionRequest.BulkTransactionItem expenseItem = BulkTransactionRequest.BulkTransactionItem.builder()
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .date(date)
                .notes("Weekly Groceries")
                .importType(BulkTransactionRequest.ImportTransactionType.EXPENSE)
                .categoryId(groceryCategory.getId())
                .accountId(checkingAccount.getId())
                .build();

        BulkTransactionRequest.BulkTransactionItem transferItem = BulkTransactionRequest.BulkTransactionItem.builder()
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .date(date)
                .notes("Move checking to savings")
                .importType(BulkTransactionRequest.ImportTransactionType.TRANSFER)
                .accountId(checkingAccount.getId())
                .transferToAccountId(checkingAccount2.getId())
                .build();

        BulkTransactionRequest.BulkTransactionItem savingsItem = BulkTransactionRequest.BulkTransactionItem.builder()
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .date(date)
                .notes("Emergency savings deposit")
                .importType(BulkTransactionRequest.ImportTransactionType.SAVINGS)
                .categoryId(savingsCategory.getId())
                .accountId(checkingAccount.getId())
                .savingsToAccountId(savingsAccount.getId())
                .savingsType(SavingsTransactionType.DEPOSIT)
                .build();

        BulkTransactionRequest request = BulkTransactionRequest.builder()
                .transactions(List.of(incomeItem, expenseItem, transferItem, savingsItem))
                .build();

        mockMvc.perform(post("/v1/transactions/bulk")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(4)));

        // Verify balances in database
        Account updatedChecking = accountRepository.findById(checkingAccount.getId()).orElseThrow();
        Account updatedChecking2 = accountRepository.findById(checkingAccount2.getId()).orElseThrow();
        Account updatedSavings = accountRepository.findById(savingsAccount.getId()).orElseThrow();

        // Checking: 1000 + 1500 (Salary) - 50 (Groceries) - 200 (Transfer) - 100 (Savings deposit) = 2150
        // Checking 2: 500 + 200 (Transfer) = 700
        // Savings: 500 + 100 (Savings deposit) = 600
        
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("2150.0000").compareTo(updatedChecking.getBalance()));
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("700.0000").compareTo(updatedChecking2.getBalance()));
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("600.0000").compareTo(updatedSavings.getBalance()));
    }

    @Test
    public void testDetectDuplicates_SpecialTypes() throws Exception {
        LocalDateTime date = LocalDateTime.of(2026, 6, 23, 12, 0, 0);

        // 1. Pre-insert a transfer transaction pair
        var txSrc = transactionRepository.save(com.budgettracker.backend.model.Transaction.builder()
                .userId(testUser.getId())
                .categoryId(groceryCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .convertedAmount(new BigDecimal("100.00"))
                .convertedCurrency("USD")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(date)
                .build());

        var txDest = transactionRepository.save(com.budgettracker.backend.model.Transaction.builder()
                .userId(testUser.getId())
                .categoryId(groceryCategory.getId())
                .accountId(savingsAccount.getId())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .convertedAmount(new BigDecimal("100.00"))
                .convertedCurrency("USD")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.INCOME)
                .date(date)
                .linkedTransactionId(txSrc.getId())
                .build());

        // Update txSrc to point to txDest
        dsl.update(TRANSACTIONS)
                .set(TRANSACTIONS.LINKED_TRANSACTION_ID, txDest.getId())
                .where(TRANSACTIONS.ID.eq(txSrc.getId()))
                .execute();

        // 2. Pre-insert a savings goal and savings goal transaction
        com.budgettracker.backend.model.SavingsGoal goal = com.budgettracker.backend.model.SavingsGoal.builder()
                .userId(testUser.getId())
                .categoryId(savingsCategory.getId())
                .goalType(com.budgettracker.backend.jooq.enums.SavingsGoalType.ONE_OFF)
                .targetAmount(new BigDecimal("2000.00"))
                .currentAmount(BigDecimal.ZERO)
                .build();

        Long goalId = dsl.insertInto(SAVINGS_GOALS)
                .set(SAVINGS_GOALS.USER_ID, goal.getUserId())
                .set(SAVINGS_GOALS.CATEGORY_ID, goal.getCategoryId())
                .set(SAVINGS_GOALS.GOAL_TYPE, goal.getGoalType())
                .set(SAVINGS_GOALS.TARGET_AMOUNT, goal.getTargetAmount())
                .set(SAVINGS_GOALS.CURRENT_AMOUNT, goal.getCurrentAmount())
                .returning(SAVINGS_GOALS.ID)
                .fetchOne()
                .getId();

        var txSavings = transactionRepository.save(com.budgettracker.backend.model.Transaction.builder()
                .userId(testUser.getId())
                .categoryId(savingsCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .convertedAmount(new BigDecimal("50.00"))
                .convertedCurrency("USD")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.SAVINGS)
                .date(date)
                .build());

        dsl.insertInto(SAVINGS_TRANSACTIONS)
                .set(SAVINGS_TRANSACTIONS.TRANSACTION_ID, txSavings.getId())
                .set(SAVINGS_TRANSACTIONS.FROM_ACCOUNT_ID, checkingAccount.getId())
                .set(SAVINGS_TRANSACTIONS.TO_ACCOUNT_ID, savingsAccount.getId())
                .set(SAVINGS_TRANSACTIONS.TYPE, com.budgettracker.backend.jooq.enums.SavingsTransactionType.DEPOSIT)
                .set(SAVINGS_TRANSACTIONS.CREATED_AT, date)
                .execute();

        // 3. Detect duplicate for transfer (matches txSrc)
        DuplicateCheckRequest.CandidateTransaction candTransfer = DuplicateCheckRequest.CandidateTransaction.builder()
                .date(LocalDateTime.of(2026, 6, 23, 0, 0, 0))
                .amount(new BigDecimal("-100.00"))
                .build();

        // 4. Detect duplicate for savings (matches txSavings)
        DuplicateCheckRequest.CandidateTransaction candSavings = DuplicateCheckRequest.CandidateTransaction.builder()
                .date(LocalDateTime.of(2026, 6, 23, 0, 0, 0))
                .amount(new BigDecimal("-50.00"))
                .build();

        DuplicateCheckRequest request = DuplicateCheckRequest.builder()
                .accountId(checkingAccount.getId())
                .transactions(List.of(candTransfer, candSavings))
                .build();

        mockMvc.perform(post("/v1/imports/detect-duplicates")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].potentialDuplicate", is(true)))
                .andExpect(jsonPath("$.results[0].importType", is("TRANSFER")))
                .andExpect(jsonPath("$.results[1].potentialDuplicate", is(true)))
                .andExpect(jsonPath("$.results[1].importType", is("SAVINGS")));
    }
}
