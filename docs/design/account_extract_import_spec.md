# Design & Functional Specification: Bank Account Extract Import

This specification details the functional requirements, technical design, architectural patterns, API contracts, and user experience flows for importing account extracts from bank apps into the **Budget Tracker** application.

---

## 1. Functional Requirements

The import system must satisfy the following requirements:

1. **File Format Support**: The system must support importing extracts in `CSV`, `JSON`, and Excel (`.xlsx` / `.xls`) formats.
2. **Column Identification**: The system must inspect and identify the columns/headers present in the imported data.
3. **Data Mapping**:
   - The user must select which column maps to the transaction **date** and which maps to the transaction **amount** (required).
   - The user can optionally map a column to the transaction **type** and define custom indicators for Income and Expense rows.
   - The user can optionally map the transaction **notes** to a column.
   - For **currency**, the user can either: default to the target account's currency, map to a column in the file, or select a fixed currency code for all imported rows.
   - The user must select a target **account** (from their existing accounts) to map these transactions to.
4. **List View Preview**: Once the file is parsed and columns mapped, the user must be presented with a preview list view displaying all extracted transactions.
5. **Duplicate Alerting**: If a transaction with the same date and amount already exists in the backend for that account and user, it must be marked in the list view as a potential duplicate.
6. **Override Duplicate Flags**: The user must be able to mark any flagged potential duplicate as "Not Duplicate" so that it will be imported.
7. **Exclude Rows**: The user must be able to remove/exclude transactions from the list view so they are skipped during the import.
8. **Inline Categorization**: For each transaction in the list view, the user must specify a category. This must be editable directly inline within the list view without opening a separate dialog.
9. **Transaction Details & Recurrence (Optional)**: For each transaction in the list view, the user can choose to add/edit a note or make it recursive (configure recurring schedule or link to an existing schedule). These advanced details can be hidden in a separate detail dialog.
10. **Immediate File Cleanup**: The uploaded file must be cleared/deleted immediately once parsing/importing is complete to protect user privacy.
11. **Accidental Navigation Warning**: Leaving the page or closing the import wizard while there are uncommitted mapped transactions must trigger a confirmation prompt to prevent loss of unsaved mapping work.

---

## 2. Architectural Overview

The import system leverages client-side (frontend) parsing to minimize server overhead, avoid heavy Java dependencies on the backend, and ensure maximum data security.

```mermaid
sequenceDiagram
    actor User
    participant Frontend as Frontend Browser (Vite/React)
    participant Backend as Backend Service (Spring Boot)
    database DB as Database (H2/PostgreSQL)

    User->>Frontend: Uploads CSV/JSON/Excel File
    Note over Frontend: Parses file locally using PapaParse & SheetJS
    Frontend->>Backend: POST /v1/imports/detect-duplicates (JSON transaction data)
    Backend->>DB: Query transactions on same date & amount
    DB-->>Backend: Duplicate records
    Backend-->>Frontend: Returns duplicate flags & existing transaction mappings
    Note over Frontend: User reviews list, fixes duplicates, maps categories/transfers
    User->>Frontend: Clicks "Confirm Import"
    Frontend->>Backend: POST /v1/transactions/bulk (JSON finalized transaction list)
    Backend->>DB: Save all transactions, recurrence rules, and update account balances
    Backend-->>Frontend: 201 Created (with Location header)
```

### 2.1 Key Design Decisions
1. **Frontend Parsing**: File parsing (for CSV, JSON, and Excel) is performed entirely in the browser using JavaScript libraries (`PapaParse` for CSV/JSON, `SheetJS` for Excel). The server never receives raw files, satisfying the security and immediate deletion requirements natively.
2. **Duplicate Detection Rule**: Checked via `/v1/imports/detect-duplicates` by matching:
   - Same User ID (`userId`)
   - Same Account ID (`accountId`)
   - Same Date (`date.toLocalDate() == dbDate.toLocalDate()`)
   - Same Absolute Amount (`amount.abs() == dbAmount.abs()`)
3. **Transaction Type Deduction**: The transaction type is deduced using one of two methods:
   - **Transaction Type Column (Optional)**: If a specific column is mapped to indicate transaction types, the system checks cell values against user-defined indicators (e.g., matching "credit"/"debit" values) or falls back to common banking terms (e.g., "cr"/"dr", "in"/"out", "deposit"/"withdrawal", "income"/"expense").
   - **Sign-Based Deduction (Fallback)**: If no type column is selected, the system defaults to checking the amount's sign: negative values default to `EXPENSE`, and positive values default to `INCOME`.
   - In the Step 3 review, these deduced types can still be manually toggled/changed to standard `Expense`, `Income`, `Transfer`, or `Savings` transactions.
4. **Manual Categorization**: Categories start as unassigned, requiring the user to manually select one for each transaction inline.
5. **Recurrence Rules**: The recurrence dialog supports:
   - Defining a new recurring rule (triggering the backend engine to schedule future instances).
   - Linking the imported transaction to an existing active recurrence rule.
   - Remaining non-recurring (default).

---

## 3. API & Data Contracts

All endpoints follow the **Zalando REST API Guidelines** configured for the project (no `/api` prefix, kebab-case paths, camelCase fields, proper status codes).

### 3.1 Duplicate Detection
* **Path**: `POST /v1/imports/detect-duplicates`
* **Request Body** (`DuplicateCheckRequest`):
```json
{
  "accountId": 12,
  "transactions": [
    {
      "date": "2026-06-23T00:00:00",
      "amount": 25.50
    }
  ]
}
```
* **Response Body** (`DuplicateCheckResponse`):
```json
{
  "results": [
    {
      "isPotentialDuplicate": true,
      "existingTransactionId": 456
    }
  ]
}
```

### 3.2 Bulk Creation
* **Path**: `POST /v1/transactions/bulk`
* **Request Body** (`BulkTransactionRequest`):
```json
{
  "transactions": [
    {
      "categoryId": 5,
      "accountId": 12,
      "amount": 25.50,
      "currency": "USD",
      "type": "EXPENSE",
      "notes": "Grocery shopping",
      "date": "2026-06-23T00:00:00",
      "recurrenceRule": {
        "frequency": "MONTHLY",
        "interval": 1,
        "startDate": "2026-06-23",
        "endDate": null
      },
      "existingRecurrenceRuleId": null
    },
    {
      "categoryId": null,
      "accountId": 12,
      "amount": 100.00,
      "currency": "USD",
      "type": "TRANSFER",
      "notes": "Move checking to savings",
      "date": "2026-06-23T00:00:00",
      "transferToAccountId": 15
    },
    {
      "categoryId": 9,
      "accountId": 12,
      "amount": 50.00,
      "currency": "USD",
      "type": "SAVINGS",
      "notes": "Emergency Fund deposit",
      "date": "2026-06-23T00:00:00",
      "savingsType": "DEPOSIT",
      "savingsToAccountId": 18
    }
  ]
}
```
* **Response**: `201 Created`
  - Returns the list of created `TransactionDto` elements.
  - Includes a `Location` header pointing to `/v1/transactions`.

---

## 4. Detailed Component Modifications

### 4.1 Backend Components (Java / Spring Boot)

* **`ImportController.java`**
  - Handles `/v1/imports/detect-duplicates`.
  - Injected with `TransactionRepository` / JOOQ queries to perform fast day-and-amount-based matching checks.

* **`TransactionController.java`**
  - Handles `POST /v1/transactions/bulk`.
  - Validates request payloads and forwards them to the service layer.

* **`TransactionService.java`**
  - Implements transactional bulk transaction inserts.
  - Validates ownership of all accounts and categories.
  - Distinguishes standard transactions, transfers (calling the transfer creation helpers to build linked debit/credit entries), and savings transactions (calling savings goal/transaction helper flows).
  - Updates target account balances in a batch context to optimize performance.

---

### 4.2 Frontend Components (React / Vite)

* **`ImportPage.jsx` & `ImportPage.module.css`**
  - Formed as a multi-step stepper/wizard interface aligned with the Material 3 design system.
  - **Step 1: File Selection & Target Account Selection**
    - Drag-and-drop file inputs (filtering `.csv`, `.json`, `.xlsx`).
    - Account selector.
  - **Step 2: Column Mapping Selector**
    - Lets users specify mapping for Date, Amount, Notes, Transaction Type (with custom indicators), and Currency.
    - Dynamically generates preview grid showing parsed columns.
  - **Step 3: Verification & Review Grid**
    - Table displaying final parsed rows.
    - Inline category selector on each row (filtered by deduced type).
    - Special inline selectors showing up if a virtual category like `Transfer` or `Savings` is chosen (e.g. source/destination account selectors).
    - Exclusion buttons to delete rows.
    - Warning alerts for duplicates with a dismiss option.
    - Modal toggles to customize notes and recurrence schedules.
  - **Page Leave Blocker**: Uses React Router navigation guards and `window.beforeunload` browser hooks to prevent unsaved state loss.

* **`importParser.js`**
  - Configures `PapaParse` and `xlsx` wrappers to handle reading files locally in the browser engine.
