package im.hikaru.ruoyi.framework.common.util.json.databind

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.annotation.JacksonStdImpl
import tools.jackson.databind.ser.std.StdScalarSerializer

/**
 * Long 序列化规则
 *
 * 会将超长 long 值转换为 string，解决前端 JavaScript 最大安全整数是 2^53-1 的问题
 *
 * @author 星语
 */
@JacksonStdImpl
class NumberSerializer private constructor() : StdScalarSerializer<Long>(Long::class.javaObjectType) {
    @Throws(tools.jackson.core.JacksonException::class)
    override fun serialize(value: Long, gen: JsonGenerator, serializers: SerializationContext) {
        // 超出范围 序列化为字符串
        if (value > MIN_SAFE_INTEGER && value < MAX_SAFE_INTEGER) {
            gen.writeNumber(value)
        } else {
            gen.writeString(value.toString())
        }
    }

    companion object {
        private const val MAX_SAFE_INTEGER = 9007199254740991L
        private const val MIN_SAFE_INTEGER = -9007199254740991L

        @JvmField
        val INSTANCE = NumberSerializer()
    }
}
