# Technical Specification: Transaction & Account Implementation (JOOQ & Caffeine Cache)

Here is the technical specification for implementing the Account and Transaction Management features in the Budget Tracker backend, including dynamic currency exchange rate caching and automated balance reconciliation.

---

## Spec 1: Implement JOOQ Repositories (Database Layer)

**Scope**: `feat: implement Account and Transaction repositories using JOOQ`

### Description
Create domain record/POJO classes and JOOQ repositories to perform CRUD operations on `accounts` and `transactions` tables.

### Tasks
- [ ] Create domain model POJOs under `com.budgettracker.backend.model/`:
  - `Account.java` (fields: `id`, `userId`, `name`, `type` [Enum], `balance`, `currency`, `createdAt`, `updatedAt`).
  - `Transaction.java` (fields: `id`, `userId`, `categoryId`, `accountId`, `recurrenceRuleId`, `amount`, `currency`, `convertedAmount`, `exchangeRate`, `type` [Enum], `notes`, `date`, `createdAt`, `updatedAt`).
- [ ] Create `AccountRepository.java` using `DSLContext` for:
  - `save(Account account)` (inserts or updates record).
  - `findById(Long id)` (fetches a single account).
  - `findByUserId(Long userId)` (fetches accounts belonging to user).
  - `deleteById(Long id)` (deletes a single account).
  - `existsById(Long id)` (checks existence).
- [ ] Create `TransactionRepository.java` using `DSLContext` for:
  - `save(Transaction transaction)` (inserts or updates record).
  - `findById(Long id)` (fetches a single transaction).
  - `deleteById(Long id)` (deletes a transaction).
  - `findAll(...)` (dynamic SQL builder allowing filtering by `accountId`, `categoryId`, date ranges `startDate` to `endDate`, and `type`).

### Acceptance Criteria
- Repositories utilize pure `DSLContext` for all SQL operations.
- Transactions filtering logic correctly uses dynamic JOOQ `Condition` statements.

---

## Spec 2: Integrate Caffeine Cache & Frankfurter Exchange Rates

**Scope**: `feat: configure Caffeine cache and integrate Frankfurter API`

### Description
Configure Spring Caching with Caffeine to store dynamic exchange rates fetched from the public Frankfurter API. Enable cross-rate conversions using a base currency to minimize remote API calls.

### Tasks
- [ ] Add caching dependencies to `pom.xml` (`spring-boot-starter-cache` and `com.github.benmanes.caffeine:caffeine`).
- [ ] Create `CacheConfig.java` under `com.budgettracker.backend.config/`:
  - Enable Spring caching with `@EnableCaching`.
  - Configure a bean for `CacheManager` using `CaffeineCacheManager`.
  - Set cache `"exchangeRates"` with a 1-hour expiration Time-to-Live (`expireAfterWrite`) and maximum size of 500.
- [ ] Implement `CurrencyExchangeService.java` to:
  - Fetch rates relative to `EUR` from `https://api.frankfurter.app/latest?from=EUR`.
  - Annotate `getExchangeRate` with `@Cacheable(value = "exchangeRates", key = "#from.toUpperCase() + '-' + #to.toUpperCase()")`.
  - Resolve cross-rates dynamically via:
    $$\text{Rate}(A \to B) = \frac{\text{Rate}(\text{EUR} \to B)}{\text{Rate}(\text{EUR} \to A)}$$
  - Add self-injection of the caching proxy reference (`self`) to avoid Spring AOP self-invocation bypass during `convert()` calls.
  - Setup background updates using Spring Scheduling (`@Scheduled`) to execute once a day.
  - Define local fallback maps for `EUR/RON` and `USD/EUR` to keep the application resilient and offline-testable.
  - Expose a configuration property `app.exchange-rate.api-enabled` to toggle remote API calls.

### Acceptance Criteria
- External API calls are cached for 1 hour.
- Internal self-invocations route through the proxy, ensuring cache hits.
- Offline settings or connection failures gracefully fall back to local static rates.

---

## Spec 3: Implement Account & Transaction Services (Business Layer)

**Scope**: `feat: implement Account and Transaction services and balance adjustments`

### Description
Implement the business services (`AccountService` and `TransactionService`) to handle operations, enforce ownership rules, and coordinate account balance adjustments.

### Tasks
- [ ] Implement `AccountService.java`:
  - Coordinate CRUD for accounts, ensuring ownership checks (`ForbiddenActionException` / `ResourceNotFoundException`).
- [ ] Implement `TransactionService.java` with balance updates:
  - Validate ownership of transaction categories and accounts.
  - Ensure transaction `type` matches category `type`.
  - Adjust linked account balances during transaction lifecycles:
    - **On Create**: Adjust balance (increase for `INCOME`, decrease for `EXPENSE`/`SAVINGS`). Convert transaction currency to account currency using `CurrencyExchangeService`.
    - **On Update**: Revert old transaction amount from old account, apply new transaction amount to new account.
    - **On Delete**: Revert transaction amount from account.

### Acceptance Criteria
- Account balance adjustments correctly apply dynamic currency conversions when transaction and account currencies differ.
- Full rollback of balance is executed on transaction deletion or modification.

---

## Spec 4: Create REST API Controllers & DTOs

**Scope**: `feat: implement Account and Transaction REST endpoints`

### Description
Implement REST controllers following the Zalando REST API Guidelines and DTOs with validation rules.

### Tasks
- [ ] Create `AccountController.java` at `/v1/accounts`:
  - `GET /v1/accounts` -> Returns `200 OK` with list.
  - `POST /v1/accounts` -> Accepts `CreateAccountRequest`, returns `201 Created` with `Location` header.
  - `PATCH /v1/accounts/{accountId}` -> Accepts `UpdateAccountRequest`, returns `200 OK`.
  - `DELETE /v1/accounts/{accountId}` -> Returns `204 No Content`.
- [ ] Create `TransactionController.java` at `/v1/transactions`:
  - `GET /v1/transactions` -> Returns `200 OK` with list, supports filter query parameters.
  - `POST /v1/transactions` -> Accepts `CreateTransactionRequest`, returns `201 Created` with `Location` header.
  - `PATCH /v1/transactions/{transactionId}` -> Accepts `UpdateTransactionRequest`, returns `200 OK`.
  - `DELETE /v1/transactions/{transactionId}` -> Returns `204 No Content`.
- [ ] Create DTO classes with JSR-380 validation (`@NotBlank`, `@NotNull`, `@DecimalMin`, `@Size`):
  - `AccountDto`, `CreateAccountRequest`, `UpdateAccountRequest`.
  - `TransactionDto`, `CreateTransactionRequest`, `UpdateTransactionRequest`.

### Acceptance Criteria
- Controllers follow kebab-case naming, lack `/api` prefix, and return plural collections.
- Requests trigger spring validations (`@Valid`).

---

## Spec 5: Integration & API Tests

**Scope**: `test: write integration tests for Accounts and Transactions`

### Description
Verify REST endpoints and service-level rules using integration tests with mock users.

### Tasks
- [ ] Create `AccountControllerIntegrationTest.java`:
  - Test CRUD operations, validation failures, and user ownership boundaries.
- [ ] Create `TransactionControllerIntegrationTest.java`:
  - Test transaction CRUD and dynamic filtering.
  - Disable Frankfurter remote API during testing using `@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")` to ensure hermetic offline testing.
  - Validate balance adjustments on accounts (using same and different currencies).
  - Verify conversions from `RON` to `EUR`.

### Acceptance Criteria
- All tests execute and pass successfully during the Maven test phase.
