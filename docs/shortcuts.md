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
