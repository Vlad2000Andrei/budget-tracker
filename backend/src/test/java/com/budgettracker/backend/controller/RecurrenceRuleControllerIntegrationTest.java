package com.budgettracker.backend.controller;

import com.budgettracker.backend.jooq.enums.CategoryType;
import com.budgettracker.backend.jooq.enums.RecurrenceFrequency;
import com.budgettracker.backend.model.Category;
import com.budgettracker.backend.model.RecurrenceRule;
import com.budgettracker.backend.model.Transaction;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.repository.RecurrenceRuleRepository;
import com.budgettracker.backend.repository.TransactionRepository;
import com.budgettracker.backend.repository.UserRepository;
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
import static com.budgettracker.backend.jooq.Tables.RECURRENCE_RULES;
import static com.budgettracker.backend.jooq.Tables.TRANSACTIONS;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
@Transactional
public class RecurrenceRuleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RecurrenceRuleRepository recurrenceRuleRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;
    private User otherUser;
    private Category utilitiesCategory;
    private RecurrenceRule rule;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(TRANSACTIONS).execute();
        dsl.deleteFrom(RECURRENCE_RULES).execute();
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("recur-user@example.com")
                .googleSub("recur-sub-123")
                .defaultCurrency("USD")
                .build());

        otherUser = userRepository.save(User.builder()
                .email("other-recur@example.com")
                .googleSub("recur-sub-999")
                .defaultCurrency("USD")
                .build());

        utilitiesCategory = categoryRepository.save(Category.builder()
                .userId(testUser.getId())
                .name("Utilities")
                .type(CategoryType.EXPENSE)
                .icon("power")
                .color("#3357FF")
                .build());

        // Create recurrence rule starting 2026-06-01
        rule = recurrenceRuleRepository.save(RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.MONTHLY)
                .interval(1)
                .startDate(LocalDate.of(2026, 6, 1))
                .build());

        // Spawn first transaction for rule (template transaction)
        transactionRepository.save(Transaction.builder()
                .userId(testUser.getId())
                .categoryId(utilitiesCategory.getId())
                .recurrenceRuleId(rule.getId())
                .amount(new BigDecimal("100.0000"))
                .currency("USD")
                .convertedAmount(new BigDecimal("100.0000"))
                .exchangeRate(BigDecimal.ONE)
                .type(CategoryType.EXPENSE)
                .date(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build());
    }

    @Test
    public void testGetRecurringTransactions_Success() throws Exception {
        // nextDate should be 2026-07-01 (template + 1 month)
        mockMvc.perform(get("/v1/recurrence-rules")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(rule.getId().intValue())))
                .andExpect(jsonPath("$[0].categoryName", is("Utilities")))
                .andExpect(jsonPath("$[0].categoryIcon", is("power")))
                .andExpect(jsonPath("$[0].amount", is(100.0)))
                .andExpect(jsonPath("$[0].frequency", is("MONTHLY")))
                .andExpect(jsonPath("$[0].nextDate", is("2026-07-01")));
    }

    @Test
    public void testDeleteRecurrenceRule_Success() throws Exception {
        mockMvc.perform(delete("/v1/recurrence-rules/" + rule.getId())
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isNoContent());

        // Verify rule is deleted
        assertFalse(recurrenceRuleRepository.findById(rule.getId()).isPresent());
    }

    @Test
    public void testDeleteRecurrenceRule_Forbidden() throws Exception {
        mockMvc.perform(delete("/v1/recurrence-rules/" + rule.getId())
                        .header("X-User-Id", otherUser.getId()))
                .andExpect(status().isForbidden());
    }
}
