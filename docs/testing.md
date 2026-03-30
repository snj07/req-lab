# Testing Guide

## Overview

ReqLab uses a layered validation strategy:

1. Unit tests (`core-network`, `core-storage`)
2. Integration tests (`qa-tests` against local dummy server)
3. End-to-end API workflow tests (`qa-tests`)
4. Desktop UI automation smoke tests (`ui-desktop`)

## Test Modules

- `test-support`: local Ktor dummy API server used by tests
- `qa-tests`: JVM integration and E2E tests
- `core-network`: protocol behavior and request mapping tests
- `core-storage`: repository and persistence behavior tests
- `ui-desktop`: Compose Desktop UI automation smoke tests

## Dummy Server Endpoints

- `GET /users`
- `POST /users`
- `PUT /users/{id}`
- `PATCH /users/{id}`
- `DELETE /users/{id}`
- `OPTIONS /users`
- `HEAD /users`
- `POST /graphql`
- `POST /upload`
- `POST /auth/login`
- `POST /oauth/token`
- `GET /auth/protected`
- `GET /stream`
- `WS /ws`

Simulation modes:

- `?mode=slow` (latency)
- `?mode=error` (server error)
- `?large=true` (large payload)

## Run Tests Locally

```bash
./gradlew :core-network:allTests
./gradlew :core-storage:allTests
./gradlew :qa-tests:jvmTest
./gradlew :ui-desktop:desktopTest
```

Run the full validation bundle:

```bash
./gradlew :qa-tests:jvmTest :core-storage:allTests :ui-desktop:desktopTest
```

## Coverage Matrix (Current Implementation)

Implemented and tested:

- HTTP methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, `HEAD`
- Auth: Basic, Bearer, API Key
- Variables: URL, headers, auth token interpolation (`{{var}}`)
- Body types currently testable with implemented engine paths:
  - JSON
  - x-www-form-urlencoded
  - Raw text
  - GraphQL payload wrapper
- Retry behavior and server error handling
- Scripting runtime (pre-request scripts, post-response tests, assertions, variable scopes)
- WebSocket connect/send/receive/disconnect
- Storage behavior via in-memory repository implementations
- Desktop shell rendering smoke tests (layout panels/labels)

Not yet fully testable because implementation is not complete yet:

- Collection drag-and-drop and rich tree operations
- History search/re-run UI flows
- Collection runner reporting UI
- Importers (Postman/OpenAPI/curl) and code generators
- Full response JSON tree explorer, diff viewer, resizable panes, command palette
- Persistent SQLDelight/SQLite restart durability

## CI

GitHub Actions workflow: `.github/workflows/release.yml`

- Push to `main`: artifact build validation across macOS, Ubuntu, and Windows.
- Tag `v*`: artifact build + GitHub Release publication.
