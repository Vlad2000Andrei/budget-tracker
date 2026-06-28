package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.CreateTokenRequest;
import com.budgettracker.backend.dto.UpdateUserRequest;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.CategoryRepository;
import com.budgettracker.backend.repository.UserRepository;
import com.budgettracker.backend.service.GoogleTokenVerifierService;
import com.budgettracker.backend.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.budgettracker.backend.jooq.Tables.CATEGORIES;
import static com.budgettracker.backend.jooq.Tables.USERS;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
@Transactional
public class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DSLContext dsl;

    @MockBean
    private GoogleTokenVerifierService googleTokenVerifierService;

    @BeforeEach
    public void setUp() {
        dsl.deleteFrom(CATEGORIES).execute();
        dsl.deleteFrom(USERS).execute();
    }

    @Test
    public void testSecuredRouteWithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/v1/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    public void testTokenExchangeAndUserProvisioning_Success() throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-abc-123");
        payload.setEmail("new-user@example.com");
        payload.set("name", "Google Display Name");

        when(googleTokenVerifierService.verifyToken("valid-google-id-token"))
                .thenReturn(payload);

        CreateTokenRequest request = CreateTokenRequest.builder()
                .googleIdToken("valid-google-id-token")
                .build();

        mockMvc.perform(post("/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/users/me"))
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.expiresAt", notNullValue()));

        // Verify user was created
        User user = userRepository.findByGoogleSub("google-sub-abc-123")
                .orElseThrow(() -> new AssertionError("User not found after provisioning"));
        org.junit.jupiter.api.Assertions.assertFalse(user.isOnboarded());
        org.junit.jupiter.api.Assertions.assertEquals("Google Display Name", user.getDisplayName());
        
        // Verify default categories seeded
        mockMvc.perform(get("/v1/categories")
                        .header("Authorization", "Bearer " + jwtService.generateToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));
    }

    @Test
    public void testGetMe_Success() throws Exception {
        User user = userRepository.save(User.builder()
                .email("profile-user@example.com")
                .googleSub("google-sub-profile-555")
                .defaultCurrency("EUR")
                .displayName("John Doe")
                .build());

        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(user.getId().intValue())))
                .andExpect(jsonPath("$.email", is("profile-user@example.com")))
                .andExpect(jsonPath("$.defaultCurrency", is("EUR")))
                .andExpect(jsonPath("$.displayName", is("John Doe")))
                .andExpect(jsonPath("$.isOnboarded", is(false)));
    }

    @Test
    public void testPatchMe_Success() throws Exception {
        User user = userRepository.save(User.builder()
                .email("patch-user@example.com")
                .googleSub("google-sub-patch-777")
                .defaultCurrency("USD")
                .displayName("Old Name")
                .build());

        String token = jwtService.generateToken(user);

        UpdateUserRequest request = UpdateUserRequest.builder()
                .defaultCurrency("RON")
                .displayName("New Name")
                .build();

        mockMvc.perform(patch("/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(user.getId().intValue())))
                .andExpect(jsonPath("$.defaultCurrency", is("RON")))
                .andExpect(jsonPath("$.displayName", is("New Name")))
                .andExpect(jsonPath("$.isOnboarded", is(true)));

        // Verify persisted to DB
        User updated = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("RON", updated.getDefaultCurrency());
        org.junit.jupiter.api.Assertions.assertEquals("New Name", updated.getDisplayName());
        org.junit.jupiter.api.Assertions.assertTrue(updated.isOnboarded());
    }

    @Test
    public void testPatchMe_ValidationFailure() throws Exception {
        User user = userRepository.save(User.builder()
                .email("validation-user@example.com")
                .googleSub("google-sub-validation-888")
                .defaultCurrency("USD")
                .build());

        String token = jwtService.generateToken(user);

        UpdateUserRequest request = UpdateUserRequest.builder()
                .defaultCurrency("invalid") // too long and lowercase
                .build();

        mockMvc.perform(patch("/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")));
    }

    @Test
    public void testTokenExchangeSetsCookie_Success() throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-cookie-sub");
        payload.setEmail("cookie-user@example.com");
        payload.set("name", "Cookie User");

        when(googleTokenVerifierService.verifyToken("valid-google-id-token"))
                .thenReturn(payload);

        CreateTokenRequest request = CreateTokenRequest.builder()
                .googleIdToken("valid-google-id-token")
                .build();

        mockMvc.perform(post("/v1/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().httpOnly("jwt", true))
                .andExpect(cookie().secure("jwt", false));
    }

    @Test
    public void testGetMeWithCookie_Success() throws Exception {
        User user = userRepository.save(User.builder()
                .email("cookie-fetch-user@example.com")
                .googleSub("google-cookie-fetch-sub")
                .defaultCurrency("EUR")
                .displayName("Cookie Fetch")
                .build());

        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/v1/users/me")
                        .cookie(new jakarta.servlet.http.Cookie("jwt", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("cookie-fetch-user@example.com")))
                .andExpect(jsonPath("$.displayName", is("Cookie Fetch")));
    }

    @Test
    public void testLogoutClearsCookie_Success() throws Exception {
        mockMvc.perform(post("/v1/tokens/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().maxAge("jwt", 0));
    }
}
