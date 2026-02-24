-- Repair migration: Ensure accounts table exists with correct structure
-- Harmonized with Hibernate's default mapping for Account entity

-- Create table if it doesn't exist (PostgreSQL syntax)
CREATE TABLE IF NOT EXISTS accounts (
    account_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id)
);

-- Create indexes if they don't exist (PostgreSQL supports IF NOT EXISTS for indexes)
CREATE INDEX IF NOT EXISTS idx_accounts_active ON accounts(active);
CREATE INDEX IF NOT EXISTS idx_accounts_name ON accounts(name);
