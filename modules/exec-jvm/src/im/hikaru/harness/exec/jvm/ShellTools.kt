package im.hikaru.harness.exec.jvm

import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.BackgroundOutputResult
import im.hikaru.harness.session.api.BackgroundProcess
import im.hikaru.harness.session.api.BackgroundProcessLifecycle
import im.hikaru.harness.session.api.BackgroundProcessesService
import im.hikaru.harness.tools.ContextualToolHandler
import im.hikaru.harness.tools.ToolCallRequest
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolExecutionResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

public const val BASH_TOOL_NAME: String = "bash"
public const val PWSH_TOOL_NAME: String = "pwsh"

/**
 * Decodes the DSH shell-tool arguments and hands a fully specified command to
 * the local subprocess service. Shell dialect differences are limited to the
 * argv builder; timeout, cwd confinement, output bounds, background inventory,
 * and cleanup stay in [SubprocessService].
 */
internal class ShellToolHandler(
    private val toolName: String,
    private val jobKind: String,
    private val argvFor: (String) -> List<String>,
    private val service: SubprocessService,
    private val processes: BackgroundProcessesService,
    private val jobs: JobsService,
    private val pollMillis: Long = 50,
    private val maxJobOutputChars: Int = 256 * 1024,
) : ContextualToolHandler {
    private data class Arguments(
        val command: String,
        val description: String,
        val timeoutMillis: Long?,
        val workdir: String?,
        val runInBackground: Boolean,
    )

    override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult {
        require(request.name == toolName) { "Unsupported tool" }
        require(request.scope == null || request.scope == execution.session.id.value) {
            "Tool scope does not match the trusted Session"
        }
        val arguments = decode(request.arguments)
        val command = ExecCommand(
            argv = argvFor(arguments.command),
            cwd = arguments.workdir,
            background = arguments.runInBackground,
            timeoutMillis = arguments.timeoutMillis,
            stdin = false,
        )
        if (!arguments.runInBackground) return service.execute(command, request.callId, execution)
        var started: BackgroundProcess? = null
        val view = jobs.start(
            JobSpec(
                kind = jobKind,
                label = arguments.command,
                owner = execution.session.id,
                outputLimitChars = maxJobOutputChars,
                producer = JobProducer { handle ->
                    val process = service.startBackground(command, request.callId, execution)
                    started = process
                    BackgroundJobHooks(processes, execution.session.id, process, handle, pollMillis)
                },
            ),
            execution,
        )
        checkNotNull(started) { "Background producer did not start a process" }
        // A background call acknowledges the job handle, not process completion.
        return ToolExecutionResult(listOf(TextBlock("started background job ${view.id.value}")))
    }

    private fun decode(value: String): Arguments {
        val element = try {
            Json.parseToJsonElement(value)
        } catch (error: Exception) {
            throw IllegalArgumentException("Arguments must be valid JSON", error)
        }
        val objectValue = element as? JsonObject
            ?: throw IllegalArgumentException("Arguments must be an object")
        require(objectValue.keys.all { it in ALLOWED_KEYS }) {
            "Only command, description, timeoutMs, workdir and run_in_background are allowed (got ${objectValue.keys})"
        }
        fun requiredString(name: String): String {
            val primitive = objectValue[name] as? JsonPrimitive
                ?: throw IllegalArgumentException("$name must be a string")
            require(primitive.isString && primitive.content.isNotBlank()) { "$name must be a nonblank string" }
            return primitive.content
        }
        fun optionalString(name: String): String? =
            if (name in objectValue) requiredString(name) else null
        val timeoutMillis = objectValue["timeoutMs"]?.let { element ->
            val primitive = element as? JsonPrimitive
                ?: throw IllegalArgumentException("timeoutMs must be a number")
            val millis = primitive.longOrNull
                ?: throw IllegalArgumentException("timeoutMs must be an integer")
            require(millis > 0) { "timeoutMs must be a positive number" }
            millis
        }
        val runInBackground = objectValue["run_in_background"]?.let { element ->
            val primitive = element as? JsonPrimitive
                ?: throw IllegalArgumentException("run_in_background must be a boolean")
            primitive.booleanOrNull
                ?: throw IllegalArgumentException("run_in_background must be a boolean")
        } ?: false
        return Arguments(
            command = requiredString("command"),
            description = requiredString("description"),
            timeoutMillis = timeoutMillis,
            workdir = optionalString("workdir"),
            runInBackground = runInBackground,
        )
    }

    private companion object {
        val ALLOWED_KEYS = setOf("command", "description", "timeoutMs", "workdir", "run_in_background")
    }
}

/**
 * Adapts one background process to the generic job contract. The process
 * inventory owns capture and lifecycle; this adapter only forwards the delta
 * since its cursor into the job ring and maps exit evidence to a job outcome.
 */
private class BackgroundJobHooks(
    private val processes: BackgroundProcessesService,
    private val session: SessionId,
    private val process: BackgroundProcess,
    private val handle: JobHandle,
    private val pollMillis: Long,
) : JobHooks {
    private val cancelled = AtomicBoolean(false)
    // A job outlives its owning Session: settlement runs after the owner is
    // removed, which the public gateway contract rejects on purpose.
    private val owned = processes as? OwnedBackgroundProcesses

    override suspend fun cancel(reason: String?) {
        if (!cancelled.compareAndSet(false, true)) return
        if (owned != null) owned.cancelOwned(session, process.process)
        else processes.cancel(session, process.process)
    }

    override suspend fun await(): JobOutcome {
        var cursor = 0L
        while (true) {
            val output = owned?.readOwned(session, process.process) ?: processes.read(session, process.process)
            if (output !is BackgroundOutputResult.Available) {
                return JobOutcome(JobStatus.Failed, "process record disappeared")
            }
            val bufferStart = output.totalChars - output.buffer.length
            if (output.totalChars > cursor) {
                val offset = (cursor - bufferStart).coerceIn(0L, output.buffer.length.toLong()).toInt()
                val delta = output.buffer.substring(offset)
                if (delta.isNotEmpty()) handle.append(delta)
                cursor = output.totalChars
            }
            when (val lifecycle = output.lifecycle) {
                BackgroundProcessLifecycle.Running -> delay(pollMillis)
                BackgroundProcessLifecycle.Unknown -> return JobOutcome(JobStatus.Failed, "process state is unknown")
                is BackgroundProcessLifecycle.Exited -> {
                    val status = if (lifecycle.exitCode == 0 && !output.timedOut) JobStatus.Completed else JobStatus.Failed
                    val detail = if (output.timedOut) "timed out" else "exit code: ${lifecycle.exitCode}"
                    return JobOutcome(status, detail)
                }
            }
        }
    }
}

private fun shellParameters(commandDescription: String): String = """
    {
      "type": "object",
      "required": ["description", "command"],
      "properties": {
        "description": {
          "type": "string",
          "description": "Clear, concise description of what this command does in active voice, 5-10 words (shown in the UI). Examples: \"ls\" → \"List files in current directory\"; \"git status\" → \"Show working tree status\"."
        },
        "command": {"type": "string", "description": "$commandDescription"},
        "timeoutMs": {
          "type": "number",
          "description": "Timeout in milliseconds. The executor applies its configured default and cap, and kills the command on expiry."
        },
        "workdir": {
          "type": "string",
          "description": "Working directory for this command. Defaults to the session workspace; a relative path is resolved against it."
        },
        "run_in_background": {
          "type": "boolean",
          "description": "Run in the background and return an owned process handle immediately. No timeout applies; read its output with job_output and stop it with job_kill."
        }
      }
    }
""".trimIndent()

public val BashToolSchema: ToolSchema = ToolSchema(
    name = BASH_TOOL_NAME,
    description = "Execute a bash command (`bash -c`) and return its stdout/stderr. " +
        "Each call runs in a fresh shell; pass `workdir` instead of using `cd`. " +
        "Long output is truncated to its tail. " +
        "Provide `description` before `command` in the arguments. " +
        "Before any delete or move, verify that the resolved absolute target path is the intended one; " +
        "never run it against a computed path you have not checked. " +
        "An unset variable expands to an empty string, so guard variables in such paths with " +
        "\${'$'}{VAR:?}.",
    parameters = Json.parseToJsonElement(shellParameters("The bash command to execute.")).jsonObject,
)

public val PwshToolSchema: ToolSchema = ToolSchema(
    name = PWSH_TOOL_NAME,
    description = "Execute a PowerShell command (`pwsh -Command`) and return its stdout/stderr. " +
        "Each call runs in a fresh PowerShell process; pass `workdir` instead of using `cd`. " +
        "Paths use native Windows form (`C:\\...`); read environment variables with `${'$'}env:NAME`. " +
        "Long output is truncated to its tail. " +
        "Provide `description` before `command` in the arguments. " +
        "Before any delete or move, verify that the resolved absolute target path is the intended one; " +
        "never run it against a computed path you have not checked. " +
        "Do not assign to automatic variables such as `${'$'}HOME`; variable names are case-insensitive, " +
        "so `${'$'}home` is the same read-only variable.",
    parameters = Json.parseToJsonElement(shellParameters("The PowerShell command to execute.")).jsonObject,
)
