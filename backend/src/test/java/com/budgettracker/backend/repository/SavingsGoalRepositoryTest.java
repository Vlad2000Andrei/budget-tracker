package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.SavingsGoalType;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.SavingsGoal;
import com.budgettracker.backend.model.User;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static com.budgettracker.backend.jooq.Tables.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@Transactional
public class SavingsGoalRepositoryTest {

    @Autowired
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;
    private Category savingsCategory;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(SAVINGS_GOALS).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("savings-repo-user@example.com")
                .googleSub("savings-repo-sub")
                .build());

        savingsCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Savings Goal Cat")
                .type(CategoryType.SAVINGS)
                .build());
    }

    @Test
    public void testSave_NewGoal_Defaults() {
        SavingsGoal goal = SavingsGoal.builder()
                .userId(testUser.getId())
                .categoryId(savingsCategory.getId())
                .goalType(null) // Should default to ONE_OFF
                .targetAmount(new BigDecimal("500.00"))
                .currentAmount(null) // Should default to BigDecimal.ZERO
                .build();

        SavingsGoal saved = savingsGoalRepository.save(goal);
        assertNotNull(saved.getId());
        assertEquals(SavingsGoalType.ONE_OFF, saved.getGoalType());
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getCurrentAmount()));
    }

    @Test
    public void testSave_UpdateGoal() {
        SavingsGoal goal = savingsGoalRepository.save(SavingsGoal.builder()
                .userId(testUser.getId())
                .categoryId(savingsCategory.getId())
                .goalType(SavingsGoalType.MONTHLY)
                .targetAmount(new BigDecimal("500.00"))
                .currentAmount(new BigDecimal("100.00"))
                .build());

        Long id = goal.getId();

        // Update target amount and goal type
        goal.setTargetAmount(new BigDecimal("600.00"));
        goal.setGoalType(null); // Should default to ONE_OFF in update logic too

        SavingsGoal updated = savingsGoalRepository.save(goal);
        assertEquals(id, updated.getId());
        assertEquals(new BigDecimal("600.00"), updated.getTargetAmount());
        assertEquals(SavingsGoalType.ONE_OFF, updated.getGoalType());
    }

    @Test
    public void testFindSavingsTransactions_EmptyCategories() {
        List<org.jooq.Record2<BigDecimal, String>> resultNull = savingsGoalRepository.findSavingsTransactions(testUser.getId(), null);
        List<org.jooq.Record2<BigDecimal, String>> resultEmpty = savingsGoalRepository.findSavingsTransactions(testUser.getId(), Collections.emptyList());

        assertTrue(resultNull.isEmpty());
        assertTrue(resultEmpty.isEmpty());
    }

    @Test
    public void testFindSavingsTransactionsInPeriod_EmptyCategories() {
        LocalDateTime now = LocalDateTime.now();
        List<org.jooq.Record2<BigDecimal, String>> resultNull = savingsGoalRepository.findSavingsTransactionsInPeriod(testUser.getId(), null, now.minusDays(1), now);
        List<org.jooq.Record2<BigDecimal, String>> resultEmpty = savingsGoalRepository.findSavingsTransactionsInPeriod(testUser.getId(), Collections.emptyList(), now.minusDays(1), now);

        assertTrue(resultNull.isEmpty());
        assertTrue(resultEmpty.isEmpty());
    }
}
