package com.budgettracker.backend.service;

import com.budgettracker.backend.dto.OAuthTokenResponse;
import com.budgettracker.backend.exception.ForbiddenActionException;
import com.budgettracker.backend.exception.ResourceNotFoundException;
import com.budgettracker.backend.model.OAuthAuthorizationCode;
import com.budgettracker.backend.model.OAuthRefreshToken;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.OAuthRepository;
import com.budgettracker.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
public class OAuthService {

    private final OAuthRepository oauthRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.oauth.auth-code-expiration-seconds:300}")
    private long authCodeExpirationSeconds;

    @Value("${app.oauth.refresh-token-expiration-days:30}")
    private long refreshTokenExpirationDays;

    @Autowired
    public OAuthService(OAuthRepository oauthRepository,
                        UserRepository userRepository,
                        JwtService jwtService) {
        this.oauthRepository = oauthRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public String createAuthorizationCode(User user,
                                          String clientId,
                                          String redirectUri,
                                          String codeChallenge,
                                          String codeChallengeMethod) {
        if (codeChallenge == null || codeChallenge.isBlank()) {
            throw new IllegalArgumentException("code_challenge is required for PKCE authorization");
        }
        String method = (codeChallengeMethod != null && !codeChallengeMethod.isBlank())
                ? codeChallengeMethod.toUpperCase()
                : "S256";
        if (!"S256".equals(method) && !"PLAIN".equals(method)) {
            throw new IllegalArgumentException("Unsupported code_challenge_method. Supported: S256, plain");
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        OAuthAuthorizationCode authCode = OAuthAuthorizationCode.builder()
                .code(code)
                .userId(user.getId())
                .clientId(clientId != null ? clientId : "default-client")
                .redirectUri(redirectUri != null ? redirectUri : "")
                .codeChallenge(codeChallenge.trim())
                .codeChallengeMethod(method)
                .expiresAt(LocalDateTime.now().plusSeconds(authCodeExpirationSeconds))
                .build();

        oauthRepository.saveAuthorizationCode(authCode);
        return code;
    }

    @Transactional
    public OAuthTokenResponse exchangeCodeForTokens(String code,
                                                    String codeVerifier,
                                                    String clientId,
                                                    String redirectUri) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Authorization code is required");
        }
        if (codeVerifier == null || codeVerifier.isBlank()) {
            throw new IllegalArgumentException("code_verifier is required for PKCE token exchange");
        }

        OAuthAuthorizationCode authCode = oauthRepository.findAuthorizationCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or already used authorization code"));

        // Single-use: delete immediately
        oauthRepository.deleteAuthorizationCode(code);

        if (authCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Authorization code has expired");
        }

        if (clientId != null && !clientId.isBlank() && !authCode.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("client_id does not match the authorization code");
        }

        if (redirectUri != null && !redirectUri.isBlank() && !authCode.getRedirectUri().isBlank()
                && !authCode.getRedirectUri().equals(redirectUri)) {
            throw new IllegalArgumentException("redirect_uri does not match the authorization code");
        }

        // Verify PKCE
        boolean pkceValid = verifyPkce(codeVerifier, authCode.getCodeChallenge(), authCode.getCodeChallengeMethod());
        if (!pkceValid) {
            throw new IllegalArgumentException("PKCE verification failed: invalid code_verifier");
        }

        User user = userRepository.findById(authCode.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for authorization code"));

        return issueTokensForUser(user, authCode.getClientId());
    }

    @Transactional
    public OAuthTokenResponse refreshTokens(String rawRefreshToken, String clientId) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new IllegalArgumentException("refresh_token is required");
        }

        String tokenHash = hashToken(rawRefreshToken);
        OAuthRefreshToken storedToken = oauthRepository.findRefreshTokenByHash(tokenHash)
                .orElseThrow(() -> new ForbiddenActionException("Invalid refresh token"));

        if (storedToken.getRevokedAt() != null) {
            throw new ForbiddenActionException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ForbiddenActionException("Refresh token has expired");
        }

        // Revoke the old refresh token (refresh token rotation)
        oauthRepository.revokeRefreshToken(tokenHash);

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for refresh token"));

        String effectiveClientId = (clientId != null && !clientId.isBlank()) ? clientId : storedToken.getClientId();
        return issueTokensForUser(user, effectiveClientId);
    }

    private OAuthTokenResponse issueTokensForUser(User user, String clientId) {
        String accessToken = jwtService.generateToken(user);
        long expiresInSeconds = jwtService.getExpirationMs() / 1000;

        // Generate raw refresh token
        String rawRefreshToken = "bt_rf_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = hashToken(rawRefreshToken);

        OAuthRefreshToken refreshTokenEntity = OAuthRefreshToken.builder()
                .tokenHash(tokenHash)
                .userId(user.getId())
                .clientId(clientId)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays))
                .build();

        oauthRepository.saveRefreshToken(refreshTokenEntity);

        return OAuthTokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(expiresInSeconds)
                .refreshToken(rawRefreshToken)
                .scope("read write")
                .build();
    }

    private boolean verifyPkce(String codeVerifier, String codeChallenge, String method) {
        if ("PLAIN".equalsIgnoreCase(method)) {
            return codeVerifier.equals(codeChallenge);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String computedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return computedChallenge.equals(codeChallenge);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            return false;
        }
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
