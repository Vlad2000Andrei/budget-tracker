# Technical Specification: Category Implementation (JOOQ Exclusive)

Here is the technical specification for implementing the Category Management feature in the Budget Tracker backend using **JOOQ exclusively** for all database access.

---

## Spec 1: Configure JOOQ Maven Codegen Plugin

**Scope**: `chore: configure JOOQ Maven Codegen plugin`

### Description
Configure `pom.xml` to automatically run Flyway migrations and run the JOOQ code generator during the Maven `generate-sources` phase. Since local development uses H2 in PostgreSQL compatibility mode, we will run Flyway migrations on a temporary file-based H2 database during build, and point JOOQ codegen to it.

### Tasks
- [ ] Configure the `flyway-maven-plugin` and `jooq-codegen-maven` plugins in `pom.xml` inside `<plugins>` section:
  - Configure Flyway to migrate the `src/main/resources/db/migration/` files to `jdbc:h2:file:./target/codegen_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`.
  - Configure JOOQ codegen to target H2 database, scan `PUBLIC` schema, output packages to `com.budgettracker.backend.jooq`, and output generated files to `target/generated-sources/jooq`.
- [ ] Add `target/generated-sources/jooq` to the build helper paths (or Maven will include it automatically).
- [ ] Run `.\mvnw.cmd clean compile` to generate classes.

### Acceptance Criteria
- JOOQ classes (e.g., `Categories`, `Users`, `CategoriesRecord`) are successfully generated under `target/generated-sources/jooq/com/budgettracker/backend/jooq/`.
- The compilation succeeds.

---

## Spec 2: Implement JOOQ Repositories (Database Layer)

**Scope**: `feat: implement User and Category repositories using JOOQ`

### Description
Create domain record/POJO classes and JOOQ repositories to perform CRUD operations on `users` and `categories` tables.

### Tasks
- [ ] Create DTO/POJO class `User.java` under `com.budgettracker.backend.model/` (fields: `id`, `email`, `googleSub`, `defaultCurrency`, `createdAt`, `updatedAt`).
- [ ] Create DTO/POJO class `Category.java` under `com.budgettracker.backend.model/` (fields: `id`, `userId`, `parentId`, `name`, `icon`, `color`, `type` [Enum], `createdAt`, `updatedAt`).
- [ ] Create `UserRepository.java` using `DSLContext` to fetch/insert users.
- [ ] Create `CategoryRepository.java` using `DSLContext` to perform:
  - `save(Category category)` (inserts or updates record).
  - `findById(Long id)` (fetches a single category).
  - `findByUserIdAndSystemWide(Long userId)` (fetches categories where `user_id = userId` OR `user_id IS NULL`).
  - `deleteById(Long id)` (deletes a single category).
  - `existsById(Long id)` (checks existence).
  - `hasTransactions(Long id)` (checks if `transactions` contains references to the category).
  - `hasCircularDependency(Long childId, Long parentId)` (traverses the parent chain using a recursive query to detect cycles).

### Acceptance Criteria
- Repositories utilize pure `DSLContext` for all SQL operations.
- Circular dependency checks are handled efficiently in SQL or Java.
- Tests verify basic insert, select, update, and delete actions.

---

## Spec 3: Set up Mock Authentication & User Context (Prerequisite)

**Scope**: `chore: set up user context and mock authentication helper`

### Description
All requests to `/v1/categories` require resolving the current authenticated user context. Since the main OIDC flow is not yet set up, we need a simplified mechanism (such as parsing a custom header like `X-User-Id` or using a dummy user context helper) to resolve `userId` in controllers.

### Tasks
- [ ] Create a mock security filter or context helper that extracts a user ID from a custom header (e.g. `X-User-Id`).
- [ ] Seed a default mock user in H2 during application startup (in local/test profiles) to facilitate manual testing.

### Acceptance Criteria
- REST controllers can retrieve the current authenticated `User` context.
- The mechanism is easily replaceable once the actual JWT authentication flow is implemented.

---

## Spec 4: Implement Category Business Logic (Service Layer)

**Scope**: `feat: implement CategoryService business logic and validations`

### Description
Implement the `CategoryService` to manage CRUD operations for categories and enforce business rules (hierarchy checks, deletion blocks, and user isolation).

### Tasks
- [ ] Implement `CategoryService` with the following methods:
  - `getCategories(User user)`: Fetch system-wide categories (`user_id` is null) combined with categories belonging to the current user.
  - `createCategory(CreateCategoryRequest request, User user)`: Validate and create a category. If `parentId` is provided, verify it exists, belongs to the same user or is system-wide, and matches the type.
  - `updateCategory(Long id, UpdateCategoryRequest request, User user)`: Prevent modifying system-wide categories. Validate `parentId` changes to prevent circular dependencies.
  - `deleteCategory(Long id, User user)`: Prevent deleting system-wide categories. Check if the category is referenced by any transactions. Since there is no cascading delete on transactions, throw a custom `CategoryInUseException` if transactions exist.
  - `seedDefaultCategories(User user)`: Auto-seed standard categories (Food, Utilities, Transport, Entertainment, Salary) for a new user upon provisioning.

### Acceptance Criteria
- Business rules are covered by unit tests.
- Circular references in parent-child relations are blocked.
- Standard default categories can be successfully cloned/seeded.

---

## Spec 5: Create Category REST API Controllers

**Scope**: `feat: implement /v1/categories API endpoints`

### Description
Implement the REST endpoints for Category management following the Zalando REST API Guidelines.

### Tasks
- [ ] Create `CategoryController.java` under `com.budgettracker.backend.controller/` with kebab-case paths.
- [ ] Implement the following endpoints:
  - `GET /v1/categories` -> Returns `200 OK` with JSON array of combined categories.
  - `POST /v1/categories` -> Accepts `CreateCategoryRequest` DTO, returns `201 Created` with a `Location` header pointing to `/v1/categories/{id}`.
  - `PATCH /v1/categories/{categoryId}` -> Accepts `UpdateCategoryRequest` DTO, returns `200 OK`.
  - `DELETE /v1/categories/{categoryId}` -> Returns `204 No Content`.
- [ ] Create Request/Response DTOs under `com.budgettracker.backend.dto/` using camelCase keys:
  - `CategoryDto` (id, name, type, parentId, color, icon)
  - `CreateCategoryRequest` (parentId, name, type, color, icon) with validation annotations (`@NotBlank`, `@Size(max = 100)`, hex color regex).
  - `UpdateCategoryRequest` (parentId, name, color, icon)
- [ ] Set up a central `GlobalExceptionHandler.java` using `@RestControllerAdvice` to translate business/validation exceptions into standardized error response structures (e.g. `400 Bad Request`, `404 Not Found`).

### Acceptance Criteria
- REST endpoints match the API spec exactly.
- Validation annotations are checked with `@Valid` on request bodies.
- Responses use camelCase formatting.

---

## Spec 6: Category Integration & API Tests

**Scope**: `test: write integration tests for Category API`

### Description
Verify the Category endpoints end-to-end using integration tests.

### Tasks
- [ ] Create `CategoryControllerIntegrationTest.java` under `backend/src/test/java/com/budgettracker/backend/controller/`.
- [ ] Test successful cases (GET, POST, PATCH, DELETE).
- [ ] Test validation failures (blank name, invalid hex color code).
- [ ] Test security/hierarchy violations (modifying system-wide categories, circular dependencies, type mismatches).

### Acceptance Criteria
- All integration tests execute and pass successfully during the Maven test phase.
