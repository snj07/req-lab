# Testing Guide

## Overview

ReqLab uses a layered validation strategy:

1. Unit tests (`core-network`, `core-storage`)
2. Integration tests (`qa-tests` against local dummy server)
3. End-to-end API workflow tests (`qa-tests`)
4. Desktop UI automation smoke tests (`ui-desktop`)

## Test Modules

- `qa-tests`: JVM integration and E2E tests
- `core-network`: protocol behavior and request mapping tests
- `core-storage`: repository and persistence behavior tests
- `ui-desktop`: Compose Desktop UI automation smoke tests

## Dummy Server Endpoints

- `GET /`
- `GET/POST/PUT/PATCH/DELETE/OPTIONS/HEAD /api/users` (+ `/{id}` where applicable)
- `GET /api/search`
- `GET /api/echo-headers`
- `POST /api/json`
- `POST /api/graphql`
- `POST /api/raw`
- `POST /api/form-data`
- `POST /api/urlencoded`
- `POST /api/upload`
- `GET /api/auth/basic`
- `GET /api/auth/bearer`
- `GET /api/auth/apikey`
- `GET /api/time`, `GET /api/timestamp`
- `GET /api/protected`
- `GET /api/cookies`
- `GET /api/redirect`, `GET /api/final`
- `GET /api/error/{code}`
- `GET /api/slow`
- `GET /status/200`, `GET /status/201`
- `GET /json/user`, `GET /json/array`, `GET /json/object`
- `GET /headers`, `GET /cookies`, `GET /response-time`, `GET /string-body`
- `POST /echo-body`, `POST /api/token`, `POST /api/validate`, `GET /api/echo-full`
- `WS /ws`

Simulation parameters used by current endpoints:

- `GET /api/slow?ms=<delay>`
- `GET /response-time?ms=<delay>`

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
- Body types covered in current suites:
  - JSON
  - x-www-form-urlencoded
  - Raw text
  - GraphQL
  - Form-data and binary upload flows
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
