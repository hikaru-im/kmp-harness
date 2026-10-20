package im.hikaru.harness.schedule

import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.createSession
import im.hikaru.harness.tools.ToolCallRequest
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolExecutionResult
import im.hikaru.harness.tools.ToolsKey
import im.hikaru.harness.tools.ToolsPlugin
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ToolScheduleTest {
    @Test
    fun schemasUseTheDSHNamesAndRequiredFields() {
        assertEquals(SCHEDULE_CREATE_TOOL_NAME, ScheduleCreateToolSchema.name)
        assertEquals(SCHEDULE_LIST_TOOL_NAME, ScheduleListToolSchema.name)
        assertEquals(SCHEDULE_UPDATE_TOOL_NAME, ScheduleUpdateToolSchema.name)
        assertEquals(SCHEDULE_DELETE_TOOL_NAME, ScheduleDeleteToolSchema.name)
        assertEquals(
            setOf("prompt", "title"),
            ScheduleCreateToolSchema.parameters.getValue("required").jsonArray
                .map { it.jsonPrimitive.content }.toSet(),
        )
        assertEquals(
            setOf("id"),
            ScheduleUpdateToolSchema.parameters.getValue("required").jsonArray
                .map { it.jsonPrimitive.content }.toSet(),
        )
    }

    @Test
    fun createThenListReturnsTheExactHostView() = runTest {
        val fixture = ToolFixture().also { it.start() }
        try {
            val created = fixture.call(
                SCHEDULE_CREATE_TOOL_NAME,
                """{"title":"  task  ","prompt":"  remind me  ","after_seconds":3600}""",
            )
            assertEquals(
                listOf("id", "kind", "title", "prompt", "afterSeconds", "scheduledAt", "state", "deliveryMode"),
                created.keys.toList(),
            )
            assertEquals("after", created.getValue("kind").jsonPrimitive.content)
            assertEquals("task", created.getValue("title").jsonPrimitive.content)
            assertEquals("remind me", created.getValue("prompt").jsonPrimitive.content)
            assertEquals(3600L, created.getValue("afterSeconds").jsonPrimitive.content.toLong())
            assertEquals("host", created.getValue("deliveryMode").jsonPrimitive.content)
            assertEquals(
                formatInstant(fixture.clock.now + 3_600_000),
                created.getValue("scheduledAt").jsonPrimitive.content,
            )
            assertEquals("scheduled", created.getValue("state").jsonPrimitive.content)

            val list = fixture.callArray(SCHEDULE_LIST_TOOL_NAME, "{}")
            assertEquals(1, list.size)
            assertEquals(created["id"], list.single().jsonObject["id"])
        } finally {
            fixture.close()
        }
    }

    @Test
    fun updateAndDeleteKeepTheIdAndReportValuesForMisses() = runTest {
        val fixture = ToolFixture().also { it.start() }
        try {
            val created = fixture.call(
                SCHEDULE_CREATE_TOOL_NAME,
                """{"title":"task","prompt":"prompt","every_seconds":60}""",
            )
            val id = created.getValue("id").jsonPrimitive.content

            val renamed = fixture.call(
                SCHEDULE_UPDATE_TOOL_NAME,
                """{"id":"$id","title":" renamed "}""",
            )
            assertEquals(id, renamed.getValue("id").jsonPrimitive.content)
            assertEquals("renamed", renamed.getValue("title").jsonPrimitive.content)
            assertEquals("every", renamed.getValue("kind").jsonPrimitive.content)

            val missing = fixture.call(
                SCHEDULE_UPDATE_TOOL_NAME,
                """{"id":"schedule-missing","prompt":"new"}""",
            )
            assertFalse(missing.getValue("updated").jsonPrimitive.boolean)
            assertEquals("schedule_not_found", missing.getValue("code").jsonPrimitive.content)

            val deleted = fixture.call(SCHEDULE_DELETE_TOOL_NAME, """{"id":"$id"}""")
            assertTrue(deleted.getValue("deleted").jsonPrimitive.boolean)
            assertEquals(id, deleted.getValue("id").jsonPrimitive.content)

            val again = fixture.call(SCHEDULE_DELETE_TOOL_NAME, """{"id":"$id"}""")
            assertFalse(again.getValue("deleted").jsonPrimitive.boolean)
            assertEquals("schedule_not_found", again.getValue("code").jsonPrimitive.content)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun endedRemindersReportScheduleEndedOnUpdate() = runTest {
        val fixture = ToolFixture().also { it.start() }
        try {
            val created = fixture.call(
                SCHEDULE_CREATE_TOOL_NAME,
                """{"title":"task","prompt":"prompt","after_seconds":60}""",
            )
            val id = created.getValue("id").jsonPrimitive.content
            fixture.clock.now += 60_000
            fixture.service.drive(fixture.clock.now)
            assertEquals(1, fixture.delivered.size)

            val result = fixture.call(
                SCHEDULE_UPDATE_TOOL_NAME,
                """{"id":"$id","title":"again"}""",
            )
            assertFalse(result.getValue("updated").jsonPrimitive.boolean)
            assertEquals("schedule_ended", result.getValue("code").jsonPrimitive.content)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun createValidationIsAValueErrorNotATransportFailure() = runTest {
        val fixture = ToolFixture().also { it.start() }
        try {
            assertEquals(
                "invalid_selector",
                fixture.callError(
                    SCHEDULE_CREATE_TOOL_NAME,
                    """{"title":"t","prompt":"p","after_seconds":60,"every_seconds":60}""",
                ).getValue("code").jsonPrimitive.content,
            )
            assertEquals(
                "invalid_selector",
                fixture.callError(
                    SCHEDULE_CREATE_TOOL_NAME,
                    """{"title":"t","prompt":"p","after_seconds":60,"unknown":true}""",
                ).getValue("code").jsonPrimitive.content,
            )
            assertEquals(
                "invalid_prompt",
                fixture.callError(
                    SCHEDULE_CREATE_TOOL_NAME,
                    """{"title":"t","prompt":"   ","after_seconds":60}""",
                ).getValue("code").jsonPrimitive.content,
            )
            assertEquals(
                "invalid_prompt",
                fixture.callError(
                    SCHEDULE_CREATE_TOOL_NAME,
                    """{"title":"${"t".repeat(121)}","prompt":"p","after_seconds":60}""",
                ).getValue("code").jsonPrimitive.content,
            )
            assertEquals(
                "frequency_too_high",
                fixture.callError(
                    SCHEDULE_CREATE_TOOL_NAME,
                    """{"title":"t","prompt":"p","every_seconds":30}""",
                ).getValue("code").jsonPrimitive.content,
            )
            assertEquals(
                "invalid_selector",
                fixture.callError(SCHEDULE_UPDATE_TOOL_NAME, """{"id":"schedule-x"}""")
                    .getValue("code").jsonPrimitive.content,
            )
            assertTrue(fixture.delivered.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun delegatedSessionsAreRefusedBeforeAnyEffect() = runTest {
        val fixture = ToolFixture().also { it.start() }
        try {
            val child: Session = fixture.runtime.context.createSession(
                options = CreateSessionOptions(delegationDepth = 1),
            )
            val execution = ToolExecutionContext(fixture.runtime.context, child)
            val result = fixture.callRaw(
                ToolCallRequest(
                    CallId("call-child"),
                    SCHEDULE_CREATE_TOOL_NAME,
                    """{"title":"t","prompt":"p","after_seconds":60}""",
                ),
                execution,
            )
            assertFalse(result.isError)
            val error = fixture.json(result)
            assertEquals("subagent_session", error.getValue("code").jsonPrimitive.content)
            assertEquals(
                "A delegated subagent cannot use reminders.",
                error.getValue("message").jsonPrimitive.content,
            )
            assertTrue(fixture.service.catalog().isEmpty())
        } finally {
            fixture.close()
        }
    }

    private class ToolFixture {
        val runtime = Runtime()
        val clock = FakeScheduleClock(System.currentTimeMillis() + 24 * 60 * 60_000)
        val delivered = mutableListOf<Pair<im.hikaru.harness.session.SessionId, im.hikaru.harness.llm.Message>>()
        private val directory: File = Files.createTempDirectory("schedule-tools").toFile()
        lateinit var service: DefaultScheduleService
        private lateinit var binding: Disposable
        private lateinit var session: Session

        suspend fun initialize() {
            runtime.install(SessionPlugin())
            runtime.install(AgentPlugin())
            runtime.install(ToolsPlugin())
            val store = RoomScheduleStore.open(File(directory, "harness-schedule.db"))
            service = DefaultScheduleService(
                runtime.context,
                store,
                clock,
                ScheduleConfig(),
                ScheduleDeliverer { sessionId, message -> delivered += sessionId to message },
            )
            service.initialize()
            binding = runtime.context.provide(ScheduleKey, service)
            runtime.install(ToolSchedulePlugin())
            session = runtime.context.createSession()
        }

        private var calls = 0

        private fun nextCallId(): CallId = CallId("call-${++calls}")

        suspend fun call(name: String, arguments: String): JsonObject =
            json(callRaw(ToolCallRequest(nextCallId(), name, arguments), execution()))

        suspend fun callArray(name: String, arguments: String) =
            Json.parseToJsonElement(
                (callRaw(ToolCallRequest(nextCallId(), name, arguments), execution())
                    .content.single() as TextBlock).text,
            ).jsonArray

        suspend fun callError(name: String, arguments: String): JsonObject =
            json(callRaw(ToolCallRequest(nextCallId(), name, arguments), execution()))

        suspend fun callRaw(
            request: ToolCallRequest,
            execution: ToolExecutionContext,
        ): ToolExecutionResult = runtime.context.require(ToolsKey).execute(request, execution)

        fun json(result: ToolExecutionResult): JsonObject =
            Json.parseToJsonElement((result.content.single() as TextBlock).text).jsonObject

        fun execution(): ToolExecutionContext = ToolExecutionContext(runtime.context, session)

        suspend fun close() {
            if (::service.isInitialized) service.dispose()
            runtime.context.dispose()
        }
    }

    private suspend fun ToolFixture.start() = initialize()
}
