package im.hikaru.harness.session

import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SessionDurabilityTest {
    @Test
    fun failedWriteDoesNotPublishAnEventOrConsumeItsSequence() = runTest {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            var fail = true
            val committed = mutableListOf<SessionEventEnvelope>()
            runtime.context.provide(SessionDurabilityKey, object : SessionDurability {
                override suspend fun append(header: SessionHeader, event: SessionEventEnvelope) {
                    check(!fail) { "Disk write rejected" }
                    committed += event
                }
                override suspend fun checkpoint(session: Session) = Unit
            })
            val session = runtime.context.createSession(SessionId("durability"))
            val value = UserMessageEvent(1, createUserMessage(listOf(TextBlock("keep this message"))))
            assertFailsWith<IllegalStateException> { session.append(SessionEventKeys.UserMessage, value) }
            assertTrue(session.events().isEmpty())
            assertTrue(committed.isEmpty())
            fail = false
            assertEquals(0, session.append(SessionEventKeys.UserMessage, value).seq)
            assertEquals(session.events(), committed)
            assertEquals(1, session.deriveMessages().size)
        } finally { runtime.context.dispose() }
    }
}
