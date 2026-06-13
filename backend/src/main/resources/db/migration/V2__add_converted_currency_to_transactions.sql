ALTER TABLE transactions ADD COLUMN converted_currency VARCHAR(3);

-- Update existing transactions to fallback to their user's default currency
UPDATE transactions t
SET converted_currency = (SELECT u.default_currency FROM users u WHERE u.id = t.user_id);

-- Set NOT NULL constraint after populating
ALTER TABLE transactions ALTER COLUMN converted_currency SET NOT NULL;
