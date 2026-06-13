ALTER TABLE users ADD COLUMN is_onboarded BOOLEAN NOT NULL DEFAULT FALSE;

-- Mark existing users as onboarded to prevent forcing them into welcome flow
UPDATE users SET is_onboarded = TRUE;
