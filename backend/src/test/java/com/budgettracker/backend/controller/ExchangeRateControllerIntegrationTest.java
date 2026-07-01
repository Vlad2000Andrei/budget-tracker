package com.budgettracker.backend.controller;

import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.exchange-rate.api-enabled=false",
        "app.security.mock-user-fallback.enabled=true"
})
@AutoConfigureMockMvc
@Transactional
public class ExchangeRateControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DSLContext dsl;

    private User testUser;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(USERS).execute();

        testUser = userRepository.save(User.builder()
                .email("user-me@example.com")
                .googleSub("me-sub")
                .defaultCurrency("USD")
                .displayName("Original Name")
                .build());
    }

    @Test
    public void testGetExchangeRate_Success() throws Exception {
        mockMvc.perform(get("/v1/exchange-rates")
                        .header("X-User-Id", testUser.getId())
                        .param("from", "EUR")
                        .param("to", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from", is("EUR")))
                .andExpect(jsonPath("$.to", is("USD")))
                .andExpect(jsonPath("$.rate", is(1.08)));
    }
}
