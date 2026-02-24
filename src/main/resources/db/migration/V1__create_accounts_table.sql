-- Create accounts table for multi-tenant support
-- Harmonized with Hibernate's default mapping for Account entity
CREATE TABLE accounts (
    account_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id)
);

-- Create index for active accounts
CREATE INDEX idx_accounts_active ON accounts(active);

-- Create index for account name lookup
CREATE INDEX idx_accounts_name ON accounts(name);
