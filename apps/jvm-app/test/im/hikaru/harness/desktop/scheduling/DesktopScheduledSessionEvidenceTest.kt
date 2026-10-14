package im.hikaru.harness.desktop.scheduling

import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.llm.*
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class DesktopScheduledSessionEvidenceTest {
    @Test fun onlyExactUserInputAndSameTurnEndProduceEvidence() = runBlocking {
        events { valid ->
            val exact = assertNotNull(extract(valid))
            assertEquals(ScheduledRunPhase.Succeeded, exact.outcome)
            assertEquals(1L, exact.turn)
            assertEquals(0L, exact.userMessageSeq)
            assertEquals(1L, exact.turnEndSeq)
            assertEquals(valid.last().time, exact.endedAt)
            assertNull(DesktopScheduledSessionEvidence.terminalEvidence(valid, "inbox-receipt-id"))
            assertNull(DesktopScheduledSessionEvidence.terminalEvidence(valid, ""))
            assertNull(extract(valid.take(1)))
            assertNull(extract(emptyList()))
            assertNull(extract(listOf(valid.last().copy(seq = 0))))
            assertNull(extract(listOf(valid.first(), valid.last().copy(data = Json.encodeToJsonElement(TurnEndEvent.serializer(), TurnEndEvent(2, CompletedTurnEndReason))))))
        }
    }

    @Test fun corruptSequenceTimeTurnAndDuplicateEvidenceAreRejected() = runBlocking {
        events { valid ->
            val user = valid.first()
            val end = valid.last()
            val invalid = listOf(
                listOf(user.copy(seq = -1), end), listOf(user, end.copy(seq = 0)),
                listOf(user, end.copy(seq = 2)), listOf(user.copy(time = -1), end),
                listOf(user, end.copy(time = -1)), listOf(user.copy(time = 10), end.copy(time = 9)),
                listOf(user.copy(data = JsonObject(user.data.jsonObject + ("turn" to JsonPrimitive(0)))), end),
                listOf(user, end.copy(data = JsonObject(end.data.jsonObject + ("turn" to JsonPrimitive(-1))))),
                listOf(user, user.copy(seq = 1), end.copy(seq = 2)),
                listOf(user, user.copy(seq = 1, data = JsonObject(user.data.jsonObject + ("turn" to JsonPrimitive(2)))), end.copy(seq = 2)),
                listOf(user, end, end.copy(seq = 2)),
                listOf(user, end, end.copy(seq = 2, data = Json.encodeToJsonElement(TurnEndEvent.serializer(), TurnEndEvent(1, InterruptedTurnEndReason)))),
                listOf(end.copy(seq = 0), user.copy(seq = 1)),
                listOf(user, end.copy(data = buildJsonObject { put("turn", 1) })),
            )
            invalid.forEachIndexed { index, log -> assertNull(extract(log), "Invalid log " + index) }
        }
    }

    @Test fun assistantOnlyCannotCompleteAndNonSuccessReasonsStayDistinct() = runBlocking {
        events { valid ->
            val assistant = valid.first().copy(type = SessionEventNames.ASSISTANT_MESSAGE,
                data = Json.encodeToJsonElement(AssistantMessageEvent.serializer(), AssistantMessageEvent(1, 1,
                    createAssistantMessage(listOf(TextBlock("answer")), provider = "scripted", model = "test"))))
            assertNull(extract(listOf(assistant, valid.last())))
            val syntheticInputs = listOf(
                createUserMessage(listOf(TextBlock("plugin input")), PluginMessageSource("fixture")),
                createToolResultMessage(CallId("fixture-call"), listOf(TextBlock("tool result")), false),
            )
            syntheticInputs.forEach { message ->
                val input = valid.first().copy(data = Json.encodeToJsonElement(UserMessageEvent.serializer(),
                    UserMessageEvent(1, message.copy(id = MessageId("scheduled-input")))))
                assertNull(extract(listOf(input, valid.last())))
            }
            val cases = listOf(
                ErrorTurnEndReason(LlmFailure("controlled failure", "TEST_FAILURE")) to ScheduledRunPhase.Failed,
                BlockedTurnEndReason to ScheduledRunPhase.Blocked,
                MaxTokensTurnEndReason to ScheduledRunPhase.Incomplete,
                InterruptedTurnEndReason to ScheduledRunPhase.Interrupted,
                AbortedTurnEndReason(UserTurnAbortCause) to ScheduledRunPhase.Aborted,
            )
            cases.forEach { (reason, outcome) ->
                val end = valid.last().copy(data = Json.encodeToJsonElement(TurnEndEvent.serializer(), TurnEndEvent(1, reason)))
                val evidence = assertNotNull(extract(listOf(valid.first(), end)))
                assertEquals(outcome, evidence.outcome)
                if (outcome == ScheduledRunPhase.Failed) assertEquals("TEST_FAILURE", evidence.failureCode)
            }
        }
    }

    private fun extract(events: List<SessionEventEnvelope>) = DesktopScheduledSessionEvidence.terminalEvidence(events, "scheduled-input")

    private suspend fun events(block: suspend (List<SessionEventEnvelope>) -> Unit) {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            val session = runtime.context.createSession(SessionId("evidence-test"))
            session.append(SessionEventKeys.UserMessage, UserMessageEvent(1,
                createUserMessage(listOf(TextBlock("scheduled prompt"))).copy(id = MessageId("scheduled-input"))))
            session.append(SessionEventKeys.TurnEnd, TurnEndEvent(1, CompletedTurnEndReason))
            block(session.events())
        } finally { runtime.context.dispose() }
    }
}
