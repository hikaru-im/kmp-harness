# Harness Home

`home` is the JVM filesystem value object for one platform-selected Harness directory. It exposes the standard
profile, settings, credentials and user-environment file locations under that directory.

The module never reads environment variables, command-line options or operating-system directories. Those choices
belong to platform launchers. Desktop currently resolves `--home`, then `HARNESS_HOME`, then `~/.harness`; future
Android and iOS launchers must supply their own platform directories.
