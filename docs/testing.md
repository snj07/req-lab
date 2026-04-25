# Testing Guide

## Overview

ReqLab uses a layered validation strategy:

1. Unit tests (`core-network`, `core-storage`, `core-model`, `core-scripting`)
2. Shared UI/unit tests (`ui-shared`, `editor-core`, `editor-ui`)
3. Integration and end-to-end API workflow tests (`qa-tests`)
4. Desktop UI automation and regression tests (`ui-desktop`)

## Test Modules

- `qa-tests`: JVM integration and E2E tests
- `core-network`: protocol behavior and request mapping tests
- `core-storage`: repository and persistence behavior tests
- `core-model`: model serialization and data structure tests
- `core-scripting`: script runtime, assertions, and variable scope behavior tests
- `editor-core`: editor engine/state/folding/performance micro-tests
- `editor-ui`: editor renderer/viewmodel logic and regression tests
- `ui-shared`: syntax highlighter, code folding, Postman import, i18n, and app state tests
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
./gradlew :core-model:allTests
./gradlew :core-scripting:allTests
./gradlew :editor-core:desktopTest
./gradlew :editor-ui:allTests
./gradlew :ui-shared:desktopTest
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
- Syntax highlighting: JSON, XML/HTML, GraphQL, JavaScript token colorization
- Code folding: brace-based, tag-based, comment-based region detection and fold state management
- Postman collection/environment import (v2/v2.1)

Quality-gate tests used by release packaging:

- `:editor-ui:allTests`
- `:ui-shared:desktopTest`
- `:ui-web:wasmJsTest`

Partially covered or still evolving areas:

- Advanced collection drag-and-drop edge cases under high node counts
- Deeper web UI interaction parity (desktop-level interaction coverage is stronger)
- Collection runner reporting UI depth
- OpenAPI/curl import and code-generation feature surface
- Persistent SQLDelight/SQLite restart durability

## CI

GitHub Actions workflow: `.github/workflows/release.yml`

- Push to `main`: quality gate, then artifact build validation across macOS, Ubuntu, and Windows.
- Tag `v*`: quality gate, then artifact build + GitHub Release publication.
