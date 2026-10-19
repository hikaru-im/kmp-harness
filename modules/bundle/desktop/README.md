# Desktop Bundle

The Desktop bundle is the production composition module. `DesktopProfileBundle` mounts 17 rows: logger, settings-file, credentials-local, llm, llm-koog-openai, session, agent, tools, subprocess, the platform shell tool (`tool-bash` on POSIX, `tool-pwsh` on Windows), tool-subagent, tool-subagent-control, tool-subagent-control/list-agents, agent-loop, session-api, session-persistence and llm-retry.

`DesktopPluginCatalog` compiles both shell tools so the platform-selected row can be changed by a profile patch; the default profile mounts exactly one of them.

## Profile migration

`exec-process` and the old `subagent-tools` row are not part of the approved Desktop composition and are no longer mounted by the default bundle. Profiles that list either row must remove it manually. The old `exec` tool is gone: shell execution is now `bash` (`tool-bash`) on POSIX and `pwsh` (`tool-pwsh`) on Windows, both over the `subprocess` local runner. They execute directly, without a sandbox plugin. Subagent delegation is `tool-subagent` (tool `subagent`), with `tool-subagent-control` (`send_message`, `interrupt_agent`) and the separately loadable `tool-subagent-control/list-agents` (`list_agents`).

`startDesktopProfile()` resolves and watches the DSH-compatible profile under one Desktop Harness home. Home resolution is `explicit path -> HARNESS_HOME -> ~/.harness`; plugin configuration cannot replace it. Session Room storage is fixed by the launcher at `$HARNESS_HOME/harness-sessions.db` and profile rows cannot override its path or factory.

The default profile starts without OpenAI provider settings. The provider catalog remains dormant and AgentLoop registers a factory without creating an Agent.
