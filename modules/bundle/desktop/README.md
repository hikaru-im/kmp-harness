# Desktop Bundle

The Desktop bundle is the production composition module. `DesktopPluginCatalog` and `DesktopProfileBundle` expose the same 12 rows: logger, settings-file, credentials-local, llm, llm-koog-openai, session, agent, tools, agent-loop, session-api, session-persistence and llm-retry.

`startDesktopProfile()` resolves and watches the DSH-compatible profile under one Desktop Harness home. Home resolution is `explicit path -> HARNESS_HOME -> ~/.harness`; plugin configuration cannot replace it. Session Room storage is fixed by the launcher at `$HARNESS_HOME/harness-sessions.db` and profile rows cannot override its path or factory.

The default profile starts without OpenAI provider settings. The provider catalog remains dormant and AgentLoop registers a factory without creating an Agent.
