-- Rename the single account reference to from_account_id (source of funds)
ALTER TABLE savings_goal_transactions RENAME COLUMN account_id TO from_account_id;

-- Add to_account_id (destination of funds) — required for every cash move going forward
ALTER TABLE savings_goal_transactions ADD COLUMN to_account_id BIGINT REFERENCES accounts(id) ON DELETE RESTRICT;
