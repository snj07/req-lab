# ReqLab

A lightweight API client built with Kotlin and Compose Multiplatform for desktop and web workflows.

## What is ReqLab?

ReqLab helps you build, send, inspect, and validate HTTP requests without accounts, telemetry lock-in, or mandatory cloud sync.

## Core Features

- HTTP request editor with methods, URL, params, headers, auth, and multiple body types.
- Pre-request and post-request scripting with variable access and assertions.
- Collections and history with multi-tab request workflow.
- Environment and global variables with `{{variable}}` interpolation.
- Response viewer plus request lifecycle logs and test results.

## In-app Help & About

ReqLab includes an in-app **Help & About** panel with:

- About / feature overview
- How-to usage flow
- Keyboard shortcuts reference
- Scripting overview
- Version and build information

You can open it from:

- Top toolbar (`Help` icon)
- Settings dialog (`Open Help & About`)

## Keyboard Shortcuts

These shortcuts are mapped in `MainScreen` and reflect current behavior:

| Shortcut | Action |
|---|---|
| `⌘ + Enter` / `Ctrl + Enter` | Send request (or cancel in-flight request) |
| `⌘ + Shift + [` / `Ctrl + Shift + [` | Move active tab left |
| `⌘ + Shift + ]` / `Ctrl + Shift + ]` | Move active tab right |
| `⌘ + S` / `Ctrl + S` | Save active request |
| `⌘ + W` / `Ctrl + W` | Close active tab |
| `⌘ + N` / `Ctrl + N` | Create a new request tab |
| `⌘ + ,` / `Ctrl + ,` | Open Settings |

## Importing from Postman

ReqLab can import **Postman Collection v2 / v2.1** and **Postman Environment** files directly — no conversion step needed.

- Open the sidebar and click the **Import** button in the Collections or Environments section.
- Select any `.json` file exported from Postman; ReqLab detects the format automatically.
- Both ReqLab-native and Postman formats are supported by the same button.

**What is imported from a Postman collection:**
- All folders and requests (arbitrarily nested)
- URL, method, headers (disabled headers are skipped)
- Request body (raw JSON, raw text, form-data, x-www-form-urlencoded, GraphQL, binary)
- Authentication (bearer token, basic, API key; `noauth` → None)
- Pre-request and post-request (test) scripts — `pm.*` calls are automatically rewritten to `reqlab.*`

**Script conversion notes:**
- `pm.test`, `pm.expect`, `pm.response`, `pm.request`, `pm.environment.*`, `pm.globals.*`, `pm.variables.*`, `pm.collectionVariables.*` → `reqlab.*` equivalents
- `pm.sendRequest` is commented out (not supported in ReqLab)

**What is imported from a Postman environment:**
- Environment name and all enabled variables; disabled variables are skipped

## Scripting

ReqLab supports JavaScript pre-request and post-request scripts through a configurable namespace (default: `reqlab`).

- Pre-request scripts: mutate request URL/headers/body/query/variables before dispatch.
- Post-request scripts: assert status/body/headers/timing and persist extracted values.

See the full guide: [docs/scripts.md](docs/scripts.md)

## Testing

ReqLab uses layered validation across core modules, integration tests, and UI checks.

- Core module tests (`core-network`, `core-storage`, `core-scripting`)
- Integration and E2E API tests (`qa-tests`)
- Desktop UI automation smoke tests (`ui-desktop`)

See the full guide: [docs/tests.md](docs/tests.md)

## Documentation Index

- Setup and development: [DEVELOPMENT.md](DEVELOPMENT.md)
- Architecture: [docs/architecture.md](docs/architecture.md)
- Testing guide: [docs/testing.md](docs/testing.md)
- Scripting guide: [docs/scripts.md](docs/scripts.md)
- Test execution reference: [docs/tests.md](docs/tests.md)
- Shortcut reference: [docs/shortcuts.md](docs/shortcuts.md)

## Run from Source

Requirements: JDK 17+ (JDK 21 recommended).

```bash
git clone https://github.com/snj07/req-lab.git
cd req-lab
./gradlew :ui-desktop:run
```

## Contributing

Issues and pull requests are welcome. For larger changes, open an issue first to align approach and scope.

## License

Apache 2.0 — see [LICENSE](LICENSE).
