# Technical Specification: Savings Goals Implementation (JOOQ)

This document details the technical specification for implementing the Savings Goals feature in the Budget Tracker backend, utilizing **JOOQ** for database access and transactional logic.

---

## Spec 1: Implement JOOQ Repository (Database Layer)

**Scope**: `feat: implement SavingsGoal repository and model using JOOQ`

### Description
Create the `SavingsGoal` model class and a JOOQ repository to perform CRUD operations on the `savings_goals` table.

### Tasks
- [ ] Create domain model POJO class `SavingsGoal.java` under `com.budgettracker.backend.model/` with the following Lombok-annotated fields matching the `savings_goals` table:
  - `id` (Long)
  - `userId` (Long)
  - `categoryId` (Long)
  - `targetAmount` (BigDecimal)
  - `currentAmount` (BigDecimal)
  - `targetDate` (LocalDate)
  - `createdAt` (LocalDateTime)
  - `updatedAt` (LocalDateTime)
- [ ] Create `SavingsGoalRepository.java` under `com.budgettracker.backend.repository/` using `DSLContext` to perform:
  - `save(SavingsGoal goal)`: Inserts a new savings goal or updates an existing one (returning the persisted/updated record).
  - `findById(Long id)`: Fetches a single savings goal, mapping it to a `SavingsGoal` object.
  - `findByUserId(Long userId)`: Fetches all savings goals belonging to the specified user.
  - `findByCategoryId(Long categoryId)`: Fetches savings goals associated with a category.
  - `deleteById(Long id)`: Deletes a single savings goal.
  - `existsById(Long id)`: Checks if a savings goal exists.
  - `calculateAccumulatedSavings(Long userId, List<Long> categoryIds)`: Summarizes the transaction amounts of type `SAVINGS` matching these category IDs in the user's default currency.

### Acceptance Criteria
- Repository utilizes pure `DSLContext` for all SQL queries.
- Model fields use standard camelCase properties matching the database schema.

---

## Spec 2: Implement Savings Goal Business Logic & Progress Tracking (Service Layer)

**Scope**: `feat: implement SavingsGoalService and transaction triggers`

### Description
Create the `SavingsGoalService` to coordinate CRUD operations, validate settings, and calculate current progress by checking savings transactions.

### Tasks
- [ ] Create `SavingsGoalService.java` under `com.budgettracker.backend.service/` implementing the following operations:
  - `getSavingsGoals(User user)`: Retrieve all savings goals belonging to the user.
  - `createSavingsGoal(CreateSavingsGoalRequest request, User user)`: Validate and create a new savings goal.
  - `updateSavingsGoal(Long id, UpdateSavingsGoalRequest request, User user)`: Validate and update an existing savings goal.
  - `deleteSavingsGoal(Long id, User user)`: Delete the specified savings goal.
  - `reconcileGoalAmount(Long userId, Long categoryId)`: Query all transactions associated with the goal category (and its subcategories) of type `SAVINGS`, aggregate their values in the user's default currency, and update the `currentAmount` of the savings goal in the database.
- [ ] Enforce the following validation rules:
  - **Ownership Check**: Ensure the savings goal exists and belongs to the authenticated user. Throw `ResourceNotFoundException` / `ForbiddenActionException` as appropriate.
  - **Category Validation**: Ensure the category exists, belongs to the user or is system-wide, and its `CategoryType` is strictly `SAVINGS`.
  - **Progress Updates**:
    - Inject `SavingsGoalService` into `TransactionService` (or handle via domain events/hooks).
    - When a transaction of type `SAVINGS` is **created**, **updated**, or **deleted**, trigger `reconcileGoalAmount` on the affected category to ensure `currentAmount` is dynamically reconciled.

### Acceptance Criteria
- Savings goals validate that category types are strictly `SAVINGS`.
- Recording a transaction of type `SAVINGS` automatically reconciles the progress (`currentAmount`) of the associated savings goal.

---

## Spec 3: Create Savings Goal REST API Controllers & DTOs

**Scope**: `feat: implement /v1/savings-goals API endpoints`

### Description
Implement REST controllers following the Zalando REST API Guidelines and define validated DTOs for requests and responses.

### Tasks
- [ ] Create Request/Response DTO classes under `com.budgettracker.backend.dto/` using camelCase keys and JSR-380 validation annotations:
  - `SavingsGoalDto` (id, categoryId, targetAmount, currentAmount, targetDate)
  - `CreateSavingsGoalRequest` (categoryId, targetAmount, targetDate):
    - `@NotNull` for category ID, target amount.
    - `@DecimalMin("0.01")` for target amount.
  - `UpdateSavingsGoalRequest` (targetAmount, targetDate):
    - `@NotNull` and `@DecimalMin("0.01")` for target amount.
- [ ] Create `SavingsGoalController.java` under `com.budgettracker.backend.controller/` mapping to `/v1/savings-goals`:
  - `GET /v1/savings-goals` -> Returns `200 OK` with a JSON list of the user's savings goals.
  - `POST /v1/savings-goals` -> Accepts `@Valid CreateSavingsGoalRequest`, creates the goal, and returns `201 Created` with a `Location` header pointing to `/v1/savings-goals/{id}`.
  - `PATCH /v1/savings-goals/{goalId}` -> Accepts `@Valid UpdateSavingsGoalRequest`, updates the goal, and returns `200 OK`.
  - `DELETE /v1/savings-goals/{goalId}` -> Deletes the goal and returns `204 No Content`.

### Acceptance Criteria
- REST endpoints match the path design specs (`/v1/savings-goals`).
- HTTP Status Codes correspond to creation, update, fetching, and deletion requirements.

---

## Spec 4: Integration & API Tests

**Scope**: `test: write integration tests for Savings Goals`

### Description
Implement integration tests to verify the Savings Goals endpoints and progression tracking end-to-end.

### Tasks
- [ ] Create `SavingsGoalControllerIntegrationTest.java` under `backend/src/test/java/com/budgettracker/backend/controller/`.
- [ ] Write the following test cases:
  - Successful CRUD operations, verifying proper response formats and status codes.
  - Validation failures for negative target amounts and incorrect category types (e.g. attempting to assign an `EXPENSE` category).
  - Integration with transactions: create a savings goal, post transactions under the linked category, and verify that the savings goal's `currentAmount` updates accordingly.
  - Security constraints ensuring users cannot inspect or mutate other users' savings goals.

### Acceptance Criteria
- Tests successfully verify both direct CRUD and transaction-triggered progress reconciliation.
- All integration tests pass.
