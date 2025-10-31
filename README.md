# Account Manager Service

Account management service for multi-tenant support in the Pipeline platform.

## Overview

The Account Manager service provides CRUD operations for tenant accounts. Accounts are the top-level organizational unit in the system - each account can own multiple drives (storage buckets) and connectors.

### Key Features

- **Account CRUD** - Create, read, and inactivate (soft delete) accounts
- **Idempotent Operations** - Safe to retry creates and inactivates
- **Multi-Tenant Foundation** - Provides the account abstraction for isolating tenant data
- **gRPC API** - Efficient binary protocol with Mutiny reactive support
- **MySQL Storage** - Persistent account data with TIMESTAMP columns
- **Soft Deletes** - Accounts are inactivated (active=false) rather than deleted

## Architecture

### Layers

1. **gRPC Service** (`AccountServiceImpl`) - Thin gRPC wrapper, validates requests, maps proto ↔ entity
2. **Repository** (`AccountRepository`) - Business logic and transactional data access
3. **Entity** (`Account`) - JPA/Panache entity mapped to `accounts` table
4. **Database** - MySQL with Flyway migrations

### Data Flow

```
gRPC Client → AccountServiceImpl → AccountRepository → Account Entity → MySQL
```

All gRPC operations run on worker threads (`.runSubscriptionOn(Infrastructure.getDefaultWorkerPool())`) to avoid blocking the event loop.

## API

Proto definition: `grpc/grpc-stubs/src/main/proto/repository/account/account_service.proto`

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

**Migration:** `src/main/resources/db/migration/V1__create_accounts_table.sql`

## Configuration

### Server Ports

- **Production:** HTTP/gRPC on port 38105
- **Dev:** HTTP/gRPC on port 38105 with `/account` root path
- **Test:** Random port (quarkus.http.test-port=0)

### Database

- **Dev:** MySQL via Docker Compose Dev Services on port 3306
- **Test:** MySQL via compose-test-services on port 3307
- **Prod:** Configured via environment variables

### Service Registration

The service auto-registers with the Platform Registration Service on startup (disabled in tests).

## Running the Service

### Development Mode

```bash
./gradlew :applications:account-manager:quarkusDev
```

The service will:
- Start on http://localhost:38105/account
- Connect to MySQL via Dev Services (Docker Compose)
- Auto-register with Platform Registration Service
- Enable gRPC reflection for testing

### Testing

Run all tests:
```bash
./gradlew :applications:account-manager:test
```

Test suites:
- **AccountRepositoryTest** - Repository CRUD tests against MySQL (7 tests)
- **AccountManagerWireMockTest** - WireMock gRPC mocking tests (6 tests)
- **AccountServiceTest** - End-to-end gRPC tests (3 tests)

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

## Testing

### Repository Tests

Direct tests of the repository layer against MySQL:

```java
@QuarkusTest
public class AccountRepositoryTest {
    @Inject
    AccountRepository accountRepository;

    @Test
    void testCreateAccount() {
        Account account = accountRepository.createAccount("test-id", "Test", "Description");
        assertEquals("test-id", account.accountId);
    }
}
```

### WireMock Tests

Tests using grpc-wiremock to mock the Account Service:

```java
@QuarkusTest
@QuarkusTestResource(WireMockGrpcTestResource.class)
@TestProfile(MockGrpcProfile.class)
public class AccountManagerWireMockTest {
    @InjectWireMock
    WireMockServer wireMockServer;

    @Test
    void testCreateAccount_Success() {
        new AccountManagerMock(wireMockServer.port())
            .mockCreateAccount("test-id", "Test", "Description");
        // Test your client code that calls account service
    }
}
```

### gRPC Integration Tests

End-to-end tests calling the actual gRPC service:

```java
@QuarkusTest
public class AccountServiceTest {
    @GrpcClient
    AccountServiceGrpc.AccountServiceBlockingStub accountService;

    @Test
    void testCreateAccount() {
        var response = accountService.createAccount(
            CreateAccountRequest.newBuilder()
                .setAccountId("test-id")
                .setName("Test")
                .build()
        );
        assertTrue(response.getCreated());
    }
}
```

## Dependencies

- **Quarkus gRPC** - gRPC server and client support
- **Hibernate ORM Panache** - Simplified JPA with active record pattern
- **MySQL** - Database (via quarkus-jdbc-mysql)
- **Flyway** - Database migrations
- **S3 Client** - Optional dev-only S3 bucket creation
- **grpc-wiremock** - Test mocking framework

## Future Enhancements

Per the implementation plan, the following are deferred to later phases:

- API key management (handled by Connector Admin service)
- Roles and permissions
- Account quotas and limits
- Cross-service authentication (mTLS/JWT)
- Drive ownership transfer between accounts
- Account deletion with cascade to drives

## Related Services

- **Repository Service** - Manages drives owned by accounts
- **Connector Admin Service** - Manages connectors linked to accounts (with API keys)
- **Connector Intake Service** - Authenticates via connector API keys, validates account access

## References

- Proto definition: `grpc/grpc-stubs/src/main/proto/repository/account/account_service.proto`
- Implementation plan: `docs/research/junie/2025-10-16_phase1_account_service_instruction_plan.md`
- grpc-wiremock framework: `grpc/grpc-wiremock/README.md`
- Shared dev services: `docs/SHARED-DEVSERVICES.md`
