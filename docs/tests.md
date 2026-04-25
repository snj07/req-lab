# Tests Guide

This document summarizes how to run ReqLab tests locally.

## Test Layers

- Core unit/module tests: `core-network`, `core-storage`, `core-model`, `core-scripting`
- Editor unit/module tests: `editor-core`, `editor-ui`
- Integration and E2E API tests: `qa-tests`
- Desktop UI smoke tests: `ui-desktop`

## Common Commands

```bash
./gradlew :core-network:allTests
./gradlew :core-storage:allTests
./gradlew :core-model:allTests
./gradlew :core-scripting:allTests
./gradlew :editor-core:desktopTest
./gradlew :editor-ui:allTests
./gradlew :qa-tests:jvmTest
./gradlew :ui-desktop:desktopTest
./gradlew :ui-web:wasmJsTest
```

Run a focused validation bundle:

```bash
./gradlew :qa-tests:jvmTest :core-storage:allTests :ui-desktop:desktopTest
```

Run full project checks:

```bash
./gradlew check
```

### Apple simulator tests

`core-scripting` Apple simulator tests are opt-in locally. Enable with:

```bash
./gradlew :core-scripting:iosSimulatorArm64Test -PrunAppleSimulatorTests=true
```

## Coverage Highlights

- HTTP methods and body types
- Auth schemes (Basic, Bearer, API Key)
- Variable interpolation and scripting runtime behavior
- Retry/error handling and selected WebSocket flows
- Desktop shell/UI smoke coverage

Release-quality gate also validates:

- `:editor-ui:allTests`
- `:ui-shared:desktopTest`
- `:ui-web:wasmJsTest`

For broader strategy and endpoint matrix, see [docs/testing.md](docs/testing.md).
