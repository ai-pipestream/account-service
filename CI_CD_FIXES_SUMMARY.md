# CI/CD Fixes Summary - Account Service

## Overview
This document summarizes all fixes applied to make the account-service fully functional in both local development and Docker-in-Docker (DinD) CI/CD environments.

## Git Tags
- **`v1.0.0-dind-tests-fixed`**: Initial test fixes
- **`v1.0.0-ci-fixes`**: Complete CI/CD fixes (tests + publishing)

## Problems Fixed

### 1. DinD Test Failures (All 33 Tests Now Pass!)

#### Root Cause
Kafka was advertising `localhost:9095`, causing producers to fail reconnection after initially connecting via `172.17.0.1:9095` in the DinD environment.

#### Files Changed

**`src/main/resources/application.properties`**
- Added environment variable support for test profile:
  - `TEST_MYSQL_HOST` / `TEST_MYSQL_PORT` - MySQL connection
  - `TEST_KAFKA_HOST` / `TEST_KAFKA_PORT` - Kafka bootstrap servers
  - `TEST_APICURIO_HOST` / `TEST_APICURIO_PORT` - Apicurio registry URL
- Removed duplicate property that was overriding env vars
- Added explicit bootstrap servers for outgoing Kafka channel

**`src/test/resources/compose-test-services.yml`**
- Changed Kafka advertised address to use `${DOCKER_GATEWAY_HOST:-172.17.0.1}:9095`
- Configurable for cross-platform support (Linux, Mac, Windows)

**`.gitea/workflows/build-and-publish.yml`**
- Already configured correctly with environment variables
- Uses `172.17.0.1` for accessing DinD containers from runner

#### Result
✅ All 33 tests pass in local and DinD CI environments

### 2. Maven Publishing Failure

#### Root Cause
The `quarkusBuild` task produces a directory structure, not a single file artifact, causing Gradle to fail with:
```
Expected task 'quarkusBuild' output files to contain exactly one file, 
however, it contains more than one file.
```

#### File Changed

**`build.gradle`**
```groovy
// Before (broken):
artifact(tasks.named('quarkusBuild')) { artifact ->
    artifact.classifier = 'runner'
}

// After (fixed):
artifact(file("${buildDir}/quarkus-app/quarkus-run.jar")) { artifact ->
    artifact.classifier = 'runner'
    artifact.builtBy tasks.named('quarkusBuild')
}
```

#### Result
✅ Publishing to Reposilite works successfully
⚠️ Gitea publishing has credential issue (401) - mechanism works, auth needs fixing

## Platform-Specific Configuration

### Linux (Default)
Works out of the box! Uses `172.17.0.1` (Docker bridge gateway).

```bash
./gradlew test
```

### Mac/Windows
Set the `DOCKER_GATEWAY_HOST` environment variable:

```bash
export DOCKER_GATEWAY_HOST=host.docker.internal
./gradlew test
```

### CI/CD (DinD)
Automatically configured in workflow with:
```yaml
TEST_MYSQL_HOST: 172.17.0.1
TEST_KAFKA_HOST: 172.17.0.1
TEST_APICURIO_HOST: 172.17.0.1
```

## Publishing Credentials

### Reposilite (Primary Maven Repository)
- URL: `https://maven.rokkon.com/snapshots`
- Environment variables:
  - `REPOS_USER` (optional, defaults to "admin")
  - `REPOS_PAT` (required - Reposilite token)

### Gitea (Backup Maven Registry)
- URL: `https://git.rokkon.com/api/packages/io-pipeline/maven`
- Environment variables:
  - `GIT_USER` (optional, defaults to "krickert")
  - `GIT_TOKEN` (required - Gitea access token)

## How It All Works

### 1. Docker Networking in DinD
```
┌─────────────────────────────────────┐
│  Gitea Actions Runner Container     │
│  (test JVM runs here)                │
│                                      │
│  Connects to: 172.17.0.1:9095  ─────┼──┐
└─────────────────────────────────────┘  │
                                         │
                                         │  Docker bridge
                                         │  gateway
                                         │
┌─────────────────────────────────────┐  │
│  DinD (Docker daemon inside runner) │  │
│  ┌───────────────────────────────┐  │  │
│  │  Kafka Container              │◄─┼──┘
│  │  Port 9094 mapped to 9095     │  │
│  │  Advertises: 172.17.0.1:9095  │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

### 2. Kafka Advertised Address Flow
1. Producer connects to `172.17.0.1:9095`
2. Kafka responds with metadata: "I'm at 172.17.0.1:9095"
3. Producer successfully reconnects to advertised address
4. ✅ Messages flow successfully

### 3. Test Configuration Cascade
```
Local Dev:
  TEST_KAFKA_HOST not set → defaults to localhost
  Kafka advertises 172.17.0.1:9095
  Both localhost and 172.17.0.1 work on Linux!

CI/CD (DinD):
  TEST_KAFKA_HOST=172.17.0.1 → connects to Docker gateway
  Kafka advertises 172.17.0.1:9095
  Matches connection address → success!
```

## Key Learnings

1. **Docker Bridge Gateway**: `172.17.0.1` is the standard Docker bridge gateway on Linux, accessible from both host and containers
2. **Kafka Metadata**: Kafka's advertised address MUST match how clients will reconnect
3. **DinD Isolation**: Test JVM runs in runner container, not inside nested Docker
4. **Cross-Platform**: Mac/Windows need `host.docker.internal` instead of `172.17.0.1`
5. **Gradle Artifacts**: Task outputs != file artifacts, must reference specific files

## Testing Checklist

- [x] All 33 tests pass locally on Linux
- [x] All 33 tests pass in DinD CI environment  
- [x] Publishing to Reposilite succeeds
- [x] Cross-platform configuration documented
- [ ] Gitea publishing credentials fixed (credential issue, not code issue)

## Documentation

- **DOCKER_GATEWAY_SETUP.md** - Platform-specific setup instructions
- **CI_CD_FIXES_SUMMARY.md** - This document
- Git tags with detailed commit history

## Commits Included

1. `382f9a4` - Fix Apicurio URL for DinD environment in tests
2. `594ee5f` - Remove duplicate Apicurio URL property
3. `409a757` - Fix Kafka producer bootstrap servers for DinD tests
4. `78ea72c` - Fix Kafka advertised address for Docker bridge gateway ⭐ ROOT CAUSE
5. `24219f3` - Fix Maven publishing artifact configuration
6. `b9cf090` - Add cross-platform support for Mac/Windows Docker gateway

## Future Considerations

1. Fix Gitea publishing credentials (GIT_TOKEN permissions)
2. Consider creating a shared test configuration for all services
3. Document this pattern for other microservices in the pipeline
4. Add integration tests for the publishing process

---

**Status**: ✅ Production Ready
**Tests**: 33/33 Passing
**Publishing**: Working (Reposilite)
**Cross-Platform**: Supported
