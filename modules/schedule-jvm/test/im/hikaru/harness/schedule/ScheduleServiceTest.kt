package im.hikaru.harness.schedule

import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.InboxSnapshot
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionHeader
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.createSession
import im.hikaru.harness.session.persistence.SessionPersistence
import im.hikaru.harness.session.persistence.SessionPersistenceKey
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ScheduleServiceTest {
    private val session = SessionId("session-one")
    private val otherSession = SessionId("session-two")
    private val t = instant("2026-01-01T00:00:00Z")

    @Test
    fun createListCatalogAndDeleteAreSessionScoped() = runTest {
        val harness = ScheduleHarness()
        try {
            harness.start(t)
            val created = harness.service.create(
                session,
                ScheduleCreateRequest("title", "prompt", listOf(ScheduleSelector.After(90))),
            )
            assertEquals(t + 90_000, created.scheduledAt)
            assertEquals(listOf(created), harness.service.list(session))
            assertTrue(harness.service.list(otherSession).isEmpty())
            assertEquals(1, harness.service.catalog().size)
            assertEquals(1, harness.service.catalog(session).size)
            assertTrue(harness.service.catalog(otherSession).isEmpty())

            assertEquals(false, harness.service.delete(otherSession, created.id))
            assertEquals(true, harness.service.delete(session, created.id))
            assertTrue(harness.service.list(session).isEmpty())
            assertTrue(harness.service.catalog().isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun oneShotDeliveryCommitsInactiveReceiptAndSurvivesRestart() = runTest {
        val harness = ScheduleHarness()
        try {
            harness.start(t)
            val target = t + 60_000
            val created = harness.service.create(
                session,
                ScheduleCreateRequest(
                    "one-shot",
                    "water the plants",
                    listOf(ScheduleSelector.At(ScheduleAtInput.Offset(formatInstant(target)))),
                ),
            )

            harness.clock.now = target
            harness.service.drive(target)

            assertEquals(1, harness.delivered.size)
            val (deliveredSession, message) = harness.delivered.single()
            assertEquals(session, deliveredSession)
            val text = (message.content.single() as TextBlock).text
            assertTrue(text.startsWith("[SCHEDULE REMINDER]\n"), text)
            assertTrue(text.contains("schedule_id_json: \"${created.id.value}\""), text)
            assertTrue(text.contains("reminder_prompt_json: \"water the plants\""), text)

            assertTrue(harness.service.list(session).isEmpty())
            val stored = harness.service.catalog(session).single()
            assertEquals(ScheduleStatus.Inactive, stored.status)
            assertEquals(target, stored.record.scheduledAt)
            assertEquals(
                ScheduleDeliveryReceipt(target, target, message.id.value),
                stored.lastDelivery,
            )

            harness.service.drive(target)
            assertEquals(1, harness.delivered.size)

            harness.restart()
            val reopened = harness.service.catalog(session).single()
            assertEquals(ScheduleStatus.Inactive, reopened.status)
            assertEquals(target, reopened.lastDelivery?.scheduledAt)
            assertEquals(message.id.value, reopened.lastDelivery?.messageId)
            harness.service.drive(target)
            assertEquals(1, harness.delivered.size)
        } finally {
            harness.close()
        }
    }

    @Test
    fun restartCatchesUpOnlyTheLatestMissedOccurrence() = runTest {
        val harness = ScheduleHarness()
        try {
            harness.start(t)
            val created = harness.service.create(
                session,
                ScheduleCreateRequest("every", "ping", listOf(ScheduleSelector.Every(60))),
            )
            assertEquals(t + 60_000, created.scheduledAt)

            val late = t + 10 * 60_000 + 30_000
            harness.clock.now = late
            harness.service.drive(late)

            assertEquals(1, harness.delivered.size)
            var stored = harness.service.catalog(session).single()
            assertEquals(t + 10 * 60_000, stored.lastDelivery?.scheduledAt)
            assertEquals(t + 11 * 60_000, stored.record.scheduledAt)
            assertEquals(ScheduleStatus.Active, stored.status)

            // The next committed target is still in the future, so a reload/scan
            // must not deliver the same occurrence again.
            harness.restart()
            harness.service.drive(late)
            assertEquals(1, harness.delivered.size)
            stored = harness.service.catalog(session).single()
            assertEquals(t + 11 * 60_000, stored.record.scheduledAt)

            val next = t + 11 * 60_000
            harness.clock.now = next
            harness.service.drive(next)
            assertEquals(2, harness.delivered.size)
            stored = harness.service.catalog(session).single()
            assertEquals(next, stored.lastDelivery?.scheduledAt)
            assertEquals(t + 12 * 60_000, stored.record.scheduledAt)
        } finally {
            harness.close()
        }
    }

    @Test
    fun recurringBatchGroupsDueRemindersPerSession() = runTest {
        val harness = ScheduleHarness()
        try {
            harness.start(t)
            val first = harness.service.create(
                session,
                ScheduleCreateRequest("first", "one", listOf(ScheduleSelector.Every(60))),
            )
            val second = harness.service.create(
                session,
                ScheduleCreateRequest("second", "two", listOf(ScheduleSelector.Every(60))),
            )
            val elsewhere = harness.service.create(
                otherSession,
                ScheduleCreateRequest("other", "three", listOf(ScheduleSelector.Every(60))),
            )

            harness.clock.now = t + 60_000
            harness.service.drive(t + 60_000)

            assertEquals(2, harness.delivered.size)
            val firstBatch = harness.delivered.first { it.first == session }.second
            val text = (firstBatch.content.single() as TextBlock).text
            assertTrue(text.startsWith("[SCHEDULE REMINDER BATCH]\n"), text)
            assertTrue(text.contains("\"schedule_id\":\"${first.id.value}\""), text)
            assertTrue(text.contains("\"schedule_id\":\"${second.id.value}\""), text)
            assertTrue(text.contains("\"reminder_prompt\":\"one\""), text)

            val secondBatch = harness.delivered.first { it.first == otherSession }.second
            assertTrue((secondBatch.content.single() as TextBlock).text.startsWith("[SCHEDULE REMINDER BATCH]"))
            assertTrue((secondBatch.content.single() as TextBlock).text.contains(elsewhere.id.value))

            val receipts = harness.service.catalog().associateBy { it.record.id }
            assertEquals(firstBatch.id.value, receipts.getValue(first.id).lastDelivery?.messageId)
            assertEquals(firstBatch.id.value, receipts.getValue(second.id).lastDelivery?.messageId)
            assertEquals(secondBatch.id.value, receipts.getValue(elsewhere.id).lastDelivery?.messageId)
        } finally {
            harness.close()
        }
    }

    @Test
    fun failedDeliveryLeavesTheTaskDueWithoutAReceipt() = runTest {
        val harness = ScheduleHarness()
        try {
            harness.start(t)
            val created = harness.service.create(
                session,
                ScheduleCreateRequest("after", "prompt", listOf(ScheduleSelector.After(60))),
            )
            val target = created.scheduledAt
            harness.clock.now = target
            harness.deliveryFailure = IllegalStateException("session unavailable")

            harness.service.drive(target)

            assertTrue(harness.delivered.isEmpty())
            var stored = harness.service.catalog(session).single()
            assertEquals(ScheduleStatus.Active, stored.status)
            assertEquals(target, stored.record.scheduledAt)
            assertNull(stored.lastDelivery)

            harness.deliveryFailure = null
            harness.service.drive(target)
            assertEquals(1, harness.delivered.size)
            stored = harness.service.catalog(session).single()
            assertEquals(ScheduleStatus.Inactive, stored.status)
            assertEquals(target, stored.lastDelivery?.scheduledAt)
        } finally {
            harness.close()
        }
    }

    @Test
    fun guardedSubagentSessionsAreRefusedForCreateAndUpdate() = runTest {
        val harness = ScheduleHarness()
        try {
            harness.start(t)
            val child = harness.runtime.context.createSession(
                options = CreateSessionOptions(delegationDepth = 1),
            )

            val error = assertFailsWith<ScheduleInputException> {
                harness.service.create(
                    child.id,
                    ScheduleCreateRequest("title", "prompt", listOf(ScheduleSelector.Every(60))),
                )
            }
            assertEquals(ScheduleErrorCode.SubagentSession, error.code)
            assertEquals(SUBAGENT_SESSION_MESSAGE, error.message)

            val durableChild = SessionId("session-delegated-durable")
            val binding = harness.runtime.context.provide(
                SessionPersistenceKey,
                HeaderOnlyPersistence(
                    listOf(SessionHeader(id = durableChild, createdAt = 0, delegationDepth = 1)),
                ),
            )
            try {
                val durableError = assertFailsWith<ScheduleInputException> {
                    harness.service.create(
                        durableChild,
                        ScheduleCreateRequest("title", "prompt", listOf(ScheduleSelector.Every(60))),
                    )
                }
                assertEquals(ScheduleErrorCode.SubagentSession, durableError.code)
            } finally {
                binding.dispose()
            }

            val created = harness.service.create(
                session,
                ScheduleCreateRequest("title", "prompt", listOf(ScheduleSelector.Every(60))),
            )
            val outcome = harness.service.update(
                child.id,
                ScheduleUpdateRequest(created.id, expected = created, title = "renamed"),
            )
            assertIs<ScheduleUpdateOutcome.Miss>(outcome)
            assertEquals("subagent_session", outcome.code)
        } finally {
            harness.close()
        }
    }

    @Test
    fun updateReportsConflictEndedMissAndKeepsTheId() = runTest {
        val harness = ScheduleHarness()
        try {
            harness.start(t)
            val created = harness.service.create(
                session,
                ScheduleCreateRequest("title", "prompt", listOf(ScheduleSelector.After(60))),
            )

            val renamed = harness.service.update(
                session,
                ScheduleUpdateRequest(created.id, expected = created, title = " renamed "),
            ) as ScheduleUpdateOutcome.Resolved
            assertEquals("renamed", renamed.record.title)
            assertEquals(created.id, renamed.record.id)
            assertEquals(created.scheduledAt, renamed.record.scheduledAt)
            assertTrue(renamed.updated)

            val stale = harness.service.update(
                session,
                ScheduleUpdateRequest(renamed.record.id, expected = created, prompt = "different"),
            )
            assertIs<ScheduleUpdateOutcome.Miss>(stale)
            assertEquals(SCHEDULE_CONFLICT, stale.code)

            val missing = harness.service.update(
                session,
                ScheduleUpdateRequest(ScheduleId("schedule-missing"), expected = created),
            )
            assertIs<ScheduleUpdateOutcome.Miss>(missing)
            assertEquals(SCHEDULE_NOT_FOUND, missing.code)

            harness.clock.now = created.scheduledAt
            harness.service.drive(created.scheduledAt)
            assertEquals(1, harness.delivered.size)

            val ended = harness.service.update(
                session,
                ScheduleUpdateRequest(created.id, expected = renamed.record, title = "again"),
            )
            assertIs<ScheduleUpdateOutcome.Miss>(ended)
            assertEquals(SCHEDULE_ENDED, ended.code)
        } finally {
            harness.close()
        }
    }

    @Test
    fun createRequiresExactlyOneSelector() = runTest {
        val harness = ScheduleHarness()
        try {
            harness.start(t)
            val error = assertFailsWith<ScheduleInputException> {
                harness.service.create(
                    session,
                    ScheduleCreateRequest(
                        "title",
                        "prompt",
                        listOf(ScheduleSelector.Every(60), ScheduleSelector.After(90)),
                    ),
                )
            }
            assertEquals(ScheduleErrorCode.InvalidSelector, error.code)
        } finally {
            harness.close()
        }
    }

    /** Durable headers only; simulates a restarted process with no live Session. */
    private class HeaderOnlyPersistence(
        private val headers: List<SessionHeader>,
    ) : SessionPersistence {
        override suspend fun flush(session: Session) = Unit
        override suspend fun load(id: SessionId): Session = error("load is unsupported in this test")
        override suspend fun inbox(id: SessionId, agentId: AgentId): InboxSnapshot =
            error("inbox is unsupported in this test")
        override suspend fun listHeaders(): List<SessionHeader> = headers
        override suspend fun dispose() = Unit
    }

    private class ScheduleHarness {
        val runtime = Runtime()
        val clock = FakeScheduleClock(0)
        val delivered = mutableListOf<Pair<SessionId, Message>>()
        var deliveryFailure: Throwable? = null
        private val directory: File = Files.createTempDirectory("schedule-service").toFile()
        lateinit var service: DefaultScheduleService

        suspend fun start(now: Long) {
            clock.now = now
            runtime.install(SessionPlugin())
            runtime.install(AgentPlugin())
            openService()
        }

        suspend fun restart() {
            service.dispose()
            openService()
        }

        suspend fun close() {
            if (::service.isInitialized) service.dispose()
            runtime.context.dispose()
        }

        private suspend fun openService() {
            val store = RoomScheduleStore.open(File(directory, "harness-schedule.db"))
            service = DefaultScheduleService(
                context = runtime.context,
                store = store,
                clock = clock,
                config = ScheduleConfig(),
                deliverer = ScheduleDeliverer { sessionId, message ->
                    deliveryFailure?.let { throw it }
                    delivered += sessionId to message
                },
            )
            service.initialize()
        }
    }
}
