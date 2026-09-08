package im.hikaru.ruoyi.framework.jackson.config

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.common.util.json.databind.NumberSerializer
import im.hikaru.ruoyi.framework.common.util.json.databind.TimestampKotlinLocalDateTimeDeserializer
import im.hikaru.ruoyi.framework.common.util.json.databind.TimestampKotlinLocalDateTimeSerializer
import im.hikaru.ruoyi.framework.common.util.json.databind.TimestampLocalDateTimeDeserializer
import im.hikaru.ruoyi.framework.common.util.json.databind.TimestampLocalDateTimeSerializer
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer
import tools.jackson.databind.module.SimpleModule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@AutoConfiguration(after = [JacksonAutoConfiguration::class])
class YudaoJacksonAutoConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun timestampSupportCustomizer(): JsonMapperBuilderCustomizer = JsonMapperBuilderCustomizer { builder ->
        builder.addModule(timestampSupportModule())
    }

    @Bean
    fun jsonUtils(objectMapper: ObjectMapper): JsonUtils {
        JsonUtils.init(objectMapper)
        return JsonUtils
    }

    companion object {
        @JvmStatic
        fun timestampSupportModule(): SimpleModule = SimpleModule("TimestampSupportModule").apply {
            addSerializer(Long::class.javaObjectType, NumberSerializer.INSTANCE)
            addSerializer(Long::class.javaPrimitiveType!!, NumberSerializer.INSTANCE)
            addSerializer(LocalDate::class.java, LocalDateSerializer.INSTANCE)
            addDeserializer(LocalDate::class.java, LocalDateDeserializer.INSTANCE)
            addSerializer(LocalTime::class.java, LocalTimeSerializer.INSTANCE)
            addDeserializer(LocalTime::class.java, LocalTimeDeserializer.INSTANCE)
            addSerializer(LocalDateTime::class.java, TimestampLocalDateTimeSerializer.INSTANCE)
            addDeserializer(LocalDateTime::class.java, TimestampLocalDateTimeDeserializer.INSTANCE)
            addSerializer(KotlinLocalDateTime::class.java, TimestampKotlinLocalDateTimeSerializer.INSTANCE)
            addDeserializer(KotlinLocalDateTime::class.java, TimestampKotlinLocalDateTimeDeserializer.INSTANCE)
        }
    }
}
