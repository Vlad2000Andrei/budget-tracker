package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.RolloverRuleType;
import com.budgettracker.backend.model.Budget;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.User;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.budgettracker.backend.jooq.Tables.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@Transactional
public class BudgetRepositoryTest {

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;
    private Category testCategory;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(BUDGETS).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("budget-repo-user@example.com")
                .googleSub("budget-repo-sub")
                .build());

        testCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Food")
                .type(CategoryType.EXPENSE)
                .build());
    }

    @Test
    public void testSave_NewBudget_Defaults() {
        Budget budget = Budget.builder()
                .userId(testUser.getId())
                .categoryId(testCategory.getId())
                .amountLimit(new BigDecimal("200.00"))
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .rolloverRule(null) // Should default to NONE
                .build();

        Budget saved = budgetRepository.save(budget);
        assertNotNull(saved.getId());
        assertEquals(RolloverRuleType.NONE, saved.getRolloverRule());
    }

    @Test
    public void testSave_UpdateBudget() {
        Budget budget = budgetRepository.save(Budget.builder()
                .userId(testUser.getId())
                .categoryId(testCategory.getId())
                .amountLimit(new BigDecimal("200.00"))
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .rolloverRule(RolloverRuleType.SURPLUS)
                .build());

        Long id = budget.getId();

        budget.setAmountLimit(new BigDecimal("300.00"));
        budget.setRolloverRule(null); // Should default to NONE on update

        Budget updated = budgetRepository.save(budget);
        assertEquals(id, updated.getId());
        assertEquals(new BigDecimal("300.00"), updated.getAmountLimit());
        assertEquals(RolloverRuleType.NONE, updated.getRolloverRule());
    }

    @Test
    public void testHasOverlappingBudget_Combinations() {
        Budget b1 = budgetRepository.save(Budget.builder()
                .userId(testUser.getId())
                .categoryId(testCategory.getId())
                .amountLimit(BigDecimal.TEN)
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build());

        Budget bNoCategory = budgetRepository.save(Budget.builder()
                .userId(testUser.getId())
                .categoryId(null) // User-wide budget
                .amountLimit(BigDecimal.TEN)
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build());

        // 1. Same category overlapping
        assertTrue(budgetRepository.hasOverlappingBudget(testUser.getId(), testCategory.getId(), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 15), null));
        // 2. Same category not overlapping
        assertFalse(budgetRepository.hasOverlappingBudget(testUser.getId(), testCategory.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15), null));
        // 3. User-wide overlap
        assertTrue(budgetRepository.hasOverlappingBudget(testUser.getId(), null, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 15), null));
        // 4. Overlap but excluding the same ID
        assertFalse(budgetRepository.hasOverlappingBudget(testUser.getId(), testCategory.getId(), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 15), b1.getId()));
        // 5. Overlap check with null endDate
        assertTrue(budgetRepository.hasOverlappingBudget(testUser.getId(), testCategory.getId(), LocalDate.of(2026, 6, 15), null, null));
    }
}
