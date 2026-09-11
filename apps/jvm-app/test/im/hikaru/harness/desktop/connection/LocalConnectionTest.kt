package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.boot.DesktopProfile
import im.hikaru.harness.boot.HarnessHost
import im.hikaru.harness.client.connection.AgentOptions
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.agents
import im.hikaru.harness.agent.loop.AgentLoopPlugin
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextDeltaChunk
import im.hikaru.harness.llm.llm
import im.hikaru.harness.client.connection.userMessage
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.PluginCatalog
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.api.SessionApiPlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocalConnectionTest {

    @Test
    fun shouldReturnTheSameDescriptionWithoutSerializationRoundTrip() = runTest {
        val description =
            HostDescription(
                protocolVersion = ProtocolVersion(major = 1, minor = 0),
                hostId = HostId("desktop-local-test"),
                displayName = "Local Desktop Test",
            )
        val host =
            HarnessHost.start(
                DesktopProfile(hostDescription = description)
            )

        try {
            val connection = LocalConnection(host.gateway)
            val result = connection.host.describe()

            assertTrue(result.isSuccess)
            assertSame(description, result.data)
        } finally {
            host.close()
        }
    }

    @Test
    fun shouldDeriveHistoryThroughTheTypedConnectionAndRejectMissingModelStably() = runTest {
        val host = HarnessHost.start(testProfile())
        try {
            host.runtime.context.llm.registerAdapter(
                providers = listOf("scripted"),
                adapter = object : LlmAdapter {
                    override fun stream(options: im.hikaru.harness.llm.GenerateOptions): Flow<StreamChunk> =
                        flowOf(
                            BlockStartChunk(0, "text"),
                            TextDeltaChunk(0, "ok"),
                            FinishChunk(StopFinishReason),
                        )
                },
            )
            val connection = LocalConnection(host.gateway)
            val created =
                connection.session.create(
                    agentOptions = AgentOptions(provider = "scripted", model = "test-model"),
                )
            val prompt = connection.session.prompt(created.id, userMessage("hi"))
            prompt.awaitIdle()
            val history = connection.session.history(created.id)
            assertEquals(listOf("hi", "ok"), history.flatMap { it.content }.map { (it as TextContent).text })
            val derived =
                host.runtime.context.agents
                    .get(AgentId(created.id.value))
                    ?.session
                    ?.deriveMessages()
                    ?.flatMap { message -> message.content.mapNotNull { (it as? im.hikaru.harness.llm.TextBlock)?.text } }
            assertEquals(listOf("hi", "ok"), derived)

            val missing =
                assertFailsWith<LocalConnectionException> {
                    connection.session.create(agentOptions = AgentOptions())
                }
            assertEquals(422, missing.code)
            assertTrue(missing.message!!.contains("MODEL_NOT_CONFIGURED"))
        } finally {
            host.close()
        }
    }

    private fun testProfile(): DesktopProfile =
        DesktopProfile(
            hostDescription =
                HostDescription(
                    protocolVersion = ProtocolVersion(1, 0),
                    hostId = HostId("desktop-local-test-flow"),
                    displayName = "Local Flow",
                ),
            catalog =
                PluginCatalog(
                    listOf(
                        pluginDefinition("session", SessionPlugin()),
                        pluginDefinition("llm", LlmPlugin()),
                        pluginDefinition("agent", AgentPlugin()),
                        pluginDefinition("agent-loop", AgentLoopPlugin()),
                        pluginDefinition("session-api", SessionApiPlugin()),
                    ),
                ),
            entries = listOf(
                Entry("session", "session"),
                Entry("llm", "llm"),
                Entry("agent", "agent"),
                Entry("agent-loop", "agent-loop"),
                Entry("session-api", "session-api"),
            ),
        )
}
