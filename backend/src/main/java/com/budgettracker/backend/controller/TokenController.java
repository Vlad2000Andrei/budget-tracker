package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.CreateTokenRequest;
import com.budgettracker.backend.dto.TokenResponse;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.UserRepository;
import com.budgettracker.backend.service.CategoryService;
import com.budgettracker.backend.service.GoogleTokenVerifierService;
import com.budgettracker.backend.service.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/v1/tokens")
public class TokenController {

    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final CategoryService categoryService;
    private final long jwtExpirationMs;

    @Autowired
    public TokenController(
            GoogleTokenVerifierService googleTokenVerifierService,
            UserRepository userRepository,
            JwtService jwtService,
            CategoryService categoryService,
            @Value("${app.security.jwt.expiration-ms}") long jwtExpirationMs) {
        this.googleTokenVerifierService = googleTokenVerifierService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.categoryService = categoryService;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<TokenResponse> createToken(@Valid @RequestBody CreateTokenRequest request) {
        GoogleIdToken.Payload googlePayload = googleTokenVerifierService.verifyToken(request.getGoogleIdToken());
        String googleSub = googlePayload.getSubject();
        String email = googlePayload.getEmail();
        String name = (String) googlePayload.get("name");

        User user = userRepository.findByGoogleSub(googleSub)
                .orElseGet(() -> {
                    User newUser = userRepository.save(User.builder()
                            .email(email)
                            .googleSub(googleSub)
                            .displayName(name)
                            .defaultCurrency("USD")
                            .build());
                    categoryService.seedDefaultDataForUser(newUser);
                    return newUser;
                });

        String jwt = jwtService.generateToken(user);
        Instant expiresAt = Instant.now().plusMillis(jwtExpirationMs);

        TokenResponse response = TokenResponse.builder()
                .token(jwt)
                .expiresAt(expiresAt)
                .build();

        return ResponseEntity.created(URI.create("/v1/users/me"))
                .body(response);
    }
}
