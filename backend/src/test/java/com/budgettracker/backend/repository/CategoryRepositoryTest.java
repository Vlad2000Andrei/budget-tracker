package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.User;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.budgettracker.backend.jooq.Tables.CATEGORIES;
import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@Transactional
public class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(TRANSACTIONS).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("repo-test-user@example.com")
                .googleSub("repo-test-sub")
                .defaultCurrency("USD")
                .build());
    }

    @Test
    public void testGetDescendantCategoryIds_LeafNode() {
        Category leaf = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Leaf")
                .type(CategoryType.EXPENSE)
                .build());

        List<Long> descendants = categoryRepository.getDescendantCategoryIds(leaf.getId());
        assertEquals(1, descendants.size());
        assertTrue(descendants.contains(leaf.getId()));
    }

    @Test
    public void testGetDescendantCategoryIds_ThreeLevels() {
        Category parent = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Parent")
                .type(CategoryType.EXPENSE)
                .build());

        Category child = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .parentId(parent.getId())
                .name("Child")
                .type(CategoryType.EXPENSE)
                .build());

        Category grandchild = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .parentId(child.getId())
                .name("Grandchild")
                .type(CategoryType.EXPENSE)
                .build());

        List<Long> descendants = categoryRepository.getDescendantCategoryIds(parent.getId());
        assertEquals(3, descendants.size());
        assertTrue(descendants.contains(parent.getId()));
        assertTrue(descendants.contains(child.getId()));
        assertTrue(descendants.contains(grandchild.getId()));

        List<Long> childDescendants = categoryRepository.getDescendantCategoryIds(child.getId());
        assertEquals(2, childDescendants.size());
        assertFalse(childDescendants.contains(parent.getId()));
        assertTrue(childDescendants.contains(child.getId()));
        assertTrue(childDescendants.contains(grandchild.getId()));
    }

    @Test
    public void testHasCircularDependency_Direct() {
        Category cat = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Self Parent")
                .type(CategoryType.EXPENSE)
                .build());

        assertTrue(categoryRepository.hasCircularDependency(cat.getId(), cat.getId()));
    }

    @Test
    public void testHasCircularDependency_ThreeLevels() {
        Category parent = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Parent")
                .type(CategoryType.EXPENSE)
                .build());

        Category child = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .parentId(parent.getId())
                .name("Child")
                .type(CategoryType.EXPENSE)
                .build());

        Category grandchild = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .parentId(child.getId())
                .name("Grandchild")
                .type(CategoryType.EXPENSE)
                .build());

        // Attempting to set grandchild as parent of parent creates a circular path: parent -> child -> grandchild -> parent
        assertTrue(categoryRepository.hasCircularDependency(parent.getId(), grandchild.getId()));
        // Standard hierarchy has no circular dependency
        assertFalse(categoryRepository.hasCircularDependency(grandchild.getId(), parent.getId()));
    }

    @Test
    public void testBoundaryNullChecks() {
        assertTrue(categoryRepository.getDescendantCategoryIds(null).isEmpty());
        assertFalse(categoryRepository.hasDescendantTransactions(null));
        assertFalse(categoryRepository.hasCircularDependency(null, 1L));
        assertFalse(categoryRepository.hasCircularDependency(1L, null));
    }
}
