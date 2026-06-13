package com.budgettracker.backend.controller;

import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.RolloverRuleType;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.Budget;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.SavingsGoal;
import com.budgettracker.backend.model.Transaction;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.*;
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
import static com.budgettracker.backend.jooq.Tables.BUDGETS;
import static com.budgettracker.backend.jooq.Tables.CATEGORIES;
import static com.budgettracker.backend.jooq.Tables.SAVINGS_GOALS;
import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
@Transactional
public class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;
    private Category foodCategory;
    private Category groceriesCategory;
    private Category utilitiesCategory;
    private Category savingsCategory;
    private Category salaryCategory;
    private Account checkingAccount;
    private Account usdSavingsAccount;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(TRANSACTIONS).execute();
        dsl.deleteFrom(SAVINGS_GOALS).execute();
        dsl.deleteFrom(BUDGETS).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(ACCOUNTS).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("dash-user@example.com")
                .googleSub("dash-sub-777")
                .defaultCurrency("EUR")
                .build());

        // Seeding some categories
        foodCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Food")
                .type(CategoryType.EXPENSE)
                .icon("fastfood")
                .color("#FF5733")
                .build());

        groceriesCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .parentId(foodCategory.getId())
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .icon("shopping_cart")
                .color("#FF5733")
                .build());

        utilitiesCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Utilities")
                .type(CategoryType.EXPENSE)
                .icon("power")
                .color("#3357FF")
                .build());

        savingsCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Savings")
                .type(CategoryType.SAVINGS)
                .icon("savings")
                .color("#F3FF33")
                .build());

        salaryCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Salary")
                .type(CategoryType.INCOME)
                .icon("payments")
                .color("#33FF57")
                .build());

        // Seeding accounts
        checkingAccount = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Checking EUR")
                .type(AccountType.CHECKING)
                .balance(new BigDecimal("1500.0000"))
                .currency("EUR")
                .build());

        // Seeding secondary account in different currency (USD)
        // Static conversion from USD to EUR is 0.93 (defined in CurrencyExchangeService fallback)
        usdSavingsAccount = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Savings USD")
                .type(AccountType.SAVINGS)
                .balance(new BigDecimal("1000.0000"))
                .currency("USD")
                .build());
    }

    @Test
    public void testGetDashboardSummary_Success() throws Exception {
        // Create transactions inside current month
        LocalDateTime today = LocalDateTime.now();

        // 1. Income transaction (Salary: 2500 EUR)
        transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(salaryCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("2500.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("2500.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.INCOME)
                .date(today)
                .build());

        // 2. Expense transaction in groceries (Food -> Groceries: 100 EUR)
        transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(groceriesCategory.getId())
                .accountId(checkingAccount.getId())
                .amount(new BigDecimal("100.0000"))
                .currency("EUR")
                .convertedAmount(new BigDecimal("100.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(today)
                .build());

        // 3. Expense transaction in utilities (50 USD = 46.50 EUR)
        transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(utilitiesCategory.getId())
                .accountId(usdSavingsAccount.getId())
                .amount(new BigDecimal("50.0000"))
                .currency("USD")
                .convertedAmount(new BigDecimal("46.5000"))
                .exchangeRate(new BigDecimal("0.93"))
                .type(CategoryType.EXPENSE)
                .date(today)
                .build());

        // Create a budget on parent category "Food" of 300 EUR
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate lastOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        budgetRepository.save(Budget.builder()
                .userId(testUser.getId())
                .categoryId(foodCategory.getId())
                .amountLimit(new BigDecimal("300.0000"))
                .startDate(firstOfMonth)
                .endDate(lastOfMonth)
                .rolloverRule(RolloverRuleType.NONE)
                .build());

        // Create savings goal
        savingsGoalRepository.save(SavingsGoal.builder()
                .userId(testUser.getId())
                .categoryId(savingsCategory.getId())
                .targetAmount(new BigDecimal("5000.0000"))
                .currentAmount(new BigDecimal("1200.0000"))
                .targetDate(LocalDate.now().plusMonths(6))
                .build());

        // Run request
        // Total balance = 1500 EUR (checking) + 1000 USD * 0.93 (USD conversion) = 1500 + 930 = 2430 EUR
        mockMvc.perform(get("/v1/dashboard-summary")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance", is(2430.0)))
                .andExpect(jsonPath("$.balanceCurrency", is("EUR")))
                .andExpect(jsonPath("$.monthIncome", is(2500.0)))
                .andExpect(jsonPath("$.monthExpenses", is(146.50))) // 100 EUR + 46.50 EUR
                .andExpect(jsonPath("$.budgets", hasSize(1)))
                .andExpect(jsonPath("$.budgets[0].categoryName", is("Food")))
                .andExpect(jsonPath("$.budgets[0].spent", is(100.0))) // Sums the descendant category (groceries)
                .andExpect(jsonPath("$.budgets[0].limit", is(300.0)))
                .andExpect(jsonPath("$.budgets[0].pct", is(33))) // 100/300 * 100 = 33.33 -> 33%
                .andExpect(jsonPath("$.savingsGoals", hasSize(1)))
                .andExpect(jsonPath("$.savingsGoals[0].current", is(1200.0)))
                .andExpect(jsonPath("$.savingsGoals[0].target", is(5000.0)))
                .andExpect(jsonPath("$.savingsGoals[0].pct", is(24))); // 1200/5000 * 100 = 24%
    }
}
