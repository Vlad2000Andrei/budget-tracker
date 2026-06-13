# Technical Specification: Budgets (Spending Goals) Implementation (JOOQ)

This document details the technical specification for implementing the Budgets (Spending Goals) feature in the Budget Tracker backend, utilizing **JOOQ** for database access.

---

## Spec 1: Implement JOOQ Repository (Database Layer)

**Scope**: `feat: implement Budget repository and model using JOOQ`

### Description
Create the `Budget` model class and a JOOQ repository to perform CRUD operations on the `budgets` table.

### Tasks
- [ ] Create domain model POJO class `Budget.java` under `com.budgettracker.backend.model/` with the following Lombok-annotated fields matching the `budgets` table:
  - `id` (Long)
  - `userId` (Long)
  - `categoryId` (Long)
  - `amountLimit` (BigDecimal)
  - `startDate` (LocalDate)
  - `endDate` (LocalDate)
  - `rolloverRule` (RolloverRuleType)
  - `createdAt` (LocalDateTime)
  - `updatedAt` (LocalDateTime)
- [ ] Create `BudgetRepository.java` under `com.budgettracker.backend.repository/` using `DSLContext` to perform:
  - `save(Budget budget)`: Inserts a new budget or updates an existing one (returning the persisted/updated record).
  - `findById(Long id)`: Fetches a single budget, mapping it to a `Budget` object.
  - `findByUserId(Long userId)`: Fetches all budgets belonging to the specified user.
  - `deleteById(Long id)`: Deletes a single budget.
  - `existsById(Long id)`: Checks if a budget exists.
  - `findOverlappingBudget(Long userId, Long categoryId, LocalDate startDate, LocalDate endDate, Long excludeId)`: Checks if there is already an active budget for this category overlapping with the new date range.

### Acceptance Criteria
- Repository utilizes pure `DSLContext` for all SQL queries.
- Model fields use standard camelCase properties matching the snake_case database schema fields.

---

## Spec 2: Implement Budget Business Logic (Service Layer)

**Scope**: `feat: implement BudgetService validations and logic`

### Description
Create the `BudgetService` to handle business validations, coordinate database updates, and enforce security ownership rules.

### Tasks
- [ ] Create `BudgetService.java` under `com.budgettracker.backend.service/` implementing the following operations:
  - `getBudgets(User user)`: Retrieve all budgets belonging to the current user.
  - `createBudget(CreateBudgetRequest request, User user)`: Validate and create a new budget.
  - `updateBudget(Long id, UpdateBudgetRequest request, User user)`: Validate and update an existing budget.
  - `deleteBudget(Long id, User user)`: Delete the specified budget.
- [ ] Enforce the following validation rules in `BudgetService`:
  - **Ownership Check**: Ensure the budget exists and belongs to the authenticated user. Throw `ResourceNotFoundException` if it doesn't exist, and `ForbiddenActionException` if it belongs to another user.
  - **Category Validation**: Ensure the budget's `categoryId` exists and its type is strictly `EXPENSE`. Throw `IllegalArgumentException` if a user attempts to set a budget on an `INCOME` or `SAVINGS` category.
  - **Date Constraints**: Ensure `startDate` is before or equal to `endDate`.
  - **No Overlapping Budgets**: Check that the user does not create multiple budgets for the exact same category overlapping in date ranges.
  - **Rollover Default**: Ensure `rolloverRule` defaults to `NONE`.

### Acceptance Criteria
- Business rules are validated before DB mutations.
- Multi-query operations are annotated with `@Transactional`.

---

## Spec 3: Create Budget REST API Controllers & DTOs

**Scope**: `feat: implement /v1/budgets API endpoints`

### Description
Implement REST controllers following the Zalando REST API Guidelines and define validated DTOs for requests and responses.

### Tasks
- [ ] Create Request/Response DTO classes under `com.budgettracker.backend.dto/` using camelCase keys and JSR-380 validation annotations:
  - `BudgetDto` (id, categoryId, amountLimit, startDate, endDate, rolloverRule)
  - `CreateBudgetRequest` (categoryId, amountLimit, startDate, endDate, rolloverRule):
    - `@NotNull` for category ID, amount limit, start date, and end date.
    - `@DecimalMin("0.01")` for amount limit.
  - `UpdateBudgetRequest` (amountLimit, startDate, endDate, rolloverRule):
    - `@NotNull` and `@DecimalMin("0.01")` for amount limit.
- [ ] Create `BudgetController.java` under `com.budgettracker.backend.controller/` mapping to `/v1/budgets`:
  - `GET /v1/budgets` -> Returns `200 OK` with a JSON list of the user's budgets.
  - `POST /v1/budgets` -> Accepts `@Valid CreateBudgetRequest`, creates the budget, and returns `201 Created` with a `Location` header pointing to `/v1/budgets/{id}`.
  - `PATCH /v1/budgets/{budgetId}` -> Accepts `@Valid UpdateBudgetRequest`, updates the budget, and returns `200 OK`.
  - `DELETE /v1/budgets/{budgetId}` -> Deletes the budget and returns `204 No Content`.

### Acceptance Criteria
- REST endpoints do not use `/api` prefix and follow kebab-case naming.
- Response payloads use camelCase JSON formatting.
- Location header is set properly on creation.

---

## Spec 4: Integration & API Tests

**Scope**: `test: write integration tests for Budget API`

### Description
Implement integration tests utilizing Spring Boot `MockMvc` to verify the budget endpoints and business logic.

### Tasks
- [ ] Create `BudgetControllerIntegrationTest.java` under `backend/src/test/java/com/budgettracker/backend/controller/`.
- [ ] Write the following test cases:
  - Successful budget creation, retrieval, updates, and deletion.
  - Validation failures for negative limits, invalid dates (`startDate` > `endDate`), and non-existent or wrong-typed categories (`INCOME`/`SAVINGS`).
  - Overlap validation checks blocking duplicate category budget creation.
  - Security validation testing that attempts to read, patch, or delete another user's budget yield `403 Forbidden` / `404 Not Found`.

### Acceptance Criteria
- All tests execute and pass cleanly during the Maven build phase.
