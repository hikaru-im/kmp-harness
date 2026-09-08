package im.hikaru.harness.bundle.desktop

import im.hikaru.harness.home.HarnessHome
import java.nio.file.Path

public fun resolveDesktopHarnessHome(explicit: Path? = null): HarnessHome =
    resolveDesktopHarnessHome(
        explicit = explicit,
        environment = System::getenv,
        userHome = Path.of(System.getProperty("user.home")),
    )

internal fun resolveDesktopHarnessHome(
    explicit: Path?,
    environment: (String) -> String?,
    userHome: Path,
): HarnessHome =
    HarnessHome.at(
        explicit
            ?: environment("HARNESS_HOME")?.takeIf(String::isNotBlank)?.let(Path::of)
            ?: userHome.resolve(".harness")
    )
