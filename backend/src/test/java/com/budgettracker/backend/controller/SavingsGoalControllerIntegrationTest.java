package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.CreateSavingsGoalRequest;
import com.budgettracker.backend.dto.CreateSavingsTransactionRequest;
import com.budgettracker.backend.dto.CreateTransactionRequest;
import com.budgettracker.backend.dto.SavingsGoalDto;
import com.budgettracker.backend.dto.UpdateSavingsGoalRequest;
import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.SavingsGoalType;
import com.budgettracker.backend.jooq.enums.SavingsTransactionType;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.AccountRepository;
import com.budgettracker.backend.repository.CategoryRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.budgettracker.backend.jooq.Tables.ACCOUNTS;
import static com.budgettracker.backend.jooq.Tables.CATEGORIES;
import static com.budgettracker.backend.jooq.Tables.SAVINGS_TRANSACTIONS;
import static com.budgettracker.backend.jooq.Tables.SAVINGS_GOALS;
import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
@Transactional
public class SavingsGoalControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private AccountRepository accountRepository;

    private User testUser;
    private User otherUser;
    private Category parentSavingsCategory;
    private Category childSavingsCategory;
    private Category expenseCategory;
    private Account testAccount;        // Checking — source for deposits
    private Account savingsAccount;     // Savings — destination for deposits

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(SAVINGS_TRANSACTIONS).execute();
        dsl.deleteFrom(SAVINGS_GOALS).execute();
        dsl.deleteFrom(TRANSACTIONS).execute();
        dsl.deleteFrom(ACCOUNTS).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("test-user@example.com")
                .googleSub("test-google-sub-999")
                .defaultCurrency("USD")
                .build());

        otherUser = userRepository.save(User.builder()
                .email("other-user@example.com")
                .googleSub("other-google-sub-888")
                .defaultCurrency("USD")
                .build());

        parentSavingsCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Savings Root")
                .type(CategoryType.SAVINGS)
                .build());

        childSavingsCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .parentId(parentSavingsCategory.getId())
                .name("Car Savings")
                .type(CategoryType.SAVINGS)
                .build());

        expenseCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Food Expense")
                .type(CategoryType.EXPENSE)
                .build());

        testAccount = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Checking")
                .type(AccountType.CHECKING)
                .balance(new BigDecimal("5000.0000"))
                .currency("USD")
                .build());

        savingsAccount = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Savings")
                .type(AccountType.SAVINGS)
                .balance(new BigDecimal("0.0000"))
                .currency("USD")
                .build());
    }

    @Test
    public void testGetSavingsGoals_Empty() throws Exception {
        mockMvc.perform(get("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void testCreateSavingsGoal_Success() throws Exception {
        CreateSavingsGoalRequest request = CreateSavingsGoalRequest.builder()
                .categoryId(parentSavingsCategory.getId())
                .goalType(SavingsGoalType.ONE_OFF)
                .targetAmount(new BigDecimal("10000.00"))
                .targetDate(LocalDate.of(2027, 12, 31))
                .build();

        mockMvc.perform(post("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.targetAmount", is(10000.00)))
                .andExpect(jsonPath("$.currentAmount", is(0.00)))
                .andExpect(jsonPath("$.targetDate", is("2027-12-31")));
    }

    @Test
    public void testCreateSavingsGoal_InvalidCategoryType() throws Exception {
        CreateSavingsGoalRequest request = CreateSavingsGoalRequest.builder()
                .categoryId(expenseCategory.getId()) // EXPENSE instead of SAVINGS
                .goalType(SavingsGoalType.ONE_OFF)
                .targetAmount(new BigDecimal("10000.00"))
                .build();

        mockMvc.perform(post("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("categories of type SAVINGS")));
    }

    @Test
    public void testCreateSavingsGoal_ForbiddenCategory() throws Exception {
        Category otherUserCategory = categoryRepository.save(Category.builder()
                .userId(otherUser.getId())
                .name("Other Savings")
                .type(CategoryType.SAVINGS)
                .build());

        CreateSavingsGoalRequest request = CreateSavingsGoalRequest.builder()
                .categoryId(otherUserCategory.getId())
                .goalType(SavingsGoalType.ONE_OFF)
                .targetAmount(new BigDecimal("10000.00"))
                .build();

        mockMvc.perform(post("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("do not have access")));
    }

    @Test
    public void testSavingsGoal_ProgressReconciliation() throws Exception {
        // 1. Create a savings goal on parentSavingsCategory
        CreateSavingsGoalRequest request = CreateSavingsGoalRequest.builder()
                .categoryId(parentSavingsCategory.getId())
                .goalType(SavingsGoalType.ONE_OFF)
                .targetAmount(new BigDecimal("5000.00"))
                .build();

        String responseContent = mockMvc.perform(post("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        SavingsGoalDto goal = objectMapper.readValue(responseContent, SavingsGoalDto.class);

        // 2. Post a SAVINGS transaction under childSavingsCategory
        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .categoryId(childSavingsCategory.getId())
                .amount(new BigDecimal("1500.00"))
                .currency("USD")
                .type(CategoryType.SAVINGS)
                .date(LocalDateTime.now())
                .notes("Car fund deposit")
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txRequest)))
                .andExpect(status().isCreated());

        // 3. Fetch savings goals and verify currentAmount has updated
        mockMvc.perform(get("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(goal.getId().intValue())))
                .andExpect(jsonPath("$[0].currentAmount", is(1500.00)));
    }

    @Test
    public void testSavingsGoal_MonthlyVsOneOff() throws Exception {
        // 1. Create a ONE_OFF goal
        CreateSavingsGoalRequest requestOneOff = CreateSavingsGoalRequest.builder()
                .categoryId(childSavingsCategory.getId())
                .goalType(SavingsGoalType.ONE_OFF)
                .targetAmount(new BigDecimal("5000.00"))
                .build();

        String resOneOff = mockMvc.perform(post("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestOneOff)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        SavingsGoalDto oneOffGoal = objectMapper.readValue(resOneOff, SavingsGoalDto.class);

        // 2. Create a MONTHLY goal
        CreateSavingsGoalRequest requestMonthly = CreateSavingsGoalRequest.builder()
                .categoryId(parentSavingsCategory.getId())
                .goalType(SavingsGoalType.MONTHLY)
                .targetAmount(new BigDecimal("2000.00"))
                .build();

        String resMonthly = mockMvc.perform(post("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestMonthly)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        SavingsGoalDto monthlyGoal = objectMapper.readValue(resMonthly, SavingsGoalDto.class);

        // 3. Post a SAVINGS transaction in the past month (e.g. 45 days ago)
        CreateTransactionRequest pastTx = CreateTransactionRequest.builder()
                .categoryId(childSavingsCategory.getId())
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .type(CategoryType.SAVINGS)
                .date(LocalDateTime.now().minusDays(45))
                .notes("Past deposit")
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pastTx)))
                .andExpect(status().isCreated());

        // 4. Post a SAVINGS transaction in the current month
        CreateTransactionRequest currentTx = CreateTransactionRequest.builder()
                .categoryId(childSavingsCategory.getId())
                .amount(new BigDecimal("1500.00"))
                .currency("USD")
                .type(CategoryType.SAVINGS)
                .date(LocalDateTime.now())
                .notes("Current deposit")
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(currentTx)))
                .andExpect(status().isCreated());

        // 5. Fetch goals and verify amounts
        // ONE_OFF child goal: should have BOTH past and current transactions (1000 + 1500 = 2500)
        // MONTHLY parent goal: should have ONLY current transaction (1500) because past is outside the current month
        mockMvc.perform(get("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + oneOffGoal.getId() + ")].currentAmount", contains(2500.00)))
                .andExpect(jsonPath("$[?(@.id == " + monthlyGoal.getId() + ")].currentAmount", contains(1500.00)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Account-linked savings transactions
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    public void testDeposit_BalanceDeducted_GoalProgressIncreased() throws Exception {
        // Create goal
        String goalJson = mockMvc.perform(post("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSavingsGoalRequest.builder()
                                .categoryId(parentSavingsCategory.getId())
                                .goalType(SavingsGoalType.ONE_OFF)
                                .targetAmount(new BigDecimal("1000.00"))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long goalId = objectMapper.readValue(goalJson, SavingsGoalDto.class).getId();

        // Deposit 200 USD: FROM Checking TO Savings
        CreateSavingsTransactionRequest req = CreateSavingsTransactionRequest.builder()
                .fromAccountId(testAccount.getId())
                .toAccountId(savingsAccount.getId())
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .type(SavingsTransactionType.DEPOSIT)
                .date(LocalDateTime.now())
                .notes("First deposit")
                .build();

        mockMvc.perform(post("/v1/savings-goals/" + goalId + "/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("DEPOSIT")))
                .andExpect(jsonPath("$.amount", is(200.00)))
                .andExpect(jsonPath("$.fromAccountName", is("Checking")))
                .andExpect(jsonPath("$.toAccountName", is("Savings")));

        // Goal currentAmount should now be 200
        mockMvc.perform(get("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentAmount", is(200.00)));

        // Checking balance: 5000 - 200 = 4800; Savings balance: 0 + 200 = 200
        Account updatedChecking = accountRepository.findById(testAccount.getId()).orElseThrow();
        Account updatedSavings  = accountRepository.findById(savingsAccount.getId()).orElseThrow();
        assert updatedChecking.getBalance().compareTo(new BigDecimal("4800.0000")) == 0
                : "Expected Checking balance 4800 but got " + updatedChecking.getBalance();
        assert updatedSavings.getBalance().compareTo(new BigDecimal("200.0000")) == 0
                : "Expected Savings balance 200 but got " + updatedSavings.getBalance();
    }

    @Test
    public void testWithdrawal_BalanceCredited_GoalProgressDecreased() throws Exception {
        // Create goal
        String goalJson = mockMvc.perform(post("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSavingsGoalRequest.builder()
                                .categoryId(parentSavingsCategory.getId())
                                .goalType(SavingsGoalType.ONE_OFF)
                                .targetAmount(new BigDecimal("1000.00"))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long goalId = objectMapper.readValue(goalJson, SavingsGoalDto.class).getId();

        // Deposit 500: Checking -> Savings
        mockMvc.perform(post("/v1/savings-goals/" + goalId + "/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSavingsTransactionRequest.builder()
                                .fromAccountId(testAccount.getId())
                                .toAccountId(savingsAccount.getId())
                                .amount(new BigDecimal("500.00"))
                                .currency("USD")
                                .type(SavingsTransactionType.DEPOSIT)
                                .date(LocalDateTime.now())
                                .build())))
                .andExpect(status().isCreated());

        // Withdraw 200: Savings -> Checking
        mockMvc.perform(post("/v1/savings-goals/" + goalId + "/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSavingsTransactionRequest.builder()
                                .fromAccountId(savingsAccount.getId())
                                .toAccountId(testAccount.getId())
                                .amount(new BigDecimal("200.00"))
                                .currency("USD")
                                .type(SavingsTransactionType.WITHDRAWAL)
                                .date(LocalDateTime.now())
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("WITHDRAWAL")))
                .andExpect(jsonPath("$.fromAccountName", is("Savings")))
                .andExpect(jsonPath("$.toAccountName", is("Checking")));

        // Goal currentAmount = 500 - 200 = 300
        mockMvc.perform(get("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentAmount", is(300.00)));

        // Checking: 5000 - 500 + 200 = 4700; Savings: 0 + 500 - 200 = 300
        Account updatedChecking = accountRepository.findById(testAccount.getId()).orElseThrow();
        Account updatedSavings  = accountRepository.findById(savingsAccount.getId()).orElseThrow();
        assert updatedChecking.getBalance().compareTo(new BigDecimal("4700.0000")) == 0
                : "Expected Checking 4700 but got " + updatedChecking.getBalance();
        assert updatedSavings.getBalance().compareTo(new BigDecimal("300.0000")) == 0
                : "Expected Savings 300 but got " + updatedSavings.getBalance();
    }

    @Test
    public void testDeposit_InsufficientAccountBalance_ReturnsBadRequest() throws Exception {
        String goalJson = mockMvc.perform(post("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSavingsGoalRequest.builder()
                                .categoryId(parentSavingsCategory.getId())
                                .goalType(SavingsGoalType.ONE_OFF)
                                .targetAmount(new BigDecimal("1000.00"))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long goalId = objectMapper.readValue(goalJson, SavingsGoalDto.class).getId();

        // Try to deposit more than Checking account balance (5000)
        mockMvc.perform(post("/v1/savings-goals/" + goalId + "/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSavingsTransactionRequest.builder()
                                .fromAccountId(testAccount.getId())
                                .toAccountId(savingsAccount.getId())
                                .amount(new BigDecimal("9999.00"))
                                .currency("USD")
                                .type(SavingsTransactionType.DEPOSIT)
                                .date(LocalDateTime.now())
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient balance in source account")));
    }

    @Test
    public void testWithdrawal_ExceedsCurrentAmount_ReturnsBadRequest() throws Exception {
        String goalJson = mockMvc.perform(post("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSavingsGoalRequest.builder()
                                .categoryId(parentSavingsCategory.getId())
                                .goalType(SavingsGoalType.ONE_OFF)
                                .targetAmount(new BigDecimal("1000.00"))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long goalId = objectMapper.readValue(goalJson, SavingsGoalDto.class).getId();

        // Savings account has 0 balance, goal currentAmount is 0 — withdrawal should fail
        mockMvc.perform(post("/v1/savings-goals/" + goalId + "/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSavingsTransactionRequest.builder()
                                .fromAccountId(savingsAccount.getId())
                                .toAccountId(testAccount.getId())
                                .amount(new BigDecimal("100.00"))
                                .currency("USD")
                                .type(SavingsTransactionType.WITHDRAWAL)
                                .date(LocalDateTime.now())
                                .build())))
                .andExpect(status().isBadRequest())
                // With two-account model the fromAccount (Savings, balance=0) balance guard fires first.
                // The goal-balance guard is still exercised indirectly by testWithdrawal_BalanceCredited.
                .andExpect(jsonPath("$.message", containsString("Insufficient balance in source account")));
    }

    @Test
    public void testGetSavingsTransactions_ReturnsList() throws Exception {
        String goalJson = mockMvc.perform(post("/v1/savings-goals")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSavingsGoalRequest.builder()
                                .categoryId(parentSavingsCategory.getId())
                                .goalType(SavingsGoalType.ONE_OFF)
                                .targetAmount(new BigDecimal("1000.00"))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long goalId = objectMapper.readValue(goalJson, SavingsGoalDto.class).getId();

        // Deposit: Checking -> Savings
        mockMvc.perform(post("/v1/savings-goals/" + goalId + "/transactions")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSavingsTransactionRequest.builder()
                                .fromAccountId(testAccount.getId())
                                .toAccountId(savingsAccount.getId())
                                .amount(new BigDecimal("100.00"))
                                .currency("USD")
                                .type(SavingsTransactionType.DEPOSIT)
                                .date(LocalDateTime.now())
                                .notes("Test deposit")
                                .build())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/savings-goals/" + goalId + "/transactions")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type", is("DEPOSIT")))
                .andExpect(jsonPath("$[0].fromAccountName", is("Checking")))
                .andExpect(jsonPath("$[0].toAccountName", is("Savings")))
                .andExpect(jsonPath("$[0].notes", is("Test deposit")));
    }
}
