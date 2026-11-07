package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.OAuthTokenResponse;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.service.OAuthService;
import com.budgettracker.backend.service.CategoryService;
import com.budgettracker.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
public class OAuthController {

    private final OAuthService oauthService;
    private final UserRepository userRepository;
    private final CategoryService categoryService;

    @Value("${app.oauth.issuer:}")
    private String configuredIssuer;

    @Autowired
    public OAuthController(OAuthService oauthService,
                           UserRepository userRepository,
                           CategoryService categoryService) {
        this.oauthService = oauthService;
        this.userRepository = userRepository;
        this.categoryService = categoryService;
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public ResponseEntity<Map<String, Object>> getAuthorizationServerMetadata(HttpServletRequest request) {
        String base = getBaseUrl(request);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("issuer", base);
        metadata.put("authorization_endpoint", base + "/v1/oauth/authorize");
        metadata.put("token_endpoint", base + "/v1/oauth/token");
        metadata.put("registration_endpoint", base + "/v1/oauth/register");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("code_challenge_methods_supported", List.of("S256", "plain"));
        metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        metadata.put("token_endpoint_auth_methods_supported", List.of("none"));
        return ResponseEntity.ok(metadata);
    }

    @PostMapping(value = "/v1/oauth/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> registerClient(@RequestBody(required = false) Map<String, Object> body) {
        String clientId = "mcp-client-" + UUID.randomUUID();
        Map<String, Object> response = new HashMap<>();
        response.put("client_id", clientId);
        if (body != null && body.containsKey("client_name")) {
            response.put("client_name", body.get("client_name"));
        } else {
            response.put("client_name", "MCP Client");
        }
        if (body != null && body.containsKey("redirect_uris")) {
            response.put("redirect_uris", body.get("redirect_uris"));
        } else {
            response.put("redirect_uris", List.of("http://127.0.0.1/oauth/callback"));
        }
        response.put("grant_types", List.of("authorization_code", "refresh_token"));
        response.put("response_types", List.of("code"));
        response.put("token_endpoint_auth_method", "none");
        response.put("client_id_issued_at", System.currentTimeMillis() / 1000L);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/.well-known/oauth-protected-resource")
    public ResponseEntity<Map<String, Object>> getProtectedResourceMetadata(HttpServletRequest request) {
        String base = getBaseUrl(request);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("resource", base);
        metadata.put("authorization_servers", List.of(base));
        return ResponseEntity.ok(metadata);
    }

    @GetMapping(value = "/v1/oauth/authorize", produces = MediaType.TEXT_HTML_VALUE)
    public void authorize(@RequestParam(name = "response_type", defaultValue = "code") String responseType,
                          @RequestParam(name = "client_id", required = false, defaultValue = "mcp-agent") String clientId,
                          @RequestParam(name = "redirect_uri", required = false, defaultValue = "") String redirectUri,
                          @RequestParam(name = "code_challenge", required = false) String codeChallenge,
                          @RequestParam(name = "code_challenge_method", required = false, defaultValue = "S256") String codeChallengeMethod,
                          @RequestParam(name = "state", required = false, defaultValue = "") String state,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {

        if (!"code".equalsIgnoreCase(responseType)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported response_type: " + responseType);
            return;
        }

        if (codeChallenge == null || codeChallenge.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "code_challenge is required (PKCE)");
            return;
        }

        // Check if user is authenticated in the current security context (e.g. from Bearer token or cookie)
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        User user = (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User)
                ? (User) auth.getPrincipal()
                : null;

        if (user != null) {
            String code = oauthService.createAuthorizationCode(user, clientId, redirectUri, codeChallenge, codeChallengeMethod);
            String target = buildRedirectUrl(redirectUri, code, state);
            response.sendRedirect(target);
            return;
        }

        // If not authenticated, render authorization consent page where user can log in or authorize
        response.setContentType("text/html;charset=UTF-8");
        String html = renderAuthorizePage(clientId, redirectUri, codeChallenge, codeChallengeMethod, state, getBaseUrl(request));
        response.getWriter().write(html);
    }

    @PostMapping(value = "/v1/oauth/authorize/approve")
    public void approveAuthorization(@RequestParam("user_id") Long userId,
                                     @RequestParam("client_id") String clientId,
                                     @RequestParam("redirect_uri") String redirectUri,
                                     @RequestParam("code_challenge") String codeChallenge,
                                     @RequestParam(name = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
                                     @RequestParam(name = "state", defaultValue = "") String state,
                                     HttpServletResponse response) throws IOException {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        String code = oauthService.createAuthorizationCode(user, clientId, redirectUri, codeChallenge, codeChallengeMethod);
        String target = buildRedirectUrl(redirectUri, code, state);
        response.sendRedirect(target);
    }

    @PostMapping(value = "/v1/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<OAuthTokenResponse> exchangeTokenForm(
            @RequestParam(name = "grant_type") String grantType,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "code_verifier", required = false) String codeVerifier,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "refresh_token", required = false) String refreshToken) {

        return processTokenGrant(grantType, code, codeVerifier, clientId, redirectUri, refreshToken);
    }

    @PostMapping(value = "/v1/oauth/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OAuthTokenResponse> exchangeTokenJson(@RequestBody Map<String, String> body) {
        String grantType = body.get("grant_type");
        String code = body.get("code");
        String codeVerifier = body.get("code_verifier");
        String clientId = body.get("client_id");
        String redirectUri = body.get("redirect_uri");
        String refreshToken = body.get("refresh_token");

        return processTokenGrant(grantType, code, codeVerifier, clientId, redirectUri, refreshToken);
    }

    private ResponseEntity<OAuthTokenResponse> processTokenGrant(
            String grantType,
            String code,
            String codeVerifier,
            String clientId,
            String redirectUri,
            String refreshToken) {

        if ("authorization_code".equalsIgnoreCase(grantType)) {
            OAuthTokenResponse response = oauthService.exchangeCodeForTokens(code, codeVerifier, clientId, redirectUri);
            return ResponseEntity.ok(response);
        } else if ("refresh_token".equalsIgnoreCase(grantType)) {
            OAuthTokenResponse response = oauthService.refreshTokens(refreshToken, clientId);
            return ResponseEntity.ok(response);
        } else {
            throw new IllegalArgumentException("Unsupported grant_type: " + grantType + ". Supported: authorization_code, refresh_token");
        }
    }

    private String buildRedirectUrl(String redirectUri, String code, String state) {
        StringBuilder sb = new StringBuilder(redirectUri);
        sb.append(redirectUri.contains("?") ? "&" : "?");
        sb.append("code=").append(URLEncoder.encode(code, StandardCharsets.UTF_8));
        if (state != null && !state.isBlank()) {
            sb.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String getBaseUrl(HttpServletRequest request) {
        if (configuredIssuer != null && !configuredIssuer.isBlank()) {
            return configuredIssuer.replaceAll("/+$", "");
        }
        return ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
    }

    private String renderAuthorizePage(String clientId, String redirectUri, String codeChallenge, String codeChallengeMethod, String state, String baseUrl) {
        String template = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Authorize Agent - Budget Tracker</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                        .card { background: #1e293b; border: 1px solid #334155; border-radius: 12px; padding: 32px; width: 100%; max-width: 440px; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.5); text-align: center; }
                        .badge { display: inline-block; padding: 4px 12px; border-radius: 9999px; background: #0369a1; color: #bae6fd; font-size: 12px; font-weight: 600; text-transform: uppercase; margin-bottom: 16px; }
                        h1 { font-size: 22px; font-weight: 700; margin: 0 0 12px; color: #ffffff; }
                        p { font-size: 14px; color: #94a3b8; line-height: 1.5; margin: 0 0 24px; }
                        .client-box { background: #0f172a; border-radius: 8px; padding: 12px; margin-bottom: 24px; font-size: 13px; color: #38bdf8; word-break: break-all; }
                        .scope-list { text-align: left; background: #0f172a; border-radius: 8px; padding: 16px; margin-bottom: 24px; font-size: 13px; color: #cbd5e1; }
                        .scope-list li { margin-bottom: 8px; }
                        .scope-list li:last-child { margin-bottom: 0; }
                        button { width: 100%; padding: 12px; border: none; border-radius: 8px; font-weight: 600; font-size: 15px; cursor: pointer; transition: background 0.2s; }
                        .btn-primary { background: #0284c7; color: white; margin-bottom: 12px; }
                        .btn-primary:hover { background: #0369a1; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="badge">Model Context Protocol</div>
                        <h1>Authorize AI Agent</h1>
                        <p>An AI client is requesting access to your Budget Tracker personal account.</p>
                        
                        <div class="client-box">Client: <strong>{{CLIENT_ID}}</strong></div>
                        
                        <div class="scope-list">
                            <strong>Permissions requested:</strong>
                            <ul>
                                <li>Read & Write transactions & categories</li>
                                <li>Read-only financial stats & balances</li>
                                <li>Read-only savings goals progress</li>
                            </ul>
                        </div>
                        
                        <form method="POST" action="{{BASE_URL}}/v1/oauth/authorize/approve">
                            <input type="hidden" name="user_id" value="1" />
                            <input type="hidden" name="client_id" value="{{CLIENT_ID}}" />
                            <input type="hidden" name="redirect_uri" value="{{REDIRECT_URI}}" />
                            <input type="hidden" name="code_challenge" value="{{CODE_CHALLENGE}}" />
                            <input type="hidden" name="code_challenge_method" value="{{CODE_CHALLENGE_METHOD}}" />
                            <input type="hidden" name="state" value="{{STATE}}" />
                            <button type="submit" class="btn-primary">Authorize Agent</button>
                        </form>
                    </div>
                </body>
                </html>
                """;

        return template
                .replace("{{CLIENT_ID}}", escapeHtml(clientId))
                .replace("{{BASE_URL}}", baseUrl)
                .replace("{{REDIRECT_URI}}", escapeHtml(redirectUri))
                .replace("{{CODE_CHALLENGE}}", escapeHtml(codeChallenge))
                .replace("{{CODE_CHALLENGE_METHOD}}", escapeHtml(codeChallengeMethod))
                .replace("{{STATE}}", escapeHtml(state));
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
