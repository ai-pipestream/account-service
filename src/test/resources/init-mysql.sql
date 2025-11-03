-- Infrastructure-only init script
-- Creates additional databases - schema is managed by application (Flyway/Hibernate)
-- Apicurio auto-creates its own schema, no manual tables needed

-- Create Apicurio database (schema auto-created by Apicurio Registry)
CREATE DATABASE IF NOT EXISTS apicurio_registry;

-- Grant privileges to the pipeline user
GRANT ALL PRIVILEGES ON apicurio_registry.* TO 'pipeline'@'%';

-- Flush privileges to ensure they take effect
FLUSH PRIVILEGES;