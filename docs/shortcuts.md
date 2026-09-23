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
| `Home` / `End` | Move to start/end of current line |
| `⌘+Home` / `Ctrl+Home` | Move to document start |
| `⌘+End` / `Ctrl+End` | Move to document end |
| `⌘+↑` / `⌘+↓` | Move to document start/end (macOS) |
| `PgUp` / `PgDn` | Scroll by one viewport height |
| `⌥+←` / `⌥+→` | Jump to previous/next word (macOS) |
| `Ctrl+←` / `Ctrl+→` | Jump to previous/next word (Windows/Linux) |
| `⌘+G` / `Ctrl+G` | Go to line |

### Editing

| Shortcut | Action |
|---|---|
| `Enter` | Insert newline with auto-indent matching current line's leading whitespace |
| `Tab` | Insert 2 spaces |
| `Shift+Tab` | Remove up to 2 leading spaces from the current line |
| `Backspace` | Delete character before cursor (or active selection) |
| `Delete` | Delete character after cursor |
| `⌘+Shift+D` / `Ctrl+Shift+D` | Duplicate current line |
| `⌥+↑` / `⌥+↓` | Move current line up / down |
| `⌘+/` / `Ctrl+/` | Toggle line comment (`//` or `<!-- -->`) |
| `⌘+L` / `Ctrl+L` | Select current line |

### Selection

| Shortcut | Action |
|---|---|
| `Shift + any navigation key` | Extend selection in that direction |
| `⌘+A` / `Ctrl+A` | Select all |
| Double-click | Select word under cursor |

### Find

| Shortcut | Action |
|---|---|
| `⌘+F` / `Ctrl+F` | Toggle find (and replace when the editor is editable) |
| `Enter` / `F3` | Next match |
| `Shift+Enter` / `Shift+F3` | Previous match |
| `Esc` | Close find / go-to-line |

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
| `⌘+Shift+Z` / `Ctrl+Shift+Z` / `⌘+Y` / `Ctrl+Y` | Redo |

> Read-only editors (e.g. the response body viewer) support `⌘+C` and `⌘+A` but ignore all other editing keys.
