package im.hikaru.harness.tools

import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.ContentBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.session.SessionEventKey
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.Session
import im.hikaru.harness.system.prompt.SystemPromptKey
import im.hikaru.harness.system.prompt.ToolProviderResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
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

/** In-process authority supplied by the Agent, never decoded from tool arguments or scope. */
data class ToolExecutionContext(val context: Context, val session: Session)

fun interface ContextualToolHandler : ToolHandler {
    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
        error("This handler requires trusted ToolExecutionContext")

    suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult
}

/** Scheduling class of one registered tool; undeclared tools are exclusive. */
enum class ToolExecutionMode {
    Parallel,
    Exclusive,
}

/** Registration metadata that never reaches the model-facing schema. */
data class ToolRegistrationOptions(
    /** True only for tools whose executions may overlap sibling calls. */
    val concurrencySafe: Boolean = false,
)

/** One owner replaces a complete generation without exposing partial registration. */
interface ToolRegistrationBatch : Disposable {
    fun replace(registrations: List<ToolRegistration>)
}

data class ToolRegistration(
    val schema: ToolSchema,
    val handler: ToolHandler,
    val options: ToolRegistrationOptions = ToolRegistrationOptions(),
)

interface ToolsService {
    suspend fun schemas(): List<ToolSchema>
    suspend fun execute(request: ToolCallRequest): ToolExecutionResult
    suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult =
        execute(request)
    fun register(schema: ToolSchema, handler: ToolHandler): Disposable
    fun register(
        schema: ToolSchema,
        handler: ToolHandler,
        options: ToolRegistrationOptions,
    ): Disposable = register(schema, handler)
    fun executionMode(request: ToolCallRequest): ToolExecutionMode = ToolExecutionMode.Exclusive
    /** No sequential fallback: implementations must provide actual atomic replacement. */
    fun registrationBatch(): ToolRegistrationBatch = error("Atomic tool batches are unavailable")
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

/**
 * Registers the tool registry. Concurrency is owned by the Agent loop, matching
 * DSH: this service never serializes calls itself.
 */
class ToolsPlugin : SimplePlugin {
    override val inject: Set<InjectSpec> =
        setOf(
            InjectSpec.optional(SystemPromptKey),
        )

    override suspend fun apply(
        context: Context,
        scope: im.hikaru.harness.runtime.effect.EffectScope,
    ) {
        val service = DefaultToolsService()
        scope.add(context.provide(ToolsKey, service))
        scope.add(service)
        context.get(SystemPromptKey)?.let { prompt ->
            scope.add(
                prompt.tools { ToolProviderResult(service.schemas()) },
            )
        }
    }
}

@OptIn(ExperimentalAtomicApi::class)
private class DefaultToolsService : ToolsService, Disposable {
    private data class Entry(
        val schema: ToolSchema,
        val handler: ToolHandler,
        val options: ToolRegistrationOptions,
        val owner: Any? = null,
    )

    private data class State(
        val entries: Map<String, Entry> = emptyMap(),
        val completed: Set<Pair<String, CallId>> = emptySet(),
        val inFlight: Set<Pair<String, CallId>> = emptySet(),
        val disposed: Boolean = false,
        val batches: Set<Any> = emptySet(),
    )

    private val state = AtomicReference(State())

    override suspend fun schemas(): List<ToolSchema> =
        state.load().let { current ->
            check(!current.disposed) { "Tools service is disposed" }
            current.entries.values.map { it.schema.copy(parameters = JsonObject(it.schema.parameters.toMap())) }
        }

    override fun register(schema: ToolSchema, handler: ToolHandler): Disposable =
        register(schema, handler, ToolRegistrationOptions())

    override fun register(
        schema: ToolSchema,
        handler: ToolHandler,
        options: ToolRegistrationOptions,
    ): Disposable {
        check(schema.name.isNotBlank()) { "Tool name must not be blank" }
        val entry = Entry(schema, handler, options)
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

    override fun registrationBatch(): ToolRegistrationBatch {
        val owner = Any()
        while (true) {
            val old = state.load()
            check(!old.disposed) { "Tools service is disposed" }
            if (state.compareAndSet(old, old.copy(batches = old.batches + owner))) break
        }
        return object : ToolRegistrationBatch {
            override fun replace(registrations: List<ToolRegistration>) {
                val entries = registrations.map { Entry(it.schema, it.handler, it.options, owner) }
                require(entries.all { it.schema.name.isNotBlank() }) { "Blank batch tool name" }
                require(entries.map { it.schema.name }.distinct().size == entries.size) { "Duplicate batch tool names" }
                while (true) {
                    val old = state.load()
                    check(!old.disposed && owner in old.batches) { "Tool batch is disposed" }
                    val retained = old.entries.filterValues { it.owner !== owner }
                    check(entries.none { it.schema.name in retained }) { "Tool batch name conflict" }
                    val next = retained + entries.associateBy { it.schema.name }
                    if (state.compareAndSet(old, old.copy(entries = next))) return
                }
            }

            override suspend fun dispose() {
                while (true) {
                    val old = state.load()
                    if (owner !in old.batches) return
                    val next = old.copy(
                        entries = old.entries.filterValues { it.owner !== owner },
                        batches = old.batches - owner,
                    )
                    if (state.compareAndSet(old, next)) return
                }
            }
        }
    }

    override fun executionMode(request: ToolCallRequest): ToolExecutionMode =
        if (state.load().entries[request.name]?.options?.concurrencySafe == true) {
            ToolExecutionMode.Parallel
        } else {
            ToolExecutionMode.Exclusive
        }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult = executeInternal(request, null)

    override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult =
        executeInternal(request, execution)

    private suspend fun executeInternal(request: ToolCallRequest, execution: ToolExecutionContext?): ToolExecutionResult {
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
        return try {
            check(!state.load().disposed) { "Tools service is disposed" }
            val result = if (selectedHandler is ContextualToolHandler && execution != null) {
                selectedHandler.execute(request, execution)
            } else {
                selectedHandler.execute(request)
            }
            while (true) {
                val current = state.load()
                if (current.disposed) break
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
                if (current.disposed) break
                val updated = current.copy(inFlight = current.inFlight - key)
                if (state.compareAndSet(current, updated)) break
            }
            throw error
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
