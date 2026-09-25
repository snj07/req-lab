# Model Context Protocol (MCP) in ReqLab

ReqLab is a local-first [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) **client**. It lets you configure, inspect, and exercise MCP servers beside the HTTP APIs they depend on—using the same collections, environments, `{{variables}}`, authentication editors, and response tooling.

Save a connection, connect to the server, and work with its tools, resources, and prompts. When something needs diagnosing, the session's JSON-RPC traffic remains available in Activity without mixing it into script output.

> **Scope:** ReqLab connects to MCP servers; it does not expose an MCP server. It supports MCP 2025-06-18 over Streamable HTTP and stdio, plus the 2024-11-05 legacy HTTP+SSE transport.

This is the product guide. For the local mock server, PATH shim, and end-to-end commands, see [DEVELOPMENT.md](../DEVELOPMENT.md) and [the test guide](tests.md).

---

## Quick start

1. Create a connection from the sidebar: **Add → MCP connection**.
2. In **Client**, select **HTTP** or **stdio**. For an HTTP server, start with **Auto**; it tries the current Streamable HTTP transport and falls back to legacy HTTP+SSE when appropriate.
3. Enter the server URL or desktop stdio command. Variables resolve here just as they do in HTTP requests.
4. Click **Connect**. Confirm before ReqLab starts a local stdio process.
5. Select a tool, resource, or prompt. Results open in the shared Response pane; use **Activity** for the complete protocol exchange.

If you change connection settings while connected, reconnect before testing again.

---

## Capabilities at a glance

| Area | In the workspace |
|---|---|
| Transports | Streamable HTTP (MCP 2025-06-18), Auto (Streamable first, legacy fallback), Legacy HTTP+SSE (2024-11-05), desktop stdio |
| Session | Connected / Connecting / Error / Disconnected; Connect, Disconnect, Reconnect; protocol · HTTP mode · server name; session id with copy |
| Tools | Searchable list, Form or JSON arguments, required-field gating, read-only / destructive chips, Run / Stop |
| Resources | Search, Read, Subscribe / Unsubscribe when the server advertises it; updates re-read into Response |
| Prompts | Search, Form or JSON arguments, Get prompt; rendered messages in Response |
| Auth | None, Basic, Bearer, API Key, JWT — same editors as REST. `{{var}}` in URL, command, headers, and auth |
| Headers / Params | Same key/value tables as REST; query params stay in sync with the URL |
| Activity | Per-session JSON-RPC inspector (SENT / RECEIVED / NOTIFICATION / STATE / ERROR), expand payload, copy, Clear |
| Logs | Bottom **Logs** tab: one-line MCP summaries. **Console** is scripts and app messages only |
| Client callbacks | Sampling (mock or review + optional LLM), roots list, elicitation form, ping (always handled) |
| Persistence | Collection item `kind: MCP`; import/export of transport, HTTP mode, headers, auth, roots, sampling, and elicitation settings |

The Response pane is the same viewer as REST (status, timing, size, and pretty JSON). Nested JSON stored as a string is unwrapped for display. ReqLab does not show a Cookies tab for MCP responses.

---

## Connection lifecycle

An MCP tab retains its active session while you work elsewhere in ReqLab. It disconnects when you close the tab or select **Disconnect**.

- Open an MCP collection item (identified by an **MCP** badge), then choose **HTTP** or desktop **stdio** in the **Client** tab. HTTP exposes **Auto**, **2025-06-18**, and **Legacy** modes.
- Put the URL or command in the top bar. `{{variable}}` interpolation works here, including in headers and configured authentication.
- Select **Connect**. Status progresses through Connecting to Connected or Error.
- While connected, the bar displays the negotiated protocol, HTTP mode, server name, and a copyable **Session ID**. A UUID is shown in full; longer identifiers are visually shortened without changing what Copy returns.
- `⌘/Ctrl+Enter` runs or stops the selected tool, resource read, or prompt—not only a tool call. Results open in Response.

Reconnect if Client-tab settings change while you are connected (transport, URL/command, auth, headers, sampling, LLM, roots, elicitation).

---

## Work with server capabilities

### Tools

Pick a tool, fill arguments, Run. The screenshot is a connected Streamable HTTP session calling `add` with JSON arguments; the Response body is the JSON-RPC result.

![ReqLab MCP tools workspace — connected session, tool list, Form/JSON arguments, JSON-RPC result](images/mcp-tools.png)

- Tab label includes the tool count. Search filters by name and description. Drag the list/detail split.
- **Form** builds arguments from the JSON Schema (string, number, boolean, enum). **JSON** is a raw editor. Required fields must be filled before Run is enabled.
- Tools may show **Read-only** or **Destructive** chips from server annotations.
- **Run** / **Stop** sit on the tool pane. Stop cancels the in-flight call in ReqLab (it does not send a protocol cancel notification).
- Success and tool errors use the shared Response viewer.

### Resources

- Search the list, select a resource, **Read**. Contents appear in Response.
- If the server advertises `resources.subscribe`, **Subscribe** asks it to notify on change. ReqLab re-reads subscribed URIs on `notifications/resources/updated` and shows the new contents in Response. **Unsubscribe** stops that.

### Prompts

- Search, select a prompt, fill arguments (Form or JSON), **Get prompt**.
- Rendered messages open in Response.

---

## Activity, Logs, and Console

Three different surfaces:

| Surface | What it is |
|---|---|
| **Activity** (MCP tab) | Every JSON-RPC message for this session: SENT, RECEIVED, NOTIFICATION, STATE, ERROR. Click a row to expand the pretty payload; copy copies that JSON. **Clear** empties this list only. |
| **Logs** (bottom bar) | One-line MCP summaries for the app (connect, sent/received, errors). |
| **Console** (bottom bar) | Script `console.log` and app messages. MCP wire traffic is not echoed here. |

Use Activity when you need the payload; use Logs for a compact trail.

---

## Client tab: how ReqLab answers the server

Servers can call **back** into the client. Configure how ReqLab responds before connecting; these settings are stored on the tab and round-trip in collection JSON.

### Connection

- **Transport**: HTTP or stdio.
- **HTTP mode** (HTTP only): Auto, 2025-06-18, Legacy.

### Server callbacks

| Setting | Behavior |
|---|---|
| Auto-respond sampling **on** | Silent mock reply (`mock reply from ReqLab`). |
| Auto-respond sampling **off** | Response pane: review `sampling/createMessage` → optionally **Approve generate** (LLM URL / token / max tokens) → edit `content`, `role`, `model`, `stopReason` → **Approve send**. Cancel sends `stopReason: cancelled`. Empty URL or a failed generate still opens the editable result. |
| Auto-accept elicitation **on** | Silent `accept`. |
| Auto-accept elicitation **off** | Schema form in Response; Accept or Decline. |

Ping has no switch: ReqLab always answers `ping` with an empty result.

For unfamiliar servers, leave automatic sampling and elicitation disabled. That keeps each server-initiated request visible for review before ReqLab responds.

### Roots

URI and optional name rows. ReqLab returns them on `roots/list`. Empty state is “No folders yet” plus Add.

---

## Auth, headers, and params

MCP HTTP connections reuse the REST editors:

- **Auth**: None, Basic, Bearer, API Key, JWT.
- **Headers**: key/value table (secrets supported).
- **Query params**: edit the URL or the params table; they stay in sync.

OAuth 2.1 is not an Auth-tab option yet. If a server expects a bearer token you already have, use **Bearer**.

---

## Transports

| Spec | In ReqLab |
|---|---|
| MCP 2025-06-18 | Streamable HTTP: `POST` JSON-RPC (`Accept: application/json, text/event-stream`). Optional `Mcp-Session-Id`; `DELETE` on disconnect; optional GET SSE after handshake. |
| MCP 2024-11-05 | Legacy HTTP+SSE: `GET` for the `endpoint` event, then `POST` JSON-RPC. Replies are correlated by JSON-RPC `id` on the SSE stream. |
| MCP stdio | Local subprocess, newline-delimited JSON-RPC on stdin/stdout. Desktop only. Stderr is ignored for framing. Confirm before Connect. |

**Auto** tries Streamable HTTP and falls back to legacy when the server indicates it.

HTTP example (test environment): `{{mcpBaseUrl}}` → `http://localhost:8080/mcp`. Legacy: `{{mcpLegacyUrl}}` → `http://localhost:8080/mcp/sse`.

stdio is a **full command line** (executable plus arguments), for example `npx -y @modelcontextprotocol/server-everything` or `sample-server` after the PATH shim. Quoted paths with spaces work. Because the command starts a local process, ReqLab asks for confirmation before connecting. Review the resolved command and its variables before approving it. How ReqLab resolves PATH and installs the sample shim: [DEVELOPMENT.md](../DEVELOPMENT.md).

---

## Troubleshooting a connection

| Symptom | What to check |
|---|---|
| Connection fails immediately | Confirm the URL or stdio command after variable resolution. For HTTP, begin with **Auto** unless the server documents a specific transport. |
| A server connects but calls fail | Open **Activity** to inspect the JSON-RPC request and response, then use **Logs** for the chronological connection trail. |
| A setting change has no effect | Disconnect and reconnect after changing transport, URL/command, auth, headers, sampling, LLM, roots, or elicitation settings. |
| Resource updates do not arrive | The server must advertise `resources.subscribe`; subscribe to the resource before it sends `notifications/resources/updated`. |
| A local command does not start | Confirm the executable is on PATH, quote paths containing spaces, and review the stdio command confirmation. |

Activity is the source of truth for protocol payloads. **Console** intentionally contains script output and app messages, not MCP wire traffic.

---

## Import / export

MCP tabs persist `kind: MCP`, URL or command, transport, HTTP mode, headers, auth, roots, sampling mode, LLM URL / token / max tokens, and elicitation. Older workspace JSON without those fields still loads (defaults apply).

The desktop import/export file dialog remembers the last folder (macOS, Windows, Linux). Browsers cannot set the `<input type=file>` start directory.

---

## Keyboard shortcuts (MCP)

| Shortcut | Action |
|---|---|
| `⌘ + Enter` / `Ctrl + Enter` | Run or stop the selected tool, resource read, or get-prompt |

Connect / Disconnect is the connection-bar button, not Send.

---

## Try the sample collection

Import [qa-tests/fixtures/reqlab-test-collection.json](../qa-tests/fixtures/reqlab-test-collection.json) and [qa-tests/fixtures/reqlab-test-environment.json](../qa-tests/fixtures/reqlab-test-environment.json). Folder **MCP (Model Context Protocol)** covers Streamable HTTP, auth variants, query params, legacy SSE, stdio, sampling, roots, and elicitation.

Start the mock with `./gradlew :sample-server:run`. Routes, mock tools (`echo`, `add`, `trigger_*`, …), and stdio install: [DEVELOPMENT.md](../DEVELOPMENT.md). How tests assert this: [docs/tests.md](tests.md).

---

## Implementation map

| Area | Location |
|---|---|
| Client, handshake, pending RPC | `core-network` `McpClient` |
| Streamable HTTP / legacy SSE / stdio | `StreamableHttpTransport`, `LegacyHttpSseTransport`, `McpStdio.kt` + `McpPlatform.desktop.kt` |
| Session / UI | `ui-shared` `McpSessionState`, `McpPanel` |
| Mock protocol + HTTP routes | `sample-server` `McpMock`, `McpRoutes` |

Related: [DEVELOPMENT.md](../DEVELOPMENT.md), [docs/tests.md](tests.md), [docs/architecture.md](architecture.md).
