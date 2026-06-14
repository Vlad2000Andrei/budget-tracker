-- Create Savings Goal Type Enum
CREATE TYPE savings_goal_type AS ENUM ('ONE_OFF', 'MONTHLY');

-- Add goal_type to savings_goals
ALTER TABLE savings_goals ADD COLUMN goal_type savings_goal_type NOT NULL DEFAULT 'ONE_OFF';
