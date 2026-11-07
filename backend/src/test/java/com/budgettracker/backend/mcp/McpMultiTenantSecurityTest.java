package com.budgettracker.backend.mcp;

import com.budgettracker.backend.dto.AccountDto;
import com.budgettracker.backend.dto.CategoryDto;
import com.budgettracker.backend.dto.CreateAccountRequest;
import com.budgettracker.backend.dto.CreateCategoryRequest;
import com.budgettracker.backend.dto.CreateSavingsGoalRequest;
import com.budgettracker.backend.dto.CreateTransactionRequest;
import com.budgettracker.backend.dto.DashboardSummaryDto;
import com.budgettracker.backend.dto.SavingsGoalDto;
import com.budgettracker.backend.dto.TransactionDto;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.SavingsGoalType;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.UserRepository;
import com.budgettracker.backend.service.AccountService;
import com.budgettracker.backend.service.CategoryService;
import com.budgettracker.backend.service.SavingsGoalService;
import com.budgettracker.backend.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@Transactional
public class McpMultiTenantSecurityTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TransactionService transactionService;

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

    @Autowired
    private McpSessionSecurityRegistry sessionSecurityRegistry;

    private User alice;
    private User bob;
    private AccountDto aliceAccount;
    private AccountDto bobAccount;
    private CategoryDto aliceCategory;
    private CategoryDto bobCategory;
    private TransactionDto aliceTransaction;
    private SavingsGoalDto aliceGoal;

    @BeforeEach
    void setUp() {
        // Create Alice
        alice = userRepository.save(User.builder()
                .email("alice@tenant-a.com")
                .googleSub("google-sub-alice")
                .displayName("Alice")
                .defaultCurrency("USD")
                .isOnboarded(true)
                .build());

        // Create Bob
        bob = userRepository.save(User.builder()
                .email("bob@tenant-b.com")
                .googleSub("google-sub-bob")
                .displayName("Bob")
                .defaultCurrency("USD")
                .isOnboarded(true)
                .build());

        // Alice creates data
        aliceAccount = accountService.createAccount(CreateAccountRequest.builder()
                .name("Alice Checking")
                .type(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.0000"))
                .currency("USD")
                .build(), alice);

        aliceCategory = categoryService.createCategory(CreateCategoryRequest.builder()
                .name("Alice Secret Project")
                .type(CategoryType.EXPENSE)
                .build(), alice);

        aliceTransaction = transactionService.createTransaction(CreateTransactionRequest.builder()
                .accountId(aliceAccount.getId())
                .categoryId(aliceCategory.getId())
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.now())
                .notes("Confidential Alice transaction")
                .build(), alice);

        CategoryDto aliceSavingsCat = categoryService.createCategory(CreateCategoryRequest.builder()
                .name("Alice Dream Home")
                .type(CategoryType.SAVINGS)
                .build(), alice);

        aliceGoal = savingsGoalService.createSavingsGoal(CreateSavingsGoalRequest.builder()
                .categoryId(aliceSavingsCat.getId())
                .targetAmount(new BigDecimal("50000.00"))
                .goalType(SavingsGoalType.ONE_OFF)
                .targetDate(LocalDate.now().plusYears(2))
                .build(), alice);

        // Bob creates baseline data
        bobAccount = accountService.createAccount(CreateAccountRequest.builder()
                .name("Bob Checking")
                .type(AccountType.CHECKING)
                .initialBalance(new BigDecimal("100.0000"))
                .currency("USD")
                .build(), bob);

        bobCategory = categoryService.createCategory(CreateCategoryRequest.builder()
                .name("Bob Groceries")
                .type(CategoryType.EXPENSE)
                .build(), bob);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void testBobCannotSeeOrAccessAliceTransactions() {
        authenticateAs(bob);

        // 1. Bob lists transactions: must NOT see Alice's transaction
        List<TransactionDto> bobTxs = transactionMcpTools.listTransactions(null, null, null, null, null);
        assertTrue(bobTxs.isEmpty(), "Bob should have 0 transactions");

        // 2. Bob attempts to get Alice's transaction by ID: must fail with ForbiddenActionException
        assertThrows(ForbiddenActionException.class, () -> {
            transactionMcpTools.getTransaction(aliceTransaction.getId());
        });

        // 3. Bob attempts to update Alice's transaction: must fail
        assertThrows(ForbiddenActionException.class, () -> {
            transactionMcpTools.updateTransaction(
                    aliceTransaction.getId(),
                    new BigDecimal("10.00"),
                    "USD",
                    "EXPENSE",
                    bobCategory.getId(),
                    bobAccount.getId(),
                    null,
                    "Bob attempting to hijack Alice's transaction"
            );
        });

        // 4. Bob attempts to delete Alice's transaction: must fail
        assertThrows(ForbiddenActionException.class, () -> {
            transactionMcpTools.deleteTransaction(aliceTransaction.getId(), "THIS_ONLY");
        });
    }

    @Test
    void testBobCannotCreateTransactionUsingAliceAccountOrCategory() {
        authenticateAs(bob);

        // Attempt to create a transaction using Alice's category
        assertThrows(ForbiddenActionException.class, () -> {
            transactionMcpTools.createTransaction(
                    new BigDecimal("50.00"),
                    "EXPENSE",
                    aliceCategory.getId(),
                    "USD",
                    bobAccount.getId(),
                    null,
                    "Using Alice category"
            );
        });

        // Attempt to create a transaction using Alice's account
        assertThrows(ForbiddenActionException.class, () -> {
            transactionMcpTools.createTransaction(
                    new BigDecimal("50.00"),
                    "EXPENSE",
                    bobCategory.getId(),
                    "USD",
                    aliceAccount.getId(),
                    null,
                    "Using Alice account"
            );
        });
    }

    @Test
    void testBobCannotSeeOrModifyAliceCategories() {
        authenticateAs(bob);

        // 1. Bob lists categories: Alice's custom category must be completely absent
        List<CategoryDto> bobCategories = categoryMcpTools.listCategories();
        boolean aliceCatPresent = bobCategories.stream()
                .anyMatch(c -> c.getName().equals("Alice Secret Project") || c.getId().equals(aliceCategory.getId()));
        assertFalse(aliceCatPresent, "Alice's custom category must not be visible to Bob");

        // 2. Bob attempts to update Alice's category: must fail
        assertThrows(ForbiddenActionException.class, () -> {
            categoryMcpTools.updateCategory(
                    aliceCategory.getId(),
                    "Bob Hijacked Category",
                    null,
                    null,
                    null
            );
        });

        // 3. Bob attempts to delete Alice's category: must fail
        assertThrows(ForbiddenActionException.class, () -> {
            categoryMcpTools.deleteCategory(aliceCategory.getId());
        });
    }

    @Test
    void testBobCannotSeeAliceDashboardStatsOrBalances() {
        authenticateAs(bob);

        DashboardSummaryDto bobSummary = statsMcpTools.getDashboardSummary();
        assertNotNull(bobSummary);
        assertEquals(new BigDecimal("100.0000"), bobSummary.getTotalBalance(),
                "Bob's total balance must reflect Bob's account ($100), not Alice's ($1000)");

        boolean containsAliceAccount = bobSummary.getAccounts().stream()
                .anyMatch(a -> a.getName().contains("Alice"));
        assertFalse(containsAliceAccount, "Bob's dashboard must not contain Alice's accounts");
    }

    @Test
    void testBobCannotSeeOrAccessAliceSavingsGoals() {
        authenticateAs(bob);

        // 1. Bob lists savings goals: Alice's goal must NOT be present
        List<SavingsGoalDto> bobGoals = savingsGoalMcpTools.listSavingsGoals();
        assertTrue(bobGoals.isEmpty(), "Bob should have 0 savings goals");

        // 2. Bob attempts to get Alice's savings goal: must fail
        assertThrows(ForbiddenActionException.class, () -> {
            savingsGoalMcpTools.getSavingsGoal(aliceGoal.getId());
        });

        // 3. Bob attempts to list transactions for Alice's savings goal: must fail
        assertThrows(ForbiddenActionException.class, () -> {
            savingsGoalMcpTools.listSavingsTransactions(aliceGoal.getId());
        });
    }

    @Test
    void testSessionSecurityRegistryPreventsCrossUserHijacking() {
        String aliceSessionId = "sess-alice-uuid-999";
        sessionSecurityRegistry.registerSession(aliceSessionId, alice.getId());

        // Alice accessing her own session -> OK
        assertDoesNotThrow(() -> sessionSecurityRegistry.validateSessionOwner(aliceSessionId, alice));

        // Bob attempting to send requests on Alice's session -> must throw ForbiddenActionException
        assertThrows(ForbiddenActionException.class, () -> {
            sessionSecurityRegistry.validateSessionOwner(aliceSessionId, bob);
        });
    }
}
