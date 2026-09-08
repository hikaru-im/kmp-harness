package im.hikaru.ruoyi.framework.desensitize

import im.hikaru.ruoyi.framework.common.util.spring.SpringExpressionUtils
import im.hikaru.ruoyi.framework.desensitize.core.regex.annotation.EmailDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.regex.annotation.RegexDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.BankCardDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.CarLicenseDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.ChineseNameDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.FixedPhoneDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.IdCardDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.MobileDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.PasswordDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.SliderDesensitize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.function.Supplier

class StringDesensitizeSerializerTest {
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `built in annotations mask their standard data shapes`() {
        val json = mapper.readTree(mapper.writeValueAsString(StandardPayload()))

        assertEquals("998800********31", json.text("bankCard"))
        assertEquals("粤A6***6", json.text("carLicense"))
        assertEquals("刘**", json.text("chineseName"))
        assertEquals("0108*****22", json.text("fixedPhone"))
        assertEquals("530321**********11", json.text("idCard"))
        assertEquals("132****5917", json.text("mobile"))
        assertEquals("******", json.text("password"))
        assertEquals("e****@gmail.com", json.text("email"))
    }

    @Test
    fun `generic annotations honor custom options disable expressions and edge cases`() {
        val json = mapper.readTree(mapper.writeValueAsString(CustomPayload()))

        assertEquals("1***56", json.text("slider"))
        assertEquals("******456789", json.text("regex"))
        assertEquals("123456", json.text("disabled"))
        assertEquals("***", json.text("shortValue"))
        assertEquals("998800********31", json.text("bankCardDisableIsCompatible"))
        assertEquals(true, json.get("blank").isNull)
    }

    @Test
    fun `spring expressions support variables and bean references`() {
        assertEquals(true, SpringExpressionUtils.parseExpression("#enabled", mapOf("enabled" to true)))

        GenericApplicationContext().use { context ->
            context.registerBean(
                "featureSwitch",
                FeatureSwitch::class.java,
                Supplier { FeatureSwitch(true) },
            )
            context.refresh()
            SpringExpressionUtils().setApplicationContext(context)
            assertEquals(true, SpringExpressionUtils.parseExpression("@featureSwitch.enabled"))
        }
    }

    private fun JsonNode.text(name: String): String = get(name).stringValue()

    class StandardPayload {
        @field:BankCardDesensitize
        val bankCard = "9988002866797031"

        @field:CarLicenseDesensitize
        val carLicense = "粤A66666"

        @field:ChineseNameDesensitize
        val chineseName = "刘子豪"

        @field:FixedPhoneDesensitize
        val fixedPhone = "01086551122"

        @field:IdCardDesensitize
        val idCard = "530321199204074611"

        @field:MobileDesensitize
        val mobile = "13248765917"

        @field:PasswordDesensitize
        val password = "123456"

        @field:EmailDesensitize
        val email = "example@gmail.com"
    }

    class CustomPayload {
        @field:SliderDesensitize(prefixKeep = 1, suffixKeep = 2)
        val slider = "123456"

        @field:RegexDesensitize(regex = "123", replacer = "******")
        val regex = "123456789"

        @field:SliderDesensitize(prefixKeep = 1, suffixKeep = 2, disable = "true")
        val disabled = "123456"

        @field:FixedPhoneDesensitize
        val shortValue = "123"

        @field:BankCardDesensitize(disable = "true")
        val bankCardDisableIsCompatible = "9988002866797031"

        @field:MobileDesensitize
        val blank = "   "
    }

    data class FeatureSwitch(val enabled: Boolean)
}
