package im.hikaru.harness.desktop.plugins

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentInstructionsKey
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.agents
import im.hikaru.harness.agent.loop.AgentLoopConfig
import im.hikaru.harness.agent.loop.AgentLoopPlugin
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.bundle.desktop.DesktopUserPlugins
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.plugins.InstalledPlugin
import im.hikaru.harness.client.plugins.PluginAccessBinding
import im.hikaru.harness.client.plugins.PluginAppsState
import im.hikaru.harness.client.plugins.PluginCapabilityKind
import im.hikaru.harness.client.plugins.PluginCapabilitySummary
import im.hikaru.harness.client.plugins.PluginEnabledState
import im.hikaru.harness.client.plugins.PluginId
import im.hikaru.harness.client.plugins.PluginMutationReceipt
import im.hikaru.harness.client.plugins.PluginResult
import im.hikaru.harness.client.plugins.PluginScope
import im.hikaru.harness.client.plugins.PluginSkillSummary
import im.hikaru.harness.client.plugins.PluginSkillsState
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TextDeltaChunk
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.llm
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.CompletedTurnEndReason
import im.hikaru.harness.session.RequestHeaderEvent
import im.hikaru.harness.session.SessionEventNames
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.TurnEndEvent
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real local gateway, disk inventory and agent loop; only the LLM response is scripted. */
class DesktopInstalledPluginRepositoryTest {
    @Test
    fun localUpdateReceiptDetailAndFreshAdapterReadSameSavedSnapshot() = runTest {
        withFixture { f ->
            val plugin = f.register()
            assertTrue(f.repository.canUpdateLocal)
            val oldRevision = f.listAndRevision()
            Files.writeString(f.source.resolve(".codex-plugin/plugin.json"),
                """{"name":"Demo local plugin","version":"opaque-new","skills":"skills"}""")
            Files.writeString(f.source.resolve("skills/demo/SKILL.md"), "---\nname: updated\ndescription: Updated metadata\n---\nNEW SAVED TEXT\n")
            val sourceBytes = Files.readAllBytes(f.source.resolve("skills/demo/SKILL.md"))
            val receipt = success(f.repository.updateLocal(f.scope, plugin.id, oldRevision))
            assertNotEquals(oldRevision, receipt.revision)
            val expected = plugin.copy(description = null, version = "opaque-new")
            f.assertPersisted(receipt, listOf(expected))
            val detail = success(f.repository.readDetail(f.scope, plugin.id))
            assertEquals(expected, detail.plugin)
            assertEquals(listOf(PluginSkillSummary("updated", "Updated metadata")),
                assertIs<PluginSkillsState.Available>(detail.skills).skills)
            assertFalse(DesktopUserPlugins(f.file).read().plugins.single().enabled)
            assertEquals("NEW SAVED TEXT\n", DesktopUserPlugins(f.file).read().plugins.single().skills.single().instructions)
            val bytes = Files.readAllBytes(f.file)
            assertEquals(receipt, success(f.repository.updateLocal(f.scope, plugin.id, f.listAndRevision())))
            assertContentEquals(bytes, Files.readAllBytes(f.file))
            assertContentEquals(sourceBytes, Files.readAllBytes(f.source.resolve("skills/demo/SKILL.md")))
        }
    }

    @Test
    fun localUpdateChangesNextRealAgentRequestAndDisabledUpdateStaysUninjected() = runTest {
        withFixture { f ->
            val plugin = f.register()
            success(f.repository.setEnabled(f.scope, plugin.id, true, f.listAndRevision()))
            val runtime = Runtime()
            try {
                runtime.install(SessionPlugin())
                runtime.install(LlmPlugin())
                runtime.install(AgentPlugin())
                runtime.context.provide(AgentInstructionsKey, f.service)
                val requests = mutableListOf<GenerateOptions>()
                runtime.context.llm.registerAdapter(providers = listOf("plugin-update-fixture"), adapter = object : LlmAdapter {
                    override fun stream(options: GenerateOptions): Flow<StreamChunk> {
                        requests += options
                        return flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "done"), FinishChunk(StopFinishReason))
                    }
                })
                runtime.install(AgentLoopPlugin(AgentLoopConfig(system = BASE_SYSTEM)))
                val agent = runtime.context.agents.create(options = AgentOptions(provider = "plugin-update-fixture", model = "test")).agent
                request(agent, "old enabled snapshot")
                val oldSystem = assertNotNull(requests.last().system)
                assertTrue(oldSystem.contains(SKILL_INSTRUCTIONS))
                Files.writeString(f.source.resolve("skills/demo/SKILL.md"), "---\nname: demo\n---\nUPDATED-REQUEST-MARKER\n")
                success(f.repository.updateLocal(f.scope, plugin.id, f.listAndRevision()))
                request(agent, "updated enabled snapshot")
                val newSystem = assertNotNull(requests.last().system)
                assertTrue(newSystem.startsWith(BASE_SYSTEM + "\n\n"))
                assertFalse(newSystem.contains(SKILL_INSTRUCTIONS))
                assertEquals(1, newSystem.split("UPDATED-REQUEST-MARKER").size - 1)
                success(f.repository.setEnabled(f.scope, plugin.id, false, f.listAndRevision()))
                Files.writeString(f.source.resolve("skills/demo/SKILL.md"), "---\nname: demo\n---\nLATEST-DISABLED-MARKER\n")
                success(f.repository.updateLocal(f.scope, plugin.id, f.listAndRevision()))
                request(agent, "updated but disabled")
                assertEquals(BASE_SYSTEM, requests.last().system)
                success(f.repository.setEnabled(f.scope, plugin.id, true, f.listAndRevision()))
                request(agent, "latest enabled")
                val latestSystem = assertNotNull(requests.last().system)
                assertTrue(latestSystem.startsWith(BASE_SYSTEM + "\n\n"))
                assertEquals(1, latestSystem.split("LATEST-DISABLED-MARKER").size - 1)
                assertFalse(latestSystem.contains("UPDATED-REQUEST-MARKER"))
                assertFalse(latestSystem.contains(SKILL_INSTRUCTIONS))
                val headers = agent.session.events().filter { it.type == SessionEventNames.REQUEST_HEADER }
                    .map { Json.decodeFromJsonElement(RequestHeaderEvent.serializer(), it.data).header.system }
                assertEquals(listOf(oldSystem, newSystem, BASE_SYSTEM, latestSystem), requests.map { it.system })
                assertEquals(requests.map { it.system }, headers)
            } finally { runtime.context.dispose() }
        }
    }

    @Test
    fun unboundAccessRejectsEveryReadAndWriteWithoutCreatingInventory() = runTest {
        withFixture { f ->
            assertTrue(f.repository.canRead)
            assertFalse(f.repository.canManage)
            assertFalse(f.repository.canUpdateLocal)
            f.assertDenied(f.scope, PluginId("unregistered"), "unused-token")
            assertEquals(0, f.hostReads)
            assertFalse(Files.exists(f.file.parent))
        }
    }

    @Test
    fun relayForeignHostAndDifferentConnectionToSameHostRejectEveryReadAndWrite() = runTest {
        withFixture { f ->
            val plugin = f.register()
            val revision = f.listAndRevision()
            val bytes = Files.readAllBytes(f.file)
            val otherConnection = LocalConnection(f.gateway)
            assertEquals(f.host, otherConnection.host.describe().data?.hostId)
            val invalidBindings = listOf(
                f.binding.copy(transport = ConnectionTransport.Relay),
                f.binding.copy(connection = otherConnection),
                f.binding.copy(scope = f.scope.copy(hostId = HostId("foreign-host"))),
                null,
            )
            for (binding in invalidBindings) {
                f.repository.bindAccess(f.binding)
                f.repository.bindAccess(binding)
                assertFalse(f.repository.canManage)
                val hostReads = f.hostReads
                f.assertDenied(f.scope, plugin.id, revision)
                assertEquals(hostReads, f.hostReads)
                assertContentEquals(bytes, Files.readAllBytes(f.file))
            }
            f.repository.bindAccess(f.binding)
            assertEquals(listOf(plugin), f.list())
            assertEquals(revision, f.listAndRevision())
        }
    }

    @Test
    fun replacementGenerationRejectsOldScopeAndClearsItsListedRevision() = runTest {
        withFixture { f ->
            val plugin = f.register()
            val revision = f.listAndRevision()
            val bytes = Files.readAllBytes(f.file)
            val replacement = f.binding.copy(scope = f.scope.copy(generation = 2))
            f.repository.bindAccess(replacement)
            assertTrue(f.repository.canManage)
            f.assertDenied(f.scope, plugin.id, revision)
            assertContentEquals(bytes, Files.readAllBytes(f.file))
            // The new generation must establish its own list before obtaining a CAS token.
            assertIs<PluginResult.Error>(f.repository.readRevision(replacement.scope))
            assertEquals(listOf(plugin), success(f.repository.listInstalled(replacement.scope)))
            assertEquals(revision, success(f.repository.readRevision(replacement.scope)))
            f.repository.bindAccess(replacement.copy())
            assertEquals(revision, success(f.repository.readRevision(replacement.scope)))
        }
    }

    @Test
    fun generationChangedDuringRealHostDescribeRejectsInFlightReadsAndWrites() = runTest {
        withFixture { f ->
            val plugin = f.register()
            val revision = f.listAndRevision()
            val bytes = Files.readAllBytes(f.file)
            val operations: List<suspend () -> PluginResult<*>> = listOf(
                { f.repository.listInstalled(f.scope) },
                { f.repository.readDetail(f.scope, plugin.id) },
                { f.repository.readRevision(f.scope) },
                { f.repository.registerLocal(f.scope, f.otherSource.toString(), revision) },
                { f.repository.setEnabled(f.scope, plugin.id, true, revision) },
                { f.repository.removeRegistration(f.scope, plugin.id, revision) },
                { f.repository.updateLocal(f.scope, plugin.id, revision) },
            )
            for ((index, operation) in operations.withIndex()) {
                f.repository.bindAccess(f.binding)
                f.listAndRevision()
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                f.beforeDescribe = { entered.complete(Unit); release.await() }
                val pending = async(start = CoroutineStart.UNDISPATCHED) { operation() }
                try {
                    entered.await()
                    assertFalse(pending.isCompleted)
                    f.repository.bindAccess(f.binding.copy(scope = f.scope.copy(generation = index + 2L)))
                } finally {
                    f.beforeDescribe = {}
                    release.complete(Unit)
                }
                assertIs<PluginResult.UnsupportedHost>(pending.await())
                assertContentEquals(bytes, Files.readAllBytes(f.file))
            }
            assertEquals(listOf(plugin.id.value), DesktopUserPlugins(f.file).read().plugins.map { it.id })
        }
    }

    @Test
    fun generationReplacementCancelsWritesWaitingOnRealInventoryLockWithoutCommitting() = runTest {
        withFixture { f ->
            val plugin = f.register()
            val revision = f.listAndRevision()
            val bytes = Files.readAllBytes(f.file)
            val operations: List<suspend () -> PluginResult<*>> = listOf(
                { f.repository.registerLocal(f.scope, f.otherSource.toString(), revision) },
                { f.repository.setEnabled(f.scope, plugin.id, true, revision) },
                { f.repository.removeRegistration(f.scope, plugin.id, revision) },
                { f.repository.updateLocal(f.scope, plugin.id, revision) },
            )
            for ((index, operation) in operations.withIndex()) {
                f.repository.bindAccess(f.binding)
                f.listAndRevision()
                FileChannel.open(f.lockFile, WRITE).use { channel ->
                    channel.lock().use {
                        val hostReads = f.hostReads
                        // Runs through HostDescribe and reaches the service's suspending lock retry.
                        val pending = async(start = CoroutineStart.UNDISPATCHED) { operation() }
                        assertEquals(hostReads + 1, f.hostReads)
                        assertFalse(pending.isCompleted)
                        f.repository.bindAccess(f.binding.copy(scope = f.scope.copy(generation = index + 2L)))
                        assertFailsWith<CancellationException> { pending.await() }
                    }
                }
                assertContentEquals(bytes, Files.readAllBytes(f.file))
                assertFalse(DesktopUserPlugins(f.file).read().plugins.single().enabled)
            }
        }
    }

    @Test
    fun registrationDetailEnableDisableAndRemovalPersistAcrossFreshServicesAndAdapters() = runTest {
        withFixture { f ->
            f.repository.bindAccess(f.binding)
            assertTrue(f.list().isEmpty())
            val emptyRevision = f.listAndRevision()
            assertFalse(Files.exists(f.file))
            val manifest = Files.readAllBytes(f.source.resolve(".codex-plugin/plugin.json"))
            val skill = Files.readAllBytes(f.source.resolve("skills/demo/SKILL.md"))
            val registered = success(f.repository.registerLocal(f.scope, f.source.toString(), emptyRevision))
            val plugin = f.list().single()
            assertEquals("Demo local plugin", plugin.name)
            assertEquals("Temporary fixture plugin", plugin.description)
            assertEquals("1.2.3", plugin.version)
            assertEquals(f.source.toString(), plugin.source)
            assertEquals(PluginEnabledState.Disabled, plugin.enabled)
            assertNotEquals(emptyRevision, registered.revision)
            f.assertPersisted(registered, listOf(plugin))
            val detail = success(f.repository.readDetail(f.scope, plugin.id))
            assertEquals(plugin, detail.plugin)
            assertEquals(listOf(PluginSkillSummary("demo", "Fixture skill description")),
                assertIs<PluginSkillsState.Available>(detail.skills).skills)
            assertEquals(listOf("fixture-app"), assertIs<PluginAppsState.Available>(detail.apps).names)
            assertTrue(detail.unsupportedAssets.isEmpty())
            assertEquals(
                listOf(
                    PluginCapabilitySummary(PluginCapabilityKind.McpServer, listOf("fixture-mcp")),
                    PluginCapabilitySummary(PluginCapabilityKind.Hook, listOf("before")),
                    PluginCapabilitySummary(PluginCapabilityKind.Script, listOf("check")),
                ),
                detail.capabilities,
            )

            val enabled = success(f.repository.setEnabled(f.scope, plugin.id, true, f.listAndRevision()))
            assertNotEquals(registered.revision, enabled.revision)
            f.assertPersisted(enabled, listOf(plugin.copy(enabled = PluginEnabledState.Enabled)))
            val disabled = success(f.repository.setEnabled(f.scope, plugin.id, false, f.listAndRevision()))
            assertNotEquals(enabled.revision, disabled.revision)
            f.assertPersisted(disabled, listOf(plugin))
            val removed = success(f.repository.removeRegistration(f.scope, plugin.id, f.listAndRevision()))
            f.assertPersisted(removed, emptyList())
            assertIs<PluginResult.Error>(f.repository.readDetail(f.scope, plugin.id))
            assertContentEquals(manifest, Files.readAllBytes(f.source.resolve(".codex-plugin/plugin.json")))
            assertContentEquals(skill, Files.readAllBytes(f.source.resolve("skills/demo/SKILL.md")))
        }
    }

    @Test
    fun staleRevisionRejectsAllMutationKindsAcrossIndependentRealServices() = runTest {
        withFixture { f ->
            val plugin = f.register()
            val staleRevision = f.listAndRevision()
            val other = f.freshRepository()
            success(other.listInstalled(f.scope))
            val enabled = success(other.setEnabled(f.scope, plugin.id, true, success(other.readRevision(f.scope))))
            val bytes = Files.readAllBytes(f.file)
            val operations: List<suspend () -> PluginResult<*>> = listOf(
                { f.repository.registerLocal(f.scope, f.otherSource.toString(), staleRevision) },
                { f.repository.setEnabled(f.scope, plugin.id, false, staleRevision) },
                { f.repository.removeRegistration(f.scope, plugin.id, staleRevision) },
                { f.repository.updateLocal(f.scope, plugin.id, staleRevision) },
            )
            for (operation in operations) {
                assertEquals(CAS_CONFLICT_MESSAGE, assertIs<PluginResult.Error>(operation()).error.message)
                assertContentEquals(bytes, Files.readAllBytes(f.file))
                f.assertPersisted(enabled, listOf(plugin.copy(enabled = PluginEnabledState.Enabled)))
            }
            val refreshed = f.listAndRevision()
            assertEquals(enabled.revision, refreshed)
            val disabled = success(f.repository.setEnabled(f.scope, plugin.id, false, refreshed))
            f.assertPersisted(disabled, listOf(plugin))
        }
    }

    @Test
    fun revisionRemainsTheListedSnapshotAfterExternalWriteAndCannotAuthorizeStaleUi() = runTest {
        withFixture { f ->
            val plugin = f.register()
            // This is the list the UI rendered; do not refresh it after the external change.
            assertEquals(listOf(plugin), f.list())
            val external = DesktopUserPlugins(f.file)
            val listedRevision = external.read().revision
            val changed = external.setEnabled(plugin.id.value, true, listedRevision)
            assertNotEquals(listedRevision, changed.revision)
            assertTrue(DesktopUserPlugins(f.file).read().plugins.single().enabled)
            assertEquals(listedRevision, success(f.repository.readRevision(f.scope)))
            val bytes = Files.readAllBytes(f.file)
            val rejected = assertIs<PluginResult.Error>(f.repository.setEnabled(
                f.scope, plugin.id, false, success(f.repository.readRevision(f.scope)),
            ))
            assertEquals(CAS_CONFLICT_MESSAGE, rejected.error.message)
            assertContentEquals(bytes, Files.readAllBytes(f.file))
            assertEquals(changed, DesktopUserPlugins(f.file).read())
            assertEquals(changed.revision, f.listAndRevision())
            val disabled = success(f.repository.setEnabled(f.scope, plugin.id, false, success(f.repository.readRevision(f.scope))))
            f.assertPersisted(disabled, listOf(plugin))
        }
    }

    @Test
    fun adapterMutationsUpdateNextRealAgentRequestSystemAfterEnableDisableAndRemoval() = runTest {
        withFixture { f ->
            val plugin = f.register()
            val runtime = Runtime()
            try {
                runtime.install(SessionPlugin())
                runtime.install(LlmPlugin())
                runtime.install(AgentPlugin())
                // The adapter and AgentInstructionsKey receive exactly the same disk service.
                runtime.context.provide(AgentInstructionsKey, f.service)
                val requests = mutableListOf<GenerateOptions>()
                runtime.context.llm.registerAdapter(
                    providers = listOf("plugin-fixture"),
                    adapter = object : LlmAdapter {
                        override fun stream(options: GenerateOptions): Flow<StreamChunk> {
                            requests += options
                            return flowOf(
                                BlockStartChunk(0, "text"),
                                TextDeltaChunk(0, "done"),
                                FinishChunk(StopFinishReason),
                            )
                        }
                    },
                )
                runtime.install(AgentLoopPlugin(AgentLoopConfig(system = BASE_SYSTEM)))
                val handle = runtime.context.agents.create(options = AgentOptions(provider = "plugin-fixture", model = "test"))
                val agent = handle.agent
                request(agent, "registered but disabled")
                assertEquals(BASE_SYSTEM, requests.single().system)

                success(f.repository.setEnabled(f.scope, plugin.id, true, f.listAndRevision()))
                request(agent, "enabled")
                val enabledSystem = assertNotNull(requests.last().system)
                assertTrue(enabledSystem.startsWith(BASE_SYSTEM + "\n\n"))
                assertTrue(enabledSystem.contains("### Skill \"demo\""))
                assertTrue(enabledSystem.contains(SKILL_INSTRUCTIONS))
                assertEquals(1, enabledSystem.split(SKILL_INSTRUCTIONS).size - 1)

                success(f.repository.setEnabled(f.scope, plugin.id, false, f.listAndRevision()))
                request(agent, "disabled")
                assertEquals(BASE_SYSTEM, requests.last().system)
                assertFalse(assertNotNull(requests.last().system).contains(SKILL_INSTRUCTIONS))

                // Remove an enabled registration so removal independently proves instructions disappear.
                success(f.repository.setEnabled(f.scope, plugin.id, true, f.listAndRevision()))
                request(agent, "enabled again")
                assertEquals(enabledSystem, requests.last().system)
                success(f.repository.removeRegistration(f.scope, plugin.id, f.listAndRevision()))
                request(agent, "removed")
                assertEquals(BASE_SYSTEM, requests.last().system)
                assertFalse(assertNotNull(requests.last().system).contains(SKILL_INSTRUCTIONS))
                assertEquals(listOf(BASE_SYSTEM, enabledSystem, BASE_SYSTEM, enabledSystem, BASE_SYSTEM), requests.map { it.system })
                val headers = agent.session.events().filter { it.type == SessionEventNames.REQUEST_HEADER }
                    .map { Json.decodeFromJsonElement(RequestHeaderEvent.serializer(), it.data).header.system }
                assertEquals(requests.map { it.system }, headers)
                assertTrue(DesktopUserPlugins(f.file).read().plugins.isEmpty())
            } finally {
                runtime.context.dispose()
            }
        }
    }

    @Test
    fun fileFormAppsDeclaredByPathReachTheDetailPageAsNamesOnly() = runTest {
        withFixture { f ->
            Files.writeString(f.source.resolve(".codex-plugin/plugin.json"),
                """{"name":"File form apps","description":"File apps plugin","version":"1.0.0","skills":"skills","apps":"./.app.json"}""")
            Files.writeString(f.source.resolve(".app.json"),
                """{"apps":{"default_templates":{"endpoint":"APPS_FILE_SECRET"},"connected_documents":{"required":true}}}""")
            val plugin = f.register()
            val detail = success(f.repository.readDetail(f.scope, plugin.id))
            // Names only, sorted; the plugin name != app ID, and no config body/secret survives.
            assertEquals(listOf("connected_documents", "default_templates"),
                assertIs<PluginAppsState.Available>(detail.apps).names)
            assertTrue(detail.unsupportedAssets.isEmpty())
            assertFalse(Files.readString(f.file).contains("APPS_FILE_SECRET"))
        }
    }

    @Test
    fun fileFormMcpDeclaredByPathReachTheDetailPageAsNamesOnly() = runTest {
        withFixture { f ->
            Files.writeString(f.source.resolve(".codex-plugin/plugin.json"),
                """{"name":"File form mcp","description":"File mcp plugin","version":"1.0.0","skills":"skills","mcpServers":"./.mcp.json"}""")
            Files.writeString(f.source.resolve(".mcp.json"),
                """{"mcpServers":{"codex_app":{"command":"node","env":{"TOKEN":"MCP_FILE_SECRET"}},"cua_repl":{"command":"python"}}}""")
            val plugin = f.register()
            val detail = success(f.repository.readDetail(f.scope, plugin.id))
            // Server IDs only, sorted; no command line, env value or secret survives into the detail.
            assertEquals(
                listOf(PluginCapabilitySummary(PluginCapabilityKind.McpServer, listOf("codex_app", "cua_repl"))),
                detail.capabilities,
            )
            assertTrue(detail.unsupportedAssets.isEmpty())
            assertFalse(Files.readString(f.file).contains("MCP_FILE_SECRET"))
        }
    }

    private suspend fun request(agent: Agent, text: String) {
        agent.followup(createUserMessage(listOf(TextBlock(text))))
        agent.awaitIdle()
        val turn = agent.session.events().last { it.type == SessionEventNames.TURN_END }
        assertEquals(CompletedTurnEndReason, Json.decodeFromJsonElement(TurnEndEvent.serializer(), turn.data).reason)
    }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        // Resolve macOS /var aliases before passing paths to the service's symlink checks.
        val directory = Files.createTempDirectory("desktop-installed-plugins-").toRealPath()
        try {
            val f = Fixture(directory)
            val registration = f.gateway.register(HostDescribeEndpoint) {
                f.hostReads += 1
                f.beforeDescribe()
                ApiResult(ApiResult.SUCCESS_CODE, data = HostDescription(ProtocolVersion.Current, f.host, "Real local plugin test host"))
            }
            try { block(f) } finally { registration.dispose() }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private class Fixture(val directory: Path) {
        val source: Path = createSource(directory.resolve("plugin-source"), "Demo local plugin")
        val otherSource: Path = createSource(directory.resolve("other-plugin-source"), "Other local plugin")
        val file: Path = directory.resolve("inventory/user-plugins.json")
        val lockFile: Path = file.resolveSibling(file.fileName.toString() + ".lock")
        val host = HostId("local-plugin-host")
        val gateway = ApiGateway()
        val connection = LocalConnection(gateway)
        val scope = PluginScope(host, 1)
        val binding = PluginAccessBinding(scope, connection, ConnectionTransport.Local)
        val service = DesktopUserPlugins(file)
        val repository = DesktopInstalledPluginRepository(host, connection, service)
        var hostReads = 0
        var beforeDescribe: suspend () -> Unit = {}

        suspend fun list(): List<InstalledPlugin> = success(repository.listInstalled(scope))

        suspend fun listAndRevision(): String {
            list()
            return success(repository.readRevision(scope))
        }

        suspend fun register(): InstalledPlugin {
            repository.bindAccess(binding)
            val receipt = success(repository.registerLocal(scope, source.toString(), listAndRevision()))
            val plugin = list().single()
            assertPersisted(receipt, listOf(plugin))
            return plugin
        }

        fun freshRepository(): DesktopInstalledPluginRepository =
            DesktopInstalledPluginRepository(host, connection, DesktopUserPlugins(file)).also { it.bindAccess(binding) }

        suspend fun assertPersisted(receipt: PluginMutationReceipt, plugins: List<InstalledPlugin>) {
            assertTrue(Files.isRegularFile(file))
            val reloaded = freshRepository()
            assertEquals(plugins, success(reloaded.listInstalled(scope)))
            assertEquals(receipt.revision, success(reloaded.readRevision(scope)))
            assertEquals(receipt.revision, DesktopUserPlugins(file).read().revision)
        }

        suspend fun assertDenied(scope: PluginScope, id: PluginId, revision: String) {
            assertIs<PluginResult.UnsupportedHost>(repository.listInstalled(scope))
            assertIs<PluginResult.UnsupportedHost>(repository.readDetail(scope, id))
            assertIs<PluginResult.UnsupportedHost>(repository.readRevision(scope))
            assertIs<PluginResult.UnsupportedHost>(repository.registerLocal(scope, otherSource.toString(), revision))
            assertIs<PluginResult.UnsupportedHost>(repository.setEnabled(scope, id, true, revision))
            assertIs<PluginResult.UnsupportedHost>(repository.removeRegistration(scope, id, revision))
            assertIs<PluginResult.UnsupportedHost>(repository.updateLocal(scope, id, revision))
        }
    }

    private companion object {
        const val BASE_SYSTEM = "  Base local plugin test instructions.\n"
        const val SKILL_INSTRUCTIONS = "Use the desktop-adapter-skill-marker when answering."
        const val CAS_CONFLICT_MESSAGE = "插件配置已改变，请刷新后重新操作。"

        fun <T> success(result: PluginResult<T>): T = assertIs<PluginResult.Success<T>>(result).value

        fun createSource(root: Path, name: String): Path {
            Files.createDirectories(root.resolve(".codex-plugin"))
            Files.createDirectories(root.resolve("skills/demo"))
            Files.writeString(root.resolve(".codex-plugin/plugin.json"),
                """{"name":"$name","description":"Temporary fixture plugin","version":"1.2.3","skills":"skills","apps":["fixture-app"],"mcpServers":{"fixture-mcp":{}},"hooks":{"before":{}},"scripts":{"check":{}}}""")
            Files.writeString(root.resolve("skills/demo/SKILL.md"),
                "---\nname: demo\ndescription: Fixture skill description\n---\n$SKILL_INSTRUCTIONS\n")
            return root
        }
    }
}
