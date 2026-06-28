package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.UpdateUserRequest;
import com.budgettracker.backend.model.User;
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

import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
@Transactional
public class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    public void testGetMe() throws Exception {
        mockMvc.perform(get("/v1/users/me")
                        .header("X-User-Id", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("user-me@example.com")))
                .andExpect(jsonPath("$.displayName", is("Original Name")));
    }

    @Test
    public void testUpdateMe_WithDisplayName() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .defaultCurrency("EUR")
                .displayName("Updated Name ")
                .build();

        mockMvc.perform(patch("/v1/users/me")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCurrency", is("EUR")))
                .andExpect(jsonPath("$.displayName", is("Updated Name")));
    }

    @Test
    public void testUpdateMe_WithoutDisplayName() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .defaultCurrency("RON")
                .displayName(null)
                .build();

        mockMvc.perform(patch("/v1/users/me")
                        .header("X-User-Id", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCurrency", is("RON")))
                .andExpect(jsonPath("$.displayName", is("Original Name")));
    }
}
