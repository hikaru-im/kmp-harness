package im.hikaru.harness.agent

import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.copyMessage
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionEventEnvelope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 两个语义队列的 durable projection。
 * Session event 先提交，内存队列后更新；这样失败不会留下不可恢复的 live 状态。
 */
class Inbox internal constructor(
    private val agentId: AgentId,
    private val session: Session,
    private val eventContext: Context? = null,
    initial: InboxSnapshot = InboxSnapshot(0L, emptyList(), emptyList()),
    private val onChanged: suspend (InboxSpliceOperation) -> Unit = {},
) {
    private val mutex = Mutex()
    private var nextTurn = initial.nextTurn.map(::copyItem).toMutableList()
    private var nextStep = initial.nextStep.map(::copyItem).toMutableList()
    private var version = initial.version
    private var nextItemId = initial.nextItemId()
    private var boundAgent: Agent? = null

    /** Binds the live Agent after the factory has completed successfully. */
    internal fun bindAgent(agent: Agent) {
        check(agent.id == agentId) { "Inbox agent identity does not match" }
        boundAgent = agent
    }

    suspend fun insert(
        message: Message,
        target: AgentSendTarget,
        wakeup: AgentWakeup,
        callConfig: im.hikaru.harness.llm.LlmCallConfig? = null,
    ): String =
        mutate {
            val id = "${agentId.value}:$nextItemId"
            val item =
                InboxItem(
                    id = id,
                    message = copyMessage(message),
                    target = target,
                    wakeup = wakeup,
                    callConfig = callConfig,
                )
            val queue = queueFor(target)
            val nextItems = (queue + item).map(::copyItem)
            commit(
                queue = queueName(target),
                operation = InboxSpliceOperation.INSERT,
                items = nextItems,
            )
            id
        }

    suspend fun claim(queue: InboxQueue): List<InboxItem> =
        mutate {
            val current = queueFor(queue)
            if (current.isEmpty()) return@mutate emptyList()
            val claimed =
                (if (queue == InboxQueue.NEXT_TURN) listOf(current.first()) else current.toList())
                    .map(::copyItem)
            val remaining = current.drop(claimed.size).map(::copyItem)
            commit(queue, InboxSpliceOperation.CLAIM, remaining)
            claimed
        }

    suspend fun replace(
        queue: InboxQueue,
        items: List<InboxItem>,
    ) {
        mutate {
            commit(queue, InboxSpliceOperation.REPLACE, items.map(::copyItem))
        }
    }

    suspend fun clear(queue: InboxQueue) {
        mutate {
            if (queueFor(queue).isEmpty()) return@mutate
            commit(queue, InboxSpliceOperation.CLEAR, emptyList())
        }
    }

    suspend fun snapshot(): InboxSnapshot =
        mutex.withLock {
            InboxSnapshot(
                version = version,
                nextTurn = nextTurn.map(::copyItem),
                nextStep = nextStep.map(::copyItem),
            )
        }

    private suspend fun <T> mutate(block: suspend () -> T): T {
        val permit = session.acquireOperationPermit()
        try {
            return mutex.withLock {
                currentCoroutineContext().ensureActive()
                block()
            }
        } finally {
            permit.dispose()
        }
    }

    private suspend fun commit(
        queue: InboxQueue,
        operation: InboxSpliceOperation,
        items: List<InboxItem>,
    ) {
        val nextVersion = version + 1L
        // Prepare both detached projections before a durable append can succeed.
        val projection = items.map(::copyItem).toMutableList()
        val event =
            InboxSpliceEvent(
                agent = session.id,
                queue = queue,
                operation = operation,
                version = nextVersion,
                items = items.map(::copyItem),
            )
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            session.append(AgentSessionEvents.InboxSpliced, event)
            when (queue) {
                InboxQueue.NEXT_TURN -> nextTurn = projection
                InboxQueue.NEXT_STEP -> nextStep = projection
            }
            version = nextVersion
            if (operation == InboxSpliceOperation.INSERT) nextItemId += 1L
            onChanged(operation)
            val agent = boundAgent
            if (agent != null) {
                val key =
                    when (operation) {
                        InboxSpliceOperation.INSERT -> AgentEvents.InboxInserted
                        InboxSpliceOperation.CLAIM -> AgentEvents.InboxClaimed
                        InboxSpliceOperation.REPLACE,
                        InboxSpliceOperation.CLEAR -> AgentEvents.InboxDiscarded
                    }
                eventContext?.emitContained(key, AgentNotice(agent))
            }
        }
    }

    private fun queueFor(target: AgentSendTarget): MutableList<InboxItem> =
        queueFor(
            when (target) {
                AgentSendTarget.NEXT_TURN -> InboxQueue.NEXT_TURN
                AgentSendTarget.NEXT_STEP -> InboxQueue.NEXT_STEP
            }
        )

    private fun queueFor(queue: InboxQueue): MutableList<InboxItem> =
        when (queue) {
            InboxQueue.NEXT_TURN -> nextTurn
            InboxQueue.NEXT_STEP -> nextStep
        }

    private fun queueName(target: AgentSendTarget): InboxQueue =
        when (target) {
            AgentSendTarget.NEXT_TURN -> InboxQueue.NEXT_TURN
            AgentSendTarget.NEXT_STEP -> InboxQueue.NEXT_STEP
        }

    companion object {
        private val replayJson =
            Json {
                encodeDefaults = false
                explicitNulls = false
                ignoreUnknownKeys = false
                classDiscriminator = "type"
            }

        /** 从完整 Session log 重放指定 Agent 的 Inbox projection。 */
        fun replay(
            events: List<SessionEventEnvelope>,
            agentId: AgentId,
        ): InboxSnapshot {
            var snapshot = InboxSnapshot(0L, emptyList(), emptyList())
            for (envelope in events) {
                if (envelope.type != AgentSessionEventNames.INBOX_SPLICED) continue
                val splice =
                    replayJson.decodeFromJsonElement(InboxSpliceEvent.serializer(), envelope.data)
                if (splice.agent.value != agentId.value) continue
                require(splice.version == snapshot.version + 1L) {
                    "Inbox splice versions must be contiguous"
                }
                snapshot =
                    when (splice.queue) {
                        InboxQueue.NEXT_TURN -> snapshot.copy(
                            version = splice.version,
                            nextTurn = splice.items.map(::copyItem),
                        )
                        InboxQueue.NEXT_STEP -> snapshot.copy(
                            version = splice.version,
                            nextStep = splice.items.map(::copyItem),
                        )
                    }
            }
            return snapshot
        }

        private fun copyItem(item: InboxItem): InboxItem =
            item.copy(message = copyMessage(item.message))
    }
}

private fun InboxSnapshot.nextItemId(): Long =
    (nextTurn + nextStep)
        .asSequence()
        .mapNotNull { item -> item.id.substringAfterLast(':').toLongOrNull() }
        .maxOrNull()
        ?.plus(1L)
        ?: 0L
