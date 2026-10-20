package im.hikaru.harness.schedule

import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.tools.ContextualToolHandler
import im.hikaru.harness.tools.ToolCallRequest
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolExecutionResult
import im.hikaru.harness.tools.ToolsKey
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

public const val TOOL_SCHEDULE_PLUGIN_NAME: String = "tool-schedule"
public const val SCHEDULE_CREATE_TOOL_NAME: String = "schedule_create"
public const val SCHEDULE_LIST_TOOL_NAME: String = "schedule_list"
public const val SCHEDULE_UPDATE_TOOL_NAME: String = "schedule_update"
public const val SCHEDULE_DELETE_TOOL_NAME: String = "schedule_delete"

private const val INTERNAL_ERROR_MESSAGE: String = "The schedule operation failed."
private const val SUBAGENT_TOOL_MESSAGE: String = "A delegated subagent cannot use reminders."

private const val CREATE_SELECTOR_MESSAGE: String =
    "schedule_create accepts exactly one of after_seconds, at, every_seconds, daily, weekly, or cron."
private const val UPDATE_SELECTOR_MESSAGE: String =
    "schedule_update accepts at most one of at, every_seconds, daily, weekly, or cron."
private const val UPDATE_EMPTY_MESSAGE: String =
    "schedule_update needs a new title, prompt, or one of at, every_seconds, daily, weekly, or cron."

private val CREATE_DESCRIPTION: String =
    "Create a reminder in the current session that delivers prompt when it becomes due. " +
        "Supply exactly one timing parameter: after_seconds, at, every_seconds, daily, weekly, or cron. " +
        "Local times that do not exist in the zone are skipped; repeated local times fire once, at the earlier instant. " +
        "After downtime, a recurring reminder delivers only its latest missed occurrence. Delivery can repeat after a crash."

private val LIST_DESCRIPTION: String = "List the active reminders in the current session."

private val DELETE_DESCRIPTION: String =
    "Delete a reminder in the current session, active or inactive. " +
        "Deletion does not retract a reminder message that is already queued."

private val UPDATE_DESCRIPTION: String =
    "Change a reminder in place, keeping its id. Supply a new title, prompt, or at most one timing parameter; " +
        "omitted fields keep their stored values. To change a relative delay, create a new reminder."

private val SELECTOR_SCHEMA: String =
    """"every_seconds":{"type":"number","description":"Fixed-rate interval in whole seconds, at least 60, aligned to the creation time; changing it with schedule_update re-aligns it to the save time."},"daily":{"type":"object","additionalProperties":false,"description":"Every day at a local time.","properties":{"time":{"type":"string","description":"HH:mm:ss with optional 1-3 fractional digits, for example 23:00:00."},"time_zone":{"type":"string","description":"UTC or IANA Area/Location, for example Asia/Shanghai."}},"required":["time","time_zone"]},"weekly":{"type":"object","additionalProperties":false,"description":"On the given weekdays at a local time.","properties":{"time":{"type":"string","description":"HH:mm:ss with optional 1-3 fractional digits, for example 09:00:00."},"time_zone":{"type":"string","description":"UTC or IANA Area/Location, for example Asia/Shanghai."},"weekdays":{"type":"array","description":"ISO weekdays, Monday 1 through Sunday 7, without repetitions.","items":{"type":"integer"}}},"required":["time","time_zone","weekdays"]},"cron":{"type":"object","additionalProperties":false,"description":"Five-field Vixie cron expression in a time zone.","properties":{"expression":{"type":"string","description":"minute hour day-of-month month day-of-week, for example \"*/15 9-17 * * 1-5\". When both day fields are restricted, a date matches if either one matches."},"time_zone":{"type":"string","description":"UTC or IANA Area/Location, for example Asia/Shanghai."}},"required":["expression","time_zone"]},"at":{"description":"Absolute target: an RFC 3339 date-time with offset, or a local date, time, and IANA time_zone.","oneOf":[{"type":"string"},{"type":"object","additionalProperties":false,"properties":{"date":{"type":"string"},"time":{"type":"string"},"time_zone":{"type":"string"}},"required":["date","time","time_zone"]}]}"""

private fun schema(
    name: String,
    description: String,
    properties: String,
    required: String = "",
): ToolSchema =
    ToolSchema(
        name = name,
        description = description,
        parameters = Json.parseToJsonElement("""{"type":"object","properties":{$properties},"required":[$required]}""").jsonObject,
    )

public val ScheduleCreateToolSchema: ToolSchema = schema(
    SCHEDULE_CREATE_TOOL_NAME,
    CREATE_DESCRIPTION,
    """"prompt":{"type":"string","description":"Reminder content to present when the target becomes due."},"title":{"type":"string","description":"Task name of at most 120 characters, shown on the task card and in task lists."},"after_seconds":{"type":"number","description":"Delay in whole seconds."},$SELECTOR_SCHEMA""",
    """"prompt","title"""",
)

public val ScheduleListToolSchema: ToolSchema = schema(
    SCHEDULE_LIST_TOOL_NAME,
    LIST_DESCRIPTION,
    "",
)

public val ScheduleDeleteToolSchema: ToolSchema = schema(
    SCHEDULE_DELETE_TOOL_NAME,
    DELETE_DESCRIPTION,
    """"id":{"type":"string","description":"Exact schedule id."}""",
    """"id"""",
)

public val ScheduleUpdateToolSchema: ToolSchema = schema(
    SCHEDULE_UPDATE_TOOL_NAME,
    UPDATE_DESCRIPTION,
    """"id":{"type":"string","description":"Schedule id returned by schedule_list."},"title":{"type":"string","description":"New task name of at most 120 characters."},"prompt":{"type":"string","description":"New reminder content."},$SELECTOR_SCHEMA""",
    """"id"""",
)

/** Registers the four DSH-compatible reminder tools. */
public class ToolSchedulePlugin : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(AgentKey),
        InjectSpec.required(SessionKey),
        InjectSpec.required(ScheduleKey),
        InjectSpec.required(ToolsKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val service = context.require(ScheduleKey)
        val tools = context.require(ToolsKey)
        val handler = ScheduleToolHandler(service)
        scope.add(tools.register(ScheduleCreateToolSchema, handler))
        scope.add(tools.register(ScheduleListToolSchema, handler))
        scope.add(tools.register(ScheduleUpdateToolSchema, handler))
        scope.add(tools.register(ScheduleDeleteToolSchema, handler))
    }
}

public val ToolSchedulePluginDefinition: PluginDefinition =
    pluginDefinition(TOOL_SCHEDULE_PLUGIN_NAME, ToolSchedulePlugin())

private class ScheduleToolHandler(
    private val schedule: ScheduleService,
) : ContextualToolHandler {
    override suspend fun execute(
        request: ToolCallRequest,
        execution: ToolExecutionContext,
    ): ToolExecutionResult {
        val depth = execution.session.header.delegationDepth ?: 0L
        if (depth > 0L) return errorResult("subagent_session", SUBAGENT_TOOL_MESSAGE)
        return try {
            when (request.name) {
                SCHEDULE_CREATE_TOOL_NAME -> create(request, execution)
                SCHEDULE_LIST_TOOL_NAME -> list(execution)
                SCHEDULE_UPDATE_TOOL_NAME -> update(request, execution)
                SCHEDULE_DELETE_TOOL_NAME -> delete(request, execution)
                else -> errorResult("internal_error", INTERNAL_ERROR_MESSAGE)
            }
        } catch (error: ScheduleInputException) {
            errorResult(error.code.wire, error.message ?: INTERNAL_ERROR_MESSAGE)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            errorResult("internal_error", INTERNAL_ERROR_MESSAGE)
        }
    }

    private suspend fun create(
        request: ToolCallRequest,
        execution: ToolExecutionContext,
    ): ToolExecutionResult {
        val args = request.jsonObject() ?: return errorResult("internal_error", INTERNAL_ERROR_MESSAGE)
        val allowed = setOf(
            "prompt", "title", "after_seconds", "at", "every_seconds", "daily", "weekly", "cron",
        )
        val selectors = listOf("after_seconds", "at", "every_seconds", "daily", "weekly", "cron")
            .filter { args.containsKey(it) }
        if (args.keys.any { it !in allowed } || selectors.size != 1) {
            return errorResult("invalid_selector", CREATE_SELECTOR_MESSAGE)
        }
        val prompt = args.string("prompt") ?: ""
        val title = args.string("title") ?: ""
        if (prompt.trim().isEmpty()) {
            return errorResult("invalid_prompt", "prompt must be non-empty after trimming.")
        }
        if (title.trim().isEmpty()) {
            return errorResult("invalid_prompt", REQUIRED_TITLE_MESSAGE)
        }
        if (title.trim().length > MAX_TITLE_LENGTH) {
            return errorResult("invalid_prompt", "title must be at most $MAX_TITLE_LENGTH characters.")
        }
        val after = args["after_seconds"]?.let { it as? JsonPrimitive }
        if (after != null) {
            val seconds = after.longOrNull
            if (seconds == null || seconds <= 0) {
                return errorResult("invalid_rule", "after_seconds must be a positive safe integer.")
            }
        }
        val every = args["every_seconds"]?.let { it as? JsonPrimitive }
        if (every != null) {
            val seconds = every.longOrNull
            if (seconds == null) {
                return errorResult("invalid_rule", "every_seconds must be a safe integer.")
            }
            if (seconds < MIN_EVERY_INTERVAL_SECONDS) {
                return errorResult(
                    "frequency_too_high",
                    "every_seconds must be at least $MIN_EVERY_INTERVAL_SECONDS.",
                )
            }
        }
        val selector = parseSelector(selectors.single(), args[selectors.single()]!!)
        val record = schedule.create(
            execution.session.id,
            ScheduleCreateRequest(title = title, prompt = prompt, selectors = listOf(selector)),
        )
        return valueResult(scheduleView(record))
    }

    private suspend fun list(execution: ToolExecutionContext): ToolExecutionResult {
        val records = schedule.list(execution.session.id)
        return valueResult(buildJsonArray { records.forEach { add(scheduleView(it)) } })
    }

    private suspend fun update(
        request: ToolCallRequest,
        execution: ToolExecutionContext,
    ): ToolExecutionResult {
        val args = request.jsonObject() ?: return errorResult("internal_error", INTERNAL_ERROR_MESSAGE)
        val allowed = setOf("id", "title", "prompt", "at", "every_seconds", "daily", "weekly", "cron")
        val selectors = listOf("at", "every_seconds", "daily", "weekly", "cron")
            .filter { args.containsKey(it) }
        if (args.keys.any { it !in allowed } || selectors.size > 1) {
            return errorResult("invalid_selector", UPDATE_SELECTOR_MESSAGE)
        }
        val idText = args.string("id") ?: ""
        if (idText.isEmpty() || idText.trim() != idText) {
            return errorResult(
                "invalid_rule",
                "schedule_update id must be non-empty without surrounding whitespace.",
            )
        }
        val title = args.string("title")
        val prompt = args.string("prompt")
        if (selectors.isEmpty() && title == null && prompt == null) {
            return errorResult("invalid_selector", UPDATE_EMPTY_MESSAGE)
        }
        if (title != null) {
            if (title.trim().isEmpty()) {
                return errorResult("invalid_prompt", REQUIRED_TITLE_MESSAGE)
            }
            if (title.trim().length > MAX_TITLE_LENGTH) {
                return errorResult("invalid_prompt", "title must be at most $MAX_TITLE_LENGTH characters.")
            }
        }
        if (prompt != null && prompt.trim().isEmpty()) {
            return errorResult("invalid_prompt", "prompt must be non-empty after trimming.")
        }
        val every = args["every_seconds"]?.let { it as? JsonPrimitive }
        if (every != null) {
            val seconds = every.longOrNull
            if (seconds == null) {
                return errorResult("invalid_rule", "every_seconds must be a safe integer.")
            }
            if (seconds < MIN_EVERY_INTERVAL_SECONDS) {
                return errorResult(
                    "frequency_too_high",
                    "every_seconds must be at least $MIN_EVERY_INTERVAL_SECONDS.",
                )
            }
        }
        val id = ScheduleId(idText)
        val sessionId = execution.session.id
        val expected = schedule.list(sessionId).firstOrNull { it.id == id }
        if (expected == null) {
            val ended = schedule.catalog(sessionId).any { it.record.id == id }
            return valueResult(
                buildJsonObject {
                    put("id", id.value)
                    put("updated", false)
                    put("code", if (ended) SCHEDULE_ENDED else SCHEDULE_NOT_FOUND)
                },
            )
        }
        val change = if (selectors.isEmpty()) null else {
            parseSelector(selectors.single(), args[selectors.single()]!!)
        }
        return when (
            val outcome = schedule.update(
                sessionId,
                ScheduleUpdateRequest(
                    id = id,
                    expected = expected,
                    title = title,
                    prompt = prompt,
                    change = change,
                ),
            )
        ) {
            is ScheduleUpdateOutcome.Resolved -> valueResult(scheduleView(outcome.record))
            is ScheduleUpdateOutcome.Miss ->
                if (outcome.code == "subagent_session") {
                    errorResult("subagent_session", SUBAGENT_SESSION_MESSAGE)
                } else {
                    valueResult(
                        buildJsonObject {
                            put("id", id.value)
                            put("updated", false)
                            put("code", outcome.code)
                        },
                    )
                }
        }
    }

    private suspend fun delete(
        request: ToolCallRequest,
        execution: ToolExecutionContext,
    ): ToolExecutionResult {
        val args = request.jsonObject() ?: return errorResult("internal_error", INTERNAL_ERROR_MESSAGE)
        val idText = args.string("id") ?: ""
        if (idText.isEmpty() || idText.trim() != idText) {
            return errorResult(
                "invalid_rule",
                "schedule_delete id must be non-empty without surrounding whitespace.",
            )
        }
        val id = ScheduleId(idText)
        val deleted = schedule.delete(execution.session.id, id)
        return valueResult(
            buildJsonObject {
                put("id", id.value)
                put("deleted", deleted)
                if (!deleted) put("code", SCHEDULE_NOT_FOUND)
            },
        )
    }

    private fun scheduleView(record: ScheduleRecord): JsonObject {
        val now = System.currentTimeMillis()
        return buildJsonObject {
            put("id", record.id.value)
            put("kind", record.kind.wire)
            put("title", record.title)
            put("prompt", record.prompt)
            when (record) {
                is ScheduleRecord.After -> put("afterSeconds", record.afterSeconds)
                is ScheduleRecord.At -> Unit
                is ScheduleRecord.Every -> put("everySeconds", record.everySeconds)
                is ScheduleRecord.Daily -> {
                    put("time", record.time.toString())
                    put("timeZone", record.timeZone)
                }
                is ScheduleRecord.Weekly -> {
                    put("time", record.time.toString())
                    put("timeZone", record.timeZone)
                    put("weekdays", buildJsonArray { record.weekdays.forEach { add(JsonPrimitive(it)) } })
                }
                is ScheduleRecord.Cron -> {
                    put("expression", record.expression)
                    put("timeZone", record.timeZone)
                }
            }
            put("scheduledAt", formatInstant(record.scheduledAt))
            put("state", scheduleState(record, now))
            put("deliveryMode", "host")
        }
    }

    private fun valueResult(value: JsonElement): ToolExecutionResult =
        ToolExecutionResult(listOf(TextBlock(value.toString())))

    private fun errorResult(code: String, message: String): ToolExecutionResult =
        valueResult(
            buildJsonObject {
                put("code", code)
                put("message", message)
            },
        )
}

private fun parseSelector(name: String, value: JsonElement): ScheduleSelector = when (name) {
    "after_seconds" -> ScheduleSelector.After(
        (value as? JsonPrimitive)?.longOrNull
            ?: throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "after_seconds must be a positive safe integer.",
            ),
    )

    "every_seconds" -> {
        val seconds = (value as? JsonPrimitive)?.longOrNull
            ?: throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "every_seconds must be a safe integer.",
            )
        if (seconds < MIN_EVERY_INTERVAL_SECONDS) {
            throw ScheduleInputException(
                ScheduleErrorCode.FrequencyTooHigh,
                "every_seconds must be at least $MIN_EVERY_INTERVAL_SECONDS.",
            )
        }
        ScheduleSelector.Every(seconds)
    }

    "daily" -> {
        val objectValue = value as? JsonObject
            ?: throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "daily must contain exactly time and time_zone, with time HH:mm:ss and optional 1-3 fractional digits.",
            )
        requireExactKeys(objectValue, setOf("time", "time_zone"), "daily", "time and time_zone")
        val time = objectValue.string("time")
            ?: throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "daily must contain exactly time and time_zone, with time HH:mm:ss and optional 1-3 fractional digits.",
            )
        val zone = objectValue.string("time_zone")
            ?: throw ScheduleInputException(ScheduleErrorCode.InvalidTimeZone, "time_zone must be a string.")
        ScheduleSelector.Daily(parseClockTime(time, "daily"), zone)
    }

    "weekly" -> {
        val objectValue = value as? JsonObject
            ?: throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "weekly must contain exactly time, time_zone, and weekdays, with time HH:mm:ss and optional 1-3 fractional digits.",
            )
        requireExactKeys(objectValue, setOf("time", "time_zone", "weekdays"), "weekly", "time, time_zone, and weekdays")
        val time = objectValue.string("time")
            ?: throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "weekly must contain exactly time, time_zone, and weekdays, with time HH:mm:ss and optional 1-3 fractional digits.",
            )
        val zone = objectValue.string("time_zone")
            ?: throw ScheduleInputException(ScheduleErrorCode.InvalidTimeZone, "time_zone must be a string.")
        val weekdays = (objectValue["weekdays"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            ?: throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "weekly.weekdays must be a non-empty array of ISO weekday numbers.",
            )
        if (weekdays.size != (objectValue["weekdays"] as JsonArray).size) {
            throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "weekly.weekdays must contain ISO weekday numbers 1-7.",
            )
        }
        ScheduleSelector.Weekly(parseClockTime(time, "weekly"), zone, weekdays)
    }

    "cron" -> {
        val objectValue = value as? JsonObject
            ?: throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "cron must contain exactly expression and time_zone, with a five-field cron expression.",
            )
        if (objectValue.keys != setOf("expression", "time_zone")) {
            throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "cron must contain exactly expression and time_zone, with a five-field cron expression.",
            )
        }
        val expression = objectValue.string("expression")
            ?: throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "cron must contain exactly expression and time_zone, with a five-field cron expression.",
            )
        val zone = objectValue.string("time_zone")
            ?: throw ScheduleInputException(ScheduleErrorCode.InvalidTimeZone, "time_zone must be a string.")
        ScheduleSelector.Cron(expression, zone)
    }

    "at" -> when (value) {
        is JsonPrimitive -> ScheduleSelector.At(ScheduleAtInput.Offset(value.content))
        is JsonObject -> {
            requireExactKeys(value, setOf("date", "time", "time_zone"), "at", "date, time, and time_zone")
            val date = value.string("date")
                ?: throw ScheduleInputException(
                    ScheduleErrorCode.InvalidRule,
                    "at must be an RFC 3339 instant with a Z or numeric offset, or an object with date, time, and time_zone.",
                )
            val time = value.string("time")
                ?: throw ScheduleInputException(
                    ScheduleErrorCode.InvalidRule,
                    "at must be an RFC 3339 instant with a Z or numeric offset, or an object with date, time, and time_zone.",
                )
            val zone = value.string("time_zone")
                ?: throw ScheduleInputException(ScheduleErrorCode.InvalidTimeZone, "time_zone must be a string.")
            ScheduleSelector.At(ScheduleAtInput.Local(date, time, zone))
        }
        else -> throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "at must be an RFC 3339 instant with a Z or numeric offset, or an object with date, time, and time_zone.",
        )
    }

    else -> throw ScheduleInputException(ScheduleErrorCode.InvalidSelector, CREATE_SELECTOR_MESSAGE)
}

private fun requireExactKeys(
    value: JsonObject,
    expected: Set<String>,
    selector: String,
    names: String,
) {
    if (value.keys != expected) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "$selector must contain exactly $names" +
                if (selector == "at") "." else ", with time HH:mm:ss and optional 1-3 fractional digits.",
        )
    }
}

private fun ToolCallRequest.jsonObject(): JsonObject? =
    try {
        Json.parseToJsonElement(arguments).jsonObject
    } catch (_: Throwable) {
        null
    }

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
