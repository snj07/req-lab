# Architecture

## Goals

- Keep all business logic in `commonMain`
- Keep transport, storage, and scripting swappable
- Support plugin-based extension points
- Maintain strict feature boundaries and dependency direction

## Layered Design

### Domain Layer

Located in feature modules and `core-model`:

- Use-cases and orchestration
- Immutable models (`RequestDefinition`, `ResponseDefinition`, etc.)

### Data / Infrastructure Layer

Located in core modules:

- `core-network`: Ktor execution, interceptors, retries
- `core-storage`: persistence contracts, SQLDelight adapter implementation (next milestone)
- `core-scripting`: script runtime contracts

### Presentation Layer

Located in UI modules:

- Compose MPP shell per platform
- Shared state and feature view models (next milestone)

## Dependency Rules

- UI modules can depend on feature modules only
- Feature modules can depend on core modules
- Core modules must not depend on feature or UI modules

## Extension Points

Initial extension points added in this milestone:

- `NetworkInterceptor` in `core-network`
- `ScriptEngine` in `core-scripting`

Planned extension points:

- Authentication providers
- Code generators
- Import transformers
- Test runners

## Scaling Strategy

- Use `Flow` streams for async/state updates
- Lazy-load large collections and histories
- Cache parsed/pretty-printed responses
- Keep platform engines behind common contracts
