# ReqLab

ReqLab is a Kotlin Multiplatform API client inspired by Postman, Insomnia, Hoppscotch, and Bruno.

This repository is initialized with a production-oriented, modular architecture and the first implemented milestone:

- Project architecture and module boundaries
- Gradle Kotlin DSL setup (Kotlin 2.x)
- Core network engine (Ktor Client + Coroutines/Flow + kotlinx.serialization)
- Initial domain models and shared contracts
- Initial tests for core network behavior

## Tech Stack

- Kotlin Multiplatform (Kotlin 2.1.x)
- Compose Multiplatform (Desktop, Web, Android, iOS shells)
- Ktor Client
- kotlinx.serialization
- Coroutines + Flow
- Koin (DI foundations)
- SQLDelight (storage foundations)

## Module Layout

### Core

- `core-model`
- `core-network`
- `core-storage`
- `core-scripting`

### Features

- `feature-requests`
- `feature-collections`
- `feature-history`
- `feature-environments`

### Platform UI

- `ui-desktop`
- `ui-web`
- `ui-android`
- `ui-ios`

## Build

From repository root:

```bash
./gradlew build
```

Desktop run:

```bash
./gradlew :ui-desktop:desktopRun
```

Web development build:

```bash
./gradlew :ui-web:jsBrowserDevelopmentRun
```

## Testing

```bash
./gradlew :core-network:allTests
./gradlew :core-storage:allTests
./gradlew :qa-tests:jvmTest
./gradlew :ui-desktop:desktopTest
```

Detailed testing coverage and server endpoints: `docs/testing.md`.

## Current Milestone Scope

Implemented:

- KMP project structure and module graph
- Shared request/response models
- Ktor-backed API client with:
  - HTTP method support
  - headers/query/cookies/auth/body support
  - variable interpolation (`{{var}}`)
  - retry policy
  - interceptor extension points
  - network event stream (`Flow`)

Planned next milestones:

1. Request editor UI and response viewer (pretty/raw/tree)
2. Collections tree with folders and drag-and-drop
3. Environment manager + secret variable handling
4. History + search + re-run
5. Script engine and collection runner
6. Import/export and code generation
7. WebSocket and optional gRPC support
8. Plugin system and extension SDK
