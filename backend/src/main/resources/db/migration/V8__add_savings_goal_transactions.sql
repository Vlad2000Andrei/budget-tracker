-- Enum for savings transaction direction
CREATE TYPE savings_transaction_type AS ENUM ('DEPOSIT', 'WITHDRAWAL');

-- Links a regular SAVINGS transaction to a specific savings goal and the account it moved money from/to
CREATE TABLE savings_goal_transactions (
    id               BIGSERIAL PRIMARY KEY,
    savings_goal_id  BIGINT NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
    transaction_id   BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    account_id       BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    type             savings_transaction_type NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);
