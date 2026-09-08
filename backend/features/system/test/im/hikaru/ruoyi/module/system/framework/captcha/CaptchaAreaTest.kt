package im.hikaru.ruoyi.module.system.framework.captcha

import im.hikaru.ruoyi.module.system.controller.admin.captcha.CaptchaController
import im.hikaru.ruoyi.module.system.controller.admin.ip.AreaController
import im.hikaru.ruoyi.module.system.controller.app.ip.AppAreaController
import im.hikaru.ruoyi.module.system.framework.captcha.core.PictureWordCaptchaServiceImpl
import com.anji.captcha.model.vo.CaptchaVO
import com.anji.captcha.service.impl.CaptchaServiceFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.util.Properties

class CaptchaAreaTest {
    @Test
    fun `captcha spi exposes redis cache and picture word service`() {
        assertTrue(CaptchaServiceFactory.cacheService.containsKey("redis"))
        assertTrue(CaptchaServiceFactory.instances.containsKey("pictureWord"))

        val service = CaptchaServiceFactory.instances.getValue("pictureWord")
        service.init(Properties().apply {
            setProperty("captcha.init.original", "true")
            setProperty("captcha.cacheType", "local")
            setProperty("captcha.aes.status", "true")
        })
        val response = service.get(CaptchaVO())
        assertTrue(response.isSuccess)
        val data = response.repData as CaptchaVO
        assertTrue(data.token.isNotBlank())
        assertTrue(data.originalImageBase64.isNotBlank())
        assertNotNull(data.secretKey)
    }

    @Test
    fun `picture word codes use the unambiguous character set`() {
        val code = PictureWordCaptchaServiceImpl.generateRandomText(128)
        assertEquals(128, code.length)
        assertTrue(code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
    }

    @Test
    fun `area controllers expose the china tree for admin and app`() {
        val adminTree = AreaController().getAreaTree().data
        val appTree = AppAreaController().getAreaTree().data
        assertTrue(!adminTree.isNullOrEmpty())
        assertTrue(!appTree.isNullOrEmpty())
        assertEquals(adminTree!!.first().id, appTree!!.first().id)
    }

    @Test
    fun `captcha remote id combines forwarded ip and user agent`() {
        val request = MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.1"
            addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
            addHeader("user-agent", "JUnit")
        }
        assertEquals("203.0.113.10JUnit", CaptchaController.getRemoteId(request))
    }
}
