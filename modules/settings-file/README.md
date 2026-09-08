# Settings File

`settings-file` persists the shared settings document as YAML or JSON. Its runtime configuration contains a final file
path and never resolves environment variables or platform defaults.

Desktop injects `HarnessHome/settings.yaml` and does not expose a patch-level path override. An embedded host may build
the standalone plugin definition with an explicit `path`. Reading a missing settings file creates an owner-only empty
document before the settings service starts.
