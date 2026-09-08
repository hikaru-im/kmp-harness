# Desktop Bundle

The Desktop bundle is the only production composition module that references the OpenAI Provider plugin. It exports:

- `DesktopPluginCatalog`, containing logger, settings-file, credentials-local, llm and llm-koog-openai definitions;
- `DesktopProfileBundle`, the patch layer that inserts those five explicit rows;
- `startDesktopProfile()`, which resolves and watches the single DSH-compatible profile in the Desktop Harness home.

The bundle is the only Desktop home resolver. It applies `explicit path -> HARNESS_HOME -> ~/.harness` once and injects
the resulting `HarnessHome` into profile, settings and credentials. Plugin patch documents cannot replace that home;
Desktop definitions also reject settings or credentials `path` overrides. Embedded/test definitions outside this bundle
may still use explicit file paths.

`apps/jvm-app` depends on this public bundle entrypoint and does not import Koog/OpenAI implementation types.
