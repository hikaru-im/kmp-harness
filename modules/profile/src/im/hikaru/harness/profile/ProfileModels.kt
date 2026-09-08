package im.hikaru.harness.profile

import im.hikaru.harness.loader.Entry
import kotlinx.serialization.json.JsonElement

/** Lifecycle of the user-owned profile patch document. */
public enum class ProfilePatchReload(public val manifestValue: String) {
    Startup("startup"),
    Live("live"),
    ;

    public companion object {
        public fun parse(value: String): ProfilePatchReload =
            entries.firstOrNull { it.manifestValue == value }
                ?: error("Profile patchReload must be 'startup' or 'live': $value")
    }
}

/** DSH-compatible profile metadata read from the platform Harness home. */
public data class ProfileManifest(
    val name: String,
    val bundles: List<String>,
    val patchReload: ProfilePatchReload,
)

/** Distinguishes an absent override from an explicit null configuration. */
public sealed interface PatchValue<out T> {
    public data object Absent : PatchValue<Nothing>

    public data class Present<T>(val value: T) : PatchValue<T>
}

/**
 * Portable subset of DSH's loader patch row.
 *
 * group/inject/intercept/isolate are deliberately not represented because the
 * KMP runtime has no equivalent semantics. File codecs must reject them.
 */
public data class EntryPatch(
    val id: String? = null,
    val insert: List<Entry>? = null,
    val name: String? = null,
    val config: PatchValue<JsonElement?> = PatchValue.Absent,
    val disabled: PatchValue<Boolean> = PatchValue.Absent,
)

/** One compiled bundle package and the patch layer declared by its manifest. */
public data class ProfileBundle(
    val name: String,
    val patches: List<EntryPatch>,
)

/** Compiled replacement for Node package resolution across all KMP targets. */
public class ProfileBundleCatalog(
    bundles: List<ProfileBundle>,
) {
    private val byName = bundles.associateBy(ProfileBundle::name)

    init {
        bundles.forEach { bundle ->
            require(bundle.name.isNotBlank()) { "Profile bundle name must not be blank" }
        }
        require(byName.size == bundles.size) {
            "Profile bundle catalog contains duplicate names"
        }
    }

    public fun require(name: String): ProfileBundle =
        byName[name] ?: error("Profile bundle '$name' is not compiled into this host")

    public val names: Set<String>
        get() = byName.keys
}

/** Result of applying a complete, already flattened patch list. */
public data class ProfileComposition(
    val entries: List<Entry>,
    val warnings: List<String>,
)
