package im.hikaru.ruoyi.framework.common.util.json.databind

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 基于时间戳的 LocalDateTime 反序列化器
 *
 * @author 老五
 */
class TimestampLocalDateTimeDeserializer : ValueDeserializer<LocalDateTime>() {
    @Throws(tools.jackson.core.JacksonException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): LocalDateTime {
        // 将 Long 时间戳，转换为 LocalDateTime 对象
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(p.valueAsLong), ZoneId.systemDefault())
    }

    companion object {
        @JvmField
        val INSTANCE = TimestampLocalDateTimeDeserializer()
    }
}
