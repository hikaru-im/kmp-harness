package im.hikaru.harness.exec.jvm

import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.session.api.TerminalLifecycle
import im.hikaru.harness.session.api.TerminalSessionId
import im.hikaru.harness.tools.ContextualToolHandler
import im.hikaru.harness.tools.ToolCallRequest
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolExecutionResult
import im.hikaru.harness.tools.ToolsKey
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

public const val TOOL_TERMINAL_PLUGIN_NAME: String = "tool-terminal"
public const val TERMINAL_OPEN_TOOL_NAME: String = "terminal_open"
public const val TERMINAL_SEND_TOOL_NAME: String = "terminal_send"
public const val TERMINAL_READ_TOOL_NAME: String = "terminal_read"
public const val TERMINAL_SIGNAL_TOOL_NAME: String = "terminal_signal"
public const val TERMINAL_CLOSE_TOOL_NAME: String = "terminal_close"
public const val TERMINAL_LIST_TOOL_NAME: String = "terminal_list"

public data class ToolTerminalConfig(
    val enableRunInBackground: Boolean = true,
    val maxResultChars: Int = 256 * 1024,
    val sendTimeoutMillis: Long = 30_000,
) {
    init {
        require(maxResultChars >= 64)
        require(sendTimeoutMillis > 0)
    }
}

private fun schema(name: String, description: String, parameters: String): ToolSchema =
    ToolSchema(name, description, Json.parseToJsonElement(parameters).jsonObject)

public val TerminalOpenToolSchema: ToolSchema = schema(
    TERMINAL_OPEN_TOOL_NAME,
    "Create a persistent, owner-isolated terminal session from a registered backend type. " +
        "Use this for shell or REPL state that must survive across tool calls.",
    """
    {"type":"object","required":["type"],"properties":{
      "type":{"type":"string","description":"Registered terminal backend type, usually \"shell\"."},
      "name":{"type":"string","description":"Optional owner-local display name such as \"main\" or \"gdb\"."},
      "cwd":{"type":"string","description":"Initial working directory, relative to the Session current directory when not absolute."}
    }}
    """.trimIndent(),
)

public val TerminalSendToolSchema: ToolSchema = schema(
    TERMINAL_SEND_TOOL_NAME,
    "Send text to a persistent terminal. By default Enter is submitted and the call waits for " +
        "output silence, timeout, or session exit. Background mode returns a job id for job_output/job_kill.",
    """
    {"type":"object","required":["sessionId","text"],"properties":{
      "sessionId":{"type":"string","description":"Terminal session id returned by terminal_open or terminal_list."},
      "text":{"type":"string","description":"UTF-8 text to write to the terminal."},
      "submit":{"type":"boolean","description":"Submit Enter after text (default true). Set false for control characters or incomplete REPL input."},
      "run_in_background":{"type":"boolean","description":"Return a job id immediately; collect with job_output or stop with job_kill."}
    }}
    """.trimIndent(),
)

public val TerminalReadToolSchema: ToolSchema = schema(
    TERMINAL_READ_TOOL_NAME,
    "Read a bounded page of retained output from a persistent terminal without sending input.",
    """
    {"type":"object","required":["sessionId"],"properties":{
      "sessionId":{"type":"string","description":"Terminal session id."},
      "offset":{"type":"number","description":"Newest-relative line offset (default 0)."},
      "count":{"type":"number","description":"Requested line count (default 500; backend caps apply)."}
    }}
    """.trimIndent(),
)

public val TerminalSignalToolSchema: ToolSchema = schema(
    TERMINAL_SIGNAL_TOOL_NAME,
    "Send an allowed signal to the current foreground process group of a persistent terminal.",
    """
    {"type":"object","required":["sessionId","signal"],"properties":{
      "sessionId":{"type":"string","description":"Terminal session id."},
      "signal":{"type":"string","enum":["SIGINT","SIGTERM","SIGKILL","SIGTSTP","SIGHUP"],"description":"Signal to deliver. The local PTY backend supports SIGINT and SIGTSTP; SIGKILL, SIGTERM and SIGHUP require terminal_close."}
    }}
    """.trimIndent(),
)

public val TerminalCloseToolSchema: ToolSchema = schema(
    TERMINAL_CLOSE_TOOL_NAME,
    "Close one persistent terminal and wait until its captured owned process tree is gone.",
    """
    {"type":"object","required":["sessionId"],"properties":{
      "sessionId":{"type":"string","description":"Terminal session id."}
    }}
    """.trimIndent(),
)

public val TerminalListToolSchema: ToolSchema = schema(
    TERMINAL_LIST_TOOL_NAME,
    "List persistent terminal sessions owned by the current agent.",
    """{"type":"object","properties":{}}""",
)

internal class TerminalToolHandler(
    private val terminals: TerminalsService,
    private val jobs: JobsService,
    private val config: ToolTerminalConfig,
) : ContextualToolHandler {
    override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult {
        val arguments = decode(request.arguments)
        return when (request.name) {
            TERMINAL_OPEN_TOOL_NAME -> open(arguments, execution)
            TERMINAL_SEND_TOOL_NAME -> send(arguments, execution)
            TERMINAL_READ_TOOL_NAME -> read(arguments, execution)
            TERMINAL_SIGNAL_TOOL_NAME -> signal(arguments, execution)
            TERMINAL_CLOSE_TOOL_NAME -> close(arguments, execution)
            TERMINAL_LIST_TOOL_NAME -> list(arguments, execution)
            else -> error("Unsupported terminal tool '${request.name}'")
        }
    }

    private suspend fun open(arguments: JsonObject, execution: ToolExecutionContext): ToolExecutionResult {
        requireKeys(arguments, setOf("type", "name", "cwd"))
        val type = requiredString(arguments, "type")
        val opened = terminals.open(execution, type, optionalString(arguments, "name"), optionalString(arguments, "cwd"))
        val label = opened.name?.let { "${opened.sessionId.value} ($it)" } ?: opened.sessionId.value
        val motd = opened.motd.ifEmpty { "(no startup output)" }
        val text = "started terminal session $label [type: ${opened.type}]\n$motd" +
            (if (opened.truncated) "\n[output truncated]" else "")
        return result(boundHead(text, config.maxResultChars))
    }

    private suspend fun send(arguments: JsonObject, execution: ToolExecutionContext): ToolExecutionResult {
        requireKeys(arguments, setOf("sessionId", "text", "submit", "run_in_background"))
        val id = TerminalSessionId(requiredString(arguments, "sessionId"))
        val text = requiredText(arguments, "text")
        val submit = optionalBoolean(arguments, "submit") ?: true
        val background = optionalBoolean(arguments, "run_in_background") ?: false
        if (!background) {
            val sent = terminals.send(execution, id, text, submit, config.sendTimeoutMillis)
            return result(boundHead(renderSend(sent), config.maxResultChars))
        }
        require(config.enableRunInBackground) { "Background terminal sends are disabled by tool-terminal configuration" }
        val deferred = CompletableDeferred<JobOutcome>()
        val cancelled = AtomicBoolean(false)
        val view = jobs.start(
            JobSpec(
                kind = "pty-send",
                label = "${id.value}: ${text.ifEmpty { "(input)" }}",
                owner = execution.session.id,
                outputLimitChars = config.maxResultChars,
                producer = JobProducer { handle ->
                    handle.scope.launch {
                        try {
                            val sent = terminals.send(execution, id, text, submit, config.sendTimeoutMillis)
                            val delta = renderSendRead(sent)
                            if (delta.isNotEmpty()) handle.append(delta)
                            deferred.complete(
                                JobOutcome(
                                    status = if (cancelled.get()) JobStatus.Killed else JobStatus.Completed,
                                    detail = sendDetail(sent),
                                ),
                            )
                        } catch (cancelledError: CancellationException) {
                            deferred.complete(JobOutcome(JobStatus.Killed, "cancelled"))
                            throw cancelledError
                        } catch (error: Throwable) {
                            deferred.complete(JobOutcome(JobStatus.Failed, error.message ?: "terminal send failed"))
                        }
                    }
                    object : JobHooks {
                        override suspend fun cancel(reason: String?) {
                            if (!cancelled.compareAndSet(false, true)) return
                            runCatching { terminals.signal(execution, id, TerminalSignal.SIGINT) }
                        }

                        override suspend fun await(): JobOutcome = deferred.await()
                    }
                },
            ),
            execution,
        )
        return result("started background job ${view.id.value}")
    }

    private suspend fun read(arguments: JsonObject, execution: ToolExecutionContext): ToolExecutionResult {
        requireKeys(arguments, setOf("sessionId", "offset", "count"))
        val id = TerminalSessionId(requiredString(arguments, "sessionId"))
        val offset = optionalPositiveInt(arguments, "offset", allowZero = true) ?: 0
        val count = optionalPositiveInt(arguments, "count", allowZero = false) ?: 500
        val page = terminals.read(execution, id, offset, count)
        val text = (page.text.ifEmpty { "(no retained output)" }) +
            "\n[lines: ${page.lineBegin}-${page.lineEnd} of ${page.totalLines}]" +
            (if (page.truncated) "\n[output truncated]" else "")
        return result(boundHead(text, config.maxResultChars))
    }

    private suspend fun signal(arguments: JsonObject, execution: ToolExecutionContext): ToolExecutionResult {
        requireKeys(arguments, setOf("sessionId", "signal"))
        val id = TerminalSessionId(requiredString(arguments, "sessionId"))
        val signal = try {
            TerminalSignal.valueOf(requiredString(arguments, "signal").uppercase())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("signal must be one of SIGINT, SIGTERM, SIGKILL, SIGTSTP, SIGHUP")
        }
        terminals.signal(execution, id, signal)
        return result(boundHead("delivered ${signal.name} to the foreground process group", config.maxResultChars))
    }

    private suspend fun close(arguments: JsonObject, execution: ToolExecutionContext): ToolExecutionResult {
        requireKeys(arguments, setOf("sessionId"))
        val id = TerminalSessionId(requiredString(arguments, "sessionId"))
        val closed = terminals.close(execution, id)
        val text = if (closed.alreadyClosing) "terminal session ${id.value} was already closing"
        else "closed terminal session ${id.value}"
        return result(boundHead(text, config.maxResultChars))
    }

    private suspend fun list(arguments: JsonObject, execution: ToolExecutionContext): ToolExecutionResult {
        requireKeys(arguments, emptySet())
        val sessions = terminals.list(execution)
        val text = if (sessions.isEmpty()) "(no terminal sessions)"
        else sessions.joinToString("\n") { session ->
            val name = session.name?.let { " ($it)" } ?: ""
            "${session.sessionId.value}$name [${session.type}] ${status(session.lifecycle)}"
        }
        return result(boundHead(text, config.maxResultChars))
    }

    private fun renderSend(sent: TerminalSent): String {
        val output = sent.viewport.ifEmpty { "(no new output)" }
        return output +
            "\n[wait: ${waitReason(sent.waitReason)}]" +
            "\n[session: ${status(sent.lifecycle)}]" +
            (if (sent.truncated) "\n[output truncated]" else "")
    }

    private fun renderSendRead(sent: TerminalSent): String {
        val output = sent.viewport
        return if (sent.truncated) "$output\n[output truncated]" else output
    }

    private fun sendDetail(sent: TerminalSent): String = when (sent.waitReason) {
        TerminalWaitReason.SESSION_EXIT -> "session exited"
        else -> "wait: ${waitReason(sent.waitReason)}"
    }

    private fun waitReason(reason: TerminalWaitReason): String = when (reason) {
        TerminalWaitReason.STDIN_READ -> "stdin_read"
        TerminalWaitReason.INFERRED_IDLE -> "inferred_idle"
        TerminalWaitReason.TIMEOUT -> "timeout"
        TerminalWaitReason.SESSION_EXIT -> "session_exit"
    }

    private fun status(lifecycle: TerminalLifecycle): String = when (lifecycle) {
        TerminalLifecycle.Running -> "running"
        is TerminalLifecycle.Exited -> "exited code=${lifecycle.code}"
        TerminalLifecycle.Unknown -> "unknown"
    }

    private fun result(text: String) = ToolExecutionResult(listOf(TextBlock(text)))

    private fun decode(arguments: String): JsonObject = try {
        Json.parseToJsonElement(arguments).jsonObject
    } catch (error: Exception) {
        throw IllegalArgumentException("Arguments must be a JSON object", error)
    }

    private fun requireKeys(arguments: JsonObject, allowed: Set<String>) {
        require(arguments.keys.all { it in allowed }) {
            "Only ${allowed.joinToString()} are allowed (got ${arguments.keys})"
        }
    }

    private fun requiredString(arguments: JsonObject, name: String): String {
        val primitive = arguments[name] as? JsonPrimitive
            ?: throw IllegalArgumentException("$name must be a string")
        require(primitive.isString && primitive.content.isNotEmpty()) { "$name must be a non-empty string" }
        return primitive.content
    }

    /** Terminal text may be empty: `terminal_send` can submit Enter alone. */
    private fun requiredText(arguments: JsonObject, name: String): String {
        val primitive = arguments[name] as? JsonPrimitive
            ?: throw IllegalArgumentException("$name must be a string")
        require(primitive.isString) { "$name must be a string" }
        return primitive.content
    }

    private fun optionalString(arguments: JsonObject, name: String): String? {
        if (name !in arguments) return null
        return requiredString(arguments, name)
    }

    private fun optionalBoolean(arguments: JsonObject, name: String): Boolean? {
        if (name !in arguments) return null
        val primitive = arguments[name] as? JsonPrimitive
            ?: throw IllegalArgumentException("$name must be a boolean")
        return primitive.booleanOrNull ?: throw IllegalArgumentException("$name must be a boolean")
    }

    private fun optionalPositiveInt(arguments: JsonObject, name: String, allowZero: Boolean): Int? {
        if (name !in arguments) return null
        val primitive = arguments[name] as? JsonPrimitive
            ?: throw IllegalArgumentException("$name must be a number")
        val value = primitive.longOrNull ?: throw IllegalArgumentException("$name must be an integer")
        require(if (allowZero) value >= 0 else value > 0) { "$name is out of range" }
        require(value <= Int.MAX_VALUE) { "$name is too large" }
        return value.toInt()
    }
}

public class ToolTerminalPlugin(private val config: ToolTerminalConfig = ToolTerminalConfig()) : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(ToolsKey), InjectSpec.required(TerminalsKey), InjectSpec.required(JobsKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val tools = context.require(ToolsKey)
        val handler = TerminalToolHandler(context.require(TerminalsKey), context.require(JobsKey), config)
        scope.add(tools.register(TerminalOpenToolSchema, handler))
        scope.add(tools.register(TerminalSendToolSchema, handler))
        scope.add(tools.register(TerminalReadToolSchema, handler))
        scope.add(tools.register(TerminalSignalToolSchema, handler))
        scope.add(tools.register(TerminalCloseToolSchema, handler))
        scope.add(tools.register(TerminalListToolSchema, handler))
    }
}

public val ToolTerminalPluginDefinition: PluginDefinition =
    pluginDefinition(TOOL_TERMINAL_PLUGIN_NAME, ToolTerminalPlugin())

/** Keep the head, which carries ids and session state, under a char budget. */
internal fun boundHead(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    val marker = "\n[output truncated]"
    if (maxChars <= marker.length) return text.take(maxChars)
    return text.take(maxChars - marker.length) + marker
}
