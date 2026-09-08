package im.hikaru.harness.session

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal val SessionJson =
    Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = false
        classDiscriminator = "type"
    }

internal fun JsonElement.detachedJson(): JsonElement =
    when (this) {
        is JsonObject ->
            JsonObject(
                entries.associateTo(linkedMapOf()) { (key, value) ->
                    key to value.detachedJson()
                }
            )

        is JsonArray -> JsonArray(map(JsonElement::detachedJson))
        else -> this
    }

internal fun validateLosslessJson(element: JsonElement) {
    when (element) {
        is JsonObject -> element.values.forEach(::validateLosslessJson)
        is JsonArray -> element.forEach(::validateLosslessJson)
        JsonNull -> Unit
        is JsonPrimitive -> {
            if (!element.isString && element.booleanOrNull == null) {
                val number = element.content.toDoubleOrNull()
                if (number == null || !number.isFinite()) {
                    invalidEvent("Session event contains a non-finite JSON number")
                }
            }
        }
    }
}

internal fun <T : Any> encodeSessionEvent(
    key: SessionEventKey<T>,
    value: T,
): JsonElement {
    val encoded =
        try {
            SessionJson.encodeToJsonElement(key.serializer, value)
        } catch (error: SessionException) {
            throw error
        } catch (error: Throwable) {
            invalidEvent(
                message = "Session event '${key.name}' could not be serialized",
                cause = error,
            )
        }

    validateLosslessJson(encoded)
    val normalized = normalizeCoreEvent(key.name, encoded)
    validateLosslessJson(normalized)
    return normalized.detachedJson()
}

internal fun normalizeCoreEvent(
    type: String,
    data: JsonElement,
): JsonElement =
    try {
        when (type) {
            SessionEventNames.TURN_START -> normalize(TurnStartEvent.serializer(), data)
            SessionEventNames.TURN_END -> normalize(TurnEndEvent.serializer(), data)
            SessionEventNames.STEP_START -> normalize(StepStartEvent.serializer(), data)
            SessionEventNames.STEP_END -> normalize(StepEndEvent.serializer(), data)
            SessionEventNames.USER_MESSAGE -> normalize(UserMessageEvent.serializer(), data)
            SessionEventNames.ASSISTANT_CHUNK -> normalize(AssistantChunkEvent.serializer(), data)
            SessionEventNames.ASSISTANT_MESSAGE -> normalize(AssistantMessageEvent.serializer(), data)
            SessionEventNames.REQUEST_HEADER -> {
                val event = decode(RequestHeaderEvent.serializer(), data)
                normalize(
                    RequestHeaderEvent.serializer(),
                    event.copy(header = canonicalHeader(event.header)),
                )
            }
            SessionEventNames.REQUEST_CONTEXT -> normalize(RequestContextEvent.serializer(), data)
            else -> data.detachedJson()
        }
    } catch (error: SessionException) {
        throw error
    } catch (error: Throwable) {
        invalidEvent(
            message = "Session event '$type' does not match its required shape",
            cause = error,
        )
    }

private fun <T : Any> normalize(
    serializer: KSerializer<T>,
    data: JsonElement,
): JsonElement =
    normalize(serializer, decode(serializer, data))

private fun <T : Any> normalize(
    serializer: KSerializer<T>,
    value: T,
): JsonElement =
    SessionJson.encodeToJsonElement(serializer, value).detachedJson()

internal fun <T : Any> decode(
    serializer: KSerializer<T>,
    data: JsonElement,
): T =
    SessionJson.decodeFromJsonElement(serializer, data.detachedJson())

internal fun SessionEventEnvelope.detachedCopy(): SessionEventEnvelope =
    copy(
        data = data.detachedJson(),
        sourceEventSeqs = sourceEventSeqs?.toList(),
    )
