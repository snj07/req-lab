# ReqLab Scripting Guide (Complete Reference)

This document is the full scripting reference for ReqLab’s built-in JavaScript runtime.

ReqLab supports two script execution points:

- **Pre-request script**: runs before request dispatch.
- **Test script**: runs after response is received.

Both use the same API surface.

---

## 1) Runtime model

### Namespace prefix

- Default namespace: `reqlab`
- Configurable in **Settings → Scripts → Script namespace prefix**
- Example custom prefix: `api` (then use `api.test(...)`, `api.expect(...)`, etc.)

### Backward-compatible global aliases

The runtime also exposes unprefixed globals for compatibility:

- `test`, `expect`, `response`, `request`
- `environment`, `globals`, `collectionVariables`, `variables`
- short aliases: `env`, `global`, `collection`, `vars`
- `console.log(...)`

### Execution behavior

- Scripts run in a sandboxed JS runtime.
- Execution is **synchronous** in request flow.
- Async callbacks (for example delayed callbacks) are not guaranteed to affect the current request before send completes.
- Script runtime returns:
  - test results
  - logs
  - variable mutations
  - request mutations
  - error (if any)

### Scope limitation

- Scripts currently apply to **HTTP requests**.
- WebSocket requests do not execute pre-request/test scripts.

---

## 2) API surface (everything supported)

## 2.1 Test registration

| API | Description |
|---|---|
| `reqlab.test(name, fn)` | Registers and executes one named test block |

Example:

```javascript
reqlab.test("Status is 200", function () {
  reqlab.expect(reqlab.response.code).to.equal(200)
})
```

---

## 2.2 Assertions (`reqlab.expect`)

Start an assertion chain:

```javascript
reqlab.expect(actual)
```

### Chain words (syntax sugar)

Supported chain words:

`to`, `be`, `been`, `is`, `that`, `which`, `and`, `has`, `have`, `with`, `at`, `of`, `same`, `but`, `does`, `still`, `also`, `a`, `an`

### Positive / negative toggle

| API | Description |
|---|---|
| `.not` | Negates next assertion |

### Equality / inclusion / match

| API | Notes |
|---|---|
| `.equal(expected)` | strict equality, with deep fallback for objects/arrays |
| `.eql(expected)` | alias of `equal` |
| `.equals(expected)` | alias of `equal` |
| `.include(value)` | works for strings, arrays, and object property presence |
| `.contain(value)` | alias of `include` |
| `.includes(value)` | alias of `include` |
| `.contains(value)` | alias of `include` |
| `.match(regexOrPattern)` | accepts regex or string pattern |

### Numeric assertions

| API | Notes |
|---|---|
| `.above(n)` | greater than |
| `.greaterThan(n)` | alias of `above` |
| `.below(n)` | less than |
| `.lessThan(n)` | alias of `below` |
| `.at.least(n)` | greater than or equal |
| `.at.most(n)` | less than or equal |

### Set/object/length assertions

| API | Notes |
|---|---|
| `.oneOf([a, b, c])` | value exists in list |
| `.property(name)` / `.have.property(name)` | object contains key |
| `.lengthOf(n)` | array/string length equals `n` |
| `.length(n)` | alias of `lengthOf` |

### Getter assertions

| API | Notes |
|---|---|
| `.exist` | value is not `null`/`undefined` |
| `.ok` | truthy |
| `.empty` | `""`, `null`, `undefined`, empty array, or empty object |
| `.null` | strictly `null` |
| `.true` | strictly `true` |
| `.false` | strictly `false` |
| `.undefined` | strictly `undefined` |

---

## 2.3 Response API (`reqlab.response`)

| API | Type | Description |
|---|---|---|
| `response.code` | number \| null | HTTP status code |
| `response.statusCode` | number \| null | alias of `code` |
| `response.status` | string | status text (for known codes) |
| `response.responseTime` | number \| null | request duration in ms |
| `response.time` | number \| null | alias of `responseTime` |
| `response.size` | number \| null | payload size in bytes |
| `response.text()` | string | response body string (empty string if null body) |
| `response.json()` | object \| array \| null | parses JSON body |
| `response.headers.get(name)` | string \| undefined | case-insensitive header lookup |
| `response.statusIs(code)` | void | throws if status code differs |

Notes:

- `response.json()` throws if body is invalid JSON.
- `response.status` is populated for mapped HTTP status codes.

---

## 2.4 Request API (`reqlab.request`)

### Read access

| API | Description |
|---|---|
| `request.url` | current URL (after pre-request mutations if any) |
| `request.method` | current method (after mutations if any) |
| `request.body` | current body (after mutations if any) |
| `request.headers.get(name)` | reads original header or mutated value |
| `request.query.get(name)` / `request.params.get(name)` | reads query param |

### Request mutation APIs

| API | Description |
|---|---|
| `request.setHeader(name, value)` | set/replace outgoing header |
| `request.headers.add(name, value)` | add header (set semantics) |
| `request.headers.upsert(name, value)` | upsert header |
| `request.setQueryParam(name, value)` | set/replace query parameter |
| `request.setMethod(method)` | set HTTP method (uppercased) |
| `request.setUrl(url)` | replace request URL |
| `request.setBody(content)` | replace body content |

Best practice: perform request mutations in pre-request scripts.

---

## 2.5 Variables API

ReqLab exposes four variable scopes.

### Environment scope

| API |
|---|
| `reqlab.environment.get(key)` |
| `reqlab.environment.set(key, value)` |
| `reqlab.environment.unset(key)` |
| `reqlab.environment.clear()` |
| `reqlab.environment.has(key)` |
| `reqlab.environment.toObject()` |

### Global scope

| API |
|---|
| `reqlab.globals.get(key)` |
| `reqlab.globals.set(key, value)` |
| `reqlab.globals.unset(key)` |
| `reqlab.globals.clear()` |
| `reqlab.globals.has(key)` |
| `reqlab.globals.toObject()` |

### Collection scope

| API |
|---|
| `reqlab.collectionVariables.get(key)` |
| `reqlab.collectionVariables.set(key, value)` |
| `reqlab.collectionVariables.unset(key)` |
| `reqlab.collectionVariables.clear()` |
| `reqlab.collectionVariables.has(key)` |
| `reqlab.collectionVariables.toObject()` |

### Request-local merged scope (`variables`)

| API |
|---|
| `reqlab.variables.get(key)` |
| `reqlab.variables.set(key, value)` |
| `reqlab.variables.unset(key)` |

`reqlab.variables.get(key)` resolution order:

1. request-local values set with `reqlab.variables.set`
2. environment scope
3. collection scope
4. global scope

---

## 2.6 Logging API

| API | Description |
|---|---|
| `reqlab.console.log(...args)` | writes to script/console output |
| `console.log(...args)` | global alias |

Notes:

- `null` and `undefined` are logged safely.
- Objects are JSON-stringified; circular objects are logged as `[Object]` fallback.

---

## 3) Pre-request script patterns

## 3.1 Header/query/body mutation

```javascript
var runId = "run-" + Date.now()
reqlab.environment.set("runId", runId)

reqlab.request.headers.upsert("X-Run-Id", runId)
reqlab.request.setQueryParam("source", "pre-script")
reqlab.request.setBody('{"from":"pre-request"}')
```

## 3.2 Method/URL override

```javascript
reqlab.request.setMethod("POST")
reqlab.request.setUrl("https://api.example.com/v2/items")
```

## 3.3 Defensive variable handling

```javascript
var token = reqlab.globals.get("token")
reqlab.request.headers.upsert("Authorization", token ? "Bearer " + token : "")
```

---

## 4) Test script patterns

## 4.1 Status/body assertions

```javascript
reqlab.test("status is 200", function () {
  reqlab.expect(reqlab.response.code).to.equal(200)
})

reqlab.test("response has id", function () {
  var body = reqlab.response.json()
  reqlab.expect(body).to.have.property("id")
})
```

## 4.2 Timing/size assertions

```javascript
reqlab.test("time under 2s", function () {
  reqlab.expect(reqlab.response.responseTime).to.be.below(2000)
})

reqlab.test("payload not empty", function () {
  reqlab.expect(reqlab.response.size).to.be.above(0)
})
```

## 4.3 Extract value for next request

```javascript
reqlab.test("token exists", function () {
  var token = reqlab.response.json().token
  reqlab.expect(token).to.exist
  reqlab.environment.set("token", String(token))
})
```

---

## 5) Error handling and result semantics

- A thrown top-level script error sets script `error` and marks execution failed.
- `reqlab.test(...)` captures failures per test block; mixed pass/fail is supported.
- Overall test script success is false if any assertion fails.

Recommended pattern:

```javascript
reqlab.test("json parse safe", function () {
  var threw = false
  try { reqlab.response.json() } catch (e) { threw = true }
  reqlab.expect(threw).to.be.false
})
```

---

## 6) Professional usage guidelines

- Keep pre-request logic deterministic and idempotent.
- Prefer explicit test names for readable test reports.
- Use `toObject()` and `has()` for scope introspection when debugging.
- Guard optional values (`null`/`undefined`) before strict assertions.
- Avoid async timing-dependent logic in request-critical paths.

---

## 7) Related docs

- Supplemental overview: [docs/scripts/README.md](docs/scripts/README.md)
- API-focused reference: [docs/scripts/api-reference.md](docs/scripts/api-reference.md)
