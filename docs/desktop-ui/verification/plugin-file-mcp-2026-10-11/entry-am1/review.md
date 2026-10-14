# Batch AM — file-form MCP server names (read-only)

Date: 2026-10-11. Entry: `entry-am1`.

## Why this batch

Batch AI taught the local plugin inventory to surface MCP / hooks / scripts **names**, but only when
the manifest declares them **inline** (the keys of an object, or a string array). The reverse
engineering contract (`docs/desktop-ui/reverse-engineering/plugins-package-consumption-contract-2026-10-07.md`
§1 table row `mcpServers`, §3.3) records the other observed form: the manifest declares `mcpServers`
as a **string path** (in the real cache, `./.mcp.json`), and that file's top-level `mcpServers`
object keys are the server IDs. Before this batch that `.mcp.json` was always filed under
`MCP (unsupported)`, so the server names were invisible.

This batch adds that one observed form, reusing the bounded read that batch AK introduced for apps.
It reads **names only**.

## Writes

| File | Change |
| --- | --- |
| `modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopUserPlugins.kt` | string-path `mcpServers`/`mcp` → read the file's top-level `mcpServers` object keys; generalize `readAppsFile` into `readNamesFile`; skip the consumed file in `ASSET_PATHS`; add capability KDoc |
| `modules/bundle/desktop/test/im/hikaru/harness/bundle/desktop/DesktopUserPluginsTest.kt` | +3 tests (path yields sorted names; `mcp` alias; unreadable/escaping declarations stay descriptors) |
| `apps/jvm-app/test/im/hikaru/harness/desktop/plugins/DesktopInstalledPluginRepositoryTest.kt` | +1 end-to-end test (file-form MCP names reach the detail page) |
| `docs/desktop-ui/replication-plan-2026-10-07.md` | new AM row |
| this review + `files.sha256` + `logs/` | evidence |

## Change detail

`DesktopUserPlugins.kt`:

- The MCP branch no longer goes through `capabilityNames`. It now mirrors the apps branch: the
  inline object-key and string-array forms are unchanged, and a string path resolves inside the
  source root (`relativeDirectory` guard) and is read by `readMcpFile` → `readNamesFile(path,
  "mcpServers", MAX_CAPABILITIES)`.
- `readNamesFile` keeps only the top-level object keys of the named object, rejects duplicates and
  over-limit lists, and returns `null` on any failure (missing, not a regular file, not JSON, no such
  object, escaping path). A `null` result records `MCP (unsupported)` instead of an invented list.
- The asset-scan loop skips a declaration file the manifest already pointed to (`consumed`), so a
  successfully read `.mcp.json` no longer doubles as `MCP (unsupported)`.
- Renamed the private `MAX_APPS_FILE` to `MAX_DECLARATION_FILE` (same 64 KiB cap, now shared).

Safety: only server IDs are stored. `command`, `args`, `env`, `env_vars`, endpoints and credentials
are never read into the snapshot, nothing is connected or authorized, and no process is started.
There is no UI or schema change (`capabilities` schema stays 3); the names flow through the existing
`PluginCapabilitySummary` rows.

## Acceptance

| Suite | Result |
| --- | --- |
| `desktop` module `DesktopUserPluginsTest` | **27/27** (was 24; +3) — `logs/desktop-module-DesktopUserPluginsTest.log` |
| `jvm-app` targeted plugins (repository + interaction + screenshot) | **39/39** (was 38; +1) — `logs/jvm-app-targeted-plugins.log` |
| full `jvm-app` | **649 found / 5 skipped / 644 started / 644 passed / 0 failed**, exit 0 — `logs/jvm-app-full.log` |

The first full `jvm-app` attempt (`logs/jvm-app-full-attempt1-flaky-pty.log`) hit one failure:
`PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds` threw a 30 s
`ComposeTimeoutException`. That test is unrelated to plugin parsing and is load-flaky — the class
passes in isolation (`logs/pty-font-isolated-rerun.log`, 3/3) and the second full run is clean. No
plugin test failed in either full run.

## Boundaries and residual

- Names only. This does **not** connect, authorize, list tools of, or execute any MCP server. Real
  MCP wiring stays open.
- Only the `mcpServers`/`mcp` string-path form with a top-level `mcpServers` object is newly
  enumerated. When the manifest names the file differently (e.g. `mcp.json` as the declaration
  value), the asset-scan entry still handles the bare files on disk; a declaration file with a
  different top-level key stays a descriptor.
- Hooks and scripts still have no observed file form, so their declaration files remain descriptors.
