---
name: Bug report
about: Report a reproducible bug
labels: bug
---

**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Run `./gradlew :ui-desktop:run`
2. ...
3. Observe ...

**Expected behavior**
What you expected to happen.

**Environment (please complete the following information):**
 - OS: macOS/Linux/Windows + version
 - Java: version
 - App version / commit SHA:
 - Module/area: `ui-desktop`, `ui-shared`, `core-network`, `qa-tests`, etc.
 - Run mode: desktop / web (`:ui-web:wasmJsBrowserDevelopmentRun`)

**Logs / Error output**
Paste relevant logs or stack traces.

**If test-related, include command/output**
- `./gradlew :ui-desktop:desktopTest --no-daemon`
- `./gradlew :ui-shared:allTests --no-daemon`

**Additional context**
Add any other context about the problem here.
