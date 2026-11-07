package com.budgettracker.backend.controller;

import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.UserRepository;
import com.budgettracker.backend.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
@Transactional
public class OAuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("oauth-test@example.com")
                .googleSub("google-sub-oauth-test")
                .displayName("OAuth Test User")
                .defaultCurrency("USD")
                .isOnboarded(true)
                .build());
    }

    @Test
    void testAuthorizationServerMetadata() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint", containsString("/v1/oauth/authorize")))
                .andExpect(jsonPath("$.token_endpoint", containsString("/v1/oauth/token")))
                .andExpect(jsonPath("$.registration_endpoint", containsString("/v1/oauth/register")))
                .andExpect(jsonPath("$.response_types_supported", hasItem("code")))
                .andExpect(jsonPath("$.code_challenge_methods_supported", hasItem("S256")));
    }

    @Test
    void testDynamicClientRegistration() throws Exception {
        Map<String, Object> regRequest = Map.of(
                "client_name", "Test MCP Client",
                "redirect_uris", List.of("http://127.0.0.1:37601/oauth/callback")
        );

        mockMvc.perform(post("/v1/oauth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client_id", startsWith("mcp-client-")))
                .andExpect(jsonPath("$.client_name", is("Test MCP Client")))
                .andExpect(jsonPath("$.redirect_uris[0]", is("http://127.0.0.1:37601/oauth/callback")))
                .andExpect(jsonPath("$.token_endpoint_auth_method", is("none")));
    }

    @Test
    void testProtectedResourceMetadata() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_servers", notNullValue()));
    }

    @Test
    void testUnauthenticatedMcpSse_Returns401WithWwwAuthenticateMetadata() throws Exception {
        mockMvc.perform(get("/sse"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("resource_metadata")));
    }

    @Test
    void testAuthorizePage_Unauthenticated_ReturnsConsentHtml() throws Exception {
        mockMvc.perform(get("/v1/oauth/authorize")
                        .param("client_id", "claude-desktop")
                        .param("redirect_uri", "http://localhost:12345/callback")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256")
                        .param("state", "xyz123"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Authorize AI Agent")))
                .andExpect(content().string(containsString("claude-desktop")));
    }

    @Test
    void testAuthorizePage_Authenticated_RedirectsWithCode() throws Exception {
        String token = jwtService.generateToken(testUser);

        mockMvc.perform(get("/v1/oauth/authorize")
                        .header("Authorization", "Bearer " + token)
                        .param("client_id", "cursor-agent")
                        .param("redirect_uri", "http://localhost:12345/callback")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256")
                        .param("state", "state_abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("http://localhost:12345/callback?code=")))
                .andExpect(header().string("Location", containsString("state=state_abc")));
    }

    @Test
    void testFullOAuth2PkceFlow_Success() throws Exception {
        // 1. Generate PKCE verifier and S256 challenge
        String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        // 2. Authorize
        String token = jwtService.generateToken(testUser);
        MvcResult authResult = mockMvc.perform(get("/v1/oauth/authorize")
                        .header("Authorization", "Bearer " + token)
                        .param("client_id", "test-agent")
                        .param("redirect_uri", "http://localhost:54321/callback")
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256")
                        .param("state", "mystate"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = authResult.getResponse().getHeader("Location");
        assertNotNull(location);
        String code = location.substring(location.indexOf("code=") + 5);
        if (code.contains("&")) {
            code = code.substring(0, code.indexOf("&"));
        }
        assertFalse(code.isBlank());

        // 3. Exchange code for tokens (with correct verifier)
        MvcResult tokenResult = mockMvc.perform(post("/v1/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("code_verifier", codeVerifier)
                        .param("client_id", "test-agent")
                        .param("redirect_uri", "http://localhost:54321/callback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.token_type", is("Bearer")))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andExpect(jsonPath("$.expires_in", greaterThan(0)))
                .andReturn();

        Map<String, Object> tokenResponse = objectMapper.readValue(tokenResult.getResponse().getContentAsString(), Map.class);
        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");

        // 4. Verify access token is valid and belongs to testUser
        assertTrue(jwtService.validateToken(accessToken));
        assertEquals(testUser.getId(), jwtService.getUserIdFromToken(accessToken));

        // 5. Test refresh token exchange
        MvcResult refreshResult = mockMvc.perform(post("/v1/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken)
                        .param("client_id", "test-agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andReturn();

        Map<String, Object> refreshResponse = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), Map.class);
        String newAccessToken = (String) refreshResponse.get("access_token");
        String newRefreshToken = (String) refreshResponse.get("refresh_token");

        assertTrue(jwtService.validateToken(newAccessToken));
        assertNotEquals(refreshToken, newRefreshToken, "Refresh token should rotate");

        // 6. Old refresh token should now be revoked
        mockMvc.perform(post("/v1/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken)
                        .param("client_id", "test-agent"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testOAuthToken_InvalidPkceVerifier_Fails() throws Exception {
        String codeVerifier = "correct-verifier-secret-string-1234567890";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        String token = jwtService.generateToken(testUser);
        MvcResult authResult = mockMvc.perform(get("/v1/oauth/authorize")
                        .header("Authorization", "Bearer " + token)
                        .param("client_id", "test-agent")
                        .param("redirect_uri", "http://localhost:54321/callback")
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = authResult.getResponse().getHeader("Location");
        String code = location.substring(location.indexOf("code=") + 5);
        if (code.contains("&")) {
            code = code.substring(0, code.indexOf("&"));
        }

        mockMvc.perform(post("/v1/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("code_verifier", "wrong-verifier")
                        .param("client_id", "test-agent")
                        .param("redirect_uri", "http://localhost:54321/callback"))
                .andExpect(status().isBadRequest());
    }
}
