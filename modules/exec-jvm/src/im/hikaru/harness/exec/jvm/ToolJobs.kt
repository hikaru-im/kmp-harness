package im.hikaru.harness.exec.jvm

import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.llm.PluginMessageSource
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.tools.ContextualToolHandler
import im.hikaru.harness.tools.ToolCallRequest
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolExecutionResult
import im.hikaru.harness.tools.ToolsKey
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

public const val TOOL_JOBS_PLUGIN_NAME: String = "tool-jobs"
public const val JOB_OUTPUT_TOOL_NAME: String = "job_output"
public const val JOB_LIST_TOOL_NAME: String = "job_list"
public const val JOB_KILL_TOOL_NAME: String = "job_kill"

public data class ToolJobsConfig(
    /** Wait applied when `job_output` sets `wait` without `timeout_ms`. */
    val waitTimeoutMillis: Long = 30_000,
    /** Hard cap for a single `job_output` wait. */
    val maxWaitTimeoutMillis: Long = 600_000,
    /** UTF-16 cap for one complete model-facing result. */
    val maxResultChars: Int = 256 * 1024,
) {
    init {
        require(waitTimeoutMillis in 1..maxWaitTimeoutMillis)
        require(maxResultChars > 0)
    }
}

public val JobOutputToolSchema: ToolSchema = ToolSchema(
    name = JOB_OUTPUT_TOOL_NAME,
    description = "Read a background job: output since the previous read for stream jobs, " +
        "or the result of a finished final-output job.",
    parameters = Json.parseToJsonElement(
        """
        {"type":"object","required":["job_id"],"properties":{
          "job_id":{"type":"string","description":"Job id returned by the tool that started the background work."},
          "wait":{"type":"boolean","description":"Block until the job finishes or the timeout expires; a timed-out wait leaves the job running. Defaults to false."},
          "timeout_ms":{"type":"number","description":"Max wait in milliseconds with wait: true. Defaults to and is capped by configuration."}
        }}
        """.trimIndent(),
    ).jsonObject,
)

public val JobListToolSchema: ToolSchema = ToolSchema(
    name = JOB_LIST_TOOL_NAME,
    description = "List your background jobs (running and finished) with their ids, kinds, and statuses.",
    parameters = Json.parseToJsonElement("""{"type":"object","properties":{}}""").jsonObject,
)

public val JobKillToolSchema: ToolSchema = ToolSchema(
    name = JOB_KILL_TOOL_NAME,
    description = "Request cancellation of a running background job.",
    parameters = Json.parseToJsonElement(
        """
        {"type":"object","required":["job_id"],"properties":{
          "job_id":{"type":"string","description":"Job id returned by the tool that started the background work."},
          "reason":{"type":"string","description":"Optional short reason, recorded in the log and forwarded to the job."}
        }}
        """.trimIndent(),
    ).jsonObject,
)

internal class JobToolHandler(
    private val jobs: JobsService,
    private val config: ToolJobsConfig,
    private val killedByModel: MutableSet<JobId>,
) : ContextualToolHandler {
    override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult =
        when (request.name) {
            JOB_OUTPUT_TOOL_NAME -> output(decode(request.arguments), execution)
            JOB_LIST_TOOL_NAME -> list(decode(request.arguments), execution)
            JOB_KILL_TOOL_NAME -> kill(decode(request.arguments), execution)
            else -> error("Unsupported job tool '${request.name}'")
        }

    private suspend fun output(arguments: JsonObject, execution: ToolExecutionContext): ToolExecutionResult {
        requireKeys(arguments, setOf("job_id", "wait", "timeout_ms"))
        val id = jobId(arguments)
        val wait = optionalBoolean(arguments, "wait") ?: false
        val timeout = optionalPositiveLong(arguments, "timeout_ms")
        if (wait) {
            val bounded = (timeout ?: config.waitTimeoutMillis).coerceAtMost(config.maxWaitTimeoutMillis)
            jobs.waitJob(id, bounded, execution)
        }
        val read = jobs.read(id, execution)
        val delta = read.chunks.filter { it.channel != "log" }.joinToString("") { it.text }.let { text ->
            if (!read.lossy) text else text + (if (text.isEmpty() || text.endsWith("\n")) "" else "\n") +
                "[some output was dropped from memory]"
        }
        val result = read.result
        val body = when {
            result == null -> delta
            delta.isEmpty() -> result
            else -> delta + (if (delta.endsWith("\n")) "" else "\n") + result
        }.ifEmpty { "(no new output)" }
        val text = body + (if (body.endsWith("\n")) "" else "\n") + statusLine(read.job)
        return ToolExecutionResult(listOf(TextBlock(boundTail(text, config.maxResultChars))))
    }

    private suspend fun list(arguments: JsonObject, execution: ToolExecutionContext): ToolExecutionResult {
        requireKeys(arguments, emptySet())
        val views = jobs.list(execution)
        val text = if (views.isEmpty()) "(no background jobs)"
        else views.joinToString("\n") { "${it.id.value} [${it.kind}] ${it.status.name.lowercase()} — ${it.label}" }
        return ToolExecutionResult(listOf(TextBlock(boundTail(text, config.maxResultChars))))
    }

    private suspend fun kill(arguments: JsonObject, execution: ToolExecutionContext): ToolExecutionResult {
        requireKeys(arguments, setOf("job_id", "reason"))
        val id = jobId(arguments)
        val reason = optionalString(arguments, "reason")
        val result = jobs.kill(id, execution, reason)
        if (result.outcome == JobKillOutcome.REQUESTED) killedByModel += id
        val text = if (result.outcome == JobKillOutcome.ALREADY_FINISHED) {
            "job ${id.value} had already finished ${statusLine(result.job)}"
        } else {
            "requested cancellation of job ${id.value}"
        }
        return ToolExecutionResult(listOf(TextBlock(boundTail(text, config.maxResultChars))))
    }

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

    private fun jobId(arguments: JsonObject): JobId {
        val primitive = arguments["job_id"] as? JsonPrimitive
            ?: throw IllegalArgumentException("job_id must be a string")
        require(primitive.isString && primitive.content.isNotEmpty()) { "job_id must be a non-empty string" }
        return JobId(primitive.content)
    }

    private fun optionalString(arguments: JsonObject, name: String): String? {
        if (name !in arguments) return null
        val primitive = arguments[name] as? JsonPrimitive
            ?: throw IllegalArgumentException("$name must be a string")
        require(primitive.isString) { "$name must be a string" }
        return primitive.content.takeIf(String::isNotBlank)
    }

    private fun optionalBoolean(arguments: JsonObject, name: String): Boolean? {
        if (name !in arguments) return null
        val primitive = arguments[name] as? JsonPrimitive
            ?: throw IllegalArgumentException("$name must be a boolean")
        return primitive.booleanOrNull ?: throw IllegalArgumentException("$name must be a boolean")
    }

    private fun optionalPositiveLong(arguments: JsonObject, name: String): Long? {
        if (name !in arguments) return null
        val primitive = arguments[name] as? JsonPrimitive
            ?: throw IllegalArgumentException("$name must be a number")
        val value = primitive.longOrNull ?: throw IllegalArgumentException("$name must be an integer")
        require(value > 0) { "$name must be positive" }
        return value
    }
}

public class ToolJobsPlugin(private val config: ToolJobsConfig = ToolJobsConfig()) : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(ToolsKey), InjectSpec.required(JobsKey), InjectSpec.required(AgentKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val jobs = context.require(JobsKey)
        val agents = context.require(AgentKey)
        val killedByModel = ConcurrentHashMap.newKeySet<JobId>()
        val handler = JobToolHandler(jobs, config, killedByModel)
        val tools = context.require(ToolsKey)
        scope.add(tools.register(JobOutputToolSchema, handler))
        scope.add(tools.register(JobListToolSchema, handler))
        scope.add(tools.register(JobKillToolSchema, handler))
        scope.add(
            jobs.subscribe(
                JobListener { event ->
                    if (event.cause == JobSettlementCause.TEARDOWN || event.awaited) return@JobListener
                    if (killedByModel.remove(event.job.id)) return@JobListener
                    val owner = event.job.owner ?: return@JobListener
                    val agent = agents.get(AgentId(owner.value)) ?: return@JobListener
                    val text = "background job ${event.job.id.value} (${event.job.kind}: ${event.job.label}) " +
                        "finished ${statusLine(event.job)}.\nDone; job_output."
                    val message = createUserMessage(
                        listOf(TextBlock(boundTail(text, config.maxResultChars))),
                        PluginMessageSource(TOOL_JOBS_PLUGIN_NAME),
                    )
                    if (agent.status == AgentStatus.IDLE) agent.followup(message) else agent.inject(message)
                },
            )
        )
    }
}

public val ToolJobsPluginDefinition: PluginDefinition =
    pluginDefinition(TOOL_JOBS_PLUGIN_NAME, ToolJobsPlugin())

internal fun statusLine(job: JobView): String {
    val detail = job.progress ?: job.detail
    return if (detail == null) "[status: ${job.status.name.lowercase()}]"
    else "[status: ${job.status.name.lowercase()}, $detail]"
}

/** Keep the tail, which carries settlement and recent output, under a char budget. */
internal fun boundTail(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    val marker = "[output truncated]\n"
    if (maxChars <= marker.length) return text.takeLast(maxChars)
    return marker + text.takeLast(maxChars - marker.length)
}
