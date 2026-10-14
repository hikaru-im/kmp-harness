package im.hikaru.harness.bundle.desktop

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentInstructionsService
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

public data class DesktopPluginSkill(val name: String, val description: String?, val instructions: String)
/**
 * Capability *names* the manifest declares, either inline (the keys of an object, or an array of
 * string names) or through a declaration file we can enumerate by name (the observed `.mcp.json`,
 * whose top-level `mcpServers` object keys are the server IDs). Only bounded names are kept;
 * commands, args, env, endpoints and credentials never enter the snapshot, and nothing is
 * connected, authorized or executed.
 */
public data class DesktopPluginCapabilities(
    val mcpServers: List<String> = emptyList(),
    val hooks: List<String> = emptyList(),
    val scripts: List<String> = emptyList(),
)
public data class DesktopUserPlugin(
    val id: String,
    val name: String,
    val description: String?,
    val version: String?,
    val source: String,
    val enabled: Boolean,
    val skills: List<DesktopPluginSkill>,
    val unsupportedAssets: List<String>,
    /**
     * App names the manifest declares inline (a JSON array of string names, or the keys of an `apps`
     * object) or points to with a string path (the observed `./.app.json`, whose top-level `apps`
     * object keys are the app IDs). An `apps/` directory or an apps file we cannot safely enumerate
     * stays in [unsupportedAssets]. Only bounded names are stored, never app configuration bodies.
     */
    val applications: List<String> = emptyList(),
    val capabilities: DesktopPluginCapabilities = DesktopPluginCapabilities(),
)
public data class DesktopUserPluginSnapshot(val revision: String, val plugins: List<DesktopUserPlugin>)
public class DesktopUserPluginsConflict : Exception("Plugin inventory changed; refresh and try again.")
/** Does not retain raw parser messages, source contents, or potentially secret causes. */
public class DesktopUserPluginsException(
    sanitizedMessage: String = "本机插件库存或来源无效、不受支持或不可用。",
) : Exception(sanitizedMessage)

/**
 * Registers local immutable skill text snapshots; never installs or executes code.
 * Inventory schemas 1 and 2 are read and migrated; unknown fields in each supported schema are rejected.
 * Only selected manifest metadata and capability names are stored.
 * Limits: 64 plugins, 256 total skills, 32 skills/plugin, 64 KiB/skill and manifest,
 * 512 KiB/import, 1 MiB stored skill text, 4 MiB JSON, 256 KiB composed instructions.
 * Bounded file operations are synchronous; lock contention suspends. In particular there is
 * no cancellable dispatcher handoff after the atomic commit that could hide a successful write.
 */
public class DesktopUserPlugins(document: Path) : AgentInstructionsService {
    private val document = checkedAbsolute(document)
    private val parent = this.document.parent ?: invalid()
    private val lockPath = parent.resolve(this.document.fileName.toString() + ".lock")

    public suspend fun read(): DesktopUserPluginSnapshot = sanitized {
        currentCoroutineContext().ensureActive()
        load().snapshot
    }

    public suspend fun registerLocal(
        directory: String,
        expectedRevision: String,
        isCurrent: () -> Boolean = { true },
    ): DesktopUserPluginSnapshot = sanitized {
        guard(isCurrent)
        // Import is read-only and precedes even creation of the inventory lock/parent. This also
        // rejects a document inside the source before any write could touch that source.
        val imported = importLocal(directory)
        mutate(expectedRevision, isCurrent) { records ->
            if (records.any { it.plugin.id == imported.plugin.id || it.plugin.source == imported.plugin.source }) invalid()
            records + imported
        }
    }

    public suspend fun setEnabled(
        id: String,
        enabled: Boolean,
        expectedRevision: String,
        isCurrent: () -> Boolean = { true },
    ): DesktopUserPluginSnapshot = mutate(expectedRevision, isCurrent) { records ->
        if (records.none { it.plugin.id == id }) invalid()
        records.map { if (it.plugin.id == id) it.copy(plugin = it.plugin.copy(enabled = enabled)) else it }
    }

    /** Re-import only the registered source, under the same inventory CAS and access lease. */
    public suspend fun updateLocal(
        id: String,
        expectedRevision: String,
        isCurrent: () -> Boolean = { true },
    ): DesktopUserPluginSnapshot = mutate(expectedRevision, isCurrent) { records ->
        val previous = records.singleOrNull { it.plugin.id == id } ?: invalid()
        val imported = importLocal(previous.plugin.source)
        if (imported.plugin.source != previous.plugin.source ||
            imported.plugin.name != previous.plugin.name || imported.plugin.id != id) invalid()
        val replacement = imported.copy(plugin = imported.plugin.copy(enabled = previous.plugin.enabled))
        records.map { if (it.plugin.id == id) replacement else it }
    }

    public suspend fun removeRegistration(
        id: String,
        expectedRevision: String,
        isCurrent: () -> Boolean = { true },
    ): DesktopUserPluginSnapshot = mutate(expectedRevision, isCurrent) { records ->
        if (records.none { it.plugin.id == id }) invalid()
        records.filterNot { it.plugin.id == id }
    }

    override suspend fun readInstructions(agent: Agent): String? = renderInstructions()

    /** Tests exercise the same fresh-snapshot path without constructing an unrelated Agent. */
    internal suspend fun renderInstructions(): String? = render(read().plugins)

    private suspend fun mutate(
        expectedRevision: String,
        isCurrent: () -> Boolean,
        change: (List<Record>) -> List<Record>,
    ): DesktopUserPluginSnapshot = sanitized {
        // Global rather than per-instance: also handles aliases and overlapping JVM file locks.
        processMutex.withLock {
            guard(isCurrent)
            load() // Reject existing corrupt inventory/provenance before any filesystem writes.
            prepareParent(isCurrent)
            checkTree(lockPath)
            attributes(lockPath)?.let { if (!it.isRegularFile) invalid() }
            guard(isCurrent)
            val channel = FileChannel.open(lockPath, CREATE, WRITE, NOFOLLOW_LINKS)
            var lock: FileLock? = null
            try {
                val deadline = System.nanoTime() + 5_000_000_000L
                while (lock == null) {
                    guard(isCurrent)
                    lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                    if (lock == null) {
                        if (System.nanoTime() >= deadline) invalid()
                        delay(10)
                    }
                }
                val previous = load() // Validate corruption before CAS, even for a matching SHA.
                if (previous.snapshot.revision != expectedRevision) throw DesktopUserPluginsConflict()
                guard(isCurrent)
                val records = change(previous.records)
                validateRecords(records)
                val bytes = encode(records).toByteArray(Charsets.UTF_8)
                if (bytes.size > MAX_DOCUMENT) invalid()
                val result = DesktopUserPluginSnapshot(sha(bytes), records.map { it.plugin })
                if (previous.bytes != null && previous.bytes.contentEquals(bytes)) {
                    guard(isCurrent)
                    if (load().snapshot.revision != expectedRevision) throw DesktopUserPluginsConflict()
                } else {
                    commit(bytes, expectedRevision, isCurrent)
                }
                result
            } finally {
                // Cleanup errors cannot turn a completed commit into a reported failure.
                runCatching { lock?.release() }
                runCatching { channel.close() }
            }
        }
    }

    private suspend fun commit(bytes: ByteArray, expected: String, isCurrent: () -> Boolean) {
        guard(isCurrent)
        checkTree(parent)
        val temporary = Files.createTempFile(parent, ".user-plugins-", ".tmp")
        try {
            guard(isCurrent)
            FileChannel.open(temporary, WRITE, NOFOLLOW_LINKS).use { output ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) {
                    guard(isCurrent)
                    output.write(buffer)
                }
                guard(isCurrent)
                output.force(true)
            }
            // Callbacks and temp writes can take time: detect external edits again before commit.
            guard(isCurrent)
            checkTree(lockPath)
            if (load().snapshot.revision != expected) throw DesktopUserPluginsConflict()
            currentCoroutineContext().ensureActive()
            Files.move(temporary, document, ATOMIC_MOVE, REPLACE_EXISTING)
            // Commit point: do not suspend, check cancellation, or re-read after this move.
        } finally {
            // Only the unique temp file we own; cleanup also runs on cancellation.
            runCatching { Files.deleteIfExists(temporary) }
        }
    }

    private suspend fun prepareParent(isCurrent: () -> Boolean) {
        var component = parent.root ?: invalid()
        for (part in parent) {
            component = component.resolve(part)
            val attr = attributes(component)
            if (attr == null) {
                guard(isCurrent)
                try { Files.createDirectory(component) } catch (_: FileAlreadyExistsException) {
                    if (attributes(component)?.isDirectory != true) invalid()
                }
                checkTree(component)
            } else if (!attr.isDirectory || attr.isSymbolicLink) invalid()
        }
        checkTree(document)
    }

    private fun load(): Loaded {
        checkTree(document)
        checkTree(lockPath)
        attributes(lockPath)?.let { if (!it.isRegularFile) invalid() }
        val bytes = if (attributes(document) == null) null else readBytes(document, MAX_DOCUMENT)
        val records = if (bytes == null) emptyList() else decode(strictUtf8(bytes))
        validateRecords(records)
        return Loaded(bytes, records, DesktopUserPluginSnapshot(sha(bytes ?: byteArrayOf()), records.map { it.plugin }))
    }

    private fun importLocal(directory: String): Record {
        val root = checkedAbsolute(Path.of(directory))
        checkTree(root)
        if (attributes(root)?.isDirectory != true) invalid()
        val source = root.toRealPath(NOFOLLOW_LINKS)
        if (parent.startsWith(source)) invalid() // Never store inventory/temp/lock files in a source.
        val manifestBytes = readBytes(source.resolve(".codex-plugin/plugin.json"), MAX_MANIFEST)
        val manifest = parseObject(strictUtf8(manifestBytes))
        val name = requiredString(manifest, "name", MAX_NAME)
        val description = optionalString(manifest, "description", MAX_DESCRIPTION)
        val version = optionalString(manifest, "version", MAX_NAME)
        val roots = when (val declared = manifest["skills"]) {
            null -> listOf("skills")
            is JsonPrimitive -> listOf(string(declared, MAX_PATH))
            is JsonArray -> declared.map { string(it, MAX_PATH) }.also {
                if (it.isEmpty() || it.size > MAX_SKILLS || it.distinct().size != it.size) invalid()
            }
            else -> invalid()
        }.map(::relativeDirectory)
        if (roots.distinct().size != roots.size) invalid()
        val unsupported = linkedSetOf<String>()
        if (manifest.keys.any { it !in MANIFEST_FIELDS }) unsupported += "other capabilities (unsupported)"
        for ((descriptor, keys) in ASSETS) {
            if (keys.any { manifest.containsKey(it) }) unsupported += descriptor
        }
        fun readCapabilityNames(keys: List<String>): List<String>? {
            val declared = keys.firstNotNullOfOrNull { manifest[it] } ?: return emptyList()
            return when (declared) {
                is JsonObject -> declared.keys.toList()
                is JsonArray -> declared.mapNotNull {
                    (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content
                }.takeIf { names -> names.size == declared.size }
                else -> null
            }
        }
        fun capabilityNames(descriptor: String, keys: List<String>): List<String> {
            val names = readCapabilityNames(keys) ?: run {
                unsupported += descriptor
                return emptyList()
            }
            if (names.size > MAX_CAPABILITIES || names.distinct().size != names.size) invalid()
            names.forEach { checkText(it, MAX_NAME) }
            return names.sorted()
        }
        val hooks = capabilityNames("hooks (unsupported)", listOf("hooks"))
        val scripts = capabilityNames("scripts (unsupported)", listOf("scripts"))
        // MCP servers are declared inline (the keys of a `mcpServers` object, or an array of string
        // names) or as a path to a file (the observed `./.mcp.json`, whose top-level `mcpServers`
        // object keys are the server IDs). Server commands, args, env and credentials are never read;
        // an unreadable declaration falls back to the descriptor instead of guessing.
        val mcpServers = mutableListOf<String>()
        var consumedMcpFile: Path? = null
        when (val declared = manifest["mcpServers"] ?: manifest["mcp"]) {
            null -> {}
            is JsonObject -> mcpServers += declared.keys
            is JsonArray -> {
                val names = declared.mapNotNull { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content }
                if (names.size == declared.size) mcpServers += names else unsupported += "MCP (unsupported)"
            }
            is JsonPrimitive -> if (declared.isString) {
                val file = runCatching { source.resolve(relativeDirectory(declared.content)) }.getOrNull()
                val names = file?.let(::readMcpFile)
                if (names != null) { mcpServers += names; consumedMcpFile = file }
                else unsupported += "MCP (unsupported)"
            } else unsupported += "MCP (unsupported)"
            else -> unsupported += "MCP (unsupported)"
        }
        if (mcpServers.size > MAX_CAPABILITIES || mcpServers.distinct().size != mcpServers.size) invalid()
        mcpServers.forEach { checkText(it, MAX_NAME) }
        val sortedMcpServers = mcpServers.sorted()
        // Apps are declared inline (names in an array, or the keys of an object) or as a path to an
        // apps file (the observed `./.app.json`, whose top-level `apps` object keys are the app IDs).
        // A declaration we cannot safely enumerate falls back to the descriptor so the detail page
        // stays truthful instead of guessing. Only bounded names are kept, never config bodies.
        val applications = mutableListOf<String>()
        var consumedAppsFile: Path? = null
        when (val declared = manifest["apps"]) {
            null -> {}
            is JsonArray -> {
                val names = declared.mapNotNull { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content }
                if (names.size == declared.size) applications += names else unsupported += "apps (unsupported)"
            }
            is JsonObject -> applications += declared.keys
            is JsonPrimitive -> if (declared.isString) {
                val file = runCatching { source.resolve(relativeDirectory(declared.content)) }.getOrNull()
                val names = file?.let(::readAppsFile)
                if (names != null) { applications += names; consumedAppsFile = file }
                else unsupported += "apps (unsupported)"
            } else unsupported += "apps (unsupported)"
            else -> unsupported += "apps (unsupported)"
        }
        if (applications.size > MAX_APPS || applications.distinct().size != applications.size) invalid()
        applications.forEach { checkText(it, MAX_NAME) }
        val sortedApplications = applications.sorted()
        val consumed = setOfNotNull(consumedAppsFile, consumedMcpFile)
        for ((descriptor, paths) in ASSET_PATHS) {
            for (path in paths) {
                val asset = source.resolve(path)
                checkTree(asset)
                // A manifest-declared declaration file already enumerated by name is not left unread.
                if (attributes(asset) != null && asset !in consumed) unsupported += descriptor
            }
        }
        val texts = mutableListOf<SkillText>()
        var importedBytes = manifestBytes.size
        fun addSkill(file: Path) {
            if (texts.size >= MAX_SKILLS) invalid()
            val bytes = readBytes(file, MAX_SKILL)
            importedBytes += bytes.size
            if (importedBytes > MAX_IMPORT) invalid()
            val path = source.relativize(file).joinToString("/")
            if (texts.any { it.path == path }) invalid()
            val raw = strictUtf8(bytes)
            parseSkill(raw, path)
            texts += SkillText(path, raw)
            for (attachment in children(file.parent)) {
                if (attachment.fileName.toString() != "SKILL.md") unsupported += "skill attachments (unsupported)"
            }
        }
        for (relative in roots) {
            val skillRoot = source.resolve(relative)
            checkTree(skillRoot)
            if (attributes(skillRoot)?.isDirectory != true) {
                if (!manifest.containsKey("skills") && attributes(skillRoot) == null) continue
                invalid()
            }
            val ownSkill = skillRoot.resolve("SKILL.md")
            if (attributes(ownSkill) != null) addSkill(ownSkill) else {
                for (child in children(skillRoot)) {
                    if (attributes(child)?.isDirectory == true) {
                        val file = child.resolve("SKILL.md")
                        if (attributes(file) != null) addSkill(file)
                        else unsupported += "skill attachments (unsupported)"
                    } else unsupported += "skill attachments (unsupported)"
                }
            }
        }
        if (texts.isEmpty()) invalid()
        val sorted = texts.sortedBy { it.path }
        val plugin = DesktopUserPlugin(
            registrationId(source.toString(), name), name, description, version, source.toString(), false,
            sorted.map { parseSkill(it.raw, it.path) }, unsupported.sorted(), sortedApplications,
            DesktopPluginCapabilities(sortedMcpServers, hooks, scripts),
        )
        // Reject a registration that could never fit the enabled instruction budget.
        render(listOf(plugin.copy(enabled = true)))
        val manifestDigest = sha(manifestBytes)
        return Record(plugin, manifestDigest, contentDigest(manifestDigest, sorted), sorted)
    }

    private fun readAppsFile(path: Path): List<String>? = readNamesFile(path, "apps", MAX_APPS)

    private fun readMcpFile(path: Path): List<String>? = readNamesFile(path, "mcpServers", MAX_CAPABILITIES)

    /**
     * Reads only the top-level [objectKey] object keys of a manifest-declared file (the observed
     * `./.app.json` and `./.mcp.json`). Returns null when the path is missing, not a bounded regular
     * file, not a JSON object carrying that object, or the names exceed the bounds; the caller then
     * records the unsupported descriptor instead. Configuration values, commands, endpoints and
     * credentials are never read into the snapshot.
     */
    private fun readNamesFile(path: Path, objectKey: String, maxEntries: Int): List<String>? = try {
        checkTree(path)
        val attr = attributes(path) ?: invalid()
        if (!attr.isRegularFile) invalid()
        val root = parseObject(strictUtf8(readBytes(path, MAX_DECLARATION_FILE)))
        val declared = root[objectKey] as? JsonObject ?: invalid()
        val names = declared.keys.toList()
        if (names.size > maxEntries || names.distinct().size != names.size) invalid()
        names.forEach { checkText(it, MAX_NAME) }
        names
    } catch (_: DesktopUserPluginsException) { null }

    private fun children(directory: Path): List<Path> {
        checkTree(directory)
        val entries = mutableListOf<Path>()
        Files.newDirectoryStream(directory).use { stream ->
            for (entry in stream) {
                if (entries.size >= MAX_ENTRIES) invalid()
                checkTree(entry)
                val attr = attributes(entry) ?: invalid()
                if (!attr.isDirectory && !attr.isRegularFile) invalid()
                entries.add(entry)
            }
        }
        return entries.sortedBy { it.fileName.toString() }
    }

    private fun validateRecords(records: List<Record>) {
        if (records.size > MAX_PLUGINS || records.map { it.plugin.id }.distinct().size != records.size) invalid()
        if (records.map { it.plugin.source }.distinct().size != records.size) invalid()
        var skillCount = 0
        var textBytes = 0L
        for (record in records) {
            val plugin = record.plugin
            checkText(plugin.name, MAX_NAME)
            plugin.description?.let { checkText(it, MAX_DESCRIPTION) }
            plugin.version?.let { checkText(it, MAX_NAME) }
            val source = checkedAbsolute(Path.of(plugin.source))
            if (source.toString() != plugin.source || parent.startsWith(source)) invalid()
            if (plugin.id != registrationId(plugin.source, plugin.name)) invalid()
            if (!SHA.matches(record.manifestDigest) || record.contentDigest != contentDigest(record.manifestDigest, record.texts)) invalid()
            if (record.texts.isEmpty() || record.texts.size > MAX_SKILLS) invalid()
            if (record.texts.map { it.path }.distinct().size != record.texts.size) invalid()
            if (plugin.skills != record.texts.map { parseSkill(it.raw, it.path) }) invalid()
            if (plugin.skills.map { it.name }.distinct().size != plugin.skills.size) invalid()
            if (plugin.unsupportedAssets.distinct().size != plugin.unsupportedAssets.size ||
                plugin.unsupportedAssets.any { it !in DESCRIPTORS }) invalid()
            plugin.applications.forEach { checkText(it, MAX_NAME) }
            if (plugin.applications.size > MAX_APPS || plugin.applications.distinct().size != plugin.applications.size) invalid()
            val capabilityLists = listOf(plugin.capabilities.mcpServers, plugin.capabilities.hooks, plugin.capabilities.scripts)
            capabilityLists.forEach { names ->
                if (names.size > MAX_CAPABILITIES || names.distinct().size != names.size) invalid()
                names.forEach { checkText(it, MAX_NAME) }
            }
            val copiedBytes = record.texts.sumOf { it.raw.toByteArray(Charsets.UTF_8).size.toLong() }
            if (copiedBytes > MAX_IMPORT) invalid()
            render(listOf(plugin.copy(enabled = true)))
            skillCount += record.texts.size
            textBytes += copiedBytes
            if (skillCount > MAX_TOTAL_SKILLS || textBytes > MAX_TEXT_TOTAL) invalid()
        }
        render(records.map { it.plugin })
    }

    private fun encode(records: List<Record>): String = JsonObject(mapOf(
        "schema" to JsonPrimitive(3),
        "plugins" to JsonArray(records.map { record ->
            val plugin = record.plugin
            JsonObject(mapOf(
                "id" to JsonPrimitive(plugin.id), "name" to JsonPrimitive(plugin.name),
                "description" to nullable(plugin.description), "version" to nullable(plugin.version),
                "source" to JsonPrimitive(plugin.source), "enabled" to JsonPrimitive(plugin.enabled),
                "manifestDigest" to JsonPrimitive(record.manifestDigest), "contentDigest" to JsonPrimitive(record.contentDigest),
                "skills" to JsonArray(record.texts.map { JsonObject(mapOf(
                    "path" to JsonPrimitive(it.path), "rawText" to JsonPrimitive(it.raw),
                )) }),
                "unsupportedAssets" to JsonArray(plugin.unsupportedAssets.map(::JsonPrimitive)),
                "applications" to JsonArray(plugin.applications.map(::JsonPrimitive)),
                "capabilities" to JsonObject(mapOf(
                    "mcpServers" to JsonArray(plugin.capabilities.mcpServers.map(::JsonPrimitive)),
                    "hooks" to JsonArray(plugin.capabilities.hooks.map(::JsonPrimitive)),
                    "scripts" to JsonArray(plugin.capabilities.scripts.map(::JsonPrimitive)),
                )),
            ))
        }),
    )).toString()

    private fun decode(text: String): List<Record> {
        val root = parseObject(text)
        exactKeys(root, setOf("schema", "plugins"))
        // Schemas 1/2 have no capability inventory; loading them migrates those lists to empty.
        val schema = (root["schema"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull() ?: invalid()
        if (schema !in 1..3) invalid()
        val plugins = root["plugins"] as? JsonArray ?: invalid()
        if (plugins.size > MAX_PLUGINS) invalid()
        return plugins.map { item ->
            val value = item as? JsonObject ?: invalid()
            val fields = setOf("id", "name", "description", "version", "source", "enabled",
                "manifestDigest", "contentDigest", "skills", "unsupportedAssets")
            val expectedFields = when {
                schema >= 3 -> fields + "applications" + "capabilities"
                schema >= 2 -> fields + "applications"
                else -> fields
            }
            exactKeys(value, expectedFields)
            val skills = value["skills"] as? JsonArray ?: invalid()
            if (skills.size > MAX_SKILLS) invalid()
            val texts = skills.map { skill ->
                val skillKeys = skill as? JsonObject ?: invalid()
                exactKeys(skillKeys, setOf("path", "rawText"))
                SkillText(requiredString(skillKeys, "path", MAX_PATH), requiredString(skillKeys, "rawText", MAX_SKILL))
            }
            val enabled = value["enabled"] as? JsonPrimitive ?: invalid()
            if (enabled.isString) invalid()
            val assets = value["unsupportedAssets"] as? JsonArray ?: invalid()
            if (assets.size > DESCRIPTORS.size) invalid()
            val applications = if (schema >= 2) {
                val array = value["applications"] as? JsonArray ?: invalid()
                if (array.size > MAX_APPS) invalid()
                array.map { string(it, MAX_NAME) }
            } else emptyList()
            val capabilities = if (schema >= 3) {
                val capabilityObject = value["capabilities"] as? JsonObject ?: invalid()
                exactKeys(capabilityObject, setOf("mcpServers", "hooks", "scripts"))
                fun readNames(key: String): List<String> {
                    val array = capabilityObject[key] as? JsonArray ?: invalid()
                    if (array.size > MAX_CAPABILITIES) invalid()
                    return array.map { string(it, MAX_NAME) }
                }
                DesktopPluginCapabilities(readNames("mcpServers"), readNames("hooks"), readNames("scripts"))
            } else DesktopPluginCapabilities()
            Record(DesktopUserPlugin(
                requiredString(value, "id", 64), requiredString(value, "name", MAX_NAME),
                optionalString(value, "description", MAX_DESCRIPTION), optionalString(value, "version", MAX_NAME),
                requiredString(value, "source", MAX_PATH), enabled.booleanOrNull ?: invalid(),
                texts.map { parseSkill(it.raw, it.path) }, assets.map { string(it, MAX_NAME) }, applications, capabilities,
            ), requiredString(value, "manifestDigest", 64), requiredString(value, "contentDigest", 64), texts)
        }
    }

    private data class SkillText(val path: String, val raw: String)
    private data class Record(val plugin: DesktopUserPlugin, val manifestDigest: String, val contentDigest: String, val texts: List<SkillText>)
    private data class Loaded(val bytes: ByteArray?, val records: List<Record>, val snapshot: DesktopUserPluginSnapshot)

    private companion object {
        val processMutex = Mutex()
        const val MAX_NAME = 256
        const val MAX_DESCRIPTION = 4096
        const val MAX_PATH = 4096
        const val MAX_MANIFEST = 64 * 1024
        const val MAX_DECLARATION_FILE = 64 * 1024
        const val MAX_SKILL = 64 * 1024
        const val MAX_IMPORT = 512 * 1024
        const val MAX_DOCUMENT = 4 * 1024 * 1024
        const val MAX_TEXT_TOTAL = 1024 * 1024
        const val MAX_INSTRUCTIONS = 256 * 1024
        const val MAX_PLUGINS = 64
        const val MAX_SKILLS = 32
        const val MAX_APPS = 64
        const val MAX_CAPABILITIES = 64
        const val MAX_TOTAL_SKILLS = 256
        const val MAX_ENTRIES = 256
        val SHA = Regex("[0-9a-f]{64}")
        val json = Json { isLenient = false; allowSpecialFloatingPointValues = false }
        val ASSETS = mapOf(
            "other capabilities (unsupported)" to listOf("commands", "agents", "lspServers", "assets"),
        )
        val ASSET_PATHS = mapOf(
            "apps (unsupported)" to listOf("apps", ".app.json"),
            "MCP (unsupported)" to listOf(".mcp.json", "mcp.json"),
            "hooks (unsupported)" to listOf("hooks", "hooks.json"),
            "scripts (unsupported)" to listOf("scripts"),
            "other capabilities (unsupported)" to listOf("commands", "agents"),
        )
        val DESCRIPTORS = (ASSETS.keys + ASSET_PATHS.keys + "skill attachments (unsupported)").toSet()
        val MANIFEST_FIELDS = setOf("name", "description", "version", "skills", "author", "homepage",
            "repository", "license", "keywords", "category", "apps", "mcp", "mcpServers", "hooks", "scripts") + ASSETS.values.flatten()

        fun invalid(): Nothing = throw DesktopUserPluginsException()
        suspend fun <T> sanitized(block: suspend () -> T): T = try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (conflict: DesktopUserPluginsConflict) {
            throw conflict
        } catch (failure: DesktopUserPluginsException) {
            throw failure
        } catch (_: Exception) {
            invalid()
        }

        suspend fun guard(isCurrent: () -> Boolean) {
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) throw CancellationException("Plugin operation is no longer current.")
            currentCoroutineContext().ensureActive()
        }

        fun checkedAbsolute(path: Path): Path {
            if (!path.isAbsolute || path.any { it.toString() == ".." }) invalid()
            val absolute = path.normalize()
            if (absolute.toString().toByteArray(Charsets.UTF_8).size > MAX_PATH) invalid()
            return absolute
        }

        fun attributes(path: Path): BasicFileAttributes? = try {
            Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) { null }

        fun checkTree(path: Path) {
            var current = path.root ?: invalid()
            for (part in path) {
                current = current.resolve(part)
                val attr = attributes(current) ?: continue
                if (attr.isSymbolicLink || (current != path && !attr.isDirectory)) invalid()
            }
        }

        fun readBytes(path: Path, limit: Int): ByteArray {
            checkTree(path)
            val attr = attributes(path) ?: invalid()
            if (!attr.isRegularFile || attr.size() > limit) invalid()
            FileChannel.open(path, READ, NOFOLLOW_LINKS).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteBuffer.allocate(8192)
                while (true) {
                    buffer.clear()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > limit) invalid()
                    output.write(buffer.array(), 0, count)
                }
                return output.toByteArray()
            }
        }

        fun strictUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        fun registrationId(source: String, name: String): String = sha(
            ("harness-local-plugin-v1\u0000" + source + "\u0000" + name).toByteArray(Charsets.UTF_8),
        )
        fun contentDigest(manifestDigest: String, texts: List<SkillText>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            // Length-prefix fields: stable across enumeration order and JSON formatting.
            fun field(value: String) {
                val bytes = value.toByteArray(Charsets.UTF_8)
                digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
                digest.update(bytes)
            }
            field(manifestDigest)
            for (text in texts.sortedBy { it.path }) { field(text.path); field(text.raw) }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }

        fun checkText(text: String, limit: Int) {
            if (text.isBlank() || text.indexOf('\u0000') >= 0 || text.toByteArray(Charsets.UTF_8).size > limit) invalid()
            // Also reject unpaired surrogates introduced by JSON escape sequences.
            Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(text))
        }
        fun string(value: JsonElement, limit: Int): String {
            val primitive = value as? JsonPrimitive ?: invalid()
            if (!primitive.isString) invalid()
            return primitive.content.also { checkText(it, limit) }
        }
        fun requiredString(value: JsonObject, key: String, limit: Int): String = string(value[key] ?: invalid(), limit)
        fun optionalString(value: JsonObject, key: String, limit: Int): String? =
            value[key]?.takeUnless { it == JsonNull }?.let { string(it, limit) }
        fun nullable(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
        fun exactKeys(value: JsonObject, keys: Set<String>) { if (value.keys != keys) invalid() }

        fun relativeDirectory(value: String): String {
            val relative = value.removePrefix("./").removeSuffix("/")
            val parts = relative.split('/')
            if (relative.isBlank() || parts.size > 16 || parts.any { it.isBlank() || it == "." || it == ".." } ||
                relative.any { it.code < 32 || it in "\\:*?<>|" } || Path.of(relative).isAbsolute) invalid()
            return relative
        }

        fun parseSkill(raw: String, path: String): DesktopPluginSkill {
            checkText(raw, MAX_SKILL)
            if (relativeDirectory(path) != path || !path.endsWith("/SKILL.md")) invalid()
            var name = path.split('/').dropLast(1).last()
            var description: String? = null
            val text = raw.removePrefix("\uFEFF")
            var instructions = text
            if (text.startsWith("---\n") || text.startsWith("---\r\n")) {
                var cursor = text.indexOf('\n') + 1
                val seen = mutableSetOf<String>()
                var closed = false
                while (cursor <= text.length && cursor <= 16 * 1024) {
                    val end = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
                    val line = text.substring(cursor, end).removeSuffix("\r")
                    val key = line.substringBefore(':')
                    if (line == "---") {
                        instructions = text.substring(if (end < text.length) end + 1 else end)
                        closed = true
                        break
                    }
                    if (key == "name" || key == "description") {
                        if (!seen.add(key) || ':' !in line) invalid()
                        val scalar = yamlString(line.substringAfter(':').trim())
                        if (key == "name") name = scalar else description = scalar
                    }
                    if (end == text.length) break
                    cursor = end + 1
                }
                if (!closed) invalid()
            }
            checkText(name, MAX_NAME)
            if (name.any { it.code < 32 }) invalid()
            description?.let { checkText(it, MAX_DESCRIPTION) }
            checkText(instructions, MAX_SKILL)
            return DesktopPluginSkill(name, description, instructions)
        }

        fun yamlString(value: String): String {
            if (value.startsWith('"')) return string(json.parseToJsonElement(value), MAX_DESCRIPTION)
            if (value.startsWith('\'')) {
                if (value.length < 2 || !value.endsWith('\'')) invalid()
                val body = value.substring(1, value.lastIndex)
                if (body.replace("''", "").contains('\'')) invalid()
                return body.replace("''", "'").also { checkText(it, MAX_DESCRIPTION) }
            }
            if (value.isBlank() || value.first() in "|>[{!&*@" || value.first() == 96.toChar() ||
                value in setOf("null", "Null", "NULL", "~", "true", "false", "True", "False") ||
                value.toDoubleOrNull() != null || Regex("\\s#|:\\s").containsMatchIn(value)) invalid()
            return value.also { checkText(it, MAX_DESCRIPTION) }
        }

        fun parseObject(text: String): JsonObject {
            // Cap nesting and reject duplicate JSON keys before the library parser.
            val objects = mutableListOf<MutableSet<String>?>()
            var expectingKey = false
            var index = 0
            while (index < text.length) {
                when (text[index]) {
                    '{', '[' -> {
                        if (objects.size >= 16) invalid()
                        objects.add(if (text[index] == '{') mutableSetOf<String>() else null)
                        expectingKey = text[index] == '{'
                    }
                    '}', ']' -> { if (objects.isEmpty()) invalid(); objects.removeAt(objects.lastIndex); expectingKey = false }
                    ',' -> expectingKey = objects.lastOrNull() != null
                    ':' -> expectingKey = false
                    '"' -> {
                        val start = index++
                        while (index < text.length && text[index] != '"') {
                            if (text[index] == '\\') index++
                            index++
                        }
                        if (index >= text.length) invalid()
                        if (expectingKey) {
                            val key = json.parseToJsonElement(text.substring(start, index + 1)) as? JsonPrimitive ?: invalid()
                            if (objects.lastOrNull()?.add(key.content) != true) invalid()
                        }
                        expectingKey = false
                    }
                }
                index++
            }
            if (objects.isNotEmpty()) invalid()
            return json.parseToJsonElement(text) as? JsonObject ?: invalid()
        }

        fun render(plugins: List<DesktopUserPlugin>): String? {
            val enabled = plugins.filter { it.enabled }.sortedBy { it.id }
            if (enabled.isEmpty()) return null
            val result = StringBuilder("# Registered local skill snapshots\n")
            var bytes = result.toString().toByteArray(Charsets.UTF_8).size
            fun append(value: String) {
                bytes += value.toByteArray(Charsets.UTF_8).size
                if (bytes > MAX_INSTRUCTIONS) invalid()
                result.append(value)
            }
            for (plugin in enabled) {
                append("\n## Plugin " + JsonPrimitive(plugin.name) + "\nRegistration: " + plugin.id +
                    "\nSource: " + JsonPrimitive(plugin.source) + "\n")
                for (skill in plugin.skills.sortedBy { it.name }) {
                    append("\n### Skill " + JsonPrimitive(skill.name) + "\n--- BEGIN SKILL INSTRUCTIONS ---\n")
                    append(skill.instructions)
                    append("\n--- END SKILL INSTRUCTIONS ---\n")
                }
            }
            return result.toString()
        }
    }
}
