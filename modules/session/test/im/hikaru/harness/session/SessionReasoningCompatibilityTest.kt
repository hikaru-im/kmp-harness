package im.hikaru.harness.session

import im.hikaru.harness.llm.ReasoningEffortId
import im.hikaru.harness.llm.ReasoningModeId
import im.hikaru.harness.llm.ReasoningOptions
import im.hikaru.harness.runtime.Runtime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SessionReasoningCompatibilityTest {
    @Test
    fun durableLegacyHeaderAndNewExplicitProviderDefaultShouldRemainDistinctAfterLoad() = runTest {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            val source = runtime.context.sessions.prepare()
            val legacy = SessionEventEnvelope(
                SessionEventNames.REQUEST_HEADER, 0, 1,
                Json.parseToJsonElement("""{"turn":1,"step":1,"header":{"config":{"provider":"p","model":"m","reasoningEffort":"high","maxTokens":4096},"reason":"initial"}}"""),
            )
            val loaded = runtime.context.sessions.load(source.header, listOf(legacy))
            val oldHeader = requireNotNull(loaded.session.requestHeader())
            assertEquals(ReasoningEffortId("high"), oldHeader.config.reasoningEffort)
            assertNull(oldHeader.config.reasoning)

            val explicitDefault = oldHeader.copy(config = oldHeader.config.copy(reasoningEffort = null, reasoning = ReasoningOptions()))
            loaded.session.append(SessionEventKeys.RequestHeader, RequestHeaderEvent(2, 1, explicitDefault))
            val persisted = loaded.session.events()
            val header = loaded.session.header
            loaded.dispose()
            val reopened = runtime.context.sessions.load(header, persisted)
            assertEquals(ReasoningOptions(), reopened.session.requestHeader()?.config?.reasoning)
            assertFalse(headerEquals(oldHeader, requireNotNull(reopened.session.requestHeader())))
        } finally { runtime.context.dispose() }
    }

    @Test
    fun durableExtendedReasoningModeShouldSurviveStrictEventNormalization() = runTest {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            val source = runtime.context.sessions.prepare()
            val event = SessionEventEnvelope(
                SessionEventNames.REQUEST_HEADER, 0, 1,
                Json.parseToJsonElement("""{"turn":1,"step":1,"header":{"config":{"provider":"p","model":"m","reasoning":{"mode":"vendor:between-tools","effort":"high","budgetTokens":0,"executionModeId":"pro"}},"reason":"resume"}}"""),
            )
            val loaded = runtime.context.sessions.load(source.header, listOf(event))
            assertEquals(
                ReasoningOptions(ReasoningModeId("vendor:between-tools"), ReasoningEffortId("high"), 0, "pro"),
                loaded.session.requestHeader()?.config?.reasoning,
            )
            assertEquals(loaded.session.requestHeader(), foldRequestHeader(loaded.session.events()))
        } finally { runtime.context.dispose() }
    }
}
