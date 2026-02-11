# Account Manager Service

Account management service for multi-tenant support in the Pipestream platform.

## Overview

The Account Manager service provides CRUD operations for tenant accounts. Accounts are the top-level organizational unit in the system - each account can own multiple drives (storage buckets) and connectors.

### Key Features

- **Account CRUD** - Create, read, and inactivate (soft delete) accounts
- **Idempotent Operations** - Safe to retry creates and inactivates
- **Multi-Tenant Foundation** - Provides the account abstraction for isolating tenant data
- **gRPC API** - Efficient binary protocol with Mutiny reactive support
- **Event Publishing** - Publishes account lifecycle events to Kafka with Protobuf + Apicurio Registry
- **PostgreSQL Storage** - Persistent account data with Flyway migrations
- **Soft Deletes** - Accounts are inactivated (active=false) rather than deleted

## Architecture

### Layers

1. **gRPC Service** (`AccountServiceImpl`) - Thin gRPC wrapper, validates requests, maps proto ↔ entity
2. **Repository** (`AccountRepository`) - Business logic and transactional data access
3. **Entity** (`Account`) - JPA/Panache entity mapped to `accounts` table
4. **Event Publisher** (`AccountEventPublisher`) - Publishes account events to Kafka
5. **Database** - PostgreSQL with Flyway migrations

### Data Flow

```
gRPC Client → AccountServiceImpl → AccountRepository → Account Entity → PostgreSQL
                                  → AccountEventPublisher → Kafka (account-events topic)
```

All gRPC operations run on worker threads (`.runSubscriptionOn(Infrastructure.getDefaultWorkerPool())`) to avoid blocking the event loop.

## API

Proto definitions are fetched from the [pipestream-protos](https://github.com/ai-pipestream/pipestream-protos) repository at build time via the proto-toolchain plugin.

### CreateAccount

Creates a new account or returns existing if the account_id already exists.

**Request:**
```protobuf
message CreateAccountRequest {
  string account_id = 1;    // Unique identifier (required)
  string name = 2;          // Display name (required)
  string description = 3;   // Optional description
}
```

**Response:**
```protobuf
message CreateAccountResponse {
  Account account = 1;      // The created or existing account
  bool created = 2;         // true if created, false if already existed
}
```

**Behavior:**
- Idempotent - calling with same account_id returns existing account
- Validates name is not empty
- Sets active=true and current timestamps

**Example:**
```bash
grpcurl -plaintext -d '{"account_id":"acme-corp","name":"Acme Corporation","description":"Test account"}' \
  localhost:38105 io.pipeline.repository.account.AccountService/CreateAccount
```

### GetAccount

Retrieves an account by ID. Returns both active and inactive accounts.

**Request:**
```protobuf
message GetAccountRequest {
  string account_id = 1;    // Account identifier
}
```

**Response:**
```protobuf
message Account {
  string account_id = 1;
  string name = 2;
  string description = 3;
  bool active = 4;
  google.protobuf.Timestamp created_at = 5;
  google.protobuf.Timestamp updated_at = 6;
}
```

**Errors:**
- `NOT_FOUND` - Account doesn't exist

**Example:**
```bash
grpcurl -plaintext -d '{"account_id":"acme-corp"}' \
  localhost:38105 io.pipeline.repository.account.AccountService/GetAccount
```

### InactivateAccount

Soft deletes an account by setting active=false.

**Request:**
```protobuf
message InactivateAccountRequest {
  string account_id = 1;    // Account identifier
  string reason = 2;        // Reason for inactivation (logged)
}
```

**Response:**
```protobuf
message InactivateAccountResponse {
  bool success = 1;
  string message = 2;
  int32 drives_affected = 3;  // Always 0 in MVP (drive inactivation deferred)
}
```

**Behavior:**
- Idempotent - inactivating an already inactive account succeeds
- Returns success=false if account not found (not a gRPC error)
- Drive inactivation handled separately by repo-service

**Example:**
```bash
grpcurl -plaintext -d '{"account_id":"acme-corp","reason":"Account closure"}' \
  localhost:38105 io.pipeline.repository.account.AccountService/InactivateAccount
```

## Database Schema

**Table:** `accounts`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| account_id | VARCHAR(255) | PRIMARY KEY | Unique account identifier |
| name | VARCHAR(255) | NOT NULL | Account display name |
| description | TEXT | NULL | Optional description |
| active | BOOLEAN | NOT NULL, DEFAULT TRUE | Account status |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE | Last update timestamp |

**Indexes:**
- `idx_accounts_active` on (active)
- `idx_accounts_name` on (name)

**Migrations:** `src/main/resources/db/migration/`

## Configuration

### Server Ports

- **Production:** HTTP/gRPC on port 38105
- **Dev:** HTTP/gRPC on port 38105 with `/account-manager` root path
- **Test:** Random port (quarkus.http.test-port=0)

### Database

- **Dev:** PostgreSQL via Docker Compose Dev Services on port 5432
- **Test:** PostgreSQL via Quarkus DevServices (auto-provisioned)
- **Prod:** Configured via environment variables

### Service Registration

The service auto-registers with the Platform Registration Service on startup (disabled in tests).

## Running the Service

### Development Mode

```bash
./gradlew quarkusDev
```

The service will:
- Start on http://localhost:38105/account-manager
- Connect to PostgreSQL via Dev Services (Docker Compose)
- Connect to Kafka and Apicurio Registry via Dev Services
- Auto-register with Platform Registration Service
- Enable gRPC reflection for testing

### Testing

Run all tests:
```bash
./gradlew test
```

Test suites:
- **AccountRepositoryTest** - Repository CRUD tests against PostgreSQL
- **AccountServiceTest** / **AccountServiceGrpcTest** - End-to-end gRPC tests
- **AccountEventPublisherTest** - Kafka event publishing with Protobuf serialization
- **AccountResourceTest** - HTTP endpoint tests

### Testing with grpcurl

List services:
```bash
grpcurl -plaintext localhost:38105 list
```

Call methods:
```bash
# Create account
grpcurl -plaintext -d '{"account_id":"test-123","name":"Test Account"}' \
  localhost:38105 io.pipeline.repository.account.AccountService/CreateAccount

# Get account
grpcurl -plaintext -d '{"account_id":"test-123"}' \
  localhost:38105 io.pipeline.repository.account.AccountService/GetAccount

# Inactivate account
grpcurl -plaintext -d '{"account_id":"test-123","reason":"Testing"}' \
  localhost:38105 io.pipeline.repository.account.AccountService/InactivateAccount
```

## Implementation Details

### Timestamp Mapping

The service uses `google.protobuf.Timestamp` in the gRPC API and `OffsetDateTime` in the entity/database.

**Entity → Proto:**
```java
Timestamp.newBuilder()
    .setSeconds(offsetDateTime.toEpochSecond())
    .setNanos(offsetDateTime.getNano())
    .build()
```

**Proto → Entity:**
```java
OffsetDateTime.ofInstant(
    Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()),
    ZoneOffset.UTC
)
```

### Reactive Pattern

All gRPC methods return `Uni<T>` and execute blocking database operations on worker threads:

```java
return Uni.createFrom().item(() -> {
    // Blocking database operations here
    return result;
}).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
```

This prevents blocking the Vert.x event loop.

### Idempotency

Both `createAccount` and `inactivateAccount` are idempotent:
- Creating an existing account returns the existing record with `created=false`
- Inactivating an already inactive account returns `success=true`


## Dependencies

- **Quarkus gRPC** - gRPC server and client support
- **Hibernate ORM Panache** - Simplified JPA with active record pattern
- **PostgreSQL** - Database (via quarkus-jdbc-postgresql)
- **Flyway** - Database migrations
- **SmallRye Reactive Messaging** - Kafka event publishing
- **Apicurio Registry** - Protobuf schema management (via pipestream extension)
- **Pipestream Platform** - BOM, DevServices, server extension, proto toolchain

## Related Services

- **Platform Registration Service** - Service registry and discovery
- **Repository Service** - Manages drives owned by accounts
- **Connector Admin Service** - Manages connectors linked to accounts
