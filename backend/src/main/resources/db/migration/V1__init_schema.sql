-- Enums
CREATE TYPE account_type AS ENUM ('CHECKING', 'SAVINGS', 'INVESTMENT');
CREATE TYPE category_type AS ENUM ('INCOME', 'EXPENSE', 'SAVINGS');
CREATE TYPE recurrence_frequency AS ENUM ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY');
CREATE TYPE rollover_rule_type AS ENUM ('NONE', 'SURPLUS', 'DEFICIT', 'ALL');

-- Users Table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    google_sub VARCHAR(255) NOT NULL UNIQUE,
    default_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Accounts Table
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    type account_type NOT NULL,
    balance DECIMAL(15, 4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Categories Table
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE, -- NULL means default system-wide category
    parent_id BIGINT REFERENCES categories(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(50),
    color VARCHAR(7), -- Hex code, e.g., '#FF5733'
    type category_type NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Recurrence Rules Table
CREATE TABLE recurrence_rules (
    id BIGSERIAL PRIMARY KEY,
    frequency recurrence_frequency NOT NULL,
    "interval" INT NOT NULL DEFAULT 1,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Transactions Table
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    recurrence_rule_id BIGINT REFERENCES recurrence_rules(id) ON DELETE SET NULL,
    amount DECIMAL(15, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    converted_amount DECIMAL(15, 4) NOT NULL,
    exchange_rate DECIMAL(15, 6) NOT NULL DEFAULT 1.000000,
    type category_type NOT NULL,
    notes TEXT,
    date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Budgets Table (Spending Goals)
CREATE TABLE budgets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    amount_limit DECIMAL(15, 4) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    rollover_rule rollover_rule_type NOT NULL DEFAULT 'NONE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dates CHECK (start_date <= end_date)
);

-- Savings Goals Table
CREATE TABLE savings_goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    target_amount DECIMAL(15, 4) NOT NULL,
    current_amount DECIMAL(15, 4) NOT NULL DEFAULT 0.0000,
    target_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
