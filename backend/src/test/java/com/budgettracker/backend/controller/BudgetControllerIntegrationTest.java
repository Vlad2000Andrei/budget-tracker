package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.BudgetDto;
import com.budgettracker.backend.dto.CreateBudgetRequest;
import com.budgettracker.backend.dto.UpdateBudgetRequest;
import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.RolloverRuleType;
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

import static com.budgettracker.backend.jooq.Tables.BUDGETS;
import static com.budgettracker.backend.jooq.Tables.CATEGORIES;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
@Transactional
public class BudgetControllerIntegrationTest {

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
    private Category expenseCategory;
    private Category otherCategory;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(BUDGETS).execute();
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

        expenseCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Food")
                .type(CategoryType.EXPENSE)
                .build());

        otherCategory = categoryRepository.save(Category.builder()
                .userId(otherUser.getId())
                .name("Other Food")
                .type(CategoryType.EXPENSE)
                .build());
    }

    @Test
    public void testGetBudgets_Empty() throws Exception {
        mockMvc.perform(get("/v1/budgets")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void testCreateBudget_Success() throws Exception {
        CreateBudgetRequest request = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("500.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .rolloverRule(RolloverRuleType.NONE)
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.amountLimit", is(500.00)))
                .andExpect(jsonPath("$.startDate", is("2026-06-01")))
                .andExpect(jsonPath("$.endDate", is("2026-06-30")))
                .andExpect(jsonPath("$.rolloverRule", is("NONE")));
    }

    @Test
    public void testCreateBudget_ValidationFailure_InvalidDates() throws Exception {
        CreateBudgetRequest request = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("500.00"))
                .startDate(LocalDate.of(2026, 6, 30))
                .endDate(LocalDate.of(2026, 6, 1)) // start after end
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Start date must be before or equal to end date")));
    }

    @Test
    public void testCreateBudget_ValidationFailure_InvalidCategoryType() throws Exception {
        Category incomeCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Salary")
                .type(CategoryType.INCOME)
                .build());

        CreateBudgetRequest request = CreateBudgetRequest.builder()
                .categoryId(incomeCategory.getId())
                .amountLimit(new BigDecimal("500.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("categories of type EXPENSE")));
    }

    @Test
    public void testCreateBudget_ForbiddenCategory() throws Exception {
        CreateBudgetRequest request = CreateBudgetRequest.builder()
                .categoryId(otherCategory.getId()) // Category owned by otherUser
                .amountLimit(new BigDecimal("500.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("do not have access")));
    }

    @Test
    public void testCreateBudget_OverlapCheck() throws Exception {
        // Create initial budget
        CreateBudgetRequest request1 = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("500.00"))
                .startDate(LocalDate.of(2026, 6, 10))
                .endDate(LocalDate.of(2026, 6, 20))
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Attempt overlapping budget
        CreateBudgetRequest request2 = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("300.00"))
                .startDate(LocalDate.of(2026, 6, 15))
                .endDate(LocalDate.of(2026, 6, 25))
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("overlapping budget already exists")));
    }

    @Test
    public void testUpdateBudget_Success() throws Exception {
        // Create initial budget directly in repo/db via POST
        CreateBudgetRequest request = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("500.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build();

        String content = mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        BudgetDto created = objectMapper.readValue(content, BudgetDto.class);

        UpdateBudgetRequest updateRequest = UpdateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("700.00"))
                .startDate(LocalDate.of(2026, 6, 5))
                .endDate(LocalDate.of(2026, 6, 25))
                .rolloverRule(RolloverRuleType.SURPLUS)
                .build();

        mockMvc.perform(patch("/v1/budgets/" + created.getId())
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountLimit", is(700.00)))
                .andExpect(jsonPath("$.startDate", is("2026-06-05")))
                .andExpect(jsonPath("$.endDate", is("2026-06-25")))
                .andExpect(jsonPath("$.rolloverRule", is("SURPLUS")));
    }

    @Test
    public void testUpdateBudget_Forbidden() throws Exception {
        // Create initial budget directly in repo/db via POST
        CreateBudgetRequest request = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("500.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build();

        String content = mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        BudgetDto created = objectMapper.readValue(content, BudgetDto.class);

        UpdateBudgetRequest updateRequest = UpdateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("700.00"))
                .startDate(LocalDate.of(2026, 6, 5))
                .endDate(LocalDate.of(2026, 6, 25))
                .build();

        // Attempt update with otherUser
        mockMvc.perform(patch("/v1/budgets/" + created.getId())
                        .header("X-User-Id", otherUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("permission to modify")));
    }

    @Test
    public void testDeleteBudget_Success() throws Exception {
        CreateBudgetRequest request = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("500.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build();

        String content = mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        BudgetDto created = objectMapper.readValue(content, BudgetDto.class);

        mockMvc.perform(delete("/v1/budgets/" + created.getId())
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/budgets")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void testCreateBudget_WithoutEndDate_Success() throws Exception {
        CreateBudgetRequest request = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("500.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(null)
                .rolloverRule(RolloverRuleType.NONE)
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.amountLimit", is(500.00)))
                .andExpect(jsonPath("$.startDate", is("2026-06-01")))
                .andExpect(jsonPath("$.endDate", nullValue()))
                .andExpect(jsonPath("$.rolloverRule", is("NONE")));
    }

    @Test
    public void testCreateBudget_WithoutEndDate_OverlapCheck() throws Exception {
        // 1. Create a repeating budget (null end date) starting on 2026-06-01
        CreateBudgetRequest request1 = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("500.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(null)
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // 2. Attempt to create another repeating budget starting on 2026-07-01 -> Should fail (overlap)
        CreateBudgetRequest request2 = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("300.00"))
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(null)
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("overlapping budget already exists")));

        // 3. Attempt to create a finite budget for 2026-08-01 to 2026-08-31 -> Should fail (overlap)
        CreateBudgetRequest request3 = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("200.00"))
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 31))
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("overlapping budget already exists")));
    }

    @Test
    public void testCreateOverallBudget_Success() throws Exception {
        CreateBudgetRequest request = CreateBudgetRequest.builder()
                .categoryId(null) // Overall budget
                .amountLimit(new BigDecimal("1000.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .rolloverRule(RolloverRuleType.NONE)
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.categoryId", nullValue()))
                .andExpect(jsonPath("$.amountLimit", is(1000.0)))
                .andExpect(jsonPath("$.startDate", is("2026-06-01")))
                .andExpect(jsonPath("$.endDate", is("2026-06-30")));
    }

    @Test
    public void testCreateOverallBudget_OverlapCheck() throws Exception {
        // 1. Create an overall budget for June 2026
        CreateBudgetRequest overall1 = CreateBudgetRequest.builder()
                .categoryId(null)
                .amountLimit(new BigDecimal("1000.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overall1)))
                .andExpect(status().isCreated());

        // 2. Attempt to create another overlapping overall budget -> Should fail
        CreateBudgetRequest overall2 = CreateBudgetRequest.builder()
                .categoryId(null)
                .amountLimit(new BigDecimal("500.00"))
                .startDate(LocalDate.of(2026, 6, 15))
                .endDate(LocalDate.of(2026, 7, 15))
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overall2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("overlapping budget already exists")));

        // 3. Create a category-specific budget overlapping June 2026 -> Should succeed (different categories)
        CreateBudgetRequest categoryBudget = CreateBudgetRequest.builder()
                .categoryId(expenseCategory.getId())
                .amountLimit(new BigDecimal("200.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build();

        mockMvc.perform(post("/v1/budgets")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoryBudget)))
                .andExpect(status().isCreated());
    }
}
