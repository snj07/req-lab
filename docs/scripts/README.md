# ReqLab Scripts

ReqLab scripts use a namespaced API under the `reqlab` prefix (configurable in **Settings → Scripts**).

## Where scripts run

- **Pre-request script** — runs before the HTTP request is built and sent.
- **Post-request script** — runs after the response is received.
- Scripts apply to HTTP requests. WebSocket requests currently do not execute pre-request or post-request scripts.

## Namespace prefix

The default namespace is `reqlab`. You can change it in **Settings → Scripts → Script namespace prefix**. Any identifier (letters, digits, `_`) is valid, e.g. `api`, `rl`, or `myns`.

The global (prefix-free) API is also supported for backward compatibility.

## Core API

| Namespaced form | Description |
|---|---|
| `reqlab.test(name, fn)` | Register a named test block |
| `reqlab.expect(value)` | Begin a Chai-style assertion chain |
| `reqlab.response.code` | HTTP status code (number) |
| `reqlab.response.status` | HTTP status text |
| `reqlab.response.responseTime` | Round-trip time in ms |
| `reqlab.response.size` | Response body size in bytes |
| `reqlab.response.text()` | Response body as plain text |
| `reqlab.response.json()` | Response body parsed as JSON |
| `reqlab.response.headers.get(name)` | Response header value |
| `reqlab.request.url` | Request URL |
| `reqlab.request.method` | Request method |
| `reqlab.request.body` | Request body |
| `reqlab.request.headers.add(name, value)` | Add a request header |
| `reqlab.request.headers.upsert(name, value)` | Set/replace a request header |
| `reqlab.environment.get/set/unset(key)` | Environment variables |
| `reqlab.globals.get/set/unset(key)` | Global variables |
| `reqlab.collectionVariables.get/set/unset(key)` | Collection variables |
| `reqlab.variables.get/set/unset(key)` | Merged variable lookup + request-local overrides |
| `reqlab.console.log(...)` | Log to the script output panel |

## Runtime behavior summary

- Scripts execute in a sandboxed JavaScript runtime with the configured namespace prefix.
- Pre-request scripts can mutate request URL, method, body, headers, and query params before send.
- Post-request scripts can read response code/status/time/size/body/headers and register multiple named tests.
- `environment`, `globals`, `collectionVariables`, and `variables` scopes are available inside scripts.
- The engine executes JavaScript synchronously for request flow; asynchronous callbacks are not awaited before request dispatch.

## Example — pre-request script

```javascript
var runId = "run-" + Date.now()
reqlab.environment.set("runId", runId)
reqlab.request.headers.upsert("X-Run-Id", runId)
reqlab.request.setQueryParam("source", "script")
reqlab.request.setBody('{"from":"pre-request"}')
reqlab.console.log("Prepared request", reqlab.request.method, reqlab.request.url)
```

## Example — post-request script

```javascript
reqlab.test("Status is 200", function() {
  reqlab.expect(reqlab.response.code).to.equal(200)
})

reqlab.test("Response time is acceptable", function() {
  reqlab.expect(reqlab.response.responseTime).to.be.below(2000)
})

reqlab.test("Payload shape is valid", function() {
  var body = reqlab.response.json()
  reqlab.expect(body).to.have.property("id")
  reqlab.expect(typeof body.id).to.equal("string")
  reqlab.expect(body.type).to.be.oneOf(["Subscriber", "Admin"])
})

reqlab.environment.set("lastStatus", String(reqlab.response.code))
reqlab.console.log("Captured lastStatus", reqlab.environment.get("lastStatus"))
```

## Alternative prefix

```javascript
// Settings → Scripts → "api"
api.test("Status is 200", function() {
  api.expect(api.response.code).to.equal(200)
})
```

## Undefined / empty-safe usage

```javascript
var g = reqlab.globals.get("suiteGlobal")
var c = reqlab.collectionVariables.get("suiteCollection")
var l = reqlab.variables.get("requestOnly")

reqlab.environment.set("safeGlobal", g == null ? "" : String(g))
reqlab.environment.set("safeCollection", c == null ? "" : String(c))
reqlab.environment.set("safeLocal", l == null ? "" : String(l))

reqlab.test("undefined getters are safe", function() {
  reqlab.expect(reqlab.globals.get("missingGlobal")).to.be.undefined
  reqlab.expect(reqlab.collectionVariables.get("missingCollection")).to.be.undefined
  reqlab.expect(reqlab.variables.get("missingVar")).to.be.undefined
})
```
