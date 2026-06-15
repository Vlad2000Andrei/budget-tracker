# Statistics & Overviews Page: Requirements & Design Decisions

This document details the functional, non-functional, and user interface specifications for the new Statistics & Overviews page.

---

## 1. Functional Requirements

### A. Spending, Income & Savings Overviews
* **Category Breakdown**: View total expenses, income, and savings contributions grouped by category.
* **Hierarchy Support**: Correctly aggregate child category amounts into parent categories or allow expanding to see leaf breakdowns.

### B. Budgets & Savings Goals Tracking
* **Progress Grid**: Grid of detailed progress cards showing:
  * Limit vs. Spent (for Budgets).
  * Target vs. Current saved (for Savings Goals).
  * Calculated remaining amounts or required contributions.
  * Visual progress bars (color-coded red for exceeded budgets, green for completed goals).

### C. Account Balance Evolution
* **Cumulative Balances**: Reconstruct historical account balances backwards from today using transaction history.
* **Double-Entry Support**: Correctly adjust balance calculation for `MOVE` (transfer) transactions, adding to source account and subtracting from destination account when walking history backwards.

### D. Time Period Controls
* **Day-Granular Selection**: Filter statistics using precise start and end dates.
* **Standard Presets**:
  * Single Day
  * Last 30 Days
  * Month-to-Date (MTD)
  * Year
  * Year-to-Date (YTD)
  * Custom Date Range
* **Initial State**: Defaults to standard "Last 30 Days" view.

### E. Comparative Analytics
* **Temporal Comparisons**: Support comparative metrics against:
  * Last week (exact same relative period shifted back by 7 days).
  * Last month (exact same relative period shifted back by 1 month).
* **Variance Display**: Show both absolute variance (e.g., `-$12.50`) and percentage variance (e.g., `-3.4%`).
* **Semantic Coloring**: Use green/red color coding based on the nature of the metric (e.g., green for higher income, lower expenses, higher savings).

---

## 2. Filters & Exclusions

* **Multi-Account Filtering**: Filter all charts and metrics by selecting one, multiple, or all accounts.
* **Category Tree Filter**: Select/deselect categories via a hierarchical checkbox tree (toggling parent toggles all children).
* **Exclusions**:
  * **Recurrent Set Exclusions**: Exclude a recurrent set of transactions (all transactions belonging to a specific recurrence rule).
  * **Ad-hoc Transaction Exclusions**: Exclude random individual transactions at will. Triggering this opens a modal listing all transactions in the relevant time range(s), sorted in descending order of value, with toggle switches.
* **State Persistence**: Persist selected filters and exclusions in the browser (`localStorage`) under the key `budget_tracker_stats_prefs`.


---

## 3. UI & Layout Specifications

### A. Navigation
* Add navigation link `/stats` inside `Sidebar.jsx` using a Material 3 style chart SVG icon.
* Add protected route `/stats` mapping to `StatsPage` in `App.jsx`.

### B. Interface Layout
* **Split Layout**:
  * **Left Pane**: Collapsible sidebar containing checkboxes for accounts, categories, and transaction exclusions.
  * **Right Pane**: Primary analytics section showing:
    * **Header**: Title and period picker.
    * **Category Cashflow Breakdown Card** (at the top, before charts): Grouped by type (EXPENSES, then INCOME, then SAVINGS). Shows each category/subcategory with its actual value, percentage of total for its type, and a visual horizontal fill bar.
    * **Main Chart Area**: Interactive chart with toggles for Pie, Bar, and Line views.
    * **KPI Cards**: Net Cash Flow (Income - Expense - Savings) and Savings Rate cards.
    * **Goals Progress Grid**: List of active budgets and savings goals progress.
* **Chart Toggles**: Segmented buttons to switch between **Pie**, **Bar**, and **Line** chart styles.

* **KPI Summary Cards**:
  * **Net Cash Flow Card**: Displays Income - Expense - Savings.
  * **Savings Rate Card**: Displays Savings as a percentage of Income.

---

## 4. Technical Constraints

* **Client-Side Processing**: The backend database schema remains unchanged. The frontend performs all data slicing, category trees, calculations, comparisons, and graph generation.
* **Exchange Rate Conversion**: Convert all amounts to the user's default currency (specified in their profile) using the stored transaction values (`convertedAmount`) and exchange rates.
* **Smart Data Fetching**: On page mount, fetch transactions for the current month and the previous month. Fetch older transactions from `/v1/transactions` on demand when the user selects a larger date range.
* **Visualization Library**: Recharts library used for responsive and interactive chart rendering.
