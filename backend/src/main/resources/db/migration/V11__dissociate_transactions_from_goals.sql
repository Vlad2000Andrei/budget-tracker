CREATE TABLE savings_transactions (
    id               BIGSERIAL PRIMARY KEY,
    transaction_id   BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    from_account_id  BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    to_account_id    BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    type             savings_transaction_type NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Copy any existing data
INSERT INTO savings_transactions (id, transaction_id, from_account_id, to_account_id, type, created_at)
SELECT id, transaction_id, from_account_id, to_account_id, type, created_at FROM savings_goal_transactions;

-- Drop the old table
DROP TABLE savings_goal_transactions;
