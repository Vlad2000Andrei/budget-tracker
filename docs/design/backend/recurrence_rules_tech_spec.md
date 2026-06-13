# Technical Specification: Recurrence Rules & Recurring Transactions Implementation

This document details the technical specification for implementing the Recurrence Rules and Recurring Transactions feature in the Budget Tracker backend.

---

## Spec 1: Implement JOOQ Repository (Database Layer)

**Scope**: `feat: implement RecurrenceRule model and repository using JOOQ`

### Description
Create the `RecurrenceRule` model class and a JOOQ repository to perform CRUD operations on the `recurrence_rules` table.

### Tasks
- [ ] Create domain model POJO class `RecurrenceRule.java` under `com.budgettracker.backend.model/` with the following Lombok-annotated fields matching the `recurrence_rules` table:
  - `id` (Long)
  - `frequency` (RecurrenceFrequency enum: `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`)
  - `interval` (Integer, defaults to 1)
  - `startDate` (LocalDate)
  - `endDate` (LocalDate, optional)
  - `createdAt` (LocalDateTime)
  - `updatedAt` (LocalDateTime)
- [ ] Create `RecurrenceRuleRepository.java` under `com.budgettracker.backend.repository/` using `DSLContext` to perform:
  - `save(RecurrenceRule rule)`: Inserts a new rule or updates an existing one (returning the persisted/updated record).
  - `findById(Long id)`: Fetches a single recurrence rule.
  - `deleteById(Long id)`: Deletes a single recurrence rule.
  - `findActiveRules(LocalDate date)`: Fetches all active recurrence rules where `start_date <= date` and (`end_date IS NULL` or `end_date >= date`).

### Acceptance Criteria
- Repository utilizes pure `DSLContext` for database interactions.
- Enums map correctly to the JOOQ generated enums.

---

## Spec 2: Integrate Recurrence Rules into Transactions

**Scope**: `feat: link recurrence rules to transaction creation`

### Description
Modify transaction endpoints to support setting a transaction as recurring. When a transaction is created with recurrence info, a recurrence rule is generated and linked.

### Tasks
- [ ] Create DTOs under `com.budgettracker.backend.dto/`:
  - `RecurrenceRuleDto` containing `id`, `frequency`, `interval`, `startDate`, `endDate`.
  - `CreateRecurrenceRuleRequest` containing `frequency` (not null), `interval` (not null, min 1), `startDate` (not null), `endDate` (optional).
- [ ] Modify `CreateTransactionRequest` and `TransactionDto` to include an optional nested `recurrenceRule` property:
  - In `CreateTransactionRequest`: Add `CreateRecurrenceRuleRequest recurrenceRule`.
  - In `TransactionDto`: Add `RecurrenceRuleDto recurrenceRule`.
- [ ] Update `TransactionService.java` to handle recurrence creation:
  - If `CreateTransactionRequest` contains a non-null `recurrenceRule` payload, validate and save the `RecurrenceRule` first.
  - Populate the `recurrenceRuleId` of the `Transaction` entity with the generated rule ID before persisting it.
  - This initial transaction acts as the **template transaction** for recurrence.

### Acceptance Criteria
- Creating a transaction with recurrence parameters saves both the recurrence rule and the transaction.
- The transaction correctly holds a reference to the recurrence rule in the database.

---

## Spec 3: Implement Recurring Transaction Engine (Scheduler)

**Scope**: `feat: implement background scheduled recurring transaction engine`

### Description
Implement a background scheduled task that runs periodically to evaluate active recurrence rules, calculate due occurrences, and auto-spawn corresponding transactions.

### Tasks
- [ ] Configure Spring scheduling:
  - Add `@EnableScheduling` to a configuration class (e.g. `WebConfig.java` or `BackendApplication.java`).
- [ ] Create `RecurringTransactionEngine.java` under `com.budgettracker.backend.service/`:
  - Implement a method `processRecurringTransactions()` scheduled to run once a day (e.g. `@Scheduled(cron = "0 0 0 * * *")`).
  - Retrieve active recurrence rules via `recurrenceRuleRepository.findActiveRules(LocalDate.now())`.
  - For each active rule:
    1. Fetch the **template transaction** linked to this `recurrenceRuleId` (usually the first transaction created with this rule).
    2. Retrieve the latest transaction spawned under this `recurrenceRuleId` to find the last run date.
    3. Calculate the next occurrence date(s) starting from `startDate` (or after the latest transaction date) up to `LocalDate.now()`.
    4. If one or more occurrences are due, spawn a new transaction for each occurrence date:
       - Clone properties: `userId`, `categoryId`, `accountId`, `recurrenceRuleId`, `amount`, `currency`, `notes`, `type`.
       - Set the transaction `date` to the calculated occurrence date.
       - Use `CurrencyExchangeService` to dynamically convert the amount to the user's default currency and populate `convertedAmount` and `exchangeRate`.
       - Adjust the linked account balance via `AccountService.adjustBalance()` (similar to standard transaction creation).
       - Persist the spawned transaction.

### Acceptance Criteria
- Spawning a recurring transaction uses cached exchange rates and recalculates converted amounts correctly.
- Balances of accounts linked to auto-spawned transactions are adjusted correctly.
- Mechanism handles missed occurrences (e.g., if the application was offline for a few days, it catches up and spawns all missed occurrences sequentially).

---

## Spec 4: Integration & Scheduling Tests

**Scope**: `test: write integration tests for recurring transactions`

### Description
Verify the scheduling date math, automatic transaction generation, and account balance reconciliation using unit and integration tests.

### Tasks
- [ ] Create `RecurringTransactionEngineTest.java` under `backend/src/test/java/com/budgettracker/backend/service/`.
- [ ] Implement tests:
  - Test calculation math for different frequencies (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`) and intervals.
  - Test catch-up behavior when multiple occurrences are due (e.g., after 3 days of offline time, it spawns 3 daily transactions).
  - Test account balance adjustment behavior when recurring transactions are automatically run.
  - Verify that exchange rate conversions are executed correctly during automated generation.

### Acceptance Criteria
- Integration tests cover all frequencies and intervals.
- The entire Maven build test phase compiles and completes successfully.
