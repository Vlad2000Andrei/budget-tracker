-- Make category_id optional in budgets table to allow overall budgets
ALTER TABLE budgets ALTER COLUMN category_id DROP NOT NULL;
