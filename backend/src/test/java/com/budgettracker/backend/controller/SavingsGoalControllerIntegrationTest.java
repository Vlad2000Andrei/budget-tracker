package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.CreateSavingsGoalRequest;
import com.budgettracker.backend.dto.CreateTransactionRequest;
import com.budgettracker.backend.dto.SavingsGoalDto;
import com.budgettracker.backend.dto.UpdateSavingsGoalRequest;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.budgettracker.backend.jooq.Tables.CATEGORIES;
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

    private User testUser;
    private User otherUser;
    private Category parentSavingsCategory;
    private Category childSavingsCategory;
    private Category expenseCategory;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(SAVINGS_GOALS).execute();
        dsl.deleteFrom(TRANSACTIONS).execute();
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
}
