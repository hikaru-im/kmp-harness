# Desktop Bundle

The Desktop bundle is the production composition module. `DesktopProfileBundle` mounts 25 rows: logger, settings-file, credentials-local, llm, llm-openai, session, agent, system-prompt, tools, agent-instructions, subprocess, jobs, the platform shell tool (`tool-bash` on POSIX, `tool-pwsh` on Windows), tool-jobs, terminal, tool-terminal, tool-subagent, tool-subagent-control, tool-subagent-control/list-agents, agent-loop, session-api, session-persistence, schedule, tool-schedule and llm-retry.

`DesktopPluginCatalog` compiles both shell tools so the platform-selected row can be changed by a profile patch; the default profile mounts exactly one of them.

## Profile migration

`exec-process` and the old `subagent-tools` row are not part of the approved Desktop composition and are no longer mounted by the default bundle. Profiles that list either row must remove it manually. The old `exec` tool is gone: shell execution is now `bash` (`tool-bash`) on POSIX and `pwsh` (`tool-pwsh`) on Windows, both over the `subprocess` local runner. They execute directly, without a sandbox plugin. Subagent delegation is `tool-subagent` (tool `subagent`), with `tool-subagent-control` (`send_message`, `interrupt_agent`) and the separately loadable `tool-subagent-control/list-agents` (`list_agents`).

Background shell jobs are created by `bash`/`pwsh` with `run_in_background: true` and collected through `tool-jobs` (`job_output`, `job_list`, `job_kill`). `terminal` adds owner-scoped persistent PTYs; `tool-terminal` exposes `terminal_open`, `terminal_send`, `terminal_read`, `terminal_signal`, `terminal_close` and `terminal_list`. Background terminal sends run as `pty-send` jobs. Both services execute locally without a sandbox.

`startDesktopProfile()` resolves and watches the DSH-compatible profile under one Desktop Harness home. Home resolution is `explicit path -> HARNESS_HOME -> ~/.harness`; plugin configuration cannot replace it. Session Room storage is fixed by the launcher at `$HARNESS_HOME/harness-sessions.db` and profile rows cannot override its path or factory. Reminders use their own `$HARNESS_HOME/harness-schedule.db`; `schedule` owns delivery and `tool-schedule` exposes `schedule_create`, `schedule_list`, `schedule_update` and `schedule_delete`. A recurring reminder delivers only its latest missed occurrence after downtime, and delegated subagent sessions cannot use the tools.

The default profile starts without OpenAI provider settings. The provider catalog remains dormant and AgentLoop registers a factory without creating an Agent.
