# Agent Instructions & Project Guidelines

This document serves as the instruction guide for AI agents working on the **Budget Tracker** application. It contains instructions, architectural rules, coding standards, database schema guidelines, and API conventions.

Ask for confirmation before committing and pushing, unless explicitly told not to.

---

## Critical Rule: Zero Assumptions & Explicit Clarification

* **Never Make Assumptions**: Assumptions are the primary source of bugs, architectural mismatches, and design deviations. If any requirement, code path, or user request is ambiguous, stop and ask the user for explicit clarification.
* **Always Question the User**: If you suspect the user is wrong, proposing an inefficient design, or violating established project standards, you must explicitly question them and explain why. Do not worry about "hurting feelings"—technical correctness, robust architecture, and code quality are the absolute priorities.

---

## 1. Project Overview & Tech Stack

The Budget Tracker is a multi-currency personal finance application.
* **Backend**: Spring Boot 3.3.0, Java 21, Maven.
* **Database**: PostgreSQL (for local development and testing, configured to run as an H2 in-memory database with PostgreSQL compatibility mode).
* **Migrations**: Flyway.
* **Database Access**: 
  * Use **Spring Data JPA** for standard CRUD operations, entity mapping, and simple queries.
  * Use **JOOQ** for complex querying, analytical dashboards, reporting, and high-performance SQL.
* **Client Apps (Future)**: React/Vite Web App, Android Client (Kotlin + Compose).

---

## 2. Directory Structure

Ensure code is placed in the correct directory:
* REST Controllers: `backend/src/main/java/com/budgettracker/backend/controller/`
* Entities/Models: `backend/src/main/java/com/budgettracker/backend/model/`
* Repositories (JPA): `backend/src/main/java/com/budgettracker/backend/repository/`
* DTOs: `backend/src/main/java/com/budgettracker/backend/dto/`
* Database Migrations: `backend/src/main/resources/db/migration/`

---

## 3. Database & Migration Rules

* **PostgreSQL Dialect**: Design database schemas strictly for PostgreSQL.
* **Migrations First**: Never modify the schema directly. Always use Flyway migration files located under `backend/src/main/resources/db/migration/`.
  * Name migrations sequentially, e.g., `V2__add_index.sql`, `V3__new_feature.sql`.
* **H2 Compatibility**: Since local development uses H2 in PostgreSQL compatibility mode, avoid PostgreSQL-specific non-standard syntax unless fully compatible. Use standard ANSI SQL.
* **Enums**: Map database enums (like `account_type`, `category_type`) properly to Java enums.
* **JOOQ Code Generation**: Whenever schema changes are introduced via Flyway, regenerate JOOQ sources.

---

## 4. API Design Standards

All REST API endpoints must adhere to the **Zalando REST API Guidelines**:
* **No Prefix**: Do NOT prefix endpoints with `/api`. Use `/v1/...`.
* **Path Segments**: Use kebab-case for URL paths (e.g., `/v1/savings-goals`, `/v1/recurrence-rules`).
* **Collection Nouns**: Use plural nouns for collections (e.g., `/v1/transactions`, `/v1/accounts`).
* **JSON Properties**: Use camelCase for keys in JSON request/response payloads (e.g., `googleIdToken`, `amountLimit`).
* **Query Parameters**: Use camelCase for query parameters (e.g., `startDate`, `accountId`).
* **HTTP Status Codes**:
  * Return `201 Created` for successful creation, and include a `Location` header pointing to the new resource (e.g., `/v1/accounts/{id}`).
  * Return `200 OK` for successful fetches and updates.
  * Return `204 No Content` for successful deletions.
  * Return `400 Bad Request` for validation failures.
  * Return `401 Unauthorized` / `403 Forbidden` for auth issues.

---

## 5. Security & Authentication Flow

* **Google Sign-In (OIDC)**: Clients send a Google ID Token to `POST /v1/tokens`.
* **JWT Token**: The backend verifies the token and responds with a custom JWT (`Authorization: Bearer <TOKEN>`).
* **User Provisioning**: On first login, check for user with `google_sub`. If not found, provision the user and seed default categories (e.g., Food, Utilities, Salary).
* **Authorized Requests**: Validate the JWT token for all requests, retrieving user context (like database `userId`) securely.

---

## 6. Coding Guidelines

* **Java Version**: Always target Java 21 features (pattern matching, records, sealed classes, text blocks where appropriate).
* **Lombok Usage**: Use Lombok annotations (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`, `@Slf4j`) to keep the boilerplate minimal. Avoid manual getter/setter writing.
* **Input Validation**: Use standard JSR-383 / Hibernate Bean Validation annotations (e.g., `@NotNull`, `@Size`, `@DecimalMin`) in request DTOs, and mark controller payloads with `@Valid`.
* **Transaction Management**: Mark write operations or multi-query workflows with `@Transactional` from Spring Framework.
* **Error Handling**: Use a central `@RestControllerAdvice` to catch exceptions (e.g., entity not found, validation errors) and return standardized error response structures.

---

## 7. Testing Requirements

* Write unit and integration tests using Spring Boot's `@SpringBootTest` or `@WebMvcTest`.
* Use H2 database configured in `application.properties` for testing.
* Verify database operations, business validation rules, and correct HTTP status codes in controllers.
