package im.hikaru.harness.bundle.desktop

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopUserPluginsTest {
    @Test
    fun localUpdatePreservesIdentityOrderEnabledAndRestoresExactSnapshot() = runTest {
        for (enabled in listOf(false, true)) withTemp { root ->
            val source = source(root.resolve("source"), manifest = """{"name":"sample","version":"old"}""")
            val other = source(root.resolve("other"))
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            var old = service.registerLocal(source.toString(), service.read().revision)
            old = service.registerLocal(other.toString(), old.revision)
            val target = old.plugins.first()
            old = service.setEnabled(target.id, enabled, old.revision)
            Files.writeString(source.resolve(".codex-plugin/plugin.json"), """{"name":"sample","version":"opaque older-looking version"}""")
            Files.writeString(source.resolve("skills/review/SKILL.md"), "---\nname: changed\ndescription: New metadata\n---\nUPDATED-INSTRUCTIONS\n")
            val editedSource = treeBytes(source)
            val updated = service.updateLocal(target.id, old.revision)
            assertNotEquals(old.revision, updated.revision)
            assertEquals(old.plugins.map { it.id }, updated.plugins.map { it.id })
            assertEquals(old.plugins.last(), updated.plugins.last())
            assertEquals(target.source, updated.plugins.first().source)
            assertEquals(enabled, updated.plugins.first().enabled)
            assertEquals("opaque older-looking version", updated.plugins.first().version)
            assertEquals("New metadata", updated.plugins.first().skills.single().description)
            assertEquals("UPDATED-INSTRUCTIONS\n", updated.plugins.first().skills.single().instructions)
            assertEquals(updated, DesktopUserPlugins(document).read())
            if (enabled) assertTrue(assertNotNull(service.renderInstructions()).contains("UPDATED-INSTRUCTIONS"))
            else assertNull(service.renderInstructions())
            val bytes = Files.readAllBytes(document)
            assertEquals(updated, service.updateLocal(target.id, updated.revision))
            assertContentEquals(bytes, Files.readAllBytes(document))
            assertTreeEquals(editedSource, treeBytes(source))
        }
    }

    @Test
    fun invalidLocalUpdatesKeepSavedBytesAndInstructionsIncludingMissingSourceAndTarget() = runTest {
        val invalidManifests = listOf("not-json", """{"name":"renamed"}""",
            """{"name":"sample","skills":"../outside"}""", """{"name":"sample","skills":{}}""")
        for (manifest in invalidManifests) withTemp { root ->
            val source = source(root.resolve("source"))
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            val registered = service.registerLocal(source.toString(), service.read().revision)
            val old = service.setEnabled(registered.plugins.single().id, true, registered.revision)
            val instructions = service.renderInstructions()
            val bytes = Files.readAllBytes(document)
            Files.writeString(source.resolve(".codex-plugin/plugin.json"), manifest)
            val tree = treeBytes(source)
            assertFailsWith<DesktopUserPluginsException> { service.updateLocal(old.plugins.single().id, old.revision) }
            assertFailsWith<DesktopUserPluginsException> { service.updateLocal("missing-id", old.revision) }
            assertContentEquals(bytes, Files.readAllBytes(document))
            assertEquals(old, DesktopUserPlugins(document).read())
            assertEquals(instructions, service.renderInstructions())
            assertTreeEquals(tree, treeBytes(source))
            source.toFile().deleteRecursively()
            assertFailsWith<DesktopUserPluginsException> { service.updateLocal(old.plugins.single().id, old.revision) }
            assertContentEquals(bytes, Files.readAllBytes(document))
            assertEquals(instructions, service.renderInstructions())
        }
    }

    @Test
    fun localUpdateRejectsSymlinkInvalidTextAndLimitsWithoutReplacingOldSnapshot() = runTest {
        for (kind in listOf("symlink", "utf8", "skill-budget", "manifest-budget", "count", "import-budget")) withTemp { root ->
            val source = source(root.resolve("source"))
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            val registered = service.registerLocal(source.toString(), service.read().revision)
            val old = service.setEnabled(registered.plugins.single().id, true, registered.revision)
            val instructions = service.renderInstructions()
            val bytes = Files.readAllBytes(document)
            val skill = source.resolve("skills/review/SKILL.md")
            when (kind) {
                "symlink" -> { Files.move(skill, source.resolve("actual.md")); Files.createSymbolicLink(skill, source.resolve("actual.md")) }
                "utf8" -> Files.write(skill, byteArrayOf(0xc3.toByte(), 0x28))
                "skill-budget" -> Files.writeString(skill, "x".repeat(64 * 1024 + 1))
                "manifest-budget" -> Files.writeString(source.resolve(".codex-plugin/plugin.json"), " ".repeat(64 * 1024 + 1))
                "count", "import-budget" -> repeat(if (kind == "count") 32 else 9) { index ->
                    val extra = source.resolve("skills/extra-" + index)
                    Files.createDirectories(extra)
                    Files.writeString(extra.resolve("SKILL.md"), if (kind == "count") "valid" else "x".repeat(60 * 1024))
                }
            }
            val tree = treeBytes(source)
            assertFailsWith<DesktopUserPluginsException> { service.updateLocal(old.plugins.single().id, old.revision) }
            assertContentEquals(bytes, Files.readAllBytes(document))
            assertEquals(old, service.read())
            assertEquals(instructions, service.renderInstructions())
            assertTreeEquals(tree, treeBytes(source))
        }
    }

    @Test
    fun localUpdateRevalidatesCombinedEnabledBudgetAndFinalAccessLease() = runTest {
        withTemp { root ->
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            val sources = (0..4).map { source(root.resolve("source-" + it), skill = "x".repeat(50 * 1024)) }
            var old = service.read()
            for (source in sources) old = service.registerLocal(source.toString(), old.revision)
            for (plugin in old.plugins) old = service.setEnabled(plugin.id, true, old.revision)
            val bytes = Files.readAllBytes(document)
            val instructions = service.renderInstructions()
            Files.writeString(sources.first().resolve("skills/review/SKILL.md"), "y".repeat(60 * 1024))
            assertFailsWith<DesktopUserPluginsException> { service.updateLocal(old.plugins.first().id, old.revision) }
            assertContentEquals(bytes, Files.readAllBytes(document))
            assertEquals(instructions, service.renderInstructions())
            Files.writeString(sources.first().resolve("skills/review/SKILL.md"), "UPDATED")
            var reachedWrittenTemporary = false
            assertFailsWith<CancellationException> {
                service.updateLocal(old.plugins.first().id, old.revision) {
                    val temps = Files.newDirectoryStream(document.parent, ".user-plugins-*.tmp").use { it.toList() }
                    if (temps.any { Files.size(it) > 0 }) { reachedWrittenTemporary = true; false } else true
                }
            }
            assertTrue(reachedWrittenTemporary)
            assertContentEquals(bytes, Files.readAllBytes(document))
            assertEquals(old, service.read())
            assertEquals(instructions, service.renderInstructions())
        }
    }

    @Test
    fun missingInventoryReadCreatesNothingAndHasByteShaRevision() = runTest {
        withTemp { root ->
            val document = root.resolve("missing/home/plugins.json")
            val service = DesktopUserPlugins(document)
            val empty = service.read()
            assertEquals(emptyList(), empty.plugins)
            assertEquals(sha(byteArrayOf()), empty.revision)
            assertNull(service.renderInstructions())
            assertFalse(Files.exists(document.parent))
        }
    }

    @Test
    fun roundTripConsumesOnlyEnabledImmutableSnapshotsAndRemovalOnlyRemovesRegistration() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"), skill = "---\nname: 'review'\ndescription: \"Review carefully\"\n---\nActual markdown.\n")
            val document = root.resolve("home/plugins.json")
            val original = treeBytes(source)
            val service = DesktopUserPlugins(document)
            val registered = service.registerLocal(source.toString(), service.read().revision)
            val plugin = registered.plugins.single()
            assertFalse(plugin.enabled)
            assertEquals(source.toRealPath(NOFOLLOW_LINKS).toString(), plugin.source)
            assertEquals("Review carefully", plugin.skills.single().description)
            assertEquals("Actual markdown.\n", plugin.skills.single().instructions)
            assertNull(service.renderInstructions())
            assertEquals(sha(Files.readAllBytes(document)), registered.revision)
            assertEquals(registered, DesktopUserPlugins(document).read())
            assertTreeEquals(original, treeBytes(source))

            val enabled = service.setEnabled(plugin.id, true, registered.revision)
            val contribution = assertNotNull(DesktopUserPlugins(document).renderInstructions())
            assertTrue(contribution.contains("Actual markdown.\n"))
            assertTrue(contribution.contains("Plugin \"sample\""))
            assertTrue(contribution.contains("Skill \"review\""))
            assertTrue(contribution.contains(plugin.id))
            assertTrue(contribution.contains(plugin.source))
            assertFalse(contribution.contains("description:"))
            Files.writeString(source.resolve("skills/review/SKILL.md"), "Changed source instructions.")
            Files.writeString(source.resolve(".codex-plugin/plugin.json"), "not-json")
            assertEquals(contribution, service.renderInstructions())
            assertEquals(enabled, service.read())
            val editedSource = treeBytes(source)

            val disabled = service.setEnabled(plugin.id, false, enabled.revision)
            assertNull(service.renderInstructions())
            val restored = service.setEnabled(plugin.id, true, disabled.revision)
            assertEquals(contribution, service.renderInstructions())
            val removed = service.removeRegistration(plugin.id, restored.revision)
            assertEquals(emptyList(), removed.plugins)
            assertNull(service.renderInstructions())
            assertTreeEquals(editedSource, treeBytes(source))
        }
    }

    @Test
    fun identicalNamesFromDifferentSourcesAreDistinctButSameCanonicalSourceCannotBeRegisteredTwice() = runTest {
        withTemp { root ->
            val first = source(root.resolve("one"))
            val second = source(root.resolve("two"))
            val service = DesktopUserPlugins(root.resolve("home/plugins.json"))
            val one = service.registerLocal(first.toString(), service.read().revision)
            val two = service.registerLocal(second.toString(), one.revision)
            assertEquals(listOf("sample", "sample"), two.plugins.map { it.name })
            assertNotEquals(two.plugins[0].id, two.plugins[1].id)
            assertFailsWith<DesktopUserPluginsException> {
                service.registerLocal(first.resolve(".").toString(), two.revision)
            }
            Files.writeString(first.resolve(".codex-plugin/plugin.json"), "{\"name\":\"renamed\"}")
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(first.toString(), two.revision) }
            assertEquals(two, service.read())
        }
    }

    @Test
    fun explicitLocalSkillDirectoriesAndLimitedFrontmatterHaveNoInventedDescription() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"), manifest = "{\"name\":\"sample\",\"skills\":[\"./custom\",\"other/direct\"]}")
            Files.createDirectories(source.resolve("custom/zeta"))
            Files.writeString(source.resolve("custom/zeta/SKILL.md"), "Folder instructions.")
            Files.createDirectories(source.resolve("other/direct"))
            Files.writeString(source.resolve("other/direct/SKILL.md"), "---\r\nname: 'named'\r\ndescription: 'It''s useful'\r\n---\r\nDirect instructions.\r\n")
            val service = DesktopUserPlugins(root.resolve("home/plugins.json"))
            val plugin = service.registerLocal(source.toString(), service.read().revision).plugins.single()
            assertEquals(listOf("zeta", "named"), plugin.skills.map { it.name })
            assertNull(plugin.skills.first().description)
            assertEquals("It's useful", plugin.skills.last().description)
            assertEquals("Direct instructions.\r\n", plugin.skills.last().instructions)
        }
    }

    @Test
    fun unsupportedAssetsAreDescriptorsAndNeverExposeRawConfigurationOrExecuteScripts() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"), manifest = """
                {"name":"sample","apps":{"secret":"APP_SECRET"},"mcpServers":{"token":"MCP_SECRET"},"hooks":"./hooks","scripts":"./scripts","extensions":{"token":"EXTENSION_SECRET"}}
            """.trimIndent())
            Files.writeString(source.resolve(".mcp.json"), "SECRET_CONFIG_DO_NOT_COPY")
            Files.createDirectories(source.resolve("scripts"))
            Files.writeString(source.resolve("scripts/run.js"), "throw Error('never execute');")
            Files.createDirectories(source.resolve("skills/review/scripts"))
            Files.writeString(source.resolve("skills/review/scripts/run.sh"), "touch should-not-exist")
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            val snapshot = service.registerLocal(source.toString(), service.read().revision)
            assertEquals(listOf("secret"), snapshot.plugins.single().applications)
            assertEquals(setOf("MCP (unsupported)", "hooks (unsupported)",
                "scripts (unsupported)", "skill attachments (unsupported)", "other capabilities (unsupported)"), snapshot.plugins.single().unsupportedAssets.toSet())
            val saved = Files.readString(document)
            for (secret in listOf("APP_SECRET", "MCP_SECRET", "EXTENSION_SECRET", "SECRET_CONFIG_DO_NOT_COPY", "never execute", "touch should-not-exist")) {
                assertFalse(saved.contains(secret))
            }
            assertFalse(Files.exists(source.resolve("should-not-exist")))
        }
    }

    @Test
    fun appFileDeclaredByPathYieldsSortedNamesOnlyAndNoUnsupportedDescriptor() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"), manifest = """{"name":"sample","apps":"./.app.json"}""")
            Files.writeString(source.resolve(".app.json"), """
                {"apps":{"default_templates":{"id":"default_templates","endpoint":"https://APPS_ENDPOINT_SECRET"},"connected_documents":{"id":"connected_documents","required":true}}}
            """.trimIndent())
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            val plugin = service.registerLocal(source.toString(), service.read().revision).plugins.single()
            // Plugin name != app ID, and only the bounded keys are kept, sorted.
            assertEquals(listOf("connected_documents", "default_templates"), plugin.applications)
            assertFalse(plugin.unsupportedAssets.contains("apps (unsupported)"))
            val saved = Files.readString(document)
            assertFalse(saved.contains("APPS_ENDPOINT_SECRET"))
            assertFalse(saved.contains(".app.json"))
        }
    }

    @Test
    fun unreadableOrEscapingAppsDeclarationsStayDescriptorsAndAreNeverRead() = runTest {
        // Absent file, non-JSON, no `apps` object, a directory, and a path escaping the root must all
        // keep the descriptor instead of an invented (or partially read) list.
        val cases = listOf<Pair<String, String?>>(
            "./.app.json" to null,
            "./.app.json" to "not-json",
            "./.app.json" to """{"servers":{}}""",
            "./apps" to null,
            "./../escape.json" to """{"apps":{"leaked_from_outside_root":{}}}""",
        )
        for ((declared, body) in cases) withTemp { root ->
            val source = source(root.resolve("source"), manifest = """{"name":"sample","apps":${JsonPrimitive(declared)}}""")
            when {
                declared == "./apps" -> Files.createDirectories(source.resolve("apps"))
                declared.startsWith("./../") -> Files.writeString(root.resolve("escape.json"), body!!)
                body != null -> Files.writeString(source.resolve(".app.json"), body)
            }
            val service = DesktopUserPlugins(root.resolve("home/plugins.json"))
            val plugin = service.registerLocal(source.toString(), service.read().revision).plugins.single()
            assertEquals(emptyList(), plugin.applications, declared)
            assertTrue(plugin.unsupportedAssets.contains("apps (unsupported)"), declared)
            assertFalse(Files.readString(root.resolve("home/plugins.json")).contains("leaked_from_outside_root"), declared)
        }
    }

    @Test
    fun mcpFileDeclaredByPathYieldsSortedNamesOnlyAndNoUnsupportedDescriptor() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"), manifest = """{"name":"sample","mcpServers":"./.mcp.json"}""")
            Files.writeString(source.resolve(".mcp.json"), """
                {"mcpServers":{"codex_app":{"command":"node","env":{"TOKEN":"MCP_SECRET"}},"cua_repl":{"command":"python","args":["-m","cua"]}}}
            """.trimIndent())
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            val plugin = service.registerLocal(source.toString(), service.read().revision).plugins.single()
            // Only the bounded server IDs are kept, sorted; the plugin name is unrelated.
            assertEquals(listOf("codex_app", "cua_repl"), plugin.capabilities.mcpServers)
            assertFalse(plugin.unsupportedAssets.contains("MCP (unsupported)"))
            val saved = Files.readString(document)
            assertFalse(saved.contains("MCP_SECRET"))
            assertFalse(saved.contains(".mcp.json"))
            assertFalse(saved.contains("\"command\""))
        }
    }

    @Test
    fun mcpAliasKeyWithPathAlsoEnumeratesServerNames() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"), manifest = """{"name":"sample","mcp":"./.mcp.json"}""")
            Files.writeString(source.resolve(".mcp.json"), """{"mcpServers":{"code-review":{}}}""")
            val service = DesktopUserPlugins(root.resolve("home/plugins.json"))
            val plugin = service.registerLocal(source.toString(), service.read().revision).plugins.single()
            assertEquals(listOf("code-review"), plugin.capabilities.mcpServers)
            assertFalse(plugin.unsupportedAssets.contains("MCP (unsupported)"))
        }
    }

    @Test
    fun unreadableOrEscapingMcpDeclarationsStayDescriptorsAndAreNeverRead() = runTest {
        // Absent file, non-JSON, no `mcpServers` object, a directory, and a path escaping the root must
        // all keep the descriptor instead of an invented (or partially read) list.
        val cases = listOf<Pair<String, String?>>(
            "./.mcp.json" to null,
            "./.mcp.json" to "not-json",
            "./.mcp.json" to """{"servers":{}}""",
            "./mcp.json" to null,
            "./../escape.json" to """{"mcpServers":{"leaked_from_outside_root":{}}}""",
        )
        for ((declared, body) in cases) withTemp { root ->
            val source = source(root.resolve("source"), manifest = """{"name":"sample","mcpServers":${JsonPrimitive(declared)}}""")
            when {
                declared == "./mcp.json" -> Files.createDirectories(source.resolve("mcp.json"))
                declared.startsWith("./../") -> Files.writeString(root.resolve("escape.json"), body!!)
                body != null -> Files.writeString(source.resolve(".mcp.json"), body)
            }
            val service = DesktopUserPlugins(root.resolve("home/plugins.json"))
            val plugin = service.registerLocal(source.toString(), service.read().revision).plugins.single()
            assertEquals(emptyList(), plugin.capabilities.mcpServers, declared)
            assertTrue(plugin.unsupportedAssets.contains("MCP (unsupported)"), declared)
            assertFalse(Files.readString(root.resolve("home/plugins.json")).contains("leaked_from_outside_root"), declared)
        }
    }

    @Test
    fun schemaOneInventoryMigratesInlineAppsAsEmptyAndWritesSchemaThreeOnNextMutation() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"), manifest = """
                {"name":"sample","apps":["fixture-app"]}
            """.trimIndent())
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            val registered = service.registerLocal(source.toString(), service.read().revision)
            val encoded = Json.parseToJsonElement(Files.readString(document)) as JsonObject
            val plugin = (encoded["plugins"] as JsonArray).single() as JsonObject
            val schemaOnePlugin = JsonObject(plugin.filterKeys { it != "applications" && it != "capabilities" })
            val schemaOne = JsonObject(mapOf(
                "schema" to JsonPrimitive(1),
                "plugins" to JsonArray(listOf(schemaOnePlugin)),
            ))
            Files.writeString(document, schemaOne.toString())

            val migrated = service.read()
            assertEquals(listOf("fixture-app"), registered.plugins.single().applications)
            assertEquals(emptyList(), migrated.plugins.single().applications)
            val rewritten = service.setEnabled(migrated.plugins.single().id, true, migrated.revision)
            assertTrue(rewritten.plugins.single().enabled)
            val current = Json.parseToJsonElement(Files.readString(document)) as JsonObject
            assertEquals(JsonPrimitive(3), current["schema"])
            assertEquals(JsonArray(emptyList()), ((current["plugins"] as JsonArray).single() as JsonObject)["applications"])
            assertEquals(
                JsonObject(mapOf(
                    "mcpServers" to JsonArray(emptyList()),
                    "hooks" to JsonArray(emptyList()),
                    "scripts" to JsonArray(emptyList()),
                )),
                ((current["plugins"] as JsonArray).single() as JsonObject)["capabilities"],
            )
        }
    }

    @Test
    fun casUsesExactBytesAcrossInstancesAndDetectsExternalValidEdits() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"))
            val document = root.resolve("home/plugins.json")
            val first = DesktopUserPlugins(document)
            val second = DesktopUserPlugins(document)
            val empty = first.read()
            val registered = first.registerLocal(source.toString(), empty.revision)
            val id = registered.plugins.single().id
            assertFailsWith<DesktopUserPluginsConflict> { first.setEnabled(id, true, empty.revision) }
            assertFailsWith<DesktopUserPluginsConflict> { second.removeRegistration(id, empty.revision) }
            Files.writeString(document, Files.readString(document) + "\n")
            assertEquals(registered.plugins, second.read().plugins)
            assertNotEquals(registered.revision, second.read().revision)
            assertFailsWith<DesktopUserPluginsConflict> { first.setEnabled(id, true, registered.revision) }
            assertFalse(second.read().plugins.single().enabled)
        }
    }

    @Test
    fun corruptInventoryCannotBeOverwrittenEvenWithItsActualShaAndErrorsDoNotLeakContents() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"))
            val document = root.resolve("home/plugins.json")
            Files.createDirectories(document.parent)
            val bytes = "{\"SECRET_TOKEN\": broken}".toByteArray()
            Files.write(document, bytes)
            val service = DesktopUserPlugins(document)
            val failure = assertFailsWith<DesktopUserPluginsException> { service.read() }
            assertFalse(failure.message.orEmpty().contains("SECRET_TOKEN"))
            assertNull(failure.cause)
            assertFailsWith<DesktopUserPluginsException> { service.renderInstructions() }
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(source.toString(), sha(bytes)) }
            assertContentEquals(bytes, Files.readAllBytes(document))
            assertFalse(Files.exists(document.resolveSibling("plugins.json.lock")))
            assertEquals("插件登记已不存在，请刷新列表。", DesktopUserPluginsException("插件登记已不存在，请刷新列表。").message)
        }
    }

    @Test
    fun defaultSanitizedMessageIsLocalizedAndKeepsNoDetails() {
        val failure = DesktopUserPluginsException()
        assertEquals("本机插件库存或来源无效、不受支持或不可用。", failure.message)
        assertNull(failure.cause)
    }

    @Test
    fun inventoryRejectsUnknownFieldsDuplicateIdsAndTamperedProvenanceOrSnapshots() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"))
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            service.registerLocal(source.toString(), service.read().revision)
            val original = Json.parseToJsonElement(Files.readString(document)) as JsonObject
            val plugins = original["plugins"] as JsonArray
            val plugin = plugins.single() as JsonObject
            fun replacedPlugin(key: String, value: kotlinx.serialization.json.JsonElement): JsonObject =
                JsonObject(original + ("plugins" to JsonArray(listOf(JsonObject(plugin + (key to value))))))
            val invalidDocuments = listOf(
                JsonObject(original + ("extension" to JsonPrimitive(true))),
                JsonObject(original + ("plugins" to JsonArray(listOf(plugin, plugin)))),
                replacedPlugin("source", JsonPrimitive(root.resolve("elsewhere").toString())),
                replacedPlugin("contentDigest", JsonPrimitive("0".repeat(64))),
                replacedPlugin("skills", JsonArray(listOf(JsonObject(mapOf(
                    "path" to JsonPrimitive("../SKILL.md"), "rawText" to JsonPrimitive("tampered"),
                ))))),
            )
            for (invalid in invalidDocuments) {
                val text = invalid.toString()
                Files.writeString(document, text)
                assertFailsWith<DesktopUserPluginsException> { service.read() }
                assertFailsWith<DesktopUserPluginsException> { service.removeRegistration("anything", sha(text.toByteArray())) }
                assertEquals(text, Files.readString(document))
            }
            Files.writeString(document, "{\"schema\":1,\"schema\":1,\"plugins\":[]}")
            assertFailsWith<DesktopUserPluginsException> { service.read() }
        }
    }

    @Test
    fun symlinksInSourceDocumentParentAndLockAreRejected() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"))
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            val empty = service.read()
            val skill = source.resolve("skills/review/SKILL.md")
            Files.move(skill, source.resolve("actual.md"))
            Files.createSymbolicLink(skill, source.resolve("actual.md"))
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(source.toString(), empty.revision) }
            assertFalse(Files.exists(document.parent))
            Files.delete(skill)
            Files.move(source.resolve("actual.md"), skill)
            Files.createSymbolicLink(root.resolve("source-alias"), source)
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(root.resolve("source-alias").toString(), empty.revision) }
            Files.createDirectories(document.parent)
            val target = root.resolve("target.json")
            Files.writeString(target, "{\"schema\":1,\"plugins\":[]}")
            Files.createSymbolicLink(document, target)
            assertFailsWith<DesktopUserPluginsException> { service.read() }
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(source.toString(), empty.revision) }
            Files.delete(document)
            val lock = document.resolveSibling("plugins.json.lock")
            Files.createSymbolicLink(lock, target)
            assertFailsWith<DesktopUserPluginsException> { service.read() }
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(source.toString(), empty.revision) }
            Files.delete(lock)
            Files.createSymbolicLink(root.resolve("home-alias"), document.parent)
            assertFailsWith<DesktopUserPluginsException> { DesktopUserPlugins(root.resolve("home-alias/plugins.json")).read() }
            assertEquals("{\"schema\":1,\"plugins\":[]}", Files.readString(target))
        }
    }

    @Test
    fun unsafeOrUnsupportedExplicitPathsAndInvalidUtf8AreRejectedBeforeAnyWrite() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"))
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            val empty = service.read()
            for (path in listOf("../outside", "/absolute", "https://example.com/skills", "skills/*", "skills/../outside", "skills\\review")) {
                Files.writeString(source.resolve(".codex-plugin/plugin.json"),
                    JsonObject(mapOf("name" to JsonPrimitive("sample"), "skills" to JsonPrimitive(path))).toString())
                assertFailsWith<DesktopUserPluginsException> { service.registerLocal(source.toString(), empty.revision) }
            }
            Files.writeString(source.resolve(".codex-plugin/plugin.json"), "{\"name\":\"sample\",\"skills\":{\"path\":\"skills\"}}")
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(source.toString(), empty.revision) }
            Files.writeString(source.resolve(".codex-plugin/plugin.json"), "{\"name\":\"sample\"}")
            Files.write(source.resolve("skills/review/SKILL.md"), byteArrayOf(0xc3.toByte(), 0x28))
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(source.toString(), empty.revision) }
            Files.write(source.resolve(".codex-plugin/plugin.json"), byteArrayOf(0xff.toByte()))
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(source.toString(), empty.revision) }
            assertFalse(Files.exists(document.parent))
        }
    }

    @Test
    fun relativeSourceAndDocumentPathsAreRejectedByServiceEvenWithoutUiValidation() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"))
            val service = DesktopUserPlugins(root.resolve("home/plugins.json"))
            val revision = service.read().revision
            // This relative path resolves to an existing source from the real working directory.
            val relative = Path.of("").toAbsolutePath().relativize(source)
            assertFalse(relative.isAbsolute)
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(relative.toString(), revision) }
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal("skills", revision) }
            assertFailsWith<DesktopUserPluginsException> { DesktopUserPlugins(Path.of("plugins.json")) }
            assertFalse(Files.exists(root.resolve("home")))
        }
    }

    @Test
    fun sourceSizeSkillCountAndComposedInstructionBoundsAreEnforcedWithoutTruncation() = runTest {
        withTemp { root ->
            val oversized = source(root.resolve("oversized"), skill = "x".repeat(64 * 1024 + 1))
            val service = DesktopUserPlugins(root.resolve("home/plugins.json"))
            val empty = service.read()
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(oversized.toString(), empty.revision) }
            Files.writeString(oversized.resolve("skills/review/SKILL.md"), "valid")
            Files.writeString(oversized.resolve(".codex-plugin/plugin.json"), " ".repeat(64 * 1024 + 1))
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(oversized.toString(), empty.revision) }
            val tooMany = source(root.resolve("too-many"))
            repeat(32) { index ->
                Files.createDirectories(tooMany.resolve("skills/s$index"))
                Files.writeString(tooMany.resolve("skills/s$index/SKILL.md"), "instructions")
            }
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(tooMany.toString(), empty.revision) }
            val aggregate = source(root.resolve("aggregate"), skill = "x".repeat(60 * 1024))
            repeat(8) { index ->
                Files.createDirectories(aggregate.resolve("skills/s$index"))
                Files.writeString(aggregate.resolve("skills/s$index/SKILL.md"), "x".repeat(60 * 1024))
            }
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(aggregate.toString(), empty.revision) }
            assertFalse(Files.exists(root.resolve("home")))

            var snapshot = empty
            val ids = mutableListOf<String>()
            repeat(5) { index ->
                val source = source(root.resolve("large-$index"), skill = "x".repeat(60 * 1024))
                snapshot = service.registerLocal(source.toString(), snapshot.revision)
                ids += snapshot.plugins.last().id
            }
            for (id in ids.take(4)) snapshot = service.setEnabled(id, true, snapshot.revision)
            val composed = assertNotNull(service.renderInstructions())
            assertTrue(composed.toByteArray().size <= 256 * 1024)
            assertFailsWith<DesktopUserPluginsException> { service.setEnabled(ids.last(), true, snapshot.revision) }
            assertEquals(snapshot, service.read())
            assertEquals(composed, service.renderInstructions())
        }
    }

    @Test
    fun registrationCannotWriteInsideSourceAndRequiresAtLeastOneNonemptySkill() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"))
            val before = treeBytes(source)
            val inside = DesktopUserPlugins(source.resolve("new-home/plugins.json"))
            assertFailsWith<DesktopUserPluginsException> { inside.registerLocal(source.toString(), inside.read().revision) }
            assertTreeEquals(before, treeBytes(source))
            val service = DesktopUserPlugins(root.resolve("home/plugins.json"))
            Files.writeString(source.resolve("skills/review/SKILL.md"), "---\nname: review\n---\n")
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(source.toString(), service.read().revision) }
            Files.delete(source.resolve("skills/review/SKILL.md"))
            assertFailsWith<DesktopUserPluginsException> { service.registerLocal(source.toString(), service.read().revision) }
        }
    }

    @Test
    fun staleOperationAndCancellationBeforeFinalReplaceLeaveInventoryUntouched() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"))
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            val empty = service.read()
            assertFailsWith<CancellationException> { service.registerLocal(source.toString(), empty.revision) { false } }
            assertFalse(Files.exists(document.parent))
            val registered = service.registerLocal(source.toString(), empty.revision)
            val before = Files.readAllBytes(document)
            var observedWrittenTemp = false
            assertFailsWith<CancellationException> {
                service.setEnabled(registered.plugins.single().id, true, registered.revision) {
                    val temporary = Files.newDirectoryStream(document.parent, ".user-plugins-*.tmp").use { it.toList() }
                    if (temporary.any { Files.size(it) > 0 }) {
                        observedWrittenTemp = true
                        false
                    } else true
                }
            }
            assertTrue(observedWrittenTemp)
            assertContentEquals(before, Files.readAllBytes(document))
            assertEquals(registered, service.read())
            assertTrue(Files.newDirectoryStream(document.parent, ".user-plugins-*.tmp").use { !it.iterator().hasNext() })
        }
    }

    @Test
    fun externalEditDuringMutationConflictsAndIsNeverAutomaticallyRetried() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"))
            val document = root.resolve("home/plugins.json")
            val service = DesktopUserPlugins(document)
            val snapshot = service.registerLocal(source.toString(), service.read().revision)
            val external = Files.readString(document) + "\n"
            var edited = false
            assertFailsWith<DesktopUserPluginsConflict> {
                service.setEnabled(snapshot.plugins.single().id, true, snapshot.revision) {
                    if (!edited && Files.newDirectoryStream(document.parent, ".user-plugins-*.tmp").use { it.iterator().hasNext() }) {
                        Files.writeString(document, external)
                        edited = true
                    }
                    true
                }
            }
            assertTrue(edited)
            assertEquals(external, Files.readString(document))
            assertFalse(service.read().plugins.single().enabled)
        }
    }

    @Test
    fun sameJvmMultipleInstancesAndConcurrentRequestsHaveExactlyOneCasWinner() = runTest {
        withTemp { root ->
            val document = root.resolve("home/plugins.json")
            val first = DesktopUserPlugins(document)
            val second = DesktopUserPlugins(document)
            val empty = first.read()
            val sources = (0..7).map { source(root.resolve("source-$it")) }
            val results = coroutineScope {
                sources.mapIndexed { index, source ->
                    async(Dispatchers.Default) {
                        try {
                            (if (index % 2 == 0) first else second).registerLocal(source.toString(), empty.revision)
                        } catch (_: DesktopUserPluginsConflict) { null }
                    }
                }.awaitAll()
            }
            assertEquals(1, results.count { it != null })
            assertEquals(results.filterNotNull().single(), first.read())
            assertEquals(1, first.read().plugins.size)
            assertEquals(sha(Files.readAllBytes(document)), second.read().revision)
        }
    }

    @Test
    fun existingFileChannelLockContentionSuspendsAndCanBeCancelledWithoutOverlappingLockFailure() = runTest {
        withTemp { root ->
            val source = source(root.resolve("source"))
            val document = root.resolve("home/plugins.json")
            Files.createDirectories(document.parent)
            val service = DesktopUserPlugins(document)
            val empty = service.read()
            val lockFile = document.resolveSibling("plugins.json.lock")
            FileChannel.open(lockFile, CREATE, WRITE).use { channel ->
                channel.lock().use {
                    var calls = 0
                    assertFailsWith<CancellationException> {
                        service.registerLocal(source.toString(), empty.revision) { ++calls < 12 }
                    }
                    assertFalse(Files.exists(document))
                }
            }
            assertEquals(1, service.registerLocal(source.toString(), empty.revision).plugins.size)
        }
    }

    private fun source(directory: Path, manifest: String = "{\"name\":\"sample\"}", skill: String = "Real instructions.\n"): Path {
        Files.createDirectories(directory.resolve(".codex-plugin"))
        Files.createDirectories(directory.resolve("skills/review"))
        Files.writeString(directory.resolve(".codex-plugin/plugin.json"), manifest)
        Files.writeString(directory.resolve("skills/review/SKILL.md"), skill)
        return directory
    }

    private suspend fun withTemp(block: suspend (Path) -> Unit) {
        // Resolve the temp root once: macOS's /var alias is outside the source paths under test.
        val root = Files.createTempDirectory("desktop-user-plugins-test-").toRealPath()
        try { block(root) } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun treeBytes(root: Path): Map<String, ByteArray> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }.toList()
            .associate { root.relativize(it).toString() to Files.readAllBytes(it) }
    }

    private fun assertTreeEquals(expected: Map<String, ByteArray>, actual: Map<String, ByteArray>) {
        assertEquals(expected.keys, actual.keys)
        for (key in expected.keys) assertContentEquals(expected.getValue(key), actual.getValue(key), key)
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
