package im.hikaru.ruoyi.framework.common.util.json.databind

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdScalarSerializer
import java.time.ZoneId

/** Converts [kotlinx.datetime.LocalDateTime] values to epoch-millisecond JSON values. */
class TimestampKotlinLocalDateTimeSerializer private constructor() :
    StdScalarSerializer<LocalDateTime>(LocalDateTime::class.java) {

    @Throws(JacksonException::class)
    override fun serialize(value: LocalDateTime, gen: JsonGenerator, serializers: SerializationContext) {
        gen.writeNumber(value.toJavaLocalDateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    companion object {
        @JvmField
        val INSTANCE = TimestampKotlinLocalDateTimeSerializer()
    }
}
