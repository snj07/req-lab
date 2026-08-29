# ReqLab Scripting Guide (Complete Reference)

This document is the full scripting reference for ReqLab’s built-in JavaScript runtime.

ReqLab supports two script execution points:

- **Pre-request script**: runs before request dispatch.
- **Post-request script**: runs after response is received.

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
- WebSocket requests do not execute pre-request/post-request scripts.

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
| `response.statusOk()` | void | throws if status is not 2xx |
| `response.hasHeader(name, value?)` | void | throws if header absent or value mismatches |
| `response.to.have.status(code)` | void | Postman-compatible chain — calls `statusIs(code)` |
| `response.to.have.header(name, value?)` | void | Postman-compatible chain — calls `hasHeader(name, value?)` |
| `response.to.be.ok` | void | Postman-compatible chain — calls `statusOk()` |
| `response.streamEvents` | array | Raw SSE/NDJSON event payloads (empty for non-stream responses) |
| `response.llm.assembledText` | string | Concatenated assistant text from stream deltas or `choices[0].message.content` |
| `response.llm.finishReason` | string \| null | `stop`, `tool_calls`, etc. |
| `response.llm.usage` | object \| null | Token usage object when the provider sent one (`prompt_tokens`, `completion_tokens`, `total_tokens`) |
| `response.llm.jsonContent()` | object \| null | Parses `assembledText` as JSON (JSON-mode chat); `null` if it is not JSON |

Notes:

- `response.json()` throws if body is invalid JSON.
- `response.status` is populated for mapped HTTP status codes.
- For SSE, `response.text()` / Body view use assembled assistant text, not the raw `data:` frames. Use `response.streamEvents` or the RAW tab for individual chunks.

```javascript
reqlab.test("streamed chat", function () {
  reqlab.expect(reqlab.response.llm.assembledText).to.include("Hello")
  reqlab.expect(reqlab.response.llm.finishReason).to.equal("stop")
  reqlab.expect(reqlab.response.streamEvents.length).to.be.above(0)
})
```

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
| `reqlab.environment.replaceIn(template)` |

### Global scope

| API |
|---|
| `reqlab.globals.get(key)` |
| `reqlab.globals.set(key, value)` |
| `reqlab.globals.unset(key)` |
| `reqlab.globals.clear()` |
| `reqlab.globals.has(key)` |
| `reqlab.globals.toObject()` |
| `reqlab.globals.replaceIn(template)` |

### Collection scope

| API |
|---|
| `reqlab.collectionVariables.get(key)` |
| `reqlab.collectionVariables.set(key, value)` |
| `reqlab.collectionVariables.unset(key)` |
| `reqlab.collectionVariables.clear()` |
| `reqlab.collectionVariables.has(key)` |
| `reqlab.collectionVariables.toObject()` |
| `reqlab.collectionVariables.replaceIn(template)` |

### Request-local merged scope (`variables`)

| API |
|---|
| `reqlab.variables.get(key)` |
| `reqlab.variables.set(key, value)` |
| `reqlab.variables.unset(key)` |
| `reqlab.variables.has(key)` |

`reqlab.variables.get(key)` resolution order:

1. request-local values set with `reqlab.variables.set`
2. environment scope
3. collection scope
4. global scope

`reqlab.variables.has(key)` checks all four scopes in the same order.

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

## 2.7 Info API (`reqlab.info`)

Read-only metadata about the current script execution context.

| API | Type | Description |
|---|---|---|
| `reqlab.info.requestName` | string | Name of the current request (empty string) |
| `reqlab.info.requestId` | string | Internal request ID (empty string) |
| `reqlab.info.iteration` | number | Current iteration number (1-based) |
| `reqlab.info.iterationCount` | number | Total number of iterations (1) |
| `reqlab.info.eventName` | string | Script event name (empty string) |

Note: These are stub values. ReqLab does not have a collection runner, so iteration-related fields always return their default.

---

## 2.8 Stub APIs (`reqlab.iterationData`, `reqlab.cookies`)

These APIs are present to prevent runtime errors when importing Postman collections that use them. They do not provide real data.

### `reqlab.iterationData`

| API | Returns |
|---|---|
| `.get(key)` | always `undefined` |
| `.has(key)` | always `false` |
| `.toObject()` | always `{}` |

### `reqlab.cookies`

| API | Returns |
|---|---|
| `.get(name)` | always `undefined` |
| `.has(name)` | always `false` |
| `.jar()` | returns a no-op jar object with `.get()`, `.set()`, `.unset()`, `.clear()` |

---

## 2.9 `reqlab.sendRequest()` — HTTP sub-requests from scripts

`reqlab.sendRequest()` lets you make a real HTTP request from inside a pre-request or test script. The callback runs after the HTTP response arrives, and any `reqlab.test()` assertions or `reqlab.environment.set()` calls inside the callback are merged into the overall script result.

### Signature

```javascript
reqlab.sendRequest(urlOrOptions, callback)
```

### String URL form (simple GET)

```javascript
reqlab.sendRequest("https://api.example.com/token", function(err, resp) {
    reqlab.environment.set("token", resp.json().token)
})
```

### Options object form (POST with body and headers)

```javascript
reqlab.sendRequest({
    url: reqlab.variables.get("baseUrl") + "/auth",
    method: "POST",
    header: [{ key: "Content-Type", value: "application/json" }],
    body: {
        mode: "raw",
        raw: JSON.stringify({ user: "alice", password: "s3cr3t" })
    }
}, function(err, resp) {
    reqlab.environment.set("authToken", resp.json().access_token)
})
```

### Options object fields

| Field | Type | Description |
|---|---|---|
| `url` | string | Request URL (required) |
| `method` | string | HTTP method, default `"GET"` |
| `header` | array or object | Headers — array of `{key, value}` objects or plain key/value map |
| `headers` | object | Alternative header map (same as `header` when it's an object) |
| `body` | string or `{raw, mode}` | Request body. Pass a string directly or a Postman-style `{mode:"raw", raw:"..."}` object |

### Callback response object (`resp`)

| Property/Method | Description |
|---|---|
| `resp.code` | HTTP status code (integer) |
| `resp.status` | Status text, e.g. `"OK"` |
| `resp.responseTime` | Elapsed milliseconds |
| `resp.json()` | Parses response body as JSON |
| `resp.text()` | Returns raw response body as string |
| `resp.headers.get(name)` | Returns a response header by name (case-insensitive) |

### Using assertions in the callback

Assertions added inside the callback contribute to the overall test result:

```javascript
reqlab.sendRequest("{{baseUrl}}/api/token", function(err, resp) {
    reqlab.test("token endpoint returns 200", function() {
        reqlab.expect(resp.code).to.equal(200)
    })
    reqlab.test("token is present", function() {
        reqlab.expect(resp.json().token).to.exist
    })
})
```

### Typical pattern: fetch auth token before the main request

```javascript
// Pre-request script
reqlab.sendRequest({
    url: reqlab.variables.get("baseUrl") + "/api/token",
    method: "POST",
    header: [{ key: "Content-Type", value: "application/json" }],
    body: { mode: "raw", raw: JSON.stringify({ user: "alice", role: "admin" }) }
}, function(err, resp) {
    var tok = resp.json().token
    reqlab.environment.set("authToken", tok)
    reqlab.request.headers.upsert("Authorization", "Bearer " + tok)
})
```

### Error handling

If the sub-request fails (network error, invalid URL), the error is appended to the script logs and the callback is not invoked. The main script continues normally.

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

## 4) Post-request script patterns

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
- Overall post-request script success is false if any assertion fails.

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

---

## 8) Postman migration guide

When a Postman collection is imported, ReqLab automatically rewrites all `pm.*` calls and the even older `postman.*` sandbox calls to their `reqlab.*` equivalents. The table below shows every mapping applied.

### 8.1 Modern `pm.*` API (Postman v6+)

| Postman | ReqLab | Notes |
|---|---|---|
| `pm.test(name, fn)` | `reqlab.test(name, fn)` | |
| `pm.expect(v)` | `reqlab.expect(v)` | |
| `pm.response.to.have.status(code)` | `reqlab.response.statusIs(code)` | Converted before generic `pm.response` replacement |
| `pm.response.to.have.header(name)` | `reqlab.response.hasHeader(name)` | |
| `pm.response.to.be.ok` | `reqlab.response.statusOk()` | |
| `pm.response` | `reqlab.response` | Catch-all for remaining `pm.response.*` access |
| `pm.request` | `reqlab.request` | Full object reference |
| `pm.environment.get(k)` | `reqlab.environment.get(k)` | |
| `pm.environment.set(k, v)` | `reqlab.environment.set(k, v)` | |
| `pm.environment.unset(k)` | `reqlab.environment.unset(k)` | |
| `pm.environment.clear()` | `reqlab.environment.clear()` | |
| `pm.environment.has(k)` | `reqlab.environment.has(k)` | |
| `pm.globals.get(k)` | `reqlab.globals.get(k)` | Preserved in globals scope |
| `pm.globals.set(k, v)` | `reqlab.globals.set(k, v)` | |
| `pm.globals.unset(k)` | `reqlab.globals.unset(k)` | |
| `pm.globals.clear()` | `reqlab.globals.clear()` | |
| `pm.globals.has(k)` | `reqlab.globals.has(k)` | |
| `pm.collectionVariables.get(k)` | `reqlab.collectionVariables.get(k)` | Preserved in collection scope |
| `pm.collectionVariables.set(k, v)` | `reqlab.collectionVariables.set(k, v)` | |
| `pm.collectionVariables.unset(k)` | `reqlab.collectionVariables.unset(k)` | |
| `pm.collectionVariables.clear()` | `reqlab.collectionVariables.clear()` | |
| `pm.collectionVariables.has(k)` | `reqlab.collectionVariables.has(k)` | |
| `pm.variables.get(k)` | `reqlab.variables.get(k)` | Merged scope with priority resolution |
| `pm.variables.set(k, v)` | `reqlab.variables.set(k, v)` | |
| `pm.variables.unset(k)` | `reqlab.variables.unset(k)` | |
| `pm.variables.has(k)` | `reqlab.variables.has(k)` | |
| `pm.info` | `reqlab.info` | Stub — see Section 2.7 |
| `pm.iterationData` | `reqlab.iterationData` | Stub — see Section 2.8 |
| `pm.cookies` | `reqlab.cookies` | Stub — see Section 2.8 |
| `pm.execution.setNextRequest(req)` | `reqlab.execution.setNextRequest(req)` | Supported via execution API mapping |
| `pm.execution.skipRequest()` | `reqlab.execution.skipRequest()` | Supported via execution API mapping |
| `pm.sendRequest(url, cb)` | `reqlab.sendRequest(url, cb)` | Fully supported — see Section 2.9 |

### 8.2 Legacy `postman.*` API (Postman pre-v6)

Collections exported from older versions of Postman use a `postman.*` namespace and global sandbox variables. All of these are converted on import.

| Postman (old) | ReqLab | Notes |
|---|---|---|
| `postman.setEnvironmentVariable(k, v)` | `reqlab.environment.set(k, v)` | |
| `postman.getEnvironmentVariable(k)` | `reqlab.environment.get(k)` | |
| `postman.clearEnvironmentVariable(k)` | `reqlab.environment.unset(k)` | |
| `postman.clearEnvironmentVariables()` | `reqlab.environment.clear()` | |
| `postman.setGlobalVariable(k, v)` | `reqlab.globals.set(k, v)` | Preserved in globals scope |
| `postman.getGlobalVariable(k)` | `reqlab.globals.get(k)` | |
| `postman.clearGlobalVariable(k)` | `reqlab.globals.unset(k)` | |
| `postman.clearGlobalVariables()` | `reqlab.globals.clear()` | |
| `postman.setNextRequest(req)` | `reqlab.execution.setNextRequest(req)` | Supported via execution API mapping |
| `responseBody` (global string) | `response.text()` | Old sandbox global |
| `responseCode.code` (global object) | `response.code` | Old sandbox global |
| `responseCode.name` | `response.status` | Old sandbox global |

### 8.3 Example — full legacy login script

Original Postman script (old API):

```javascript
var jsonData = JSON.parse(responseBody);
if (responseCode.code === 200) {
    console.log("Login succeeded for user " + postman.getEnvironmentVariable('USERNAME'));
    postman.setEnvironmentVariable('SESSION_ID', jsonData.sessionId);
    postman.setEnvironmentVariable('ORG_ID', jsonData.orgId);
} else {
    postman.setNextRequest(null);
    console.log("Login failed for user " + postman.getEnvironmentVariable('USERNAME'));
}
```

After ReqLab import (converted automatically):

```javascript
var jsonData = JSON.parse(response.text());
if (response.code === 200) {
    console.log("Login succeeded for user " + reqlab.environment.get('USERNAME'));
    reqlab.environment.set('SESSION_ID', jsonData.sessionId);
    reqlab.environment.set('ORG_ID', jsonData.orgId);
} else {
  reqlab.execution.setNextRequest(null);
  console.log("Login failed for user " + reqlab.environment.get('USERNAME'));
}
```

### 8.4 What is NOT automatically migrated

| Feature | Why |
|---|---|
| Unrecognized custom Postman globals/helpers | ReqLab rewrites known APIs only; non-standard helpers require manual edits |

> **Note:** `pm.sendRequest()` is fully supported and translated to `reqlab.sendRequest()` on import. See [Section 2.9](#29-reqlabsendrequest--http-sub-requests-from-scripts) for the complete API reference.