package im.hikaru.harness.agent.loop

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentAttemptResume
import im.hikaru.harness.agent.AgentAttemptResumer
import im.hikaru.harness.agent.AgentErrorCode
import im.hikaru.harness.agent.AgentFactoryRequest
import im.hikaru.harness.agent.AgentInstructionsKey
import im.hikaru.harness.agent.AgentRequestErrorDecision
import im.hikaru.harness.agent.AgentRequestErrorEvent
import im.hikaru.harness.agent.AgentEvents
import im.hikaru.harness.agent.AgentPreStepEvent
import im.hikaru.harness.agent.AgentRequestEvent
import im.hikaru.harness.agent.AgentSendTarget
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.agent.AgentStatusNotice
import im.hikaru.harness.agent.AgentTurnStoppingEvent
import im.hikaru.harness.agent.AgentWakeup
import im.hikaru.harness.agent.Inbox
import im.hikaru.harness.agent.InboxQueue
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.llm.AbortedFinishReason
import im.hikaru.harness.llm.BlockAssembler
import im.hikaru.harness.llm.ContentBlock
import im.hikaru.harness.llm.ErrorFinishReason
import im.hikaru.harness.llm.FinishReason
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmCallConfig
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.LlmRuntime
import im.hikaru.harness.llm.LlmSessionId
import im.hikaru.harness.llm.MaxTokensFinishReason
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ReasoningBlock
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolCallsFinishReason
import im.hikaru.harness.llm.createToolResultMessage
import im.hikaru.harness.llm.copyMessage
import im.hikaru.harness.llm.normalizeLlmFailure
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.AbortedTurnEndReason
import im.hikaru.harness.session.AssistantChunkEvent
import im.hikaru.harness.session.AssistantMessageEvent
import im.hikaru.harness.session.CompletedTurnEndReason
import im.hikaru.harness.session.EpochHeader
import im.hikaru.harness.session.EpochReason
import im.hikaru.harness.session.ErrorTurnEndReason
import im.hikaru.harness.session.RequestContextEvent
import im.hikaru.harness.session.RequestHeaderEvent
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionAdmissionPermit
import im.hikaru.harness.session.SessionEventKeys
import im.hikaru.harness.session.SessionEventNames
import im.hikaru.harness.session.SessionRequestContext
import im.hikaru.harness.session.StepEndEvent
import im.hikaru.harness.session.StepStartEvent
import im.hikaru.harness.session.TurnEndEvent
import im.hikaru.harness.session.TurnEndReason
import im.hikaru.harness.session.TurnStartEvent
import im.hikaru.harness.session.UserMessageEvent
import im.hikaru.harness.session.UserTurnAbortCause
import im.hikaru.harness.tools.ToolCallRequest
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolCallEvent
import im.hikaru.harness.tools.ToolResultEvent
import im.hikaru.harness.tools.ToolSessionEvents
import im.hikaru.harness.tools.tools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Default Agent implementation. It is intentionally package-private to callers. */
internal class AgentLoopAgent(
    request: AgentFactoryRequest,
    private val llm: LlmRuntime,
    private val config: AgentLoopConfig,
    private val callConfig: LlmCallConfig,
) : Agent, AgentAttemptResumer {
    override val id: AgentId = request.id
    override val options: AgentOptions = request.options
    override val session: Session = request.session
    override val inbox: Inbox = request.inbox
    override val context: Context = request.context

    private val mutex = Mutex()
    /** The child Context owns this scope and joins it during disposal. */
    private val scope: CoroutineScope = context.managedScope()
    private class DriverCandidate(val permit: SessionAdmissionPermit) {
        val adoption = CompletableDeferred<Boolean>()
        lateinit var job: Job
    }

    private var driver: DriverCandidate? = null
    private var disposed = false
    private val stopping = mutableSetOf<Any>()
    private var restartAfterStops = false
    private var wakeupGeneration = 0L
    private var nextTurn = 0L
    private var initialized = false
    private var initialization: CompletableDeferred<Unit>? = null
    private var currentStatus = AgentStatus.IDLE

    init {
        context.effect(Disposable { shutdown() })
    }

    override val status: AgentStatus
        get() = currentStatus

    override suspend fun awaitIdle() {
        while (true) {
            // Include a candidate that has cleared its installed identity but
            // is still releasing its permit in the innermost finally.
            val jobs = mutex.withLock { scope.coroutineContext[Job]?.children?.toList().orEmpty() }
            if (jobs.isEmpty()) return
            jobs.forEach { it.join() }
        }
    }

    override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) {
        val permit = session.acquireOperationPermit()
        try {
            val stop = Any()
            val job = mutex.withLock {
                if (disposed) return
                stopping += stop
                driver?.job
            }
            // Once registered, this request owns its stop through join, clear
            // and reconciliation, even if its caller is cancelled.
            withContext(NonCancellable) {
                var cleared = false
                try {
                    job?.cancel(cause as? CancellationException ?: CancellationException(cause?.message))
                    job?.join()
                    if (!keepInbox) {
                        inbox.clear(InboxQueue.NEXT_TURN)
                        inbox.clear(InboxQueue.NEXT_STEP)
                        cleared = true
                    }
                } finally {
                    val reconcile = mutex.withLock {
                        stopping.remove(stop)
                        restartAfterStops = restartAfterStops || cleared
                        (stopping.isEmpty() && restartAfterStops).also {
                            if (it) restartAfterStops = false
                        }
                    }
                    if (reconcile) reconcileDriver()
                }
            }
        } finally {
            permit.dispose()
        }
    }

    override suspend fun send(
        message: im.hikaru.harness.llm.Message,
        target: AgentSendTarget,
        wakeup: AgentWakeup,
    ): String {
        val permit = session.acquireOperationPermit()
        try {
            ensureInitialized()
            val itemId = inbox.insert(message, target, wakeup)
            if (wakeup != AgentWakeup.NONE) {
                mutex.withLock { wakeupGeneration += 1L }
                startDriver()
            }
            return itemId
        } finally {
            permit.dispose()
        }
    }

    override suspend fun resumeAttempt(request: AgentAttemptResume) {
        val permit = session.acquireOperationPermit()
        try {
            ensureInitialized()
            mutex.withLock {
                check(!disposed) { "Agent is disposed" }
                check(driver == null) { "Agent is already running" }
                check(stopping.isEmpty()) { "Agent is stopping" }
                check(scope.coroutineContext[Job]?.isActive == true) { "Agent scope is cancelled" }
            }
            check(startDriver(resume = request)) { "Agent cannot adopt the restored attempt" }
        } finally {
            permit.dispose()
        }
    }

    private suspend fun shutdown() {
        val job =
            mutex.withLock {
                if (disposed) return
                disposed = true
                driver?.job
            }
        job?.cancel(CancellationException("Agent disposed"))
        job?.join()
        scope.coroutineContext[Job]?.cancel()
    }

    /** Called only under mutex; no Inbox access or callbacks belong here. */
    private fun canStartDriver(previous: DriverCandidate?, previousJob: Job?): Boolean =
        !disposed && stopping.isEmpty() && driver === previous &&
            scope.coroutineContext[Job]?.isActive == true && previousJob?.isActive != false

    private suspend fun startDriver(
        resume: AgentAttemptResume? = null,
        previous: DriverCandidate? = null,
        previousJob: Job? = null,
        generation: Long? = null,
    ): Boolean {
        mutex.withLock {
            if (!canStartDriver(previous, previousJob)) return false
            if (generation != null && wakeupGeneration != generation) return false
        }
        val permit = session.acquireOperationPermit()
        var candidate: DriverCandidate? = null
        var job: Job? = null
        var adopted = false
        try {
            currentCoroutineContext().ensureActive()
            // Admission can suspend; recheck the owned scope before creating
            // a candidate, then check both Jobs again at installation.
            mutex.withLock {
                if (!canStartDriver(previous, previousJob)) return false
                if (generation != null && wakeupGeneration != generation) return false
            }
            val record = DriverCandidate(permit)
            candidate = record
            // UNDISPATCHED enters the outer try even in a cancelled scope.
            // The adoption barrier prevents initialization or notices here.
            val launched = scope.launch(start = CoroutineStart.UNDISPATCHED) { runDriver(record, resume) }
            job = launched
            record.job = launched
            currentCoroutineContext().ensureActive()
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                if (launched.isActive && canStartDriver(previous, previousJob) &&
                    (generation == null || wakeupGeneration == generation)
                ) {
                    driver = record
                    adopted = true
                }
            }
            return adopted
        } finally {
            if (adopted) {
                // Also opened if the caller is cancelled after installation.
                candidate!!.adoption.complete(true)
            } else {
                withContext(NonCancellable) {
                    try {
                        candidate?.adoption?.complete(false)
                        job?.cancel()
                        job?.join()
                    } finally {
                        // Covers synchronous launch failure as well as every
                        // unadopted candidate; disposal is identity/idempotent.
                        permit.dispose()
                    }
                }
            }
        }
    }

    private suspend fun reconcileDriver(previous: DriverCandidate? = null, previousJob: Job? = null) {
        while (true) {
            val generation = mutex.withLock {
                if (!canStartDriver(previous, previousJob)) return
                wakeupGeneration
            }
            val queued = inbox.snapshot().nextTurn.isNotEmpty()
            if (!queued) {
                val settled = mutex.withLock {
                    if (!canStartDriver(previous, previousJob)) return
                    if (wakeupGeneration != generation) false else {
                        if (driver === previous) driver = null
                        true
                    }
                }
                if (settled) return
            } else {
                if (startDriver(previous = previous, previousJob = previousJob, generation = generation)) return
                mutex.withLock {
                    if (!canStartDriver(previous, previousJob) || wakeupGeneration == generation) return
                }
            }
        }
    }

    private suspend fun runDriver(candidate: DriverCandidate, resume: AgentAttemptResume?) {
        val realJob = currentCoroutineContext()[Job]!!
        var adopted = false
        var normalExit = false
        try {
            adopted = candidate.adoption.await()
            if (!adopted) return
            ensureInitialized()
            updateStatus(AgentStatus.RUNNING)
            if (resume != null) {
                runRestoredTurn(resume)
                currentCoroutineContext().ensureActive()
            }
            while (true) {
                currentCoroutineContext().ensureActive()
                val item = inbox.claim(InboxQueue.NEXT_TURN).firstOrNull() ?: break
                runTurn(item.message)
                currentCoroutineContext().ensureActive()
            }
            normalExit = true
        } finally {
            withContext(NonCancellable) {
                try {
                    if (adopted) {
                        // Keep this identity registered throughout IDLE; a
                        // concurrent cancel must still find the real Job.
                        updateStatus(AgentStatus.IDLE)
                        if (normalExit && realJob.isActive && scope.coroutineContext[Job]?.isActive == true) {
                            // The next candidate acquires its own activity
                            // before this driver's permit can be released.
                            reconcileDriver(candidate, realJob)
                        }
                    }
                } finally {
                    try {
                        mutex.withLock { if (driver === candidate) driver = null }
                    } finally {
                        candidate.permit.dispose()
                    }
                }
            }
        }
    }

    private suspend fun ensureInitialized() {
        var loader = false
        val completion = mutex.withLock {
            if (initialized) return
            initialization ?: CompletableDeferred<Unit>().also {
                initialization = it
                loader = true
            }
        }
        if (!loader) {
            completion.await()
            return
        }
        try {
            val maxTurn =
                session.events().asSequence()
                    .filter { it.type == SessionEventNames.TURN_START }
                    .mapNotNull { it.data.jsonObject["turn"]?.jsonPrimitive?.longOrNull }
                    .maxOrNull()
                    ?: 0L
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                nextTurn = maxOf(nextTurn, maxTurn)
                initialized = true
                initialization = null
            }
            completion.complete(Unit)
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                mutex.withLock { if (initialization === completion) initialization = null }
                completion.completeExceptionally(error)
            }
            throw error
        }
    }

    private suspend fun runTurn(initialMessage: im.hikaru.harness.llm.Message) {
        val turn = mutex.withLock { ++nextTurn }
        session.append(SessionEventKeys.TurnStart, TurnStartEvent(turn))
        var stepStarted = false
        var step = 1L
        var stepMessages = listOf(initialMessage)
        var endReason: TurnEndReason = CompletedTurnEndReason
        try {
            while (true) {
                context.waterfall(
                    AgentEvents.PreStep,
                    AgentPreStepEvent(this, turn, step),
                ) { Unit }
                session.append(SessionEventKeys.StepStart, StepStartEvent(turn, step))
                stepStarted = true
                stepMessages.forEach { appendUser(turn, it) }
                val extra = inbox.claim(InboxQueue.NEXT_STEP)
                extra.forEach { appendUser(turn, it.message) }
                when (val outcome = runStep(turn, step)) {
                    is StepOutcome.Finished -> {
                        endReason = outcome.reason
                        break
                    }
                    is StepOutcome.Continue -> {
                        withContext(NonCancellable) {
                            session.append(SessionEventKeys.StepEnd, StepEndEvent(turn, step))
                        }
                        stepStarted = false
                        step += 1L
                        stepMessages = outcome.messages
                    }
                }
            }
        } catch (_: CancellationException) {
            endReason = AbortedTurnEndReason(UserTurnAbortCause)
        } catch (error: Throwable) {
            endReason = ErrorTurnEndReason(normalizeLlmFailure(error))
        } finally {
            withContext(NonCancellable) {
                if (stepStarted) {
                    session.append(SessionEventKeys.StepEnd, StepEndEvent(turn, step))
                }
                context.sequential(
                    AgentEvents.TurnStopping,
                    AgentTurnStoppingEvent(this@AgentLoopAgent, turn, endReason),
                )
                session.append(SessionEventKeys.TurnEnd, TurnEndEvent(turn, endReason))
            }
        }
    }

    private suspend fun runRestoredTurn(resume: AgentAttemptResume) {
        val turn = resume.turn
        var step = resume.step
        var stepStarted = true
        var stepMessages = emptyList<im.hikaru.harness.llm.Message>()
        var initialAttempt = resume.attempt
        var restoringCurrentStep = true
        var endReason: TurnEndReason = CompletedTurnEndReason
        try {
            while (true) {
                if (restoringCurrentStep) {
                    resume.beforeAttempt()
                    restoringCurrentStep = false
                } else {
                    context.waterfall(
                        AgentEvents.PreStep,
                        AgentPreStepEvent(this, turn, step),
                    ) { Unit }
                    session.append(SessionEventKeys.StepStart, StepStartEvent(turn, step))
                    stepStarted = true
                    stepMessages.forEach { appendUser(turn, it) }
                    val extra = inbox.claim(InboxQueue.NEXT_STEP)
                    extra.forEach { appendUser(turn, it.message) }
                }

                when (val outcome = runStep(turn, step, initialAttempt)) {
                    is StepOutcome.Finished -> {
                        endReason = outcome.reason
                        break
                    }
                    is StepOutcome.Continue -> {
                        withContext(NonCancellable) {
                            session.append(SessionEventKeys.StepEnd, StepEndEvent(turn, step))
                        }
                        stepStarted = false
                        step += 1L
                        stepMessages = outcome.messages
                        initialAttempt = 1
                    }
                }
            }
        } catch (_: CancellationException) {
            endReason = AbortedTurnEndReason(UserTurnAbortCause)
        } catch (error: Throwable) {
            endReason = ErrorTurnEndReason(normalizeLlmFailure(error))
        } finally {
            withContext(NonCancellable) {
                if (stepStarted) {
                    session.append(SessionEventKeys.StepEnd, StepEndEvent(turn, step))
                }
                context.sequential(
                    AgentEvents.TurnStopping,
                    AgentTurnStoppingEvent(this@AgentLoopAgent, turn, endReason),
                )
                session.append(SessionEventKeys.TurnEnd, TurnEndEvent(turn, endReason))
            }
        }
    }

    private suspend fun appendUser(turn: Long, message: im.hikaru.harness.llm.Message) {
        session.append(
            SessionEventKeys.UserMessage,
            UserMessageEvent(turn = turn, message = copyMessage(message)),
        )
    }

    private sealed interface StepOutcome {
        data class Finished(val reason: TurnEndReason) : StepOutcome
        data class Continue(val messages: List<im.hikaru.harness.llm.Message>) : StepOutcome
    }

    private suspend fun runStep(
        turn: Long,
        step: Long,
        initialAttempt: Int = 1,
    ): StepOutcome {
        require(initialAttempt > 0) { "Initial attempt must be positive" }
        var attempt = initialAttempt - 1
        while (true) {
            attempt += 1
            val prepared =
                try {
                    llm.prepareCall(callConfig)
                } catch (error: Throwable) {
                    return StepOutcome.Finished(ErrorTurnEndReason(normalizeLlmFailure(error)))
                }
            try {
                val instructions = context.get(AgentInstructionsKey)?.readInstructions(this)?.takeIf { it.isNotBlank() }
                val system = when {
                    instructions == null -> config.system
                    config.system == null -> instructions
                    else -> "${config.system}\n\n$instructions"
                }
                val toolsService = context.tools
                val toolSchemas = toolsService?.schemas()?.takeIf { it.isNotEmpty() }
                val request =
                    GenerateOptions(
                        provider = prepared.config.provider,
                        model = prepared.config.model,
                        messages = session.deriveMessages(),
                        reasoningEffort = prepared.config.reasoningEffort,
                        system = system,
                        tools = toolSchemas,
                        temperature = prepared.config.temperature,
                        maxTokens = prepared.config.maxTokens,
                        stop = prepared.config.stop,
                        sessionId = LlmSessionId(session.id.value),
                    )
                context.waterfall(
                    AgentEvents.Request,
                    AgentRequestEvent(this, turn, step, attempt, request),
                ) { Unit }
                session.append(
                    SessionEventKeys.RequestHeader,
                    RequestHeaderEvent(
                        turn = turn,
                        step = step,
                        header = EpochHeader(
                            config = prepared.config,
                            adapterDefaults = prepared.adapterDefaults,
                            system = request.system,
                            tools = toolSchemas,
                            reason = if (attempt == 1) EpochReason.INITIAL else EpochReason.CHANGE,
                        ),
                    ),
                )
                prepared.context?.let { modelContext ->
                    session.append(
                        SessionEventKeys.RequestContext,
                        RequestContextEvent(
                            turn = turn,
                            step = step,
                            context = SessionRequestContext(
                                provider = prepared.config.provider,
                                model = prepared.config.model,
                                contextWindow = modelContext.contextWindow,
                            ),
                        ),
                    )
                }

                val assembler = BlockAssembler()
                try {
                    prepared.stream(request).collect { chunk ->
                        session.append(
                            SessionEventKeys.AssistantChunk,
                            AssistantChunkEvent(turn, step, chunk),
                        )
                        assembler.push(chunk)
                    }
                } catch (_: CancellationException) {
                    val blocks = assembler.blocks()
                    if (hasVisibleContent(blocks)) {
                        withContext(NonCancellable) {
                            session.append(
                                SessionEventKeys.AssistantMessage,
                                AssistantMessageEvent(
                                    turn = turn,
                                    step = step,
                                    message = assembler.message(prepared.config.provider, prepared.config.model),
                                    usage = assembler.usage,
                                    interrupted = true,
                                ),
                            )
                        }
                    }
                    return StepOutcome.Finished(AbortedTurnEndReason(UserTurnAbortCause))
                }

                when (val finish = assembler.finish) {
                    is ErrorFinishReason -> {
                        val decision =
                            context.waterfall(
                                AgentEvents.RequestError,
                                AgentRequestErrorEvent(this, turn, step, attempt, finish.failure, prepared.retryPolicy),
                            ) { AgentRequestErrorDecision.Unhandled }
                        if (decision is AgentRequestErrorDecision.Retry) continue
                        return StepOutcome.Finished(ErrorTurnEndReason(finish.failure))
                    }
                    is AbortedFinishReason -> {
                        val decision =
                            context.waterfall(
                                AgentEvents.RequestError,
                                AgentRequestErrorEvent(
                                    this,
                                    turn,
                                    step,
                                    attempt,
                                    finish.failure,
                                    prepared.retryPolicy,
                                ),
                            ) { AgentRequestErrorDecision.Unhandled }
                        if (decision is AgentRequestErrorDecision.Retry) continue
                        return StepOutcome.Finished(ErrorTurnEndReason(finish.failure))
                    }
                    is ToolCallsFinishReason -> {
                        if (toolsService == null) {
                            return StepOutcome.Finished(ErrorTurnEndReason(toolsUnavailable()))
                        }
                        return executeTools(turn, step, assembler, prepared.config.provider, prepared.config.model, toolsService)
                    }
                    else -> {
                        val blocks = assembler.blocks()
                        if (blocks.any { it is ToolCallBlock }) {
                            if (toolsService == null) {
                                return StepOutcome.Finished(ErrorTurnEndReason(toolsUnavailable()))
                            }
                            return executeTools(turn, step, assembler, prepared.config.provider, prepared.config.model, toolsService)
                        }
                        session.append(
                            SessionEventKeys.AssistantMessage,
                            AssistantMessageEvent(
                                turn = turn,
                                step = step,
                                message = assembler.message(prepared.config.provider, prepared.config.model),
                                usage = assembler.usage,
                            ),
                        )
                        return StepOutcome.Finished(
                            when (finish) {
                                is MaxTokensFinishReason -> im.hikaru.harness.session.MaxTokensTurnEndReason
                                else -> CompletedTurnEndReason
                            }
                        )
                    }
                }
            } finally {
                prepared.dispose()
            }
        }
    }

    private suspend fun executeTools(
        turn: Long,
        step: Long,
        assembler: BlockAssembler,
        provider: String,
        model: String,
        toolsService: im.hikaru.harness.tools.ToolsService,
    ): StepOutcome {
        val calls = assembler.blocks().filterIsInstance<ToolCallBlock>()
        if (calls.isEmpty() || calls.map { it.id }.distinct().size != calls.size) {
            return StepOutcome.Finished(ErrorTurnEndReason(toolsUnavailable()))
        }
        val registered = toolsService.schemas().map { it.name }.toSet()
        if (calls.any { it.name !in registered }) {
            return StepOutcome.Finished(ErrorTurnEndReason(toolsUnavailable("One or more tool calls are not registered")))
        }
        session.append(
            SessionEventKeys.AssistantMessage,
            AssistantMessageEvent(
                turn = turn,
                step = step,
                message = assembler.message(provider, model),
                usage = assembler.usage,
            ),
        )
        val results = mutableListOf<im.hikaru.harness.llm.Message>()
        for (call in calls) {
            val request = ToolCallRequest(call.id, call.name, call.arguments, scope = session.id.value)
            session.append(ToolSessionEvents.Call, ToolCallEvent(session.id, turn, step, request))
            val result =
                try {
                    toolsService.execute(request, ToolExecutionContext(context, session))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    im.hikaru.harness.tools.ToolExecutionResult(
                        content = listOf(TextBlock("TOOL_EXECUTION_ERROR")),
                        isError = true,
                    )
                }
            session.append(ToolSessionEvents.Result, ToolResultEvent(session.id, turn, step, call.id, result))
            val message = createToolResultMessage(call.id, result.content, result.isError)
            results += message
        }
        return StepOutcome.Continue(results)
    }

    private suspend fun updateStatus(next: AgentStatus) {
        val changed = mutex.withLock {
            if (currentStatus == next) false else true.also { currentStatus = next }
        }
        if (changed) {
            context.emitContained(AgentEvents.Status, AgentStatusNotice(this, next))
        }
    }

    private fun toolsUnavailable(message: String = "The provider returned tool calls but no Tools service is installed"): LlmFailure =
        LlmFailure(
            message = message,
            code = "TOOLS_NOT_AVAILABLE",
        )

    private fun hasVisibleContent(blocks: List<ContentBlock>): Boolean =
        blocks.any { block ->
            when (block) {
                is TextBlock -> block.text.isNotEmpty()
                is ReasoningBlock -> block.text.isNotEmpty()
                else -> false
            }
        }
}
