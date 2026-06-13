package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.CategoryDto;
import com.budgettracker.backend.dto.CreateCategoryRequest;
import com.budgettracker.backend.dto.UpdateCategoryRequest;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.User;
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
import java.time.LocalDateTime;

import static com.budgettracker.backend.jooq.Tables.CATEGORIES;
import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
@Transactional
public class CategoryControllerIntegrationTest {

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

    private User testUser;

    @BeforeEach
    public void setUp() {
        // Clean database tables for testing
        dsl.deleteFrom(TRANSACTIONS).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(USERS).execute();

        // Create a test user
        testUser = userRepository.save(User.builder()
                .email("test-user@example.com")
                .googleSub("test-google-sub-999")
                .defaultCurrency("USD")
                .build());
    }

    @Test
    public void testGetCategories_Empty() throws Exception {
        mockMvc.perform(get("/v1/categories")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void testCreateCategory_Success() throws Exception {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .color("#FF5733")
                .icon("shopping_cart")
                .build();

        mockMvc.perform(post("/v1/categories")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Groceries")))
                .andExpect(jsonPath("$.type", is("EXPENSE")))
                .andExpect(jsonPath("$.color", is("#FF5733")))
                .andExpect(jsonPath("$.icon", is("shopping_cart")));
    }

    @Test
    public void testCreateCategory_ValidationFailure() throws Exception {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("") // Blank name
                .type(null) // Missing type
                .color("invalid-hex") // Invalid hex format
                .build();

        mockMvc.perform(post("/v1/categories")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.details.name", notNullValue()))
                .andExpect(jsonPath("$.details.type", notNullValue()))
                .andExpect(jsonPath("$.details.color", notNullValue()));
    }

    @Test
    public void testCreateCategory_ParentTypeMismatch() throws Exception {
        // Create an INCOME parent category
        Category parent = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Salary Parent")
                .type(CategoryType.INCOME)
                .build());

        // Attempt to create an EXPENSE child referencing an INCOME parent
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("Groceries Child")
                .type(CategoryType.EXPENSE)
                .parentId(parent.getId())
                .build();

        mockMvc.perform(post("/v1/categories")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("type must match")));
    }

    @Test
    public void testUpdateCategory_Success() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Old Name")
                .type(CategoryType.EXPENSE)
                .color("#111111")
                .build());

        UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                .name("New Name")
                .color("#FF5733")
                .build();

        mockMvc.perform(patch("/v1/categories/" + category.getId())
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("New Name")))
                .andExpect(jsonPath("$.color", is("#FF5733")));
    }

    @Test
    public void testUpdateCategory_CircularDependency() throws Exception {
        // Create food (parent) and groceries (child)
        Category food = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Food")
                .type(CategoryType.EXPENSE)
                .build());

        Category groceries = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .parentId(food.getId())
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .build());

        // Attempt to update Food's parent to be Groceries (creates cycle)
        UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                .name("Food")
                .parentId(groceries.getId())
                .build();

        mockMvc.perform(patch("/v1/categories/" + food.getId())
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Circular dependency")));
    }

    @Test
    public void testDeleteCategory_Success() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("To Delete")
                .type(CategoryType.EXPENSE)
                .build());

        mockMvc.perform(delete("/v1/categories/" + category.getId())
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isNoContent());

        // Verify it was deleted
        mockMvc.perform(get("/v1/categories")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void testDeleteCategory_InUse() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("In Use")
                .type(CategoryType.EXPENSE)
                .build());

        // Manually insert a transaction referencing this category using JOOQ
        dsl.insertInto(TRANSACTIONS)
                .set(TRANSACTIONS.USER_ID, testUser.getId())
                .set(TRANSACTIONS.CATEGORY_ID, category.getId())
                .set(TRANSACTIONS.AMOUNT, new BigDecimal("45.50"))
                .set(TRANSACTIONS.CURRENCY, "USD")
                .set(TRANSACTIONS.CONVERTED_AMOUNT, new BigDecimal("45.50"))
                .set(TRANSACTIONS.CONVERTED_CURRENCY, "USD")
                .set(TRANSACTIONS.EXCHANGE_RATE, new BigDecimal("1.00"))
                .set(TRANSACTIONS.TYPE, CategoryType.EXPENSE)
                .set(TRANSACTIONS.DATE, LocalDateTime.now())
                .execute();

        // Attempt to delete category should fail
        mockMvc.perform(delete("/v1/categories/" + category.getId())
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("active transactions")));
    }
}
