# Technical Specification: Authentication, User Accounts, and Google Sign-in

Here is the technical specification for implementing Google-based authentication, user account provisioning, custom JWT sessions, and user settings management in the Budget Tracker backend.

---

## Spec 1: Configure Dependencies in pom.xml

**Scope**: `chore: add security, google client and jwt dependencies`

### Description
Add Spring Security, Google API Client, and JSON Web Token (JJWT) dependencies to the Maven `pom.xml` configuration to support passwordless OIDC token verification and local stateless JWT sessions.

### Tasks
- [ ] Add the following dependencies to `<dependencies>` inside `backend/pom.xml`:
  - **Spring Security Starter**:
    ```xml
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    ```
  - **Google API Client**:
    ```xml
    <dependency>
        <groupId>com.google.api-client</groupId>
        <artifactId>google-api-client</artifactId>
        <version>2.6.0</version>
    </dependency>
    ```
  - **JSON Web Token (JJWT) API**:
    ```xml
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.5</version>
    </dependency>
    ```
  - **JJWT Runtime Implementations**:
    ```xml
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    ```
- [ ] Run `./mvnw.cmd clean compile` to download the dependencies and verify compilation.

### Acceptance Criteria
- Maven coordinates resolve successfully.
- Code compiles without version collision issues.

---

## Spec 2: Configure Properties & Secrets

**Scope**: `chore: configure security and token secret properties`

### Description
Expose configuration parameters for the Google Client ID and custom JWT properties within the application configurations.

### Tasks
- [ ] Add properties to `backend/src/main/resources/application.properties` (and corresponding `.properties` files for tests if applicable):
  ```properties
  # Google Sign-In Configurations
  app.security.google.client-id=${GOOGLE_CLIENT_ID:your-placeholder-client-id.apps.googleusercontent.com}

  # JWT Token Configurations
  app.security.jwt.secret=${JWT_SECRET:at-least-256-bit-long-secret-key-for-hmac-sha-256-signing-placeholder}
  app.security.jwt.expiration-ms=${JWT_EXPIRATION_MS:3600000}
  ```

### Acceptance Criteria
- Properties are easily overridden by environment variables (`GOOGLE_CLIENT_ID`, `JWT_SECRET`, `JWT_EXPIRATION_MS`).
- JWT secret is long enough to meet the signature requirements of HMAC-SHA-256 algorithms (minimum 256 bits).

---

## Spec 3: Implement Google OIDC Token Verification Service

**Scope**: `feat: implement GoogleTokenVerifierService`

### Description
Implement a helper service containing the official Google OIDC validation logic. The service verifies the cryptographic signature of incoming Google ID Tokens to confirm validity, issuer, and client-id matching.

### Tasks
- [ ] Create `GoogleTokenVerifierService.java` in `com.budgettracker.backend.service/`.
- [ ] Retrieve the client-id using Spring `@Value("${app.security.google.client-id}")`.
- [ ] Initialize a `GoogleIdTokenVerifier` instance using:
  ```java
  GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
          new NetHttpTransport(), 
          JacksonFactory.getDefaultInstance()
  )
  .setAudience(Collections.singletonList(clientId))
  .build();
  ```
- [ ] Implement `GoogleIdToken.Payload verifyToken(String idTokenString)`:
  - Call `verifier.verify(idTokenString)`.
  - Validate that the returned token object is not null.
  - Return the payload object containing user email and subject (`sub`).
  - Wrap any signature/parsing exceptions in a custom exception (e.g. `InvalidGoogleTokenException` or standard `IllegalArgumentException` caught in `@RestControllerAdvice`).

### Acceptance Criteria
- Service checks audience (must match backend's client ID).
- Service checks issuer (must match `accounts.google.com` or `https://accounts.google.com`).
- Expired or tampered tokens result in a validation failure exception.

---

## Spec 4: Implement JWT Utility Service

**Scope**: `feat: implement JwtService for token operations`

### Description
Implement a service responsible for signing and parsing custom, short-lived JSON Web Tokens (JWTs) representing authenticated sessions on the backend.

### Tasks
- [ ] Create `JwtService.java` in `com.budgettracker.backend.service/`.
- [ ] Retrieve properties `app.security.jwt.secret` (decoded using Base64/Bytes) and `app.security.jwt.expiration-ms` via `@Value`.
- [ ] Implement `String generateToken(User user)`:
  - Build custom JWT containing claims:
    - Subject (`sub`): User email or user ID.
    - Custom claim: `userId` (internal database user ID).
    - Issued At: Current time.
    - Expiration: Current time + expiration-ms.
  - Sign with the Secret Key using HMAC-SHA-256 algorithm.
- [ ] Implement `Long getUserIdFromToken(String token)`:
  - Parse the JWT claims using the parsed key.
  - Extract the custom `userId` claim.
- [ ] Implement `boolean validateToken(String token)`:
  - Validate the signature and check expiration. Return true if valid.
  - Catch `ExpiredJwtException`, `MalformedJwtException`, `SignatureException`, and log warnings without throwing server exceptions during normal request pipelines.

### Acceptance Criteria
- Token is signed, secure, and statelessly decodable.
- Expiration checks are fully functional.

---

## Spec 5: Configure Stateless Spring Security & JWT Filter

**Scope**: `feat: configure Spring Security stateless filter chain`

### Description
Configure Spring Security to disable stateful session tracking, restrict access to secured `/v1/**` endpoints, and inject a custom filter that authenticates incoming Bearer JWT tokens.

### Tasks
- [ ] Create `JwtAuthenticationFilter.java` in `com.budgettracker.backend.config/` extending `OncePerRequestFilter`:
  - Intercept requests and read the `Authorization` header.
  - If header is present and starts with `Bearer `, extract the token.
  - Validate the token using `JwtService`.
  - If valid, extract `userId` from token.
  - Load the user details from the database (`UserRepository.findById(userId)`).
  - Construct a `UsernamePasswordAuthenticationToken` using the user details (as the security principal) and empty authorities.
  - Set the security context:
    ```java
    SecurityContextHolder.getContext().setAuthentication(authentication);
    ```
  - Continue the filter chain.
- [ ] Create `SecurityConfig.java` in `com.budgettracker.backend.config/` annotated with `@Configuration` and `@EnableWebSecurity`:
  - Define a `SecurityFilterChain` bean:
    - Disable CSRF (`csrf -> csrf.disable()`).
    - Disable basic HTTP and form login.
    - Configure session management to stateless (`SessionCreationPolicy.STATELESS`).
    - Set authorize rules:
      - Permit anonymous `POST /v1/tokens`.
      - Permit anonymous access to Swagger/OpenAPI endpoints (`/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-ui.html`).
      - Permit anonymous access to Actuator/Health checks (`/health` or `/actuator/health`).
      - Secure all other `/v1/**` endpoints (`anyRequest().authenticated()`).
    - Add `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`.
    - Set a custom `AuthenticationEntryPoint` to catch unauthorized access attempts and return a standardized JSON error message (`{"error": "Unauthorized Access", "status": 401}`) instead of a redirects/HTML page.

### Acceptance Criteria
- Requesting a secured endpoint without a valid JWT header returns `401 Unauthorized` as a JSON payload.
- Requesting `/v1/tokens` or Swagger remains open to anonymous users.
- Valid tokens permit access to the API controllers.

---

## Spec 6: Create Token Exchange & User Provisioning API

**Scope**: `feat: implement /v1/tokens endpoint and user auto-provisioning`

### Description
Implement the token exchange REST endpoint. The API accepts Google ID tokens, performs authentication, provisions the user if they do not already exist, seeds default budget categories, and issues custom JWTs.

### Tasks
- [ ] Create `CreateTokenRequest.java` in `com.budgettracker.backend.dto/`:
  - Contains `String googleIdToken` (validated with `@NotBlank`).
- [ ] Create `TokenResponse.java` in `com.budgettracker.backend.dto/`:
  - Contains `String token` and `LocalDateTime expiresAt` (or `Instant`).
- [ ] Create `TokenController.java` in `com.budgettracker.backend.controller/` mapping requests to `/v1/tokens`:
  - Implement `POST /v1/tokens` returning `201 Created`:
    - Inject `GoogleTokenVerifierService`, `UserRepository`, `JwtService`, and `CategoryService`.
    - Verify Google ID Token.
    - Extract `googleSub` and `email`.
    - Check database for the user: `userRepository.findByGoogleSub(googleSub)`.
    - **First-Time Provisioning Flow**:
      - If not found:
        - Build a new `User` entity (`email`, `googleSub`, `defaultCurrency` = `"USD"`).
        - Persist the user: `User savedUser = userRepository.save(user)`.
        - Seed default categories: `categoryService.seedDefaultCategories(savedUser)`.
      - If found:
        - Retrieve the user.
    - Generate custom JWT using `JwtService`.
    - Build `TokenResponse` with the custom token and expiration timestamp.
    - Return `201 Created` with a `Location` header pointing to `/v1/users/me`.

### Acceptance Criteria
- Exchanging valid Google token successfully yields a custom JWT.
- Seeding logic is triggered only on the first login of a newly provisioned user.
- Response matches the JSON spec (camelCase keys).

---

## Spec 7: Implement User Profile & Settings Endpoints

**Scope**: `feat: implement /v1/users/me endpoints`

### Description
Expose REST endpoints allowing authenticated users to fetch details about their account profile and update configurations, such as their default currency preference.

### Tasks
- [ ] Create DTOs under `com.budgettracker.backend.dto/`:
  - `UserDto` containing `id`, `email`, `defaultCurrency`.
  - `UpdateUserRequest` containing `defaultCurrency` (validated using `@NotBlank` and a 3-letter currency regex e.g. `^[A-Z]{3}$`).
- [ ] Create `UserController.java` in `com.budgettracker.backend.controller/` mapping to `/v1/users/me`:
  - Inject `UserRepository`.
  - Implement `GET /v1/users/me` returning `200 OK` with the authenticated `User` context payload.
  - Implement `PATCH /v1/users/me` accepting `@Valid UpdateUserRequest`:
    - Resolve current `User` context.
    - Set user `defaultCurrency` from request (validated uppercase ISO-4217 code).
    - Save updated user: `userRepository.save(user)`.
    - Return updated `UserDto` profile.

### Acceptance Criteria
- User profile queries return only the current authenticated user's details.
- PATCH currency checks accept only valid 3-letter ISO codes (e.g. `USD`, `EUR`, `RON`).
- Endpoint is fully secured.

---

## Spec 8: Adapt MVC User Argument Resolver

**Scope**: `refactor: update UserArgumentResolver to bridge Spring Security`

### Description
Refactor the custom MVC `UserArgumentResolver` to extract the authenticated user entity directly from Spring Security context, ensuring compatibility with other controllers accepting `User user` method parameters.

### Tasks
- [ ] Update `UserArgumentResolver.java` under `com.budgettracker.backend.config/`:
  - Change `resolveArgument(...)` to retrieve the security authentication:
    ```java
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    ```
  - Check if `authentication` is not null, is authenticated, and the principal is an instance of `User`.
  - If valid, return the `User` principal directly.
  - **Dev/Test Fallback Support**:
    - If no authentication is present, check for the `X-User-Id` header (to avoid breaking current category/transaction tests that depend on header-based user seeding or mock setups).
    - If neither is found, fallback to provisioning/returning the default mock sub user (under `dev` / `test` spring profile environments).

### Acceptance Criteria
- MVC controllers successfully and securely resolve `User` parameters when valid JWT credentials exist.
- Backward compatibility for current header-based testing is preserved under test/development profiles.

---

## Spec 9: Security and Authentication Integration Tests

**Scope**: `test: write integration tests for authentication and security`

### Description
Implement automated testing for validation, route restriction, custom token exchange, and authenticated user endpoints.

### Tasks
- [ ] Create `AuthControllerIntegrationTest.java` in `backend/src/test/java/com/budgettracker/backend/controller/`:
  - Mock `GoogleTokenVerifierService` bean using Spring's `@MockBean`.
  - Test case: Secured routes return `401 Unauthorized` without a valid token.
  - Test case: Exchanging a valid token succeeds, inserts user records, calls seeding service, and yields a valid JWT.
  - Test case: Accessing secured routes (e.g. `/v1/categories`, `/v1/users/me`) using the yielded JWT returns `200 OK` with correct details.
  - Test case: Requesting `/v1/users/me` with invalid authorization formats yields `401 Unauthorized`.
  - Test case: Patching default currency updates the database user record correctly.
- [ ] Run all project tests using `./mvnw.cmd test` to ensure all existing category/transaction tests and new security tests succeed.

### Acceptance Criteria
- All authentication integration tests run and pass cleanly.
- Application builds successfully.
