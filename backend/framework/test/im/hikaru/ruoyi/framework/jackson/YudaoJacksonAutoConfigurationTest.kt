package im.hikaru.ruoyi.framework.jackson

import im.hikaru.ruoyi.framework.jackson.config.YudaoJacksonAutoConfiguration
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.ZoneId
import java.time.LocalDateTime

class YudaoJacksonAutoConfigurationTest {
    @Test
    fun `customizer installs long and local date time support`() {
        val builder = JsonMapper.builder()
        YudaoJacksonAutoConfiguration().timestampSupportCustomizer().customize(builder)
        val mapper = builder.build()

        assertEquals("\"9007199254740992\"", mapper.writeValueAsString(9_007_199_254_740_992L))
        val dateTime = LocalDateTime.of(2026, 7, 18, 0, 30, 0)
        assertEquals(
            dateTime,
            mapper.readValue(mapper.writeValueAsString(dateTime), LocalDateTime::class.java),
        )

        val epochMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val kotlinDateTime = dateTime.toKotlinLocalDateTime()
        assertEquals(
            kotlinDateTime,
            mapper.readValue(epochMillis.toString(), KotlinLocalDateTime::class.java),
        )
        assertEquals(
            kotlinDateTime,
            mapper.readValue("\"$epochMillis\"", KotlinLocalDateTime::class.java),
        )
        assertEquals(
            kotlinDateTime,
            mapper.readValue("\"2026-07-18T00:30:00\"", KotlinLocalDateTime::class.java),
        )
    }
}
