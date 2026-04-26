# Testing Guide

## Overview

ReqLab uses a layered validation strategy:

1. Unit tests (`core-network`, `core-storage`, `core-model`, `core-scripting`)
2. Shared UI/unit tests (`ui-shared`, `editor-core`, `editor-ui`)
3. Integration and end-to-end API workflow tests (`qa-tests`)
4. Desktop UI automation and regression tests (`ui-desktop`)

## Test Modules

### `core-model`
Model serialization and data structure behavior.
- `RealtimeModelsTest` — WebSocket message and realtime model serialization
- `ResponseMetricsTimingTest` — phase-level response timing (DNS, TCP, TLS, TTFB, body, total)

### `core-network`
Protocol behavior, request mapping, and variable substitution.
- `KtorApiClientTest` — HTTP methods (GET/POST/PUT/PATCH/DELETE/OPTIONS/HEAD), body type delivery (JSON, form-data, urlencoded, raw, binary), auth headers, dynamic variables in URL/headers, timing metrics, retry logic
- `VariableResolverTest` — variable interpolation, scope precedence, unresolved-variable passthrough

### `core-scripting`
Script runtime, assertions, and variable scopes.
- `ReqLabScriptEngineTest` — JS execution isolation, `req`/`res` bindings, pre-request scripts, post-response assertions, variable set/get across script phases, assertion error messages

### `core-storage`
Repository and persistence behavior.
- `InMemoryRepositoriesTest` — CRUD for requests, tabs, environments, collections; ordering/rename/delete invariants

### `editor-core`
Editor engine micro-tests (15 test classes).
- `GapBufferTest` — insert/delete at arbitrary offsets, gap recentering, boundary cases
- `DocumentModelTest` — line counting, offset-to-line mapping, large document edits
- `EditorDocumentTest` — high-level document operations via `EditorDocument` facade
- `EditorEngineTest` — integrated edit/cursor/undo/redo sequences
- `FoldingModelTest` — fold/unfold, nested regions, `visibleLineCount` consistency
- `DisplayLineMapCapacityTest` — prefix-sum correctness, capacity expansion
- `DisplayLineMapFoldPreservationTest` — fold-state preservation after reflow
- `CursorStateTest` — cursor clamping, word-boundary navigation
- `SelectionModelTest` — extend/collapse, word/line select edge cases
- `LineIndexTest` — line start/end lookup, newline scanning performance
- `LanguageModeTest` — mode detection, auto-detection from content hints
- `JsonModeTest` — JSON tokenizer correctness on valid/invalid input
- `JavaScriptModeTest` — JS token classification (keywords, strings, regex literals)
- `XmlHtmlModeTest` — tag, attribute, and CDATA tokenization
- `PerformanceBenchmarkTest` — lexer throughput and display-line rebuild timing

### `editor-ui`
Editor renderer/ViewModel logic and regression tests.
- `DiagnosticsAndFoldUpdateTest` — diagnostic overlay rendering, fold version consistency
- `EditorViewModelFixTest` — ViewModel command sequencing, state emission correctness
- `PerformanceIssueReproTest` — regression guards for previously found performance problems

### `ui-shared`
Cross-platform shared UI behavior (16 test classes).
- `EditorArchitectureTest` — architectural invariants: clean layer separation, no illegal cross-layer deps
- `EditorEngineIntegrationTest` — integrated editor operations through the shared API
- `EditorQaUnitTest` — unit-level QA coverage for editor shared behaviour
- `BodyEditorStateTest` — body editor tab state: switching, reset, content preservation
- `CodeFoldingTest` — brace-based, tag-based, and comment-based fold region detection
- `SyntaxHighlighterTest` — JSON/XML/HTML/GraphQL/JS token colorization
- `LargeDocumentHighlightingTest` — highlighting performance on large payloads
- `IssueFixTest` — regression guards for tracked UI-shared bugs
- `PreReleaseQaTest` — pre-release sanity checks for the shared layer
- `RequestExecutorLogResolutionTest` — variable resolution in log/request-executor calls
- `PostmanImporterTest` — Postman v2/v2.1 collection and environment import correctness
- `ImportExportFormDataTest` — form-data field serialization round-trip
- `AppStateBehaviorTest` — app state transitions, active-tab tracking, collection mutations
- `FirstLaunchStateTest` — first-launch default state, initial tab creation
- `I18nCompletenessTest` — all string keys present in every supported locale
- `I18nProviderTest` — locale switching, fallback behavior, pluralization

### `ui-desktop`
Compose Desktop UI automation, regression, integration, and persistence tests (50+ test classes).

**Shell and tab management:**
- `DesktopShellUiTest` — layout panels, header/sidebar/main panel visibility, label correctness
- `TabManagementUiTest` — tab open/close/reorder, tab overflow handling
- `FirstLaunchUiTest` — first-launch onboarding state, empty-state rendering

**Request flow and integration:**
- `integration/RequestFlowsIntegrationTest` — full send/receive cycles (GET, POST, auth, variables)
- `integration/SettingsImpactIntegrationTest` — setting changes propagate to request behavior
- `integration/RequestSettingsPersistenceWorkflowIntegrationTest` — settings survive save+reload
- `integration/HistorySidebarSyncIntegrationTest` — history list stays in sync after requests
- `integration/CopyCommandIntegrationTest` — "Copy as cURL / Kotlin / JS" output correctness

**Settings and environments:**
- `SettingsDialogUiTest` — settings dialog open/close, field mutation, save
- `EnvironmentEditorUiTest` — environment creation/editing, active-env switching
- `GlobalVariablesUiTest` — global variable add/edit/delete, popup interactions
- `VariablePopupTest` — variable popup positioning, keyboard navigation
- `components/VariablePopupContractsTest` — popup contract: trigger, select, dismiss
- `components/VariableHighlightTest` — `{{var}}` highlight rendering in URL and body editors
- `components/UrlParamSyncTest` — URL query param ↔ params table bi-directional sync

**Response and body:**
- `ResponseBodyUiTest` — response body rendering, raw/pretty/tree tabs, syntax highlighting
- `BodyTableUxTest` — key/value body table UX: add/edit/delete/enable

**Import and export:**
- `ImportExportUiTest` — Postman collection import/export round-trip via UI
- `persistence/PostmanImportIntegrationTest` — Postman import against sample collections
- `persistence/PostmanImportSapBydFixtureTest` — SAP ByDesign fixture import correctness
- `persistence/ImportExportFixturesIntegrationTest` — fixture-based import/export parity
- `persistence/ImportExportRepositoryTest` — repository-level import/export operations

**Editor-specific UI tests:**
- `EditorQaUiTest` — editor component smoke tests at the desktop level
- `EditorInteractionBugTest` — regression guards for tracked editor interaction bugs
- `EditorInputCorrectnessTest` — typed input, paste, backspace correctness
- `EditorLargePasteScrollUiTest` — scroll position after large-paste operations
- `EditorScrollAndSelectionBugTest` — scroll/selection consistency after edits
- `EditorScrollKeyClickBackspaceUndoTest` — compound keyboard+click+undo sequences
- `EditorConstraintSelectionContextMenuTest` — context-menu on constrained selections
- `EditorV2RegressionTest` — V2 editor architecture regression suite
- `EditorNoWrapRegressionTest` — no-wrap mode invariants
- `EditorKnownIssuesBugTest` — known-issue guards preventing regressions
- `GutterLayoutStabilityTest` — gutter width stability across content/fold changes
- `LargeTextEditorUiTest` — large document load/render, no jank
- `LargePayloadSaveUiTest` — large body save + reload correctness
- `SliderResizeTest` — panel-resize slider behavior

**History and toolbar:**
- `HistoryValidationUiTest` — history list content, ordering, and badge counts
- `ToolbarTooltipUiTest` — tooltip text accuracy for toolbar buttons
- `NewFeaturesUiTest` — UI presence checks for newly shipped features

**Regression and QA:**
- `UiRegressionFixTest` — general UI regression suite
- `ArchitecturalFixesUiTest` — verifies architectural changes didn't break UI contracts
- `QaFixesUiTest` — QA-driven regression fixes verification
- `QaFixesStateTest` — state-level regression checks for QA-found issues
- `state/AppStateCollectionTest` — app state collection mutation correctness

**Network and persistence:**
- `network/NetworkClientFactoryTest` — desktop-side network client factory wiring
- `persistence/SettingsRepositoryTest` — settings persistence read/write
- `persistence/TabsRepositoryTest` — tab state persistence and restore
- `persistence/WorkspaceRepositoryTest` — workspace-level persistence operations
- `components/SidebarCollectionHelpersTest` — sidebar collection helper contract
- `components/SidebarPerformanceTest` — sidebar render performance under large collections

**Platform:**
- `ui/shared/platform/PlatformStorageTest` — platform storage abstraction

### `qa-tests`
JVM integration and E2E tests against the running `sample-server`.
- `ApiClientE2ETest` — all HTTP methods, body types (JSON, form-data, urlencoded, raw, binary), auth (Basic, Bearer, API Key), dynamic variables, timing metrics, retry
- `NetworkClientWsAndMultipartE2ETest` — WebSocket connect/send/receive/disconnect; multipart upload
- `SampleCollectionE2ETest` — scripted collection run against the sample collection fixture
- `ScriptingDocsIntegrationTest` — scripting doc examples execute correctly end-to-end
- `WebSocketE2ETest` — WebSocket lifecycle: connect, send, receive, close, reconnect

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
- Variables: URL, headers, body, auth token interpolation (`{{var}}`); 4-level scope precedence
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
- Settings dialog open/close, field mutation, and persistence across save/reload
- Environment editor: create, edit, variable binding, active-environment switching
- Global variables: add/edit/delete/popup UI
- Response viewer: raw, pretty, and tree tabs; syntax highlighting
- Import/Export: Postman v2/v2.1 collection and environment import/export; SAP ByDesign fixture
- Variable popup: trigger, keyboard navigation, dismiss, `{{var}}` highlight in editors
- URL ↔ query-params table bi-directional sync
- History sidebar: list content, ordering, badge counts, sync after requests
- Copy command formats: cURL, Kotlin, JavaScript — output correctness
- Body table UX: key/value row add/edit/delete/enable
- Desktop shell rendering (layout panels, header/sidebar/main, labels)
- Tab management: open/close/reorder, overflow handling, persistence
- App state transitions: tab activation, collection mutations, first-launch defaults
- i18n completeness: all string keys present in every supported locale; locale switching
- Syntax highlighting: JSON, XML/HTML, GraphQL, JavaScript token colorization
- Code folding: brace-based, tag-based, comment-based region detection and fold state management
- Editor operations: GapBuffer insert/delete, DocumentModel line mapping, cursor/selection, undo/redo, display-line reflow, folding
- Editor ViewModel: command sequencing, state emission, diagnostic overlays
- Editor UI regressions: large-paste scroll, no-wrap mode, gutter stability, known-issue guards
- Network client factory wiring (desktop-side initialization)
- Sidebar collection helpers and performance under large collections
- Platform storage abstraction

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
- OAuth2 and JWT auth scheme E2E coverage

## CI

GitHub Actions workflow: `.github/workflows/release.yml`

- Push to `main`: quality gate, then artifact build validation across macOS, Ubuntu, and Windows.
- Tag `v*`: quality gate, then artifact build + GitHub Release publication.
