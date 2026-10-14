# Desktop screenshot capture report

Date: 2026-10-05

The empty-session Desktop shell was launched with an isolated home directory and captured at three window sizes:

| Capture | Dimensions | Visual check |
|---|---:|---|
| [empty-session-1440x900.png](empty-session-1440x900.png) | 1440×900 | Passed: dark shell, sidebar, status badge, and content text are readable. |
| [empty-session-1280x800.png](empty-session-1280x800.png) | 1280×800 | Passed: layout remains readable and stable. |
| [empty-session-1024x768.png](empty-session-1024x768.png) | 1024×768 | Passed: compact layout remains readable and stable. |

The PNG dimensions were verified with `file`. These are implementation captures from the local Desktop app; alignment against Codex Desktop reference pixels remains unverified.
