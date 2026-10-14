# Shell refactor acceptance capture

Date: 2026-10-05

Acceptance pass for the App Rail + Workspace Navigator shell refactor. Every image is an
unmodified window capture of the local Desktop app (`jvm-app`), taken with
`screencapture -l <windowID>` so the PNG equals the window size exactly.

## Environment

| Field | Value |
| --- | --- |
| Operating system | macOS 26.6.2 |
| System appearance | Light (window chrome); Harness content theme is dark |
| Display | 2560×1440, UI looks like 2560×1440, scale 100% |
| Harness revision | `7e7d52091eaa1d68d4c5b3c45b5b18d3f86cacb8` plus 31 uncommitted files |
| Launch | `./kotlin run -m jvm-app -- --home <temp> --window-size <WxH> --destination <dest>` |
| Harness home | isolated temp directory, no credentials, no account |
| Locale | Chinese (reference contract asks for `en-US`; see caveats) |
| Codex reference | `/Applications/ChatGPT.app` `26.930.31730`, SHA-256 `64023959…776e`, user screenshot of the live window |

## Captures

| Image | Destination | Window | Result |
| --- | --- | --- | --- |
| `home-1440x900.png` | home | 1440×900 | Pass: four-region layout, labeled New Task row, Composer dock, rail 64, navigator ~273 |
| `home-1280x800.png` | home | 1280×800 | Pass: layout stable, no clipping |
| `home-1024x768.png` | home | 1024×768 | Pass: rail, navigator, header and content all remain visible |
| `hosts-1440x900.png` | host | 1440×900 | Pass: host name plus `Host ID` shown, rail shows no selected item |
| `account-1440x900.png` | account | 1440×900 | Pass: connection status card is shown here, with login form below |
| `settings-1440x900.png` | settings | 1440×900 | Pass: section navigation (General/Appearance/Models/Plugins/Shortcuts) plus content |
| `plugins-1440x900.png` | plugins | 1440×900 | Pass: plugin inventory row with enable state |
| `sessions-1440x900.png` | sessions | 1440×900 | Pass: thread creation fields plus Composer dock |

Rail selection was verified both visually and in code: Home selects the first icon, Plugins
selects the puzzle icon, and Hosts selects nothing because it is not a rail destination.

## Structural comparison with the Codex reference

Agreement:

- Two-column left side: narrow icon rail plus a workspace navigator, then the main surface.
- Rail width 64 and navigator width ~273 sit inside the Codex 240–520 preferred range.
- Dark content theme, connection badge, and section headings in the navigator.
- Settings is a sectioned page rather than a flat placeholder.

Remaining differences, in priority order:

1. **Rail account entry.** Codex places the account avatar at the rail bottom. Harness opens Account
   from the existing page route and still has no bottom avatar action.
2. **Navigator sections are empty.** `最近任务` and `项目` render their empty states because there
   is no Project/Task catalog yet; Codex shows populated trees.
3. **No right Project Context Panel.** The panel outlet exists but is not mounted by default.
4. **Header title.** Home shows `主页`; Codex shows the active thread title.
5. **Window chrome contrast.** System appearance is Light, so the macOS title bar is light above
   dark content. This is an environment difference, not a layout defect.

## States still unverified

The fixture file `fixtures/desktop-reference-fixtures.json` is not wired into the app, so these
matrix states cannot be produced yet without real data: `conversation`, `streaming`, `tool`,
`error`, `no-host`, and `sidebar-collapsed`. The `login`, `settings`, and empty states are covered
above.

## Caveats

- Captures use the Chinese locale; the reference contract asks for `en-US`. This affects copy,
  not geometry.
- Comparison against Codex is structural, from one live Codex window capture at 2560×1440. It is
  not a measured pixel diff, so no row in `state-matrix.md` is promoted to a golden expectation.
