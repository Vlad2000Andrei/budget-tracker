package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.CreateAccountRequest;
import com.budgettracker.backend.dto.UpdateAccountRequest;
import com.budgettracker.backend.jooq.enums.AccountType;
import com.budgettracker.backend.model.Account;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.AccountRepository;
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

import static com.budgettracker.backend.jooq.Tables.ACCOUNTS;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
@Transactional
public class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;
    private User otherUser;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(ACCOUNTS).execute();
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("test-user@example.com")
                .googleSub("test-sub-111")
                .defaultCurrency("USD")
                .build());

        otherUser = userRepository.save(User.builder()
                .email("other-user@example.com")
                .googleSub("other-sub-222")
                .defaultCurrency("EUR")
                .build());
    }

    @Test
    public void testGetAccounts_Success() throws Exception {
        accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Checking")
                .type(AccountType.CHECKING)
                .balance(BigDecimal.TEN)
                .currency("USD")
                .build());

        mockMvc.perform(get("/v1/accounts")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Checking")))
                .andExpect(jsonPath("$[0].balance", is(10.0)));
    }

    @Test
    public void testCreateAccount_Success() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .name("Savings")
                .type(AccountType.SAVINGS)
                .currency("EUR")
                .initialBalance(new java.math.BigDecimal("150.50"))
                .build();

        mockMvc.perform(post("/v1/accounts")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Savings")))
                .andExpect(jsonPath("$.type", is("SAVINGS")))
                .andExpect(jsonPath("$.balance", is(150.50)))
                .andExpect(jsonPath("$.currency", is("EUR")));
    }

    @Test
    public void testCreateAccount_ValidationFailure() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .name("") // Blank
                .type(null) // Null
                .currency("US") // Too short
                .build();

        mockMvc.perform(post("/v1/accounts")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")));
    }

    @Test
    public void testUpdateAccount_Success() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("Old Name")
                .type(AccountType.CHECKING)
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .build());

        UpdateAccountRequest request = UpdateAccountRequest.builder()
                .name("New Name")
                .build();

        mockMvc.perform(patch("/v1/accounts/" + account.getId())
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("New Name")));
    }

    @Test
    public void testUpdateAccount_Forbidden() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .userId(otherUser.getId())
                .name("Other Account")
                .type(AccountType.CHECKING)
                .balance(BigDecimal.ZERO)
                .currency("EUR")
                .build());

        UpdateAccountRequest request = UpdateAccountRequest.builder()
                .name("Hacked Name")
                .build();

        mockMvc.perform(patch("/v1/accounts/" + account.getId())
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testDeleteAccount_Success() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .userId(testUser.getId())
                .name("To Delete")
                .type(AccountType.CHECKING)
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .build());

        mockMvc.perform(delete("/v1/accounts/" + account.getId())
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/accounts")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void testDeleteAccount_Forbidden() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .userId(otherUser.getId())
                .name("Other Account")
                .type(AccountType.CHECKING)
                .balance(BigDecimal.ZERO)
                .currency("EUR")
                .build());

        mockMvc.perform(delete("/v1/accounts/" + account.getId())
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isForbidden());
    }
}
