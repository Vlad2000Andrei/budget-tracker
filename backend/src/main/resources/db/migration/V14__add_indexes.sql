-- Accounts
CREATE INDEX idx_accounts_user_id ON accounts(user_id);

-- Categories
CREATE INDEX idx_categories_user_id ON categories(user_id);
CREATE INDEX idx_categories_parent_id ON categories(parent_id);

-- Transactions
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_category_id ON transactions(category_id);
CREATE INDEX idx_transactions_date ON transactions(date DESC);
CREATE INDEX idx_transactions_linked_tx_id ON transactions(linked_transaction_id);
CREATE INDEX idx_transactions_recurrence_rule_id ON transactions(recurrence_rule_id);

-- Budgets & Savings Goals
CREATE INDEX idx_budgets_user_id ON budgets(user_id);
CREATE INDEX idx_budgets_category_id ON budgets(category_id);
CREATE INDEX idx_savings_goals_user_id ON savings_goals(user_id);
CREATE INDEX idx_savings_goals_category_id ON savings_goals(category_id);

-- Savings Transactions
CREATE INDEX idx_savings_transactions_tx_id ON savings_transactions(transaction_id);
CREATE INDEX idx_savings_transactions_from_acc ON savings_transactions(from_account_id);
CREATE INDEX idx_savings_transactions_to_acc ON savings_transactions(to_account_id);
