# Changelog

All notable changes to ReqLab are documented here.

---

## [1.17.0] — 2026-08-29

### Added

- **HTTP streaming in the request tab**: SSE (`text/event-stream`) and NDJSON responses are read line by line on a single POST instead of buffering the full body. Tokens appear live while the request is in flight. After completion, Body shows the assembled answer, RAW shows each event payload, and Timing reports TTFB, time to first token, and time to last token.
- **LLM script helpers**: Post-request scripts can assert on `reqlab.response.streamEvents`, `reqlab.response.llm.assembledText`, `reqlab.response.llm.finishReason`, `reqlab.response.llm.usage`, and `reqlab.response.llm.jsonContent()`.
- **OpenAI-compatible sample-server mocks**: `GET /v1/models`, `POST /v1/chat/completions` (stream and non-stream), `POST /v1/chat/ndjson`, `POST /v1/embeddings`, plus tool calls, JSON mode, 401/429/500, slow, and early-close paths. `?demo=true` streams a multi-token assistant reply (~200ms per token) so streaming is visible in the UI; `?chunkMs=` overrides the delay.
- **LLM collection fixtures**: Folder **LLM (OpenAI-compatible)** in `qa-tests/fixtures/reqlab-test-collection.json`, with environment variables `llmBaseUrl`, `llmApiKey`, and `llmModel`. Use **LLM Chat Completions Stream (visible)** to watch tokens arrive.

---

## [1.16.0] — 2026-06-07

### Fixed

- **Response Headers tab — text not selectable**: Header key/value `Text` nodes were not wrapped in `SelectionContainer`, making it impossible to select or copy header content. Each header row is now individually wrapped so text can be selected and copied.
- **Response Cookies tab — text not selectable**: Same issue as above applied to cookie rows. Each cookie row is now individually wrapped in `SelectionContainer`.
- **Search bar — no auto-focus on open**: When the search bar was opened via the toolbar button or `Cmd/Ctrl+F`, the text field was not focused automatically and the user had to click on it before typing. The search input now receives focus immediately on appearance via `FocusRequester`, allowing instant typing after the shortcut.
- **Selected environment not restored on restart**: After closing and reopening the app, the active environment always reset to the first one in the list. The selected environment name is now persisted via `SettingsRepository` (`settings.selectedEnvName`) and resolved back to the correct environment by name after the workspace is loaded on startup.
- **System header values not editable (`Accept`, `User-Agent`)**: `syncSystemHeaders()` was unconditionally overwriting the values of `Accept` and `User-Agent` back to their defaults on every body-type change and request send, discarding any user edits. Only `Content-Type` is now force-updated (it derives from the selected body type); `Accept` and `User-Agent` are only inserted when missing, preserving any values the user has set.
