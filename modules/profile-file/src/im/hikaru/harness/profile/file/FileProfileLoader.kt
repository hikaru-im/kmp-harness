package im.hikaru.harness.profile.file

import im.hikaru.harness.home.HarnessHome
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.profile.EntryPatch
import im.hikaru.harness.profile.ProfileBundleCatalog
import im.hikaru.harness.profile.ProfileComposition
import im.hikaru.harness.profile.ProfileManifest
import im.hikaru.harness.profile.ProfilePatchReload
import im.hikaru.harness.profile.composeProfile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public data class ProfileLoadRequest(
    val overlays: List<Path> = emptyList(),
    val launcherPatches: List<EntryPatch> = emptyList(),
)

public data class LoadedFileProfile(
    val manifest: ProfileManifest,
    val entries: List<Entry>,
    val warnings: List<String>,
    val watchedFiles: List<Path>,
)

/** Reads and composes the platform's DSH-compatible Harness home. */
public class FileProfileLoader(
    public val home: HarnessHome,
    private val bundles: ProfileBundleCatalog,
    private val defaultManifestName: String = "harness-profile",
    private val defaultBundles: List<String> = emptyList(),
    private val defaultPatchReload: ProfilePatchReload = ProfilePatchReload.Live,
    private val patchCodec: ProfilePatchYamlCodec = ProfilePatchYamlCodec(),
) {
    public suspend fun load(request: ProfileLoadRequest): LoadedFileProfile =
        withContext(Dispatchers.IO) {
            initialize()
            val manifestPath = home.manifest
            val patchPath = home.profilePatch
            val manifest = readManifest(manifestPath)
            val layers = mutableListOf<List<EntryPatch>>()

            manifest.bundles.forEach { name ->
                layers += bundles.require(name).patches
            }
            layers += readPatch(patchPath)
            request.overlays.forEach { overlay ->
                layers += readPatch(overlay.toAbsolutePath().normalize())
            }
            layers += request.launcherPatches

            val composed: ProfileComposition = composeProfile(layers = layers)
            validateEntries(composed.entries)

            LoadedFileProfile(
                manifest = manifest,
                entries = composed.entries,
                warnings = composed.warnings,
                watchedFiles =
                    buildList {
                        add(manifestPath)
                        add(patchPath)
                        addAll(request.overlays.map { it.toAbsolutePath().normalize() })
                    }.distinct(),
            )
        }

    private fun initialize() {
        home.directory.createDirectories()
        val manifestPath = home.manifest
        if (!Files.exists(manifestPath)) {
            val manifest =
                ProfileManifest(
                    name = defaultManifestName,
                    bundles = defaultBundles,
                    patchReload = defaultPatchReload,
                )
            Files.writeString(manifestPath, ProfileManifestCodec.encode(manifest))
        }
        val patchPath = home.profilePatch
        if (!Files.exists(patchPath)) {
            Files.writeString(patchPath, PROFILE_PATCH_TEMPLATE)
        }
        val rootPath = home.profileRoot
        if (!Files.exists(rootPath) || Files.readString(rootPath) != EMPTY_ROOT) {
            Files.writeString(rootPath, EMPTY_ROOT)
        }
    }

    private fun readManifest(path: Path): ProfileManifest =
        try {
            ProfileManifestCodec.decode(Files.readString(path), path.toString())
        } catch (error: Throwable) {
            throw ProfileFileException(ProfileFileStage.Manifest, path, error)
        }

    private fun readPatch(path: Path): List<EntryPatch> =
        try {
            patchCodec.decode(Files.readString(path), path.toString())
        } catch (error: Throwable) {
            throw ProfileFileException(ProfileFileStage.Patch, path, error)
        }

    private fun validateEntries(entries: List<Entry>) {
        val ids = linkedSetOf<String>()
        entries.forEach { entry ->
            require(entry.id.isNotBlank()) { "Profile entry id must not be blank" }
            require(entry.name.isNotBlank()) { "Profile entry '${entry.id}' name must not be blank" }
            require(ids.add(entry.id)) { "Duplicate profile entry id '${entry.id}'" }
        }
    }

    private companion object {
        const val EMPTY_ROOT = "[]\n"
        const val PROFILE_PATCH_TEMPLATE =
            "# Applied after every compiled bundle layer.\n" +
                "# Config overrides replace the complete config object.\n" +
                "[]\n"
    }
}

public enum class ProfileFileStage {
    Manifest,
    Patch,
}

public class ProfileFileException(
    public val stage: ProfileFileStage,
    public val path: Path,
    cause: Throwable,
) : IllegalStateException("Failed to read ${stage.name.lowercase()} file $path", cause)
