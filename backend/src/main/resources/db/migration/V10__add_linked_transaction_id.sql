-- Add linked_transaction_id to support double-entry transfer transactions
ALTER TABLE transactions ADD COLUMN linked_transaction_id BIGINT REFERENCES transactions(id) ON DELETE SET NULL;
