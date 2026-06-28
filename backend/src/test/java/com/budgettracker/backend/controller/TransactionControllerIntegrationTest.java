package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.CreateTransactionRequest;
import com.budgettracker.backend.dto.UpdateTransactionRequest;
import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.model.Transaction;
import com.budgettracker.backend.repository.AccountRepository;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.repository.TransactionRepository;
import com.budgettracker.backend.repository.UserRepository;
import com.budgettracker.backend.jooq.enums.RecurrenceFrequency;
import com.budgettracker.backend.jooq.enums.SavingsGoalType;
import com.budgettracker.backend.model.RecurrenceRule;
import com.budgettracker.backend.repository.RecurrenceRuleRepository;
import com.budgettracker.backend.repository.SavingsGoalRepository;
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

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.budgettracker.backend.jooq.Tables.ACCOUNTS;
import static com.budgettracker.backend.jooq.Tables.CATEGORIES;
import static com.budgettracker.backend.jooq.Tables.RECURRENCE_RULES;
import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
@Transactional
public class TransactionControllerIntegrationTest {

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
    private RecurrenceRuleRepository recurrenceRuleRepository;

    @Autowired
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;
    private User otherUser;
    private Category expenseCategory;
    private Category savingsCategory;
    private Category otherCategory;
    private Account eurAccount;
    private Account ronAccount;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(TRANSACTIONS).execute();
        dsl.deleteFrom(RECURRENCE_RULES).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(ACCOUNTS).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("test-user@example.com")
                .googleSub("test-sub-111")
                .defaultCurrency("EUR")
                .build());

        otherUser = userRepository.save(User.builder()
                .email("other-user@example.com")
                .googleSub("other-sub-222")
                .defaultCurrency("EUR")
                .build());

        expenseCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Food")
                .type(CategoryType.EXPENSE)
                .build());

        savingsCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Car Savings")
                .type(CategoryType.SAVINGS)
                .build());

        otherCategory = categoryRepository.save(Category.builder()
                .userId(otherUser.getId())
                .name("Other Category")
                .type(CategoryType.EXPENSE)
                .build());

        eurAccount = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("EUR Checking")
                .type(AccountType.CHECKING)
                .balance(new BigDecimal("1000.0000"))
                .currency("EUR")
                .build());

        ronAccount = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("RON Savings")
                .type(AccountType.SAVINGS)
                .balance(new BigDecimal("500.0000"))
                .currency("RON")
                .build());
    }

    @Test
    public void testCreateTransaction_Success_EUR() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .amount(new BigDecimal("45.90"))
                .currency("EUR")
                .type(CategoryType.EXPENSE)
                .notes("Weekly groceries")
                .date(LocalDateTime.of(2026, 6, 13, 12, 0))
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.amount", is(45.9)))
                .andExpect(jsonPath("$.convertedAmount", is(45.9))) // same currency
                .andExpect(jsonPath("$.exchangeRate", is(1.0)))
                .andExpect(jsonPath("$.notes", is("Weekly groceries")));

        // Verify account balance updated (reduced by 45.90)
        Account updatedAccount = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("954.1000"), updatedAccount.getBalance());
    }

    @Test
    public void testCreateTransaction_Success_RON_to_EURAccount() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .amount(new BigDecimal("100.00"))
                .currency("RON")
                .type(CategoryType.EXPENSE)
                .notes("RON shopping on EUR account")
                .date(LocalDateTime.now())
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.convertedAmount", is(20.1207))) // 100 * 0.201207
                .andExpect(jsonPath("$.exchangeRate", is(0.201207)));

        // Verify EUR Account balance updated: 1000 - (100 * 0.201207) = 979.8793
        Account updatedAccount = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("979.8793"), updatedAccount.getBalance());
    }

    @Test
    public void testCreateTransaction_ValidationFailure() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .categoryId(null) // invalid
                .amount(new BigDecimal("-10.00")) // invalid
                .currency("") // invalid
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")));
    }

    @Test
    public void testCreateTransaction_ForbiddenCategory() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .categoryId(otherCategory.getId()) // category of other user
                .accountId(eurAccount.getId())
                .amount(BigDecimal.ONE)
                .currency("EUR")
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testUpdateTransaction_Success() throws Exception {
        // Create initial transaction linked to eurAccount (EXPENSE, amount 100 EUR)
        Transaction initial = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .amount(new BigDecimal("100.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("100.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build());

        // Set initial balance manually to match transaction existence
        eurAccount.setBalance(new BigDecimal("900.0000"));
        accountRepository.save(eurAccount);

        UpdateTransactionRequest request = UpdateTransactionRequest.builder()
                .categoryId(expenseCategory.getId())
                .accountId(ronAccount.getId()) // switch to ronAccount!
                .amount(new BigDecimal("200.00")) // change to 200 RON
                .currency("RON")
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build();

        mockMvc.perform(patch("/v1/transactions/" + initial.getId())
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(200.0)))
                .andExpect(jsonPath("$.convertedAmount", is(40.2414))) // 200 * 0.201207
                .andExpect(jsonPath("$.accountId", is(ronAccount.getId().intValue())));

        // Verify eurAccount balance was restored: 900 + 100 = 1000
        Account updatedEur = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("1000.0000"), updatedEur.getBalance());

        // Verify ronAccount balance was adjusted: 500 - 200 = 300
        Account updatedRon = accountRepository.findById(ronAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("300.0000"), updatedRon.getBalance());
    }

    @Test
    public void testUpdateTransaction_SameAccount_DifferentAmountAndCategory() throws Exception {
        // Create initial transaction linked to eurAccount (EXPENSE, amount 100 EUR)
        Transaction initial = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .amount(new BigDecimal("100.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("100.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build());

        // Set initial balance manually to match transaction existence
        eurAccount.setBalance(new BigDecimal("900.0000"));
        accountRepository.save(eurAccount);

        UpdateTransactionRequest request = UpdateTransactionRequest.builder()
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId()) // keep eurAccount!
                .amount(new BigDecimal("150.00")) // change to 150 EUR
                .currency("EUR")
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build();

        mockMvc.perform(patch("/v1/transactions/" + initial.getId())
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(150.0)))
                .andExpect(jsonPath("$.accountId", is(eurAccount.getId().intValue())));

        // Verify eurAccount balance was correctly adjusted: 900 + 100 - 150 = 850
        Account updatedEur = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("850.0000"), updatedEur.getBalance());
    }

    @Test
    public void testDeleteTransaction_Success() throws Exception {
        Transaction initial = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .amount(new BigDecimal("100.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("100.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build());

        // Set initial balance manually to match transaction existence
        eurAccount.setBalance(new BigDecimal("900.0000"));
        accountRepository.save(eurAccount);

        mockMvc.perform(delete("/v1/transactions/" + initial.getId())
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isNoContent());

        // Verify eurAccount balance restored to 1000
        Account updatedEur = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("1000.0000"), updatedEur.getBalance());
    }

    @Test
    public void testGetTransactions_Filter() throws Exception {
        transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .amount(BigDecimal.TEN)
                .currency("EUR")
                .convertedAmount(BigDecimal.TEN)
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build());

        transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .amount(BigDecimal.ONE)
                .currency("EUR")
                .convertedAmount(BigDecimal.ONE)
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 15, 10, 0))
                .build());

        mockMvc.perform(get("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount", is(10.0)))
                .andExpect(header().string("X-Has-Older-Transactions", "false"));
    }

    @Test
    public void testGetTransactions_HasOlderHeader() throws Exception {
        // Save older transaction (May)
        transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .amount(BigDecimal.TEN)
                .currency("EUR")
                .convertedAmount(BigDecimal.TEN)
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build());

        // Save newer transaction (June)
        transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .amount(BigDecimal.ONE)
                .currency("EUR")
                .convertedAmount(BigDecimal.ONE)
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 15, 10, 0))
                .build());

        // Query June - should have older transaction (from May)
        mockMvc.perform(get("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .param("startDate", "2026-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(header().string("X-Has-Older-Transactions", "true"));

        // Query May - should not have any older transaction
        mockMvc.perform(get("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .param("startDate", "2026-05-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Has-Older-Transactions", "false"));
    }

    @Test
    public void testCreateSavingsTransactions_Balances() throws Exception {
        // 1. Deposit positive savings amount to a SAVINGS account: Should INCREASE balance
        CreateTransactionRequest requestDepositSavingsAcc = CreateTransactionRequest.builder()
                .categoryId(savingsCategory.getId())
                .accountId(ronAccount.getId())
                .amount(new BigDecimal("100.00"))
                .currency("RON")
                .type(CategoryType.SAVINGS)
                .date(LocalDateTime.now())
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDepositSavingsAcc)))
                .andExpect(status().isCreated());

        Account updatedRon = accountRepository.findById(ronAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("600.0000"), updatedRon.getBalance());

        // 2. Withdraw negative savings amount from a SAVINGS account: Should DECREASE balance
        CreateTransactionRequest requestWithdrawSavingsAcc = CreateTransactionRequest.builder()
                .categoryId(savingsCategory.getId())
                .accountId(ronAccount.getId())
                .amount(new BigDecimal("-50.00"))
                .currency("RON")
                .type(CategoryType.SAVINGS)
                .date(LocalDateTime.now())
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestWithdrawSavingsAcc)))
                .andExpect(status().isCreated());

        updatedRon = accountRepository.findById(ronAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("550.0000"), updatedRon.getBalance());

        // 3. Deposit positive savings amount to a CHECKING account: Should DECREASE balance
        CreateTransactionRequest requestDepositChecking = CreateTransactionRequest.builder()
                .categoryId(savingsCategory.getId())
                .accountId(eurAccount.getId())
                .amount(new BigDecimal("200.00"))
                .currency("EUR")
                .type(CategoryType.SAVINGS)
                .date(LocalDateTime.now())
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDepositChecking)))
                .andExpect(status().isCreated());

        Account updatedEur = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("800.0000"), updatedEur.getBalance());

        // 4. Withdraw negative savings amount from a CHECKING account: Should INCREASE balance
        CreateTransactionRequest requestWithdrawChecking = CreateTransactionRequest.builder()
                .categoryId(savingsCategory.getId())
                .accountId(eurAccount.getId())
                .amount(new BigDecimal("-50.00"))
                .currency("EUR")
                .type(CategoryType.SAVINGS)
                .date(LocalDateTime.now())
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestWithdrawChecking)))
                .andExpect(status().isCreated());

        updatedEur = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("850.0000"), updatedEur.getBalance());
    }

    @Test
    public void testCreateTransaction_NegativeAmountValidation_ExpenseAndIncome() throws Exception {
        // 1. Negative amount for EXPENSE: Should fail validation
        CreateTransactionRequest requestExpenseNeg = CreateTransactionRequest.builder()
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .amount(new BigDecimal("-10.00"))
                .currency("EUR")
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestExpenseNeg)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Transaction amount must be greater than zero")));

        // 2. Zero amount for SAVINGS: Should fail validation
        CreateTransactionRequest requestSavingsZero = CreateTransactionRequest.builder()
                .categoryId(savingsCategory.getId())
                .accountId(eurAccount.getId())
                .amount(BigDecimal.ZERO)
                .currency("EUR")
                .type(CategoryType.SAVINGS)
                .date(LocalDateTime.now())
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestSavingsZero)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Savings transaction amount cannot be zero")));
    }

    @Test
    public void testDeleteTransaction_ThisOnly() throws Exception {
        // Create recurrence rule
        RecurrenceRule rule = recurrenceRuleRepository.save(RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.WEEKLY)
                .interval(1)
                .startDate(LocalDate.of(2026, 6, 1))
                .build());

        // Create 3 transactions: tx1 (June 1st), tx2 (June 8th), tx3 (June 15th)
        Transaction tx1 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .recurrenceRuleId(rule.getId())
                .amount(new BigDecimal("10.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build());

        Transaction tx2 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .recurrenceRuleId(rule.getId())
                .amount(new BigDecimal("10.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 8, 10, 0))
                .build());

        Transaction tx3 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .recurrenceRuleId(rule.getId())
                .amount(new BigDecimal("10.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 15, 10, 0))
                .build());

        // Manually adjust the balance of the account
        eurAccount.setBalance(new BigDecimal("970.0000"));
        accountRepository.save(eurAccount);

        // Delete tx2 with THIS_ONLY
        mockMvc.perform(delete("/v1/transactions/" + tx2.getId())
                        .header("X-User-Id", testUser.getId())
                        .param("mode", "THIS_ONLY"))
                .andExpect(status().isNoContent());

        // Verify database: tx1 and tx3 remain, tx2 deleted
        assertTrue(transactionRepository.findById(tx1.getId()).isPresent());
        assertFalse(transactionRepository.findById(tx2.getId()).isPresent());
        assertTrue(transactionRepository.findById(tx3.getId()).isPresent());

        // Verify rule still exists
        assertTrue(recurrenceRuleRepository.findById(rule.getId()).isPresent());

        // Verify account balance: 970 + 10 = 980
        Account updatedEur = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("980.0000"), updatedEur.getBalance());
    }

    @Test
    public void testDeleteTransaction_All() throws Exception {
        // Create recurrence rule
        RecurrenceRule rule = recurrenceRuleRepository.save(RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.WEEKLY)
                .interval(1)
                .startDate(LocalDate.of(2026, 6, 1))
                .build());

        // Create 3 transactions: tx1 (June 1st), tx2 (June 8th), tx3 (June 15th)
        Transaction tx1 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .recurrenceRuleId(rule.getId())
                .amount(new BigDecimal("10.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build());

        Transaction tx2 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .recurrenceRuleId(rule.getId())
                .amount(new BigDecimal("10.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 8, 10, 0))
                .build());

        Transaction tx3 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .recurrenceRuleId(rule.getId())
                .amount(new BigDecimal("10.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 15, 10, 0))
                .build());

        // Manually adjust the balance of the account
        eurAccount.setBalance(new BigDecimal("970.0000"));
        accountRepository.save(eurAccount);

        // Delete tx2 with ALL
        mockMvc.perform(delete("/v1/transactions/" + tx2.getId())
                        .header("X-User-Id", testUser.getId())
                        .param("mode", "ALL"))
                .andExpect(status().isNoContent());

        // Verify database: tx1, tx2, tx3 deleted
        assertFalse(transactionRepository.findById(tx1.getId()).isPresent());
        assertFalse(transactionRepository.findById(tx2.getId()).isPresent());
        assertFalse(transactionRepository.findById(tx3.getId()).isPresent());

        // Verify rule deleted
        assertFalse(recurrenceRuleRepository.findById(rule.getId()).isPresent());

        // Verify account balance: 970 + 30 = 1000
        Account updatedEur = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("1000.0000"), updatedEur.getBalance());
    }

    @Test
    public void testDeleteTransaction_Future() throws Exception {
        // Create recurrence rule
        RecurrenceRule rule = recurrenceRuleRepository.save(RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.WEEKLY)
                .interval(1)
                .startDate(LocalDate.of(2026, 6, 1))
                .build());

        // Create 3 transactions: tx1 (June 1st), tx2 (June 8th), tx3 (June 15th)
        Transaction tx1 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .recurrenceRuleId(rule.getId())
                .amount(new BigDecimal("10.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build());

        Transaction tx2 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .recurrenceRuleId(rule.getId())
                .amount(new BigDecimal("10.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 8, 10, 0))
                .build());

        Transaction tx3 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .recurrenceRuleId(rule.getId())
                .amount(new BigDecimal("10.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 15, 10, 0))
                .build());

        // Manually adjust the balance of the account
        eurAccount.setBalance(new BigDecimal("970.0000"));
        accountRepository.save(eurAccount);

        // Delete tx2 with FUTURE
        mockMvc.perform(delete("/v1/transactions/" + tx2.getId())
                        .header("X-User-Id", testUser.getId())
                        .param("mode", "FUTURE"))
                .andExpect(status().isNoContent());

        // Verify database: tx1 remains, tx2 and tx3 deleted
        assertTrue(transactionRepository.findById(tx1.getId()).isPresent());
        assertFalse(transactionRepository.findById(tx2.getId()).isPresent());
        assertFalse(transactionRepository.findById(tx3.getId()).isPresent());

        // Verify rule exists and has new end date (tx2 date (June 8) - 1 day = June 7)
        RecurrenceRule updatedRule = recurrenceRuleRepository.findById(rule.getId()).orElseThrow();
        assertEquals(LocalDate.of(2026, 6, 7), updatedRule.getEndDate());

        // Verify account balance: 970 + 20 = 990
        Account updatedEur = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("990.0000"), updatedEur.getBalance());
    }

    @Test
    public void testCreateTransfer_Success() throws Exception {
        Account checkingAccount2 = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("EUR Checking 2")
                .type(AccountType.CHECKING)
                .balance(new BigDecimal("200.0000"))
                .currency("EUR")
                .build());

        com.budgettracker.backend.dto.CreateTransferRequest request = com.budgettracker.backend.dto.CreateTransferRequest.builder()
                .fromAccountId(eurAccount.getId())
                .toAccountId(checkingAccount2.getId())
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .date(LocalDateTime.now())
                .notes("Transfer EUR checking to EUR checking 2")
                .build();

        mockMvc.perform(post("/v1/transactions/transfer")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.amount", is(100.0)))
                .andExpect(jsonPath("$.type", is("MOVE")))
                .andExpect(jsonPath("$.linkedTransactionId", notNullValue()));

        // Verify balance updates:
        // EUR Checking: 1000 - 100 = 900
        Account updatedEur = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("900.0000"), updatedEur.getBalance());

        // EUR Checking 2: 200 + 100 = 300
        Account updatedChecking2 = accountRepository.findById(checkingAccount2.getId()).orElseThrow();
        assertEquals(new BigDecimal("300.0000"), updatedChecking2.getBalance());
    }

    @Test
    public void testCreateTransfer_DifferentTypes_Fails() throws Exception {
        com.budgettracker.backend.dto.CreateTransferRequest request = com.budgettracker.backend.dto.CreateTransferRequest.builder()
                .fromAccountId(eurAccount.getId())
                .toAccountId(ronAccount.getId()) // one CHECKING, one SAVINGS
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .date(LocalDateTime.now())
                .notes("Transfer EUR checking to RON savings")
                .build();

        mockMvc.perform(post("/v1/transactions/transfer")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Source and destination accounts must be of the same type")));
    }

    @Test
    public void testCreateTransfer_InsufficientBalance_Success() throws Exception {
        Account checkingAccount2 = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("EUR Checking 2")
                .type(AccountType.CHECKING)
                .balance(new BigDecimal("200.0000"))
                .currency("EUR")
                .build());

        com.budgettracker.backend.dto.CreateTransferRequest request = com.budgettracker.backend.dto.CreateTransferRequest.builder()
                .fromAccountId(eurAccount.getId())
                .toAccountId(checkingAccount2.getId())
                .amount(new BigDecimal("1500.00")) // exceeding eurAccount balance of 1000
                .currency("EUR")
                .date(LocalDateTime.now())
                .notes("Transfer exceeding balance")
                .build();

        mockMvc.perform(post("/v1/transactions/transfer")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.amount", is(1500.0)))
                .andExpect(jsonPath("$.type", is("MOVE")))
                .andExpect(jsonPath("$.linkedTransactionId", notNullValue()));

        // Verify balance updates allow negative values:
        // EUR Checking: 1000 - 1500 = -500
        Account updatedEur = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("-500.0000"), updatedEur.getBalance());

        // EUR Checking 2: 200 + 1500 = 1700
        Account updatedChecking2 = accountRepository.findById(checkingAccount2.getId()).orElseThrow();
        assertEquals(new BigDecimal("1700.0000"), updatedChecking2.getBalance());
    }

    @Test
    public void testDeleteTransfer_Cascades() throws Exception {
        // Create two transactions manually and link them to simulate a transfer
        Transaction t1 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .amount(new BigDecimal("50.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("50.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build());

        Transaction t2 = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(ronAccount.getId())
                .amount(new BigDecimal("50.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("248.5000"))
                .exchangeRate(new BigDecimal("4.970000"))
                .type(CategoryType.INCOME)
                .date(LocalDateTime.now())
                .build());

        transactionRepository.updateLinkedTransactionId(t1.getId(), t2.getId());
        transactionRepository.updateLinkedTransactionId(t2.getId(), t1.getId());
        t1.setLinkedTransactionId(t2.getId());

        // Set initial balances to represent post-transaction balances
        eurAccount.setBalance(new BigDecimal("950.0000"));
        accountRepository.save(eurAccount);

        ronAccount.setBalance(new BigDecimal("748.5000"));
        accountRepository.save(ronAccount);

        // Delete source transaction t1: Should automatically cascade and delete t2, and restore balances.
        mockMvc.perform(delete("/v1/transactions/" + t1.getId())
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isNoContent());

        // Verify both transactions are gone
        assertFalse(transactionRepository.findById(t1.getId()).isPresent());
        assertFalse(transactionRepository.findById(t2.getId()).isPresent());

        // Verify balances restored:
        // EUR Checking: 950 + 50 = 1000
        Account restoredEur = accountRepository.findById(eurAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("1000.0000"), restoredEur.getBalance());

        // RON Savings: 748.5000 - 248.5000 = 500
        Account restoredRon = accountRepository.findById(ronAccount.getId()).orElseThrow();
        assertEquals(new BigDecimal("500.0000"), restoredRon.getBalance());
    }

    @Test
    public void testCreateSavingsTransaction_NoGoal_Success() throws Exception {
        Category customSavingsCat = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("General Savings")
                .type(CategoryType.SAVINGS)
                .build());

        com.budgettracker.backend.dto.CreateSavingsTransactionRequest request = com.budgettracker.backend.dto.CreateSavingsTransactionRequest.builder()
                .fromAccountId(eurAccount.getId())
                .toAccountId(ronAccount.getId())
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .type(com.budgettracker.backend.jooq.enums.SavingsTransactionType.DEPOSIT)
                .date(LocalDateTime.now())
                .categoryId(customSavingsCat.getId())
                .build();

        mockMvc.perform(post("/v1/transactions/savings")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.savingsGoalId", nullValue()))
                .andExpect(jsonPath("$.amount", is(100.0)))
                .andExpect(jsonPath("$.type", is("DEPOSIT")));
    }

    @Test
    public void testCreateSavingsTransaction_NoGoal_AutoLinksToExistingGoal() throws Exception {
        com.budgettracker.backend.model.SavingsGoal goal = savingsGoalRepository.save(com.budgettracker.backend.model.SavingsGoal.builder()
                .userId(testUser.getId())
                .categoryId(savingsCategory.getId())
                .goalType(com.budgettracker.backend.jooq.enums.SavingsGoalType.ONE_OFF)
                .targetAmount(new BigDecimal("5000.00"))
                .currentAmount(BigDecimal.ZERO)
                .build());

        com.budgettracker.backend.dto.CreateSavingsTransactionRequest request = com.budgettracker.backend.dto.CreateSavingsTransactionRequest.builder()
                .fromAccountId(eurAccount.getId())
                .toAccountId(ronAccount.getId())
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .type(com.budgettracker.backend.jooq.enums.SavingsTransactionType.DEPOSIT)
                .date(LocalDateTime.now())
                .categoryId(savingsCategory.getId())
                .build();

        mockMvc.perform(post("/v1/transactions/savings")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.savingsGoalId", is(goal.getId().intValue())))
                .andExpect(jsonPath("$.amount", is(50.0)));
    }

    @Test
    public void testUpdateTransaction_AddRecurrenceRule() throws Exception {
        var request = CreateTransactionRequest.builder()
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .notes("Single Tx")
                .build();

        String res = mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long txId = objectMapper.readValue(res, com.budgettracker.backend.dto.TransactionDto.class).getId();

        var updateRequest = UpdateTransactionRequest.builder()
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .notes("Single Tx updated to recurring")
                .recurrenceRule(com.budgettracker.backend.dto.CreateRecurrenceRuleRequest.builder()
                        .frequency(RecurrenceFrequency.WEEKLY)
                        .interval(1)
                        .startDate(LocalDate.now())
                        .build())
                .build();

        mockMvc.perform(patch("/v1/transactions/" + txId)
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceRuleId", notNullValue()));
    }

    @Test
    public void testUpdateTransaction_ChangeCategoryToSavings() throws Exception {
        savingsGoalRepository.save(com.budgettracker.backend.model.SavingsGoal.builder()
                .userId(testUser.getId())
                .categoryId(savingsCategory.getId())
                .goalType(SavingsGoalType.ONE_OFF)
                .targetAmount(new BigDecimal("1000.00"))
                .currentAmount(BigDecimal.ZERO)
                .build());

        var request = CreateTransactionRequest.builder()
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build();

        String res = mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long txId = objectMapper.readValue(res, com.budgettracker.backend.dto.TransactionDto.class).getId();

        var updateRequest = UpdateTransactionRequest.builder()
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .categoryId(savingsCategory.getId())
                .accountId(eurAccount.getId())
                .type(CategoryType.SAVINGS)
                .date(LocalDateTime.now())
                .build();

        mockMvc.perform(patch("/v1/transactions/" + txId)
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());
    }

    @Test
    public void testDeleteTransaction_AllModes() throws Exception {
        var request = CreateTransactionRequest.builder()
                .amount(new BigDecimal("30.00"))
                .currency("EUR")
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .recurrenceRule(com.budgettracker.backend.dto.CreateRecurrenceRuleRequest.builder()
                        .frequency(RecurrenceFrequency.DAILY)
                        .interval(1)
                        .startDate(LocalDate.now())
                        .build())
                .build();

        String res = mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var dto = objectMapper.readValue(res, com.budgettracker.backend.dto.TransactionDto.class);
        Long txId = dto.getId();
        Long ruleId = dto.getRecurrenceRuleId();

        transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .amount(new BigDecimal("30.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("30.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now().plusDays(1))
                .recurrenceRuleId(ruleId)
                .build());

        mockMvc.perform(delete("/v1/transactions/" + txId)
                        .header("X-User-Id", testUser.getId())
                        .param("mode", "THIS_ONLY"))
                .andExpect(status().isNoContent());

        List<Transaction> remaining = transactionRepository.findByRecurrenceRuleId(ruleId);
        assertEquals(1, remaining.size());
        assertTrue(recurrenceRuleRepository.findById(ruleId).isPresent());

        Long siblingId = remaining.get(0).getId();
        mockMvc.perform(delete("/v1/transactions/" + siblingId)
                        .header("X-User-Id", testUser.getId())
                        .param("mode", "ALL"))
                .andExpect(status().isNoContent());

        assertTrue(transactionRepository.findByRecurrenceRuleId(ruleId).isEmpty());
        assertFalse(recurrenceRuleRepository.findById(ruleId).isPresent());
    }

    @Test
    public void testDeleteTransaction_Forbidden() throws Exception {
        var tx = transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(expenseCategory.getId())
                .accountId(eurAccount.getId())
                .amount(new BigDecimal("10.00"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("10.00"))
                .convertedCurrency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .build());
        mockMvc.perform(delete("/v1/transactions/" + tx.getId())
                        .header("X-User-Id", otherUser.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testGetTransactions_NoStartDate() throws Exception {
        mockMvc.perform(get("/v1/transactions")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk());
    }
}

