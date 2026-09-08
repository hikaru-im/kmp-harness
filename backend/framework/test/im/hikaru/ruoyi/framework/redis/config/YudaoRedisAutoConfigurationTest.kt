package im.hikaru.ruoyi.framework.redis.config

import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class YudaoRedisAutoConfigurationTest {

    @Test
    fun `redis serializer round trips kotlinx datetime values with type information`() {
        val serializer = YudaoRedisAutoConfiguration.buildRedisSerializer()
        val source = CacheValue().apply {
            name = "admin"
            createdAt = LocalDateTime(2026, 7, 19, 10, 30)
        }

        val restored = assertInstanceOf(CacheValue::class.java, serializer.deserialize(serializer.serialize(source)))

        assertEquals(source.name, restored.name)
        assertEquals(source.createdAt, restored.createdAt)
    }

    @Test
    fun `redis serializer reads the legacy object shaped kotlinx datetime cache`() {
        val serializer = YudaoRedisAutoConfiguration.buildRedisSerializer()
        val legacyJson = """{
            "@class":"${CacheValue::class.java.name}",
            "name":"admin",
            "createdAt":{
                "@class":"kotlinx.datetime.LocalDateTime",
                "value${'$'}kotlinx_datetime":"2026-07-19T10:30:00"
            }
        }""".trimIndent()

        val restored = assertInstanceOf(CacheValue::class.java, serializer.deserialize(legacyJson.encodeToByteArray()))

        assertEquals("admin", restored.name)
        assertEquals(LocalDateTime(2026, 7, 19, 10, 30), restored.createdAt)
    }

    class CacheValue {
        var name: String? = null
        var createdAt: LocalDateTime? = null
    }
}
