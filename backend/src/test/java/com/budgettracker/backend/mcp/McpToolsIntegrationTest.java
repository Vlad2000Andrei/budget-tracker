package com.budgettracker.backend.mcp;

import com.budgettracker.backend.dto.*;
import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.SavingsGoalType;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.UserRepository;
import com.budgettracker.backend.service.AccountService;
import com.budgettracker.backend.service.CategoryService;
import com.budgettracker.backend.service.SavingsGoalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@Transactional
public class McpToolsIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private SavingsGoalService savingsGoalService;

    @Autowired
    private TransactionMcpTools transactionMcpTools;

    @Autowired
    private CategoryMcpTools categoryMcpTools;

    @Autowired
    private StatsMcpTools statsMcpTools;

    @Autowired
    private SavingsGoalMcpTools savingsGoalMcpTools;

    @Autowired
    private AccountMcpTools accountMcpTools;

    private User testUser;
    private AccountDto checkingAccount;
    private AccountDto savingsAccount;
    private CategoryDto groceriesCategory;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("mcp-user@example.com")
                .googleSub("google-sub-mcp-user")
                .displayName("MCP User")
                .defaultCurrency("USD")
                .isOnboarded(true)
                .build());

        checkingAccount = accountService.createAccount(CreateAccountRequest.builder()
                .name("Checking Account")
                .type(AccountType.CHECKING)
                .initialBalance(new BigDecimal("500.0000"))
                .currency("USD")
                .build(), testUser);

        savingsAccount = accountService.createAccount(CreateAccountRequest.builder()
                .name("Savings Account")
                .type(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.0000"))
                .currency("USD")
                .build(), testUser);

        groceriesCategory = categoryService.createCategory(CreateCategoryRequest.builder()
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .icon("shopping_cart")
                .color("#FF5733")
                .build(), testUser);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                testUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCreateTransactionWithAutoResolvedDefaultAccount() {
        // Create an expense without specifying accountId -> should default to checking account
        TransactionDto tx = transactionMcpTools.createTransaction(
                new BigDecimal("45.50"),
                "EXPENSE",
                groceriesCategory.getId(),
                "USD",
                null, // omitted accountId
                null, // date defaults to now
                "Trader Joe's groceries"
        );

        assertNotNull(tx.getId());
        assertEquals(0, new BigDecimal("45.50").compareTo(tx.getAmount()));
        assertEquals("USD", tx.getCurrency());
        assertEquals(checkingAccount.getId(), tx.getAccountId(), "Should auto-resolve to primary account");

        // Verify balance decreased
        List<AccountDto> accounts = accountMcpTools.listAccounts();
        AccountDto updatedChecking = accounts.stream()
                .filter(a -> a.getId().equals(checkingAccount.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("454.5000"), updatedChecking.getBalance());
    }

    @Test
    void testCreateTransferBetweenAccounts() {
        TransactionDto transfer = transactionMcpTools.createTransfer(
                checkingAccount.getId(),
                savingsAccount.getId(),
                new BigDecimal("150.00"),
                "USD",
                null,
                "Transfer to savings"
        );

        assertNotNull(transfer.getId());

        List<AccountDto> accounts = accountMcpTools.listAccounts();
        AccountDto chk = accounts.stream().filter(a -> a.getId().equals(checkingAccount.getId())).findFirst().orElseThrow();
        AccountDto sav = accounts.stream().filter(a -> a.getId().equals(savingsAccount.getId())).findFirst().orElseThrow();

        assertEquals(new BigDecimal("350.0000"), chk.getBalance());
        assertEquals(new BigDecimal("1150.0000"), sav.getBalance());
    }

    @Test
    void testCategoryManagement_CreateUpdateDelete() {
        CategoryDto created = categoryMcpTools.createCategory("Hobbies", "EXPENSE", null, "sports", "#00FF00");
        assertNotNull(created.getId());
        assertEquals("Hobbies", created.getName());

        CategoryDto updated = categoryMcpTools.updateCategory(created.getId(), "Outdoor Hobbies", null, "terrain", "#008800");
        assertEquals("Outdoor Hobbies", updated.getName());

        String deleteMsg = categoryMcpTools.deleteCategory(created.getId());
        assertTrue(deleteMsg.contains("successfully deleted"));
    }

    @Test
    void testStatsTools_SpendingBreakdownAndCashFlow() {
        // Add expense
        transactionMcpTools.createTransaction(
                new BigDecimal("80.00"),
                "EXPENSE",
                groceriesCategory.getId(),
                "USD",
                checkingAccount.getId(),
                null,
                "Weekly food"
        );

        SpendingBreakdownDto breakdown = statsMcpTools.getSpendingBreakdown(null, null, null);
        assertNotNull(breakdown);
        assertEquals(new BigDecimal("80.0000"), breakdown.getTotalExpense());
        assertFalse(breakdown.getCategories().isEmpty());
        assertEquals("Groceries", breakdown.getCategories().get(0).getCategoryName());

        NetCashFlowDto cashFlow = statsMcpTools.getNetCashFlow(null, null);
        assertNotNull(cashFlow);
        assertEquals(new BigDecimal("80.0000"), cashFlow.getTotalExpenses());
        assertEquals(new BigDecimal("-80.0000"), cashFlow.getNetCashFlow());
    }

    @Test
    void testSavingsGoals_StrictlyReadOnlyEnforcement() {
        CategoryDto savingsCat = categoryService.createCategory(CreateCategoryRequest.builder()
                .name("Emergency Fund")
                .type(CategoryType.SAVINGS)
                .build(), testUser);

        SavingsGoalDto goal = savingsGoalService.createSavingsGoal(CreateSavingsGoalRequest.builder()
                .categoryId(savingsCat.getId())
                .targetAmount(new BigDecimal("10000.00"))
                .goalType(SavingsGoalType.ONE_OFF)
                .targetDate(LocalDate.now().plusMonths(12))
                .build(), testUser);

        // Read tools work
        List<SavingsGoalDto> goals = savingsGoalMcpTools.listSavingsGoals();
        assertEquals(1, goals.size());
        assertEquals(goal.getId(), goals.get(0).getId());

        SavingsGoalDto fetched = savingsGoalMcpTools.getSavingsGoal(goal.getId());
        assertEquals(0, new BigDecimal("10000.00").compareTo(fetched.getTargetAmount()));

        List<SavingsTransactionDto> txs = savingsGoalMcpTools.listSavingsTransactions(goal.getId());
        assertNotNull(txs);

        // Verify NO write methods exist on SavingsGoalMcpTools or StatsMcpTools via reflection
        for (Method method : SavingsGoalMcpTools.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.startsWith("create"), "SavingsGoalMcpTools must not have create methods: " + method.getName());
            assertFalse(name.startsWith("update"), "SavingsGoalMcpTools must not have update methods: " + method.getName());
            assertFalse(name.startsWith("delete"), "SavingsGoalMcpTools must not have delete methods: " + method.getName());
        }

        for (Method method : StatsMcpTools.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.startsWith("create"), "StatsMcpTools must not have create methods: " + method.getName());
            assertFalse(name.startsWith("update"), "StatsMcpTools must not have update methods: " + method.getName());
            assertFalse(name.startsWith("delete"), "StatsMcpTools must not have delete methods: " + method.getName());
        }
    }
}
