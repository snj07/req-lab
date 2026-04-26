# Keyboard Shortcuts

These shortcuts are currently implemented in the main UI key handler.

| Shortcut | Action |
|---|---|
| `⌘ + Enter` / `Ctrl + Enter` | Send request, or cancel active in-flight request |
| `⌘ + Shift + [` / `Ctrl + Shift + [` | Move active tab left |
| `⌘ + Shift + ]` / `Ctrl + Shift + ]` | Move active tab right |
| `⌘ + S` / `Ctrl + S` | Save active request |
| `⌘ + W` / `Ctrl + W` | Close active tab |
| `⌘ + N` / `Ctrl + N` | Open new request tab |
| `⌘ + ,` / `Ctrl + ,` | Open settings dialog |

## Notes

- `⌘` applies to macOS; `Ctrl` applies to Windows/Linux.
- If no tab is active, tab-specific actions may have no effect.
- Shortcut behavior is centralized in `MainScreen`.

---

## Editor Shortcuts

These shortcuts apply inside any `CodeEditor` instance (request body, pre-request script, post-response test, response viewer).

### Navigation

| Shortcut | Action |
|---|---|
| `←` / `→` / `↑` / `↓` | Move cursor by character/line |
| `Home` / `End` | Move to start/end of current display line |
| `⌘+Home` / `Ctrl+Home` | Move to document start |
| `⌘+End` / `Ctrl+End` | Move to document end |
| `PgUp` / `PgDn` | Scroll by one viewport height |
| `Ctrl+←` / `Ctrl+→` | Jump to previous/next word boundary |

### Editing

| Shortcut | Action |
|---|---|
| `Enter` | Insert newline with auto-indent matching current line's leading whitespace |
| `Tab` | Insert 4 spaces |
| `Shift+Tab` | Remove up to 4 leading spaces from the current line |
| `Backspace` | Delete character before cursor (or active selection) |
| `Delete` | Delete character after cursor |

### Selection

| Shortcut | Action |
|---|---|
| `Shift + any navigation key` | Extend selection in that direction |
| `⌘+A` / `Ctrl+A` | Select all |
| Double-click | Select word under cursor |

### Clipboard

| Shortcut | Action |
|---|---|
| `⌘+C` / `Ctrl+C` | Copy selection (works in read-only mode too) |
| `⌘+X` / `Ctrl+X` | Cut selection |
| `⌘+V` / `Ctrl+V` | Paste at cursor |

### History (edit mode only)

| Shortcut | Action |
|---|---|
| `⌘+Z` / `Ctrl+Z` | Undo |
| `⌘+Shift+Z` / `Ctrl+Shift+Z` / `Ctrl+Y` | Redo |

> Read-only editors (e.g. the response body viewer) support `⌘+C` and `⌘+A` but ignore all other editing keys.
