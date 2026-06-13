# Web Client Design Specification

## Decisions Log

A complete record of every explicit design decision made during the initial design session. Future implementors must not deviate from these without a documented reason.

| # | Area | Decision | Rationale |
|---|---|---|---|
| 1 | Framework | React + Vite, CSS Modules | CSS Modules adds per-component scoping over plain CSS with zero runtime cost; built into Vite with no config |
| 2 | Design system | Material 3 Expressive | Google's latest guidelines; emotional, alive, premium feel |
| 3 | Color mode | Light only for MVP | Reduces scope; dark mode pre-structured via CSS variables for future addition |
| 4 | Seed color | Green/Teal `#2A9D8F` | Conveys money, growth, modern fintech feel; distinct from typical banking blue |
| 5 | Theming | Single CSS variable seed, full palette as CSS custom properties | Retheme = change one variable; no code changes elsewhere |
| 6 | Navigation | Left sidebar (4 items: Dashboard, Goals, Accounts, Settings) | Clean, uncluttered; finance apps benefit from persistent wayfinding |
| 7 | Add Transaction UX | Slide-up bottom sheet modal triggered by persistent FAB | Adding a transaction is the #1 daily action; must be reachable from anywhere in 0 clicks |
| 8 | Transaction happy path | 3 interactions: open → amount → category → save | Minimise friction for an action done multiple times per day |
| 9 | Transaction type default | Expense | Most common transaction type |
| 10 | Exchange rate | Auto-fetched, manual override allowed | Convenience for common case; control for edge cases |
| 11 | Quick-repeat shortcut | Not implemented | Keep it simple for MVP |
| 12 | Offline support | Hard fail with clear error message | Online-only app for MVP; no queuing complexity |
| 13 | Date field | Defaults to today, always visible and editable | Common case is today; past/future entry must not require extra steps |
| 14 | Category picker | 6 recent chips (filtered by type) + flat search with breadcrumbs | Chips cover ~80% of daily use in 0 interactions; flat search eliminates tree-drilling |
| 15 | Dashboard | Doubles as the full transaction log (no separate Transactions page) | Reduces nav; the log IS the primary content |
| 16 | Dashboard summary | Cards: total balance, monthly income vs. expenses, goals progress bars | Instant financial health check on landing |
| 17 | Recurring transactions | Tab on the Dashboard transaction log (`All` \| `Recurring`) | First-class concept, not hidden in Settings; same mental model as transactions |
| 18 | Goals page | Single page, tabbed: `Spending Budgets` \| `Savings Goals` | Both are "planned money behaviour"; grouping reduces nav clutter |
| 19 | Goals on Dashboard | Compact progress card with progress bars and percentages | Quick health check without navigating away |
| 20 | Category manager | Tree view, Settings only | Structural editing needs the hierarchy; transaction entry does not |
| 21 | HTTP client | Axios | Cleaner interceptor support for attaching JWT `Authorization` headers to all requests |
| 22 | JWT storage | Session memory — React context (`AuthContext`) | Most secure for MVP; lost on tab close by design; no XSS risk from localStorage |
| 23 | Routing | React Router v6 (client-side SPA) | Industry standard; `<Outlet>` pattern fits the app shell + protected route model |
| 24 | Google Sign-In integration | GIS SDK via CDN (`accounts.google.com/gsi/client`) | Modern, recommended by Google; no npm dependency required |
| 25 | Phase 1 page scope | Login + Dashboard; Goals / Accounts / Settings as empty stubs | Iterative delivery; stubs preserve the full navigation shell from day one |
| 26 | Dashboard data (Phase 1) | Hardcoded placeholder values; no live API calls | Decouple layout work from API integration; wiring is a follow-up session |
| 27 | Add Transaction modal (Phase 1) | Built now; Save calls real `POST /v1/transactions` | Auth is live, so the write path can be wired immediately without extra cost |
| 28 | Backend dev port | `19092` | Non-standard port chosen by the backend dev server |
| 29 | Vite dev proxy | `/v1/**` proxied to `http://localhost:19092` | Eliminates CORS issues in development; frontend code calls `/v1/...` with no origin in the path |

---



React + Vite. **CSS Modules** for component-level style scoping. Global design tokens (M3 tonal palette, typography, shape, motion) live in a single `global.css` as CSS custom properties on `:root`. Component styles are co-located as `Component.module.css` files and imported directly into their component. No UI component library unless explicitly decided later.

---

## Design System — Material 3 Expressive

Follow Google's **Material 3 Expressive** guidelines (m3.material.io). The philosophy is *emotional + alive*: bold use of color, shape, and motion to create an interface that feels responsive and premium, not just functional.

### Color

The palette is derived from a **single seed color** using the M3 tonal system. All colors are defined as CSS custom properties on `:root`, making the theme swappable by changing one variable.

```css
:root {
  /* ── Seed (change this to retheme the entire app) ── */
  --md-seed: #2A9D8F; /* Green/Teal */

  /* ── Tonal Palette (generated from seed) ── */
  --md-primary:            #1A7A6E;
  --md-on-primary:         #FFFFFF;
  --md-primary-container:  #A8F0E6;
  --md-on-primary-container: #00201C;

  --md-secondary:          #4A635F;
  --md-on-secondary:       #FFFFFF;
  --md-secondary-container: #CCE8E3;
  --md-on-secondary-container: #051F1C;

  --md-tertiary:           #456179;
  --md-on-tertiary:        #FFFFFF;
  --md-tertiary-container: #CCE5FF;
  --md-on-tertiary-container: #001E31;

  --md-error:              #BA1A1A;
  --md-on-error:           #FFFFFF;
  --md-error-container:    #FFDAD6;
  --md-on-error-container: #410002;

  --md-surface:            #F4FBF9;
  --md-on-surface:         #161D1C;
  --md-surface-variant:    #DAE5E2;
  --md-on-surface-variant: #3F4947;
  --md-outline:            #6F7978;
  --md-outline-variant:    #BEC9C7;

  --md-background:         #F4FBF9;
  --md-on-background:      #161D1C;
}
```

> **To retheme**: Update `--md-seed` and regenerate the tonal palette using the [Material Theme Builder](https://m3.material.io/theme-builder). Paste the new tokens into `:root`. No other code changes needed.

Light mode only for MVP. Dark mode tokens can be added under a `[data-theme="dark"]` selector later without restructuring.

---

### Typography

Use **Roboto Flex** (Google Fonts variable font). Apply the M3 role-based type scale via CSS variables:

```css
:root {
  --md-font-family: 'Roboto Flex', sans-serif;

  /* Display */
  --md-display-large:  57px / 64px;
  --md-display-medium: 45px / 52px;
  --md-display-small:  36px / 44px;

  /* Headline */
  --md-headline-large:  32px / 40px;
  --md-headline-medium: 28px / 36px;
  --md-headline-small:  24px / 32px;

  /* Title */
  --md-title-large:  22px / 28px;
  --md-title-medium: 16px / 24px; /* weight 500 */
  --md-title-small:  14px / 20px; /* weight 500 */

  /* Body */
  --md-body-large:  16px / 24px;
  --md-body-medium: 14px / 20px;
  --md-body-small:  12px / 16px;

  /* Label */
  --md-label-large:  14px / 20px; /* weight 500 */
  --md-label-medium: 12px / 16px; /* weight 500 */
  --md-label-small:  11px / 16px; /* weight 500 */
}
```

---

### Shape

M3 Expressive uses **generous rounding**. Larger radius = more prominent element.

| Element | Shape |
|---|---|
| FAB | Full pill (9999px) |
| Buttons | Full pill (9999px) |
| Cards | Large rounded (16px) |
| Chips | Full pill (9999px) |
| Bottom sheet | Top corners 28px |
| Input fields | Extra-small (4px) |
| Dialog / Modal | Extra-large (28px) |

---

### Motion

Use **spring-based / physics easing**, not linear or simple ease-in-out.

- M3 standard easing: `cubic-bezier(0.2, 0, 0, 1.0)` — for elements entering the screen.
- M3 decelerate easing: `cubic-bezier(0, 0, 0, 1)` — for elements settling.
- Duration: 200–400ms. Prefer shorter (200ms) for small UI feedback, longer (350–400ms) for modal/sheet transitions.
- Elements must **respond**, not just animate. State changes (hover, press, focus) use the M3 ripple/state-layer system with `--md-primary` at low opacity overlay.

---

### Component Conventions

| UI Pattern | M3 Component |
|---|---|
| Left sidebar navigation | Navigation Drawer |
| Add Transaction entry point | Extended FAB (bottom-right) |
| Add Transaction form | Bottom Sheet (modal) |
| Category quick-select | Filter Chips |
| Account / Goal display | Outlined Cards |
| Summary stats | Filled Cards |
| Inline actions | Icon Buttons |
| Primary actions (Save) | Filled Button |
| Secondary actions (Cancel) | Text Button |

---

## Layout

A persistent **left sidebar** with collapsible behaviour on smaller screens.

```
┌─────────────────────────────────────────┐
│  💰 Budget Tracker        [User avatar] │
├──────────┬──────────────────────────────┤
│          │                              │
│ Dashboard│        Main Content          │
│ Goals    │                              │
│ Accounts │                              │
│          │                              │
│ ──────── │                              │
│ Settings │                              │
└──────────┴──────────────────────────────┘
                                    [＋ FAB]
```

A **floating action button (FAB)** is pinned to the bottom-right corner on every page. Tapping it opens the Add Transaction modal.

---

## Pages

### 1. Login
Google Sign-In button only. On success, redirects to Dashboard. No other content.

---

### 2. Dashboard
The primary page users land on. Two sections:

**Top — Summary Cards (horizontal row):**
- Total balance across all accounts.
- Current month: total income vs. total expenses.
- Goals progress card: compact progress bars per active budget and savings goal, showing % spent or saved. Serves as a quick financial health check.

**Bottom — Transaction Log:**
Two tabs:
- `All` — full chronological list of all transactions, filterable by date range, account, category, and type (INCOME / EXPENSE / SAVINGS).
- `Recurring` — lists all active recurrence rule templates. Users can view, edit frequency/end date, or delete a recurrence from here. Recurring transactions in the `All` tab are tagged with a 🔁 icon.

*Rationale: Dashboard doubles as the transaction log to avoid redundant navigation. Recurring templates live in the log tab because they are conceptually the same as transactions — just automated.*

---

### 3. Goals
Two tabs:

- `Spending Budgets` — list of active budgets per expense category. Shows limit, amount spent, % used, and date range. CRUD actions inline.
- `Savings Goals` — list of savings goals per savings category. Shows target amount, current amount, % achieved, and target date. CRUD actions inline.

*Rationale: Budgets and savings goals are both "planned money behaviour" — grouping them reduces nav clutter without hiding either.*

---

### 4. Accounts
List of account cards. Each card shows: name, type (CHECKING / SAVINGS), currency, and current balance. Full CRUD (add, edit, delete).

---

### 5. Settings
- **Default currency**: user-configurable.
- **Category manager**: tree view of all categories (INCOME / EXPENSE / SAVINGS, parent → child). Full CRUD. This is the only place the hierarchy is navigated as a tree.
- **Profile**: display email, sign out.

---

## Add Transaction Modal

Triggered by the FAB from any page. Implemented as a **slide-up modal / bottom sheet** — never a separate page.

### Form Fields (in order)

| Field | Behaviour |
|---|---|
| **Type** | Toggle: `Expense` / `Income` / `Savings`. Defaults to `Expense`. Filters category list. |
| **Amount** | Auto-focused on open. Numeric input. |
| **Currency** | Defaults to user's default currency. Changing it reveals the exchange rate field. |
| **Exchange Rate** | Hidden unless currency ≠ default. Auto-fetched from an exchange rate API. User can manually override the fetched value. |
| **Category** | See category picker UX below. |
| **Date** | Defaults to today. Always visible and editable via date picker. |
| **Account** | Optional. Remembers the last used account. |
| **Notes** | Optional. Collapsed by default, expandable. |
| **Make Recurring** | Optional toggle, collapsed by default. Reveals: frequency (Daily / Weekly / Monthly / Yearly), interval (e.g. every 2 weeks), and end date (optional). |

### Happy Path (most common case)
1. Tap FAB.
2. Type amount.
3. Pick category (chip or search).
4. Tap Save.

Three interactions. Everything else is optional and out of the way.

### Error Handling
- If the network is unavailable, show a clear inline error and ask the user to retry. No offline queuing for MVP.

---

## Category Picker UX

Used inside the Add Transaction modal. Designed to minimise friction given potentially large category lists.

**Two layers:**

1. **Chips (zero effort):** Show the 6 most recently used categories, pre-filtered by the selected transaction type. Covers the majority of daily use.

2. **Flat search (fallback):** A search bar below the chips that queries all categories simultaneously. Results are displayed flat (no tree navigation) but include breadcrumb context:
   ```
   🍕 Food › Restaurants
   🛒 Food › Groceries
   🚌 Transport › Public Transit
   ```
   Type 2–3 characters, tap result.

*Rationale: Tree navigation requires at minimum two interactions per leaf category. Flat search reduces this to one. The tree view is reserved for the Category Manager in Settings where structural editing is needed.*

---

## Recurring Transactions

- Created via the "Make Recurring" toggle in the Add Transaction modal.
- All instances generated by a recurrence rule are tagged 🔁 in the transaction log.
- The `Recurring` tab on the Dashboard shows all active recurrence templates for management (edit / delete).
- Recurring entries are not hidden in Settings — they are a first-class concept in the transaction log.
