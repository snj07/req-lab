# ReqLab Features

ReqLab is a desktop-first API testing tool built with Kotlin and Compose Multiplatform. It is designed for fast request iteration, scriptable validations, and reproducible API test workflows.

## Overview

ReqLab supports end-to-end API testing with:

- HTTP request authoring and execution
- Pre-request scripting
- Post-request scripting
- Multi-scope variables (environment, global, collection, request-local)
- Assertion-based response validation
- Reusable collections and environments
- Deterministic local sample server for testing

## Supported Features

### HTTP Request Execution

- HTTP methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, `HEAD`
- URL editing with query parameter table synchronization
- Header editing and request body editing
- Request body support for JSON, GraphQL, form-style payloads, and raw text
- Authentication modes: None, Basic, Bearer, API Key, JWT (OAuth2 planned)
- Retry controls and timeout behavior
- Copy request as `curl`

### Response Validation and Inspection

- Response status code and status text
- Response headers
- Response body (`text` and JSON parsing in scripts)
- Response timing (`responseTime`)
- Response size (`size`)
- Structured pass/fail test result reporting

### Code Editor

ReqLab features a full-featured code editor used across request body editing, script editing, and response viewing. All capabilities are built with pure Compose Multiplatform and work cross-platform (macOS, Linux, Windows, Web).

**Syntax Highlighting** — Token-level colorization for:
- JSON (keys, strings, numbers, booleans, null, braces)
- XML / HTML (tags, attributes, values, comments, doctypes)
- GraphQL (keywords, types, fields, directives, comments)
- JavaScript (keywords, builtins, strings, comments, numbers, operators)
- Plain text (default)

**Code Folding** (response viewer) — Collapse and expand regions:
- Brace-based folding for JSON, JavaScript, and GraphQL (`{ }`, `[ ]`)
- Tag-based folding for XML and HTML (`<tag>...</tag>`)
- Multi-line comment folding (`/* ... */`, `<!-- ... -->`)
- Fold All / Unfold All toolbar controls
- Fold marker badges showing hidden line count

**Search / Find** — In-editor text search:
- Case-insensitive search with match count
- Previous / Next match navigation with active match highlighting
- Keyboard-driven toggle (toolbar button)

**Formatting** — Auto-format source code:
- JSON pretty-print (indented with 2-space indent)
- XML / HTML indentation
- Toggle on/off from toolbar

**Editor Features**:
- Line numbers gutter (all editor modes)
- Word wrap toggle
- Copy to clipboard (toolbar button)
- Download to file (response viewer)
- Monospace font family
- Virtualized rendering (LazyColumn) for large responses (>200 lines)
- Full text selection support in read-only mode

### Script Runtime

ReqLab provides a JavaScript runtime for request automation.

- **Pre-request scripts**: run before dispatch to mutate outgoing request
- **Post-request scripts**: run after response to assert behavior
- Runtime namespace default: `reqlab` (global aliases also supported)
- Console logging via `reqlab.console.log(...)`

### Variable System

ReqLab supports four variable scopes:

1. **Environment variables**
   - `reqlab.environment.get/set/unset`
   - Persisted with selected environment/workspace
2. **Global variables**
   - `reqlab.globals.get/set/unset`
   - Persisted workspace-wide
3. **Collection variables**
   - `reqlab.collectionVariables.get/set/unset`
   - Session-scoped runtime map across requests
4. **Request-local variables**
   - `reqlab.variables.get/set/unset`
   - Request-scoped lifecycle (pre-request to post-request for the same request)

Variable interpolation uses `{{name}}` in URL, headers, body, and auth fields.

### Assertions and Test APIs

- Named tests: `reqlab.test(name, fn)`
- Core assertions:
  - equality: `equal`, `eql`
  - type/value checks: `true`, `false`, `null`, `undefined`, `ok`, `exist`
  - numeric checks: `above`, `below`, `at.least`, `at.most`
  - collections/strings: `include`, `match`, `oneOf`, `lengthOf`, `property`
  - negation: `not.*`

### Request Mutation APIs

Pre-request scripts can mutate outgoing request values:

- `reqlab.request.setHeader(name, value)`
- `reqlab.request.setQueryParam(name, value)`
- `reqlab.request.setMethod(method)`
- `reqlab.request.setUrl(url)`
- `reqlab.request.setBody(content)`

### Collections and Test Automation

- Collection import/export using `qa-tests/fixtures/reqlab-test-collection.json`
- **Postman Collection v2 / v2.1 import** — auto-detected and converted to ReqLab format
  - Folders, requests, headers, auth (bearer / basic / API key), body (raw JSON, form-data, urlencoded, GraphQL, binary), and scripts
  - Postman `pm.*` script namespace automatically rewritten to `reqlab.*`
  - `pm.sendRequest` calls commented out (unsupported)
- **Postman Environment import** — name and enabled variables imported; disabled variables skipped
- Request-level pre-request and post-request scripts in collection items
- Automated collection validation via `qa-tests/collection-validator.mjs`
- Deterministic sample-server endpoints for reproducible test runs

### Sample Server

The `sample-server` module provides local endpoints for deterministic API testing, including:

- status endpoints (`/status/200`, `/status/201`)
- JSON payload endpoints (`/json/user`, `/json/array`, `/json/object`)
- header and cookie test endpoints (`/headers`, `/cookies`)
- timing/body endpoints (`/response-time`, `/string-body`, `/echo-body`)
- additional API routes for QA and integration scenarios

## Scripting Capabilities

### Lifecycle

1. **Pre-request phase**
   - Resolve variables
   - Execute pre-request script
   - Apply request mutations and variable writes
2. **HTTP execution phase**
   - Send request with resolved/mutated values
3. **Post-request phase**
   - Build response context
   - Execute post-request script
   - Produce assertion results and pass/fail status

### Script API Areas

- `reqlab.response` (code/status/time/size/body/headers)
- `reqlab.request` (read context + mutation helpers)
- `reqlab.environment`
- `reqlab.globals`
- `reqlab.collectionVariables`
- `reqlab.variables`
- `reqlab.expect`
- `reqlab.test`
- `reqlab.console`

### Script Example

```javascript
reqlab.request.setHeader("X-Request-Id", "req-123")
reqlab.variables.set("localToken", "abc")

reqlab.test("status and schema basics", function () {
  const body = reqlab.response.json()
  reqlab.expect(reqlab.response.code).to.equal(200)
  reqlab.expect(body).to.have.property("id")
  reqlab.expect(body.name).to.be.ok
})
```

## Test Automation

ReqLab test automation includes:

- module-level automated tests for scripting, network, storage, model, and UI
- QA E2E tests in `qa-tests`
- collection-level validator for request scripts and assertions
- sample-server-assisted deterministic integration coverage

## Example Validation Workflow

```bash
# 1) Start deterministic sample API
./gradlew :sample-server:run

# 2) Run core automated suites
./gradlew :core-scripting:desktopTest :core-network:allTests :core-storage:allTests :core-model:allTests

# 3) Validate request collection against local server
node qa-tests/collection-validator.mjs
```

## Notes

- Script runtime is synchronous; asynchronous callbacks are not awaited before request dispatch.
- Scripts currently apply to HTTP requests only; WebSocket requests do not run pre-request/post-request scripts.
- Collection variables are session-scoped (not persisted as environment/global workspace values).
- For full script API details, see `docs/scripts/api-reference.md`.
