-- Repair migration: Ensure accounts table exists with correct structure
-- This migration handles the case where V1 was marked as complete but the table wasn't created

-- Create table if it doesn't exist (PostgreSQL syntax)
CREATE TABLE IF NOT EXISTS accounts (
    account_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes if they don't exist (PostgreSQL supports IF NOT EXISTS for indexes)
CREATE INDEX IF NOT EXISTS idx_accounts_active ON accounts(active);
CREATE INDEX IF NOT EXISTS idx_accounts_name ON accounts(name);
