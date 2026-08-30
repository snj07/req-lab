# Tests Guide

This document summarizes how to run ReqLab tests locally.

## Test Layers

- Core unit/module tests: `core-network`, `core-storage`, `core-model`, `core-scripting`
- Editor unit/module tests: `editor-core` (15 classes), `editor-ui` (3 classes)
- Cross-platform shared UI tests: `ui-shared` (16 classes)
- Integration and E2E API tests: `qa-tests`
- Desktop UI automation, integration, and persistence tests: `ui-desktop` (50+ classes)

## Common Commands

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
./gradlew :ui-web:wasmJsTest
```

Run a focused validation bundle:

```bash
./gradlew :qa-tests:jvmTest :core-storage:allTests :ui-desktop:desktopTest
```

LLM streaming E2E against the embedded sample-server:

```bash
./gradlew :qa-tests:jvmTest --tests com.reqlab.qa.LlmApiE2ETest
```

Generic SSE (`GET`/`POST /sse`) E2E:

```bash
./gradlew :qa-tests:jvmTest --tests com.reqlab.qa.SseApiE2ETest
```

MCP Streamable HTTP, legacy HTTP+SSE, stdio, sampling/roots/elicitation, LLM generate, and OAuth E2E:

```bash
./gradlew :qa-tests:jvmTest --tests com.reqlab.qa.McpHttpE2ETest
./gradlew :ui-desktop:desktopTest --tests com.reqlab.ui.desktop.McpSessionCallbackE2ETest
./gradlew :ui-desktop:desktopTest --tests com.reqlab.ui.desktop.McpCallbackPaneUiTest
```

### MCP fixtures

Import [qa-tests/fixtures/reqlab-test-collection.json](../qa-tests/fixtures/reqlab-test-collection.json) and [qa-tests/fixtures/reqlab-test-environment.json](../qa-tests/fixtures/reqlab-test-environment.json). Folder **MCP (Model Context Protocol)** covers Streamable HTTP (plain + auth + query params), legacy SSE, stdio (`{{mcpStdioCommand}}` = `sample-server`), sampling mock/manual/LLM (`{{llmBaseUrl}}` + `{{llmApiKey}}`), roots, and elicitation accept/decline.

The JS collection validator skips `STDIO` and `LEGACY_2024_11_05`. Kotlin e2e (`:qa-tests:jvmTest --tests com.reqlab.qa.McpHttpE2ETest`) is the protocol source of truth. Product guide: [docs/mcp.md](mcp.md). Mock routes and PATH shim: [DEVELOPMENT.md](../DEVELOPMENT.md).

Folder **SSE** is generic `text/event-stream` (`GET`/`POST /sse`). The **LLM (OpenAI-compatible)** folder remains OpenAI chat completions.

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

- HTTP methods, body types (JSON, form-data, urlencoded, raw, binary), auth (Basic, Bearer, API Key)
- SSE/NDJSON streaming, generic `/sse` mocks, and OpenAI-compatible `/v1` chat mocks (including `?demo=true`)
- Variable interpolation (`{{var}}`), scope precedence, and scripting runtime (pre-request, post-response, assertions, `response.llm.*`)
- Retry/error handling and WebSocket lifecycle (connect, send, receive, disconnect, reconnect)
- Settings dialog persistence and impact on request behavior
- Environment editor, global variables, variable popup UI
- Tab management, history sidebar sync, copy command formats (cURL/Kotlin/JS)
- Import/Export: Postman v2/v2.1 collections, environments, SAP ByDesign fixtures
- Editor engine: GapBuffer, DocumentModel, cursor/selection, undo/redo, folding, display-line reflow
- Editor ViewModel, syntax highlighting, code folding, i18n completeness — all in `ui-shared`
- Desktop shell, response viewer raw/pretty/tree tabs, body table UX
- Integration tests: `RequestFlowsIntegrationTest`, `SettingsImpactIntegrationTest`, `RequestSettingsPersistenceWorkflowIntegrationTest`, `HistorySidebarSyncIntegrationTest`, `CopyCommandIntegrationTest`
- E2E: `ApiClientE2ETest`, `NetworkClientWsAndMultipartE2ETest`, `WebSocketE2ETest`, `SampleCollectionE2ETest`, `ScriptingDocsIntegrationTest`

Release-quality gate also validates:

- `:editor-ui:allTests`
- `:ui-shared:desktopTest`
- `:ui-web:wasmJsTest`

For broader strategy and endpoint matrix, see [docs/testing.md](docs/testing.md).
