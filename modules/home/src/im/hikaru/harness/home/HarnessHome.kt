package im.hikaru.harness.home

import java.nio.file.Path

/** One filesystem root already selected by the owning platform launcher. */
public class HarnessHome private constructor(
    public val directory: Path,
) {
    public val manifest: Path
        get() = resolve(PACKAGE_FILENAME)

    public val profileRoot: Path
        get() = resolve(PROFILE_ROOT_FILENAME)

    public val profilePatch: Path
        get() = resolve(PROFILE_PATCH_FILENAME)

    public val settings: Path
        get() = resolve(SETTINGS_FILENAME)

    public val credentials: Path
        get() = resolve(CREDENTIALS_FILENAME)

    public val userEnvironment: Path
        get() = resolve(USER_ENVIRONMENT_FILENAME)

    public fun resolve(name: String): Path {
        require(name.isNotBlank()) { "Harness home child name must not be blank" }
        require(name != "." && name != ".." && Path.of(name).fileName.toString() == name) {
            "Harness home child name must be a single path segment: $name"
        }
        return directory.resolve(name)
    }

    override fun equals(other: Any?): Boolean =
        other is HarnessHome && directory == other.directory

    override fun hashCode(): Int =
        directory.hashCode()

    override fun toString(): String =
        "HarnessHome($directory)"

    public companion object {
        public fun at(directory: Path): HarnessHome =
            HarnessHome(directory.toAbsolutePath().normalize())
    }
}

public const val PACKAGE_FILENAME: String = "package.json"
public const val PROFILE_ROOT_FILENAME: String = "cordis.yml"
public const val PROFILE_PATCH_FILENAME: String = "cordis.patch.yml"
public const val SETTINGS_FILENAME: String = "settings.yaml"
public const val CREDENTIALS_FILENAME: String = ".credentials.yaml"
public const val USER_ENVIRONMENT_FILENAME: String = ".env"
