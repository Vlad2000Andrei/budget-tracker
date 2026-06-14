ALTER TABLE budgets DROP CONSTRAINT chk_dates;
ALTER TABLE budgets ALTER COLUMN end_date DROP NOT NULL;
ALTER TABLE budgets ADD CONSTRAINT chk_dates CHECK (end_date IS NULL OR start_date <= end_date);
