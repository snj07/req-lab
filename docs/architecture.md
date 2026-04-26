# ReqLab Architecture

## Design Goals

- All business logic lives in `commonMain` — platform-specific code is minimal.
- Transport, storage, and scripting are swappable behind interfaces.
- Strict one-way dependency graph from UI down to core.
- Feature boundaries are enforced at the Gradle module level.

---

## Module Map

```
core-model/          Shared domain models — no runtime deps
core-network/        Ktor HTTP engine, auth, retry, WebSocket, interceptors
core-storage/        Persistence contracts + JSON file adapter
core-scripting/      Script runtime contracts (pre-request / post-request JS)
editor-core/         Pure-Kotlin editor engine: document model, lexer tokens,
                     fold regions, display-line map, inline diagnostics
editor-ui/           Compose renderer (EditorRenderer, EditorViewModel, LineView,
                     IdleLexer, syntax highlighting)
feature-requests/    Request use-cases: ties core-model + core-network
ui-shared/           Shared Compose UI (jvm + wasmJs) — all composables,
                     app state, persistence adapters, platform expect/actual
ui-desktop/          Thin Compose Desktop launcher (~74 lines)
ui-web/              Thin Compose/Wasm browser launcher (~30 lines)
sample-server/       Standalone Ktor server for integration tests
qa-tests/            JVM integration + end-to-end API tests
buildSrc/            Shared Gradle build logic
```

### Key library versions

| Library | Version |
|---|---|
| Kotlin | 2.1.21 |
| Compose Multiplatform | 1.8.1 |
| Ktor | 3.1.2 |

---

## Dependency Rules

```
ui-desktop  ──▶  ui-shared
ui-web      ──▶  ui-shared
               │
               ├──▶ editor-ui  ──▶  editor-core
               ├──▶ feature-requests
               ├──▶ core-model
               ├──▶ core-network
               ├──▶ core-storage
               └──▶ core-scripting

feature-requests  ──▶  core-model  +  core-network
core-*            ──▶  (no app deps — leaf modules)
editor-core       ──▶  (no app deps — leaf module)
editor-ui         ──▶  editor-core  (+ Compose)
```

Rules enforced at the module level:

- UI launchers (`ui-desktop`, `ui-web`) depend on `ui-shared` only.
- `ui-shared` depends on feature modules, core modules, and `editor-ui`.
- Feature modules depend on core modules only.
- Core and `editor-core` modules are leaf modules with no upward dependencies.
- All business logic and UI composables live in `commonMain` sources.
- Platform-specific code uses `expect`/`actual` declarations in `ui-shared`.

---

## Layer Breakdown

### Domain Layer — `core-model`

Pure Kotlin data classes, no runtime dependencies:

- `RequestModels.kt` — request, response, body, auth, form-data types
- `CollectionModels.kt` — `CollectionNode`, folder/request tree structures
- `EnvironmentModels.kt` — `Environment`, variable entries
- `RealtimeModels.kt` — WebSocket connection and message models

#### HTTP methods

`HttpMethodType`: GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD

#### Request body types (`BodyType`)

| Value | Content-Type sent |
|---|---|
| `NONE` | *(no body)* |
| `JSON` | `application/json` |
| `XML` | `application/xml` |
| `HTML` | `text/html` |
| `JAVASCRIPT` | `application/javascript` |
| `FORM_DATA` | `multipart/form-data` |
| `X_WWW_FORM_URLENCODED` | `application/x-www-form-urlencoded` |
| `RAW_TEXT` | `text/plain` |
| `BINARY` | `application/octet-stream` |
| `GRAPHQL` | `application/json` |

`JSON`, `XML`, `HTML`, `JAVASCRIPT`, `RAW_TEXT`, and `GRAPHQL` body types are syntax-highlighted in the editor. `FORM_DATA` and `X_WWW_FORM_URLENCODED` use key-value table editors.

#### Auth types (`AuthType`)

NONE, BASIC, BEARER, API_KEY, OAUTH2, JWT

`AuthConfig` carries the type and a `params: Map<String, String>` for credentials (username/password for Basic; token for Bearer; key name + value for API Key).

#### Response model

`ResponseDefinition` is the immutable result of a completed HTTP round-trip:
- `metrics: ResponseMetrics` — statusCode, responseTimeMs, responseSizeBytes; phase-level timing (DNS, connect, TLS, server, download)
- `headers: List<KeyValueEntry>`
- `body: String?`
- `error: String?` — set when the request fails without a response

### Infrastructure Layer

| Module | Responsibility |
|---|---|
| `core-network` | `KtorApiClient` executing HTTP requests; auth schemes (Basic, Bearer, API Key, OAuth2, JWT); retry; `{{variable}}` interpolation in URL/headers/body; WebSocket; `NetworkInterceptor` interface |
| `core-storage` | `PlatformStorage` abstraction; workspace, tab, settings, and environment JSON persistence |
| `core-scripting` | JavaScript runtime contracts; pre-request / post-request execution; variable scope injection (`environment`, `globals`, `collectionVariables`); `pm.*` → `reqlab.*` API rewriter |
| `feature-requests` | `RequestExecutionService` — orchestrates an HTTP round-trip: resolves variables → runs pre-request script → dispatches via `KtorApiClient` → feeds response into post-request scripts |

### Editor Layer

| Module | Responsibility |
|---|---|
| `editor-core` | `DocumentModel` (gap-buffer text storage), `DisplayLineMap` (fold/wrap mapping), `StyleBuffer` (token colour cache), `FoldingModel`, `LanguageRegistry`, per-language providers (`JsonMode`, `XmlMode`, `JavaScriptMode`, `GraphQLMode`, `HtmlMode`, `PlainTextMode`), `InlineEditorError` |
| `editor-ui` | `EditorViewModel` (document state coordinator), `EditorRenderer` (Compose LazyColumn renderer), `LineView` (single visible line composable), `IdleLexer` (background incremental tokenizer), `SyntaxHighlighter`, `SyntaxHighlighterRegistry`, `EditorTheme` |

### Presentation Layer — `ui-shared`

All shared Compose code — compiled for both `jvm("desktop")` and `wasmJs{browser()}`.

```
ui-shared/src/commonMain/kotlin/com/reqlab/ui/shared/
├── MainScreen.kt                   Root composable: toolbar, sidebar, tab bar, editor, dialogs
├── components/
│   ├── RequestBar.kt               Method dropdown, URL field, Send/Save/Retry/cURL buttons
│   ├── KeyValueEditor.kt           Reusable key-value table (Params, Headers)
│   ├── BodyEditor.kt               Body type selector + CodeEditor instance
│   ├── AuthEditor.kt               Auth-type selector + credential fields
│   ├── ScriptEditor.kt             Code editor for Pre-request and Post-request scripts
│   ├── RequestTabsBar.kt           Horizontal tab bar: scroll, context menu, unsaved indicators
│   ├── RequestExecutor.kt          sendRequest, saveRequest, buildCurlCommand, auth config
│   ├── RequestEditor.kt            Top-level editor composable; delegates to sub-components
│   ├── ResponseViewer.kt           Pretty/raw response body, headers, metrics
│   ├── Sidebar.kt                  Collections tree, environments, history, search
│   ├── TopToolbar.kt               Environment picker, theme toggle, settings button
│   ├── BottomPanel.kt              Network log panel
│   ├── GraphQLPanel.kt             GraphQL body/variables/schema editor
│   ├── SettingsDialog.kt           Settings + workspace import/export
│   ├── GlobalVariablesDialog.kt    Global variable editor
│   ├── HelpAboutDialog.kt          In-app help, shortcuts, scripting guide
│   ├── CodeEditor.kt               Public CodeEditor composable (wraps EditorRenderer)
│   ├── CodeFolding.kt              Fold region detection helpers (delegates to editor-core)
│   └── SyntaxHighlighter.kt        Language enum + format helpers
├── state/
│   ├── AppState.kt                 Root Compose state: tabs, environments, collections, settings
│   └── AppStateDemoData.kt         Demo data helpers
├── persistence/
│   ├── ImportExportRepository.kt   JSON serialization for workspace import/export
│   ├── PostmanImporter.kt          Postman collection v2/v2.1 + environment importer
│   ├── SettingsRepository.kt       Settings persistence via PlatformStorage
│   ├── TabsRepository.kt           Tab state persistence
│   └── WorkspaceRepository.kt      Collections + environments persistence
├── network/
│   └── NetworkClientFactory.kt     expect/actual Ktor HttpClient factory
└── platform/
    └── PlatformApi.kt              expect/actual: UUID, clipboard, storage, file I/O, cursors
```

Platform-specific actuals live in `desktopMain/` (JVM) and `wasmJsMain/` (browser).

### Platform Launchers

**`ui-desktop`** (~74 lines):  
`Main.kt` opens a Compose `Window`, loads settings/tabs/workspace via repository classes, then renders `MainScreen`. Configures window icon and title from branding assets.

**`ui-web`** (~30 lines):  
`Main.kt` uses `CanvasBasedWindow` to render `MainScreen` inside an HTML `<canvas>` element.

---

## State Management

`AppState` is the single root Compose state object. Instantiated once by the platform launcher and threaded through `MainScreen`. It holds:

- `tabs: List<RequestTabState>` — all open tabs
- `activeTabIndex: Int`
- `collections: List<CollectionNode>` — loaded workspace tree
- `environments: List<Environment>`
- `activeEnvironmentId: String?`
- `globalVariables: List<KeyValueEntry>`
- `sidebarSearchQuery: String`
- `settings: AppSettings`
- `history: List<HistoryItem>` — recent request history
- `consoleLogs: List<ConsoleEntry>` — network log panel entries

State flows one way: user interaction → `AppState` mutation → Compose recomposition.

### `RequestTabState` — per-tab mutable state

Each open tab holds (key fields):

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Display name (taken from collection or manually edited) |
| `method` | `HttpMethodType` | HTTP method |
| `url` | `String` | Raw URL (may contain `{{var}}` tokens) |
| `params` | `List<MutableKeyValue>` | Query parameters |
| `headers` | `List<MutableKeyValue>` | Request headers (system + user) |
| `bodyType` | `BodyType` | Active body type |
| `bodyContents` | `Map<BodyType, String>` | Per-type body text (preserved on switch) |
| `formRows` | `List<MutableFormDataRow>` | FORM_DATA structured rows |
| `urlencodedRows` | `List<MutableFormDataRow>` | URL-encoded structured rows |
| `authType` | `AuthType` | Auth scheme |
| `authUsername/Password/Token/ApiKey/ApiValue` | `String` | Auth credentials |
| `preRequestScript` | `String` | JavaScript pre-request script |
| `testScript` | `String` | JavaScript post-request script |
| `retryEnabled`, `retryCount`, `retryDelayMs` | — | Retry policy |
| `response` | `ResponseDefinition?` | Last response (null if not yet sent) |
| `isLoading` | `Boolean` | True while request is in-flight |
| `isDirty` | `Boolean` | Unsaved changes indicator |
| `currentJob` | `Job?` | Cancellable coroutine for the in-flight request |

System headers (`Content-Type`, `Accept`, `User-Agent`) are pre-populated as locked rows that users can override but not delete.

### `AppSettings`

| Setting | Default | Description |
|---|---|---|
| `theme` | DARK | DARK / LIGHT / SYSTEM |
| `language` | EN | UI language |
| `responseLayout` | RIGHT | Response panel on the right or bottom |
| `requestTimeoutSec` | 30 | HTTP request timeout |
| `followRedirects` | true | Follow 3xx redirects |
| `autoSaveRequests` | false | Auto-save on send |
| `confirmBeforeDelete` | true | Confirm dialog on delete |
| `httpProxy` / `httpsProxy` | "" | Proxy URLs |
| `proxyEnabled` | false | Enable proxy |
| `scriptPrefix` | "reqlab" | Script namespace (e.g. `reqlab.test`, `api.test`) |

### Variable Interpolation

`{{variable}}` tokens in URL, headers, body, and query parameters are resolved at dispatch time by `core-network`'s interpolation engine. Resolution order (first match wins):

1. Script-injected variables (set by the current pre-request script)
2. Tab-local overrides
3. Active environment variables
4. Global variables

---

## Persistence

All state is persisted as JSON via `java.util.prefs.Preferences` (desktop) or `localStorage` (web) behind the `PlatformStorage` expect/actual contract.

| Repository | Data |
|---|---|
| `WorkspaceRepository` | Collections tree + environments |
| `TabsRepository` | Open tabs and per-tab state |
| `SettingsRepository` | App settings |
| `ImportExportRepository` | Workspace export / import (file-based) |

Format is plain JSON. No SQLite or SQLDelight is used currently.

---

## Scripting Pipeline

```
Pre-request script
     │
     ▼
ScriptEngine.execute(script, context)
     │
     ├── Mutates: URL, method, headers, body, query params
     └── Writes:  environment / global / collection variables

HTTP Request dispatch (core-network)

Post-request script
     │
     ▼
ScriptEngine.execute(script, context)
     │
     ├── Reads:  response code, body, headers, timing, size
     ├── Writes: environment / global / collection variables
     └── Produces: named test results (pass / fail + message)
```

Scripts run synchronously. The JS runtime is sandboxed — no file system or network access available from scripts.

---

## Code Editor Architecture

See [`editor-architecture.md`](editor-architecture.md) for the full deep-dive.

Summary:

- `editor-core` provides the pure-Kotlin document model (gap-buffer, fold map, style buffer, incremental lexer tokens).
- `editor-ui` provides the Compose renderer (`EditorRenderer`) driven by `EditorViewModel`.
- `ui-shared/CodeEditor.kt` is the public composable used by `BodyEditor`, `ScriptEditor`, and `ResponseViewer`.
- The gutter (line numbers + fold indicators) and content column share a single `LazyColumn` — synchronised scrolling with zero extra complexity.

---

## Extension Points

Implemented:

- `NetworkInterceptor` in `core-network` — hooks into every HTTP request/response cycle.
- `ScriptEngine` in `core-scripting` — swap the JS runtime implementation.
- `SyntaxHighlighterRegistry` in `editor-ui` — register custom language highlighters at runtime.

Planned:

- Authentication providers (OAuth 2.0, NTLM, Digest)
- Code generators (curl, fetch, axios, OkHttp)
- Import transformers (OpenAPI, cURL, Insomnia)
- Collection test runner with report export

---

## Scalability Strategy

### Current capacity

- Documents up to several hundred KB render without issues.
- Syntax highlighting runs on a background `IdleLexer` coroutine — no UI-thread blocking.
- Folded lines are hidden via `DisplayLineMap`; only visible lines are composed.
- `LazyColumn` ensures only on-screen rows are measured and drawn.
- Gutter width is pre-allocated for at least 2 digits, preventing layout shifts at the 9→10 line boundary.

### Path to 100 MB documents

| Phase | Status | Work |
|---|---|---|
| A | Done | Gap-buffer document model; background idle lexer; display-line map; fold-state preservation across edits |
| B | Next | `ViewportModel` — expose only the visible window to Compose; document ↔ viewport offset mapping |
| C | Planned | Incremental tokenizer + incremental fold recomputation for dirty ranges only |
| D | Planned | CI performance gates: open-time, type-latency, replace-all-latency at 10/25/50/100 MB |
