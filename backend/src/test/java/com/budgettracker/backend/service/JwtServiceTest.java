package com.budgettracker.backend.service;

import com.budgettracker.backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtServiceTest {

    private static final String TEST_SECRET = "test-jwt-secret-key-that-is-at-least-256-bits-long-for-hmac-sha-256";
    private static final long EXPIRATION_MS = 28800000L; // 8 hours

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, EXPIRATION_MS);
        testUser = User.builder()
                .id(42L)
                .email("user@example.com")
                .displayName("Test User")
                .defaultCurrency("USD")
                .build();
    }

    @Test
    void testGenerateTokenAndValidate_Success() {
        String token = jwtService.generateToken(testUser);
        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertEquals(42L, jwtService.getUserIdFromToken(token));
    }

    @Test
    void testValidateToken_InvalidToken_ReturnsFalse() {
        assertFalse(jwtService.validateToken("invalid.token.structure"));
        assertFalse(jwtService.validateToken(""));
        assertFalse(jwtService.validateToken(null));
    }

    @Test
    void testValidateToken_TamperedToken_ReturnsFalse() {
        String token = jwtService.generateToken(testUser);
        String tampered = token.substring(0, token.length() - 5) + "abcde";
        assertFalse(jwtService.validateToken(tampered));
    }

    @Test
    void testValidateToken_ExpiredToken_ReturnsFalse() {
        // JwtService with negative expiration (already expired at creation)
        JwtService expiredJwtService = new JwtService(TEST_SECRET, -1000L);
        String expiredToken = expiredJwtService.generateToken(testUser);
        assertFalse(jwtService.validateToken(expiredToken));
    }

    @Test
    void testDifferentSecret_FailsValidation() {
        String anotherSecret = "another-secret-key-that-is-at-least-256-bits-long-for-hmac-sha-256";
        JwtService anotherJwtService = new JwtService(anotherSecret, EXPIRATION_MS);
        String tokenFromAnother = anotherJwtService.generateToken(testUser);
        assertFalse(jwtService.validateToken(tokenFromAnother));
    }
}
