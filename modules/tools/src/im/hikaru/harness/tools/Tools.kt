package im.hikaru.harness.tools

import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.ContentBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.session.SessionEventKey
import im.hikaru.harness.session.SessionId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.sync.Semaphore
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Serializable
data class ToolCallRequest(
    val callId: CallId,
    val name: String,
    val arguments: String,
    val scope: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Tool name must not be blank" }
    }
}

@Serializable
data class ToolExecutionResult(
    val content: List<ContentBlock>,
    val isError: Boolean = false,
)

fun interface ToolHandler {
    suspend fun execute(request: ToolCallRequest): ToolExecutionResult
}

interface ToolsService {
    suspend fun schemas(): List<ToolSchema>
    suspend fun execute(request: ToolCallRequest): ToolExecutionResult
    fun register(schema: ToolSchema, handler: ToolHandler): Disposable
}

object ToolsKey : ServiceKey<ToolsService>("tools")

val Context.tools: ToolsService?
    get() = get(ToolsKey)

@Serializable
data class ToolCallEvent(
    val session: SessionId,
    val turn: Long,
    val step: Long,
    val call: ToolCallRequest,
)

@Serializable
data class ToolResultEvent(
    val session: SessionId,
    val turn: Long,
    val step: Long,
    val callId: CallId,
    val result: ToolExecutionResult,
)

object ToolSessionEvents {
    val Call = SessionEventKey("tool/call", ToolCallEvent.serializer())
    val Result = SessionEventKey("tool/result", ToolResultEvent.serializer())
}

class ToolsPlugin : im.hikaru.harness.runtime.plugin.SimplePlugin {
    constructor(maxConcurrentCalls: Int = 1) {
        require(maxConcurrentCalls > 0) { "maxConcurrentCalls must be positive" }
        this.maxConcurrentCalls = maxConcurrentCalls
    }

    private val maxConcurrentCalls: Int

    override suspend fun apply(
        context: Context,
        scope: im.hikaru.harness.runtime.effect.EffectScope,
    ) {
        val service = DefaultToolsService(maxConcurrentCalls)
        scope.add(context.provide(ToolsKey, service))
        scope.add(service)
    }
}

@OptIn(ExperimentalAtomicApi::class)
private class DefaultToolsService(maxConcurrentCalls: Int) : ToolsService, Disposable {
    private data class Entry(val schema: ToolSchema, val handler: ToolHandler)

    private data class State(
        val entries: Map<String, Entry> = emptyMap(),
        val completed: Set<Pair<String, CallId>> = emptySet(),
        val inFlight: Set<Pair<String, CallId>> = emptySet(),
        val disposed: Boolean = false,
    )

    private val state = AtomicReference(State())
    private val permits = Semaphore(maxConcurrentCalls)

    override suspend fun schemas(): List<ToolSchema> =
        state.load().let { current ->
            check(!current.disposed) { "Tools service is disposed" }
            current.entries.values.map { it.schema.copy(parameters = JsonObject(it.schema.parameters.toMap())) }
        }

    override fun register(schema: ToolSchema, handler: ToolHandler): Disposable {
        check(schema.name.isNotBlank()) { "Tool name must not be blank" }
        val entry = Entry(schema, handler)
        while (true) {
            val current = state.load()
            check(!current.disposed) { "Tools service is disposed" }
            check(schema.name !in current.entries) { "Tool '${schema.name}' is already registered" }
            val updated = current.copy(entries = current.entries + (schema.name to entry))
            if (state.compareAndSet(current, updated)) break
        }
        return Disposable {
            while (true) {
                val current = state.load()
                if (current.entries[schema.name] !== entry) return@Disposable
                val updated = current.copy(entries = current.entries - schema.name)
                if (state.compareAndSet(current, updated)) return@Disposable
            }
        }
    }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        val key = (request.scope ?: "") to request.callId
        var handler: ToolHandler? = null
        while (handler == null) {
                val current = state.load()
                check(!current.disposed) { "Tools service is disposed" }
                check(key !in current.completed && key !in current.inFlight) {
                    "Tool call '${request.callId.value}' already has a result"
                }
                val selected = current.entries[request.name]?.handler
                    ?: error("Tool '${request.name}' is not registered")
                val updated = current.copy(inFlight = current.inFlight + key)
                if (state.compareAndSet(current, updated)) handler = selected
        }
        val selectedHandler = checkNotNull(handler)
        var acquired = false
        return try {
            permits.acquire()
            acquired = true
            val result = selectedHandler.execute(request)
            while (true) {
                val current = state.load()
                val updated = current.copy(
                    inFlight = current.inFlight - key,
                    completed = current.completed + key,
                )
                if (state.compareAndSet(current, updated)) break
            }
            result.copy(content = result.content.toList())
        } catch (error: Throwable) {
            while (true) {
                val current = state.load()
                val updated = current.copy(inFlight = current.inFlight - key)
                if (state.compareAndSet(current, updated)) break
            }
            throw error
        } finally {
            if (acquired) {
                permits.release()
            }
        }
    }

    override suspend fun dispose() {
        while (true) {
            val current = state.load()
            if (current.disposed) return
            if (state.compareAndSet(current, State(disposed = true))) return
        }
    }
}
