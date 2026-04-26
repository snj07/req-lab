# Script API Reference

All APIs are available under the configurable namespace prefix (default: `reqlab`). The global (unprefixed) form is also supported for backward compatibility.

## Test and assertion

| Form | Description |
|---|---|
| `reqlab.test(name, fn)` | Register a named test block |
| `reqlab.expect(actual).to.equal(expected)` | Strict equality |
| `reqlab.expect(actual).to.eql(expected)` | Deep equality |
| `reqlab.expect(actual).to.include(text)` | Substring or array containment |
| `reqlab.expect(actual).to.match(/regex/)` | Regex match |
| `reqlab.expect(actual).to.be.oneOf([a, b])` | Value in array |
| `reqlab.expect(actual).to.be.above(n)` | Greater than |
| `reqlab.expect(actual).to.be.below(n)` | Less than |
| `reqlab.expect(actual).to.be.at.least(n)` | Greater than or equal |
| `reqlab.expect(actual).to.be.at.most(n)` | Less than or equal |
| `reqlab.expect(actual).to.exist` | Not null/undefined |
| `reqlab.expect(actual).to.be.ok` | Truthy |
| `reqlab.expect(actual).to.be.empty` | Empty string / array |
| `reqlab.expect(actual).to.be.null` | Strictly null |
| `reqlab.expect(actual).to.be.true` | Strictly true |
| `reqlab.expect(actual).to.be.false` | Strictly false |
| `reqlab.expect(actual).to.be.undefined` | Strictly undefined |
| `reqlab.expect(obj).to.have.property(key)` | Object has property |
| `reqlab.expect(arrOrStr).to.have.lengthOf(n)` | Length equals n |
| `reqlab.expect(actual).to.not.equal(expected)` | Not equal |

Chain words supported in assertion syntax:
- `.to`, `.be`, `.been`, `.is`, `.that`, `.which`, `.and`, `.has`, `.have`, `.with`, `.at`, `.of`, `.same`, `.but`, `.does`, `.still`, `.also`, `.a`, `.an`

Examples:

```javascript
reqlab.test("status + payload", function () {
	const body = reqlab.response.json()
	reqlab.expect(reqlab.response.code).to.equal(200)
	reqlab.expect(body.type).to.be.oneOf(["Subscriber", "Admin"])
	reqlab.expect(body).to.have.property("name")
	reqlab.expect(body.name).to.match(/Jane/)
})
```

## `reqlab.response`

| Property / Method | Description |
|---|---|
| `reqlab.response.code` | HTTP status code (number, e.g. `200`) |
| `reqlab.response.status` | HTTP status text (e.g. `"OK"`) |
| `reqlab.response.responseTime` | Round-trip time in milliseconds |
| `reqlab.response.size` | Response body size in bytes |
| `reqlab.response.text()` | Response body as a plain string |
| `reqlab.response.json()` | Response body parsed as JSON; supports path access (`reqlab.response.json().user.id`) |
| `reqlab.response.headers.get(name)` | Response header value by name (case-insensitive) |

Notes:
- `reqlab.response.json()` throws if body is not valid JSON.
- `reqlab.response.headers.get(name)` is case-insensitive.
- `reqlab.response.size` and `reqlab.response.responseTime` are numeric.

## `reqlab.request`

| Property / Method | Description |
|---|---|
| `reqlab.request.url` | Request URL (read) |
| `reqlab.request.method` | Request HTTP method (read) |
| `reqlab.request.body` | Request body (read) |
| `reqlab.request.headers.add(name, value)` | Add a header (pre-request only) |
| `reqlab.request.headers.upsert(name, value)` | Set or replace a header (pre-request only) |
| `reqlab.request.query.get(name)` | Read a query parameter |

Supported mutators:
- `reqlab.request.setHeader(name, value)`
- `reqlab.request.setQueryParam(name, value)`
- `reqlab.request.setMethod(method)`
- `reqlab.request.setUrl(url)`
- `reqlab.request.setBody(content)`

Mutation semantics:
- Pre-request scripts can mutate the outgoing request.
- Post-request scripts can read `reqlab.request.*` values from the executed request context.

## Variable scopes

| Scope | Get | Set | Unset | Lifecycle |
|---|---|---|---|---|
| Environment | `reqlab.environment.get(key)` | `reqlab.environment.set(key, val)` | `reqlab.environment.unset(key)` | Environment-scoped; persisted with workspace/environment state |
| Globals | `reqlab.globals.get(key)` | `reqlab.globals.set(key, val)` | `reqlab.globals.unset(key)` | Workspace-wide; persisted with workspace state |
| Collection | `reqlab.collectionVariables.get(key)` | `reqlab.collectionVariables.set(key, val)` | `reqlab.collectionVariables.unset(key)` | Session-scoped runtime map; available across requests during current app session |
| Request-local merged view | `reqlab.variables.get(key)` | `reqlab.variables.set(key, val)` | `reqlab.variables.unset(key)` | Request-scoped overrides (pre-request → test of same request), not persisted |

Backward-compat aliases: `env`, `global`, `collection`, `vars`.

Resolution order for `reqlab.variables.get(key)`:
1. request-local values set via `reqlab.variables.set`
2. environment values
3. collection variables
4. globals

Undefined-safe patterns:

```javascript
const val = reqlab.globals.get("optionalKey")
reqlab.environment.set("safeOptional", val == null ? "" : String(val))

reqlab.test("missing keys are safe", function () {
	reqlab.expect(reqlab.globals.get("missingGlobal")).to.be.undefined
	reqlab.expect(reqlab.collectionVariables.get("missingCollection")).to.be.undefined
	reqlab.expect(reqlab.variables.get("missingRequestVar")).to.be.undefined
})
```

## Logging

- `reqlab.console.log(arg1, arg2, ...)`
- `console.log(arg1, arg2, ...)` (global alias)

## Async behavior

- Scripts run in a synchronous request pipeline.
- Promise APIs may exist depending on runtime, but asynchronous callbacks are not awaited before request dispatch.
- Use pre-request scripts for synchronous preparation only.

## Production recommendations

- Keep pre-request logic deterministic and idempotent.
- Avoid relying on async timing (`setTimeout`, delayed Promise callbacks).
- Guard optional values with null/undefined checks before assertions.
- Prefer explicit named tests (`reqlab.test("name", fn)`) for clear UI reporting.

