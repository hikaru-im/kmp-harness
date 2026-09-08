package im.hikaru.ruoyi.framework.common.util.json.databind

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.jsontype.TypeDeserializer
import java.time.Instant
import java.time.ZoneId

/** Converts epoch-millisecond JSON values to [kotlinx.datetime.LocalDateTime]. */
class TimestampKotlinLocalDateTimeDeserializer : ValueDeserializer<LocalDateTime>() {
    @Throws(tools.jackson.core.JacksonException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): LocalDateTime = when (p.currentToken()) {
        JsonToken.VALUE_NUMBER_INT -> fromEpochMillis(p.valueAsLong)
        JsonToken.VALUE_STRING -> parseString(p.valueAsString)
        JsonToken.START_OBJECT -> fromLegacyObject(p.readValueAsTree())
        JsonToken.PROPERTY_NAME -> fromLegacyProperties(p)
        else -> throw IllegalArgumentException("Unsupported kotlinx.datetime.LocalDateTime token: ${p.currentToken()}")
    }

    override fun deserializeWithType(
        p: JsonParser,
        ctxt: DeserializationContext?,
        typeDeserializer: TypeDeserializer,
    ): Any = typeDeserializer.deserializeTypedFromAny(p, ctxt)

    private fun fromEpochMillis(value: Long): LocalDateTime =
        java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault()).toKotlinLocalDateTime()

    private fun parseString(value: String): LocalDateTime {
        val trimmed = value.trim()
        return trimmed.toLongOrNull()?.let(::fromEpochMillis) ?: LocalDateTime.parse(trimmed)
    }

    private fun fromLegacyObject(node: JsonNode): LocalDateTime {
        node.get("value\$kotlinx_datetime")?.asString()?.takeIf(String::isNotBlank)?.let {
            return LocalDateTime.parse(it)
        }
        return LocalDateTime(
            requireNotNull(node.get("year")) { "Legacy LocalDateTime is missing year" }.asInt(),
            (node.get("monthNumber") ?: node.get("month")).asInt(),
            (node.get("dayOfMonth") ?: node.get("day")).asInt(),
            node.get("hour")?.asInt() ?: 0,
            node.get("minute")?.asInt() ?: 0,
            node.get("second")?.asInt() ?: 0,
            node.get("nanosecond")?.asInt() ?: 0,
        )
    }

    private fun fromLegacyProperties(p: JsonParser): LocalDateTime {
        var value: String? = null
        var year: Int? = null
        var month: Int? = null
        var day: Int? = null
        var hour = 0
        var minute = 0
        var second = 0
        var nanosecond = 0
        var token = p.currentToken()
        while (token != null && token != JsonToken.END_OBJECT) {
            if (token != JsonToken.PROPERTY_NAME) {
                token = p.nextToken()
                continue
            }
            val name = p.currentName()
            token = p.nextToken()
            when (name) {
                "value\$kotlinx_datetime" -> value = p.valueAsString
                "year" -> year = p.valueAsInt
                "monthNumber" -> month = p.valueAsInt
                "dayOfMonth" -> day = p.valueAsInt
                "hour" -> hour = p.valueAsInt
                "minute" -> minute = p.valueAsInt
                "second" -> second = p.valueAsInt
                "nanosecond" -> nanosecond = p.valueAsInt
                else -> if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) p.skipChildren()
            }
            token = p.nextToken()
        }
        value?.takeIf(String::isNotBlank)?.let { return LocalDateTime.parse(it) }
        return LocalDateTime(requireNotNull(year), requireNotNull(month), requireNotNull(day), hour, minute, second, nanosecond)
    }

    companion object {
        @JvmField
        val INSTANCE = TimestampKotlinLocalDateTimeDeserializer()
    }
}
