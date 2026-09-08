package im.hikaru.ruoyi.framework.common.util.json.databind

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdScalarSerializer
import java.lang.reflect.Field
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于时间戳的 LocalDateTime 序列化器
 *
 * 迁移说明：原实现用 Hutool ReflectUtil.getFields，现改为 JDK 反射递归收集字段。
 *
 * @author 老五
 */
class TimestampLocalDateTimeSerializer private constructor() : StdScalarSerializer<LocalDateTime>(LocalDateTime::class.java) {
    @Throws(JacksonException::class)
    override fun serialize(value: LocalDateTime, gen: JsonGenerator, serializers: SerializationContext) {
        // 情况一：有 JsonFormat 自定义注解，则使用它。https://github.com/YunaiV/ruoyi-vue-pro/pull/1019
        val fieldName = gen.streamWriteContext().currentName()
        if (fieldName != null) {
            val currentValue = gen.currentValue()
            if (currentValue != null) {
                val clazz = currentValue.javaClass
                val fieldMap = FIELD_CACHE.computeIfAbsent(clazz) { buildFieldMap(it) }
                val field = fieldMap[fieldName]
                // 进一步修复：https://gitee.com/zhijiantianya/ruoyi-vue-pro/pulls/1480
                if (field != null && field.isAnnotationPresent(JsonFormat::class.java)) {
                    val jsonFormat = field.getAnnotation(JsonFormat::class.java)
                    try {
                        val formatter = DateTimeFormatter.ofPattern(jsonFormat.pattern)
                        gen.writeString(formatter.format(value))
                        return
                    } catch (ex: Exception) {
                        log.warn(
                            "[serialize][({}#{}) 使用 JsonFormat pattern 失败，尝试使用默认的 Long 时间戳]",
                            clazz.name,
                            fieldName,
                            ex,
                        )
                    }
                }
            }
        }

        // 情况二：默认将 LocalDateTime 对象，转换为 Long 时间戳
        gen.writeNumber(value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    /**
     * 构建字段映射（缓存）。JDK 反射递归收集本类及父类所有字段。
     *
     * @param clazz 类
     * @return 字段映射
     */
    private fun buildFieldMap(clazz: Class<*>): Map<String, Field> {
        val fieldMap = HashMap<String, Field>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                field.isAccessible = true
                var fieldName = field.name
                val jsonProperty = field.getAnnotation(JsonProperty::class.java)
                if (jsonProperty != null) {
                    val value = jsonProperty.value
                    if (value.isNotEmpty() && "\u0000" != value) {
                        fieldName = value
                    }
                }
                fieldMap.putIfAbsent(fieldName, field)
            }
            current = current.superclass
        }
        return fieldMap
    }

    companion object {
        private val log = LoggerFactory.getLogger(TimestampLocalDateTimeSerializer::class.java)
        private val FIELD_CACHE: MutableMap<Class<*>, Map<String, Field>> = ConcurrentHashMap()

        @JvmField
        val INSTANCE = TimestampLocalDateTimeSerializer()
    }
}
