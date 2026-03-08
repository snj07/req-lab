# ReqLab

> A lightweight, open-source API client for macOS, Windows, and Linux — built with Kotlin and Compose Multiplatform.

ReqLab lets you craft, send, and inspect HTTP requests right from your desktop, without telemetry, accounts, or cloud sync required.

---

## Features

### Request Editor
- **HTTP methods** — GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD
- **URL bar** — live two-way sync between the URL and the Params table
- **Query Parameters** — inline key-value editor, toggle individual params on/off
- **Headers** — key-value editor with system-header detection and user/system labels
- **Request Body** — JSON, GraphQL, Form Data, URL-encoded, Raw Text, Binary
- **Authentication** — None, Basic, Bearer token, API Key, JWT, OAuth 2.0
- **Pre-request Scripts** — run code before a request fires
- **Test Scripts** — assert conditions after a response arrives
- **Retry Controls** — configure attempt count and delay between retries
- **Copy as cURL** — one-click cURL command generation
- **Save requests** — persist edits between sessions

### Collections & Workspaces
- Organise requests into named collections with nested folders
- Drag-and-drop reordering
- Workspace state persisted automatically on close

### Environments & Variables
- Multiple named environments (Local, Staging, Production, …)
- Variable interpolation using `{{variableName}}` syntax anywhere in URL, headers, body, or auth fields
- Secret variables masked in the UI

### Tabs
- Open multiple requests simultaneously in independent tabs
- Unsaved indicator (●) per tab
- Per-tab close button; right-click context menu for close/close others/close all
- Keyboard shortcuts: `⌘↵` Send · `⌘S` Save · `⌘W` Close tab · `⌘N` New tab

### Response Viewer
- Status code, status text, response time, and payload size at a glance
- Pretty-printed and raw response body views
- Response headers panel

### Network Log
- Live event stream showing request lifecycle (started, retries, success, errors)
- Colour-coded log levels (info, warning, error, success)

### Settings
- Configurable request timeout
- HTTP proxy support (HTTP and HTTPS)
- Follow/ignore redirects toggle
- Auto-save on edit
- Confirm-before-delete prompt
- Light and dark theme

---

## Getting Started

### Run from source

ReqLab currently runs from source. See [DEVELOPMENT.md](DEVELOPMENT.md) for prerequisites.

```bash
git clone https://github.com/snj07/req-lab.git
cd req-lab
./gradlew :ui-desktop:run
```

> **Requirements:** JDK 17 or later (JDK 21 recommended).

### Pre-built binaries

Packaged releases are planned. Watch the [Releases](https://github.com/snj07/req-lab/releases) page for updates.

---

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `⌘↵` / `Ctrl+Enter` | Send request |
| `⌘S` / `Ctrl+S` | Save request |
| `⌘W` / `Ctrl+W` | Close current tab |
| `⌘N` / `Ctrl+N` | New tab |
| `⌘,` / `Ctrl+,` | Open Settings |
| `⌘⇧[` / `Ctrl+Shift+[` | Move tab left |
| `⌘⇧]` / `Ctrl+Shift+]` | Move tab right |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.1 (Multiplatform) |
| UI | Compose Multiplatform 1.8 |
| HTTP engine | Ktor Client 3.1 |
| Serialization | kotlinx.serialization |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle with Kotlin DSL |

---

## Contributing

Bug reports and pull requests are welcome. Please open an issue first for significant changes so the approach can be discussed.

Developer setup, module layout, build commands, and testing instructions are in [DEVELOPMENT.md](DEVELOPMENT.md).

---

## License

Apache 2.0 — see [LICENSE](LICENSE) for details.
