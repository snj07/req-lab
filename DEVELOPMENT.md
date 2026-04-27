# Development Guide

Everything you need to build, run, test, and contribute to ReqLab.

---

## Prerequisites

| Tool | Minimum | Recommended |
|---|---|---|
| JDK | 17 | 21 (tested with Zulu 21) |
| OS | macOS / Linux / Windows | — |

> The Gradle Wrapper is bundled — no separate Gradle installation needed.

---

## Repository Layout

```
req-lab/
├── core-model/          # Shared domain models (RequestDefinition, ResponseDefinition, …)
├── core-network/        # Ktor-backed HTTP engine, interceptors, retry, WebSocket
├── core-storage/        # Persistence contracts and JSON file adapter
├── core-scripting/      # Script engine contracts (pre-request / post-request scripts)
├── editor-core/         # Pure-Kotlin editor engine: document model, lexer, fold map, diagnostics
├── editor-ui/           # Compose editor renderer: EditorRenderer, EditorViewModel, IdleLexer
├── feature-requests/    # Request use-cases wiring core-model + core-network
├── ui-shared/           # Shared Compose UI code (jvm + wasmJs) — 95% of all UI
├── ui-desktop/          # Thin Compose Desktop launcher (~74 lines)
├── ui-web/              # Thin Compose/Wasm browser launcher (~30 lines, CanvasBasedWindow)
├── qa-tests/            # JVM integration + end-to-end tests
├── sample-server/       # Standalone Ktor server for manual/exploratory testing
├── buildSrc/            # Shared Gradle build logic
├── docs/                # Architecture and testing reference docs
├── gradle/              # Version catalog (libs.versions.toml) and Gradle Wrapper
└── settings.gradle.kts  # Module declarations
```

### Key library versions

| Library | Version |
|---|---|
| Kotlin | 2.1.21 |
| Compose Multiplatform | 1.8.1 |
| Ktor | 3.1.2 |

Full version catalog: [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

---

## Building

Build all modules:

```bash
./gradlew build
```

If build fails with `:kotlinStoreYarnLock` and a message that the lock file changed, refresh and store the lock, then rerun:

```bash
./gradlew kotlinUpgradeYarnLock
./gradlew kotlinStoreYarnLock
./gradlew build
```

Compile only the shared UI module (fast check during development):

```bash
./gradlew :ui-shared:compileKotlinDesktop
```

## Branding Assets

App icons are versioned in:

- `ui-desktop/src/desktopMain/resources/icons/` (PNG sizes + `.icns` + `.ico`)
- `ui-web/src/wasmJsMain/resources/icons/` (favicon and PWA icons)

Desktop window icon is loaded from `Main.kt`; native package icons are configured in
`ui-desktop/build.gradle.kts` under `nativeDistributions`.

---

## Running

### Desktop application

```bash
./gradlew :ui-desktop:run
```

To run in the background (returns the prompt immediately):

```bash
./gradlew :ui-desktop:run &
```

### Desktop jar artifact (fat jar)

Build runnable jar:

```bash
./gradlew :ui-desktop:packageReqLabJar
```

Run jar directly:

```bash
java -jar ui-desktop/build/distribute/ReqLab-$(grep '^appVersion=' gradle.properties | cut -d= -f2).jar
```

Run jar via Gradle launcher task (sets Dock app name/icon on macOS):

```bash
./gradlew :ui-desktop:runReqLabJar
```

### Sample server (for manual testing)

The `sample-server` module runs a local Ktor server that mirrors the endpoints used by integration tests:

```bash
./gradlew :sample-server:run
```

Default address: `http://localhost:8080`

Available endpoints (selected):

| Method | Path | Notes |
|---|---|---|
| GET | `/` | Health check |
| GET/POST/PUT/PATCH/DELETE/OPTIONS/HEAD | `/api/users` (+ `/{id}` where applicable) | HTTP methods coverage |
| GET | `/api/search` | Query param coverage |
| GET | `/api/echo-headers` | Header echo |
| POST | `/api/json` | JSON body echo |
| POST | `/api/graphql` | GraphQL body coverage |
| POST | `/api/raw` | Raw text body |
| POST | `/api/form-data` | Multipart form data |
| POST | `/api/urlencoded` | URL-encoded body |
| POST | `/api/upload` | Upload / binary payload handling |
| GET | `/api/auth/basic` | Basic auth checks |
| GET | `/api/auth/bearer` | Bearer token checks |
| GET | `/api/auth/apikey` | API key checks |
| GET | `/api/time` / `/api/timestamp` | Time helpers |
| GET | `/api/protected` | Script token-protected endpoint |
| GET | `/api/cookies` | Cookies set/echo |
| GET | `/api/redirect` → `/api/final` | Redirect flow |
| GET | `/api/error/{code}` | Error response matrix |
| GET | `/api/slow` | Slow response simulation |
| GET | `/status/200`, `/status/201` | Deterministic script assertions |
| GET | `/json/user`, `/json/array`, `/json/object` | Script/runtime payload checks |
| GET | `/headers`, `/cookies`, `/response-time`, `/string-body` | Script-runtime support endpoints |
| POST | `/echo-body`, `/api/validate`, `/api/token`, `/api/echo-full` | Script and validation helpers |
| WS | `/ws` | WebSocket echo |

Query string simulation modes:

- `?mode=slow` — adds artificial latency
- `?mode=error` — returns a 500 error
- `?large=true` — returns a large payload

### Web (Compose/Wasm — canvas-based)

```bash
./gradlew :ui-web:wasmJsBrowserDevelopmentRun
```

Production bundle:

```bash
./gradlew :ui-web:wasmJsBrowserProductionWebpack
```

---

## Testing

### Run all tests

```bash
./gradlew check
```

### Run tests by module

```bash
# Core protocol + network behaviour
./gradlew :core-network:allTests

# Storage persistence behaviour
./gradlew :core-storage:allTests

# JVM integration + end-to-end tests (requires no external server)
./gradlew :qa-tests:jvmTest

# Compose Desktop UI smoke tests
./gradlew :ui-desktop:desktopTest
```

### Integration test bundle only

```bash
./gradlew :qa-tests:jvmTest :core-storage:allTests :ui-desktop:desktopTest
```

### Test structure

| Module | Scope |
|---|---|
| `core-network` | Unit tests: HTTP method support, auth, variable interpolation, retry, WebSocket |
| `core-storage` | Unit tests: in-memory repository behaviour, serialization round-trips |
| `qa-tests` | JVM integration and E2E tests running real requests against the embedded server |
| `ui-desktop` | Compose Desktop smoke tests: layout, panel visibility, tab behaviour |

### CI

GitHub Actions release packaging is defined in [`.github/workflows/release.yml`](.github/workflows/release.yml).

- **Push to `main`** — runs a quality gate first, then builds desktop artifacts for macOS/Linux/Windows.
- **Push tag `v*`** — runs the same quality gate, then builds artifacts and publishes a GitHub release.
- **Manual dispatch** — allows on-demand artifact builds.

---

## Project Architecture

See [`docs/architecture.md`](docs/architecture.md) for a full write-up. Key rules:

- **UI launchers** (`ui-desktop`, `ui-web`) depend on **ui-shared** only.
- **ui-shared** depends on **feature modules** and **core modules**.
- **Feature modules** depend on **core modules** only.
- **Core modules** do not depend on feature or UI modules.
- All business logic and UI composables live in `commonMain` sources.
- Platform-specific code uses `expect`/`actual` declarations in `ui-shared`.

### `ui-shared` component map

All shared UI code lives in
`ui-shared/src/commonMain/kotlin/com/reqlab/ui/shared/`:

```
MainScreen.kt              — root composable (toolbar, sidebar, editors, dialogs)
components/
├── RequestBar.kt          — method dropdown, URL field, Send/Save/Retry/cURL buttons
├── KeyValueEditor.kt      — reusable key-value table (Params, Headers)
├── BodyEditor.kt          — body type selector + content editor
├── AuthEditor.kt          — auth type selector + credential fields
├── ScriptEditor.kt        — code editor for Pre-request and Post-request scripts
├── RequestTabsBar.kt      — horizontal tab bar with scroll, context menu, indicators
├── RequestExecutor.kt     — sendRequest, saveRequest, buildAuthConfig, buildCurlCommand
├── RequestEditor.kt       — top-level editor composable, delegates to above
├── ResponseViewer.kt      — pretty/raw response body, headers, metrics
├── Sidebar.kt             — collections tree, environments, history
├── TopToolbar.kt          — environment picker, theme toggle, settings button
├── BottomPanel.kt         — network log panel
├── SettingsDialog.kt      — settings + workspace import/export
└── …dialogs and utilities
state/
├── AppState.kt            — root observable state (tabs, environments, collections, settings)
├── RequestTabState.kt     — per-tab mutable state
└── …
persistence/
├── ImportExportRepository.kt — JSON serialization for import/export (string-based)
├── SettingsRepository.kt  — settings persistence via PlatformStorage
├── TabsRepository.kt      — tab state persistence via PlatformStorage
└── WorkspaceRepository.kt — workspace persistence via PlatformStorage
network/
└── NetworkClientFactory.kt — expect/actual Ktor HttpClient factory
platform/
└── PlatformApi.kt         — expect/actual for UUID, clipboard, storage, file I/O, cursors
theme/
└── ReqLabTheme.kt         — Material 3 colour tokens, typography, code font
```

Platform-specific actuals are in `desktopMain/` (JVM) and `wasmJsMain/` (browser).

### `ui-desktop` (thin launcher)

`ui-desktop/src/desktopMain/kotlin/com/reqlab/ui/desktop/Main.kt` — ~44 lines.
Opens a Compose `Window`, loads settings/tabs/workspace, and renders `MainScreen`.

### `ui-web` (thin launcher)

`ui-web/src/wasmJsMain/kotlin/com/reqlab/ui/web/Main.kt` — ~15 lines.
Uses `CanvasBasedWindow` to render `MainScreen` in an HTML `<canvas>` element.

---

## Useful Gradle Tasks

```bash
# List all available tasks for a module
./gradlew :ui-desktop:tasks --all

# Force re-run tests (skip UP-TO-DATE cache)
./gradlew :ui-desktop:desktopTest --rerun-tasks

# Build and show a dependency report for a module
./gradlew :ui-desktop:dependencies

# Compile shared UI for desktop
./gradlew :ui-shared:compileKotlinDesktop

# Compile shared UI for wasmJs
./gradlew :ui-shared:compileKotlinWasmJs

# Build web production bundle
./gradlew :ui-web:wasmJsBrowserProductionWebpack

# Run desktop
./gradlew :ui-desktop:run

# Run web dev server
./gradlew :ui-web:wasmJsBrowserDevelopmentRun
```


## Remove data
```
# macOS — delete the prefs file
defaults delete com.reqlab.ui.shared.platform
# or remove the file directly
rm ~/Library/Preferences/com.reqlab.ui.plist

# Linux
rm -rf ~/.java/.userPrefs/com/reqlab/

# Windows (PowerShell)
Remove-Item -Path "HKCU:\Software\JavaSoft\Prefs\com\reqlab" -Recurse
```