package im.hikaru.ruoyi.framework.encrypt

import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiErrorLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.encrypt.config.YudaoApiEncryptAutoConfiguration
import im.hikaru.ruoyi.framework.encrypt.core.annotation.ApiEncrypt
import im.hikaru.ruoyi.framework.encrypt.core.crypto.ApiCryptoFactory
import im.hikaru.ruoyi.framework.jackson.config.YudaoJacksonAutoConfiguration
import im.hikaru.ruoyi.framework.web.config.YudaoWebAutoConfiguration
import im.hikaru.ruoyi.framework.xss.config.YudaoXssAutoConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

private const val ENCRYPT_HEADER = "X-Api-Encrypt"
private const val REQUEST_KEY = "52549111389893486934626385991395"
private const val RESPONSE_KEY = "96103715984234343991809655248883"

@WebMvcTest(
    controllers = [ApiEncryptMvcTest.TestController::class],
    properties = [
        "spring.application.name=encrypt-test",
        "yudao.web.admin-api.controller=**",
        "yudao.web.admin-ui.url=http://localhost",
        "yudao.xss.enable=true",
        "yudao.api-encrypt.enable=true",
        "yudao.api-encrypt.algorithm=AES",
        "yudao.api-encrypt.request-key=52549111389893486934626385991395",
        "yudao.api-encrypt.response-key=96103715984234343991809655248883",
    ],
)
@AutoConfigureMockMvc
@ContextConfiguration(classes = [ApiEncryptMvcTest.TestApplication::class])
@ImportAutoConfiguration(
    YudaoWebAutoConfiguration::class,
    YudaoJacksonAutoConfiguration::class,
    YudaoXssAutoConfiguration::class,
    YudaoApiEncryptAutoConfiguration::class,
)
@Import(
    ApiEncryptMvcTest.SupportConfiguration::class,
    ApiEncryptMvcTest.TestController::class,
)
class ApiEncryptMvcTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val requestEncryptor = ApiCryptoFactory.createEncryptor("AES", REQUEST_KEY)
    private val responseDecryptor = ApiCryptoFactory.createDecryptor("AES", RESPONSE_KEY)

    @Test
    fun `annotated endpoint decrypts request cleans json and encrypts response`() {
        val result = performEncrypted(
            "/admin-api/test/encrypted",
            """{"value":"<script>alert('xss')</script><b>safe</b>"}""",
        )

        assertEquals("true", result.response.getHeader(ENCRYPT_HEADER))
        assertEquals(ENCRYPT_HEADER, result.response.getHeader("Access-Control-Expose-Headers"))
        val decrypted = responseDecryptor.decrypt(result.response.contentAsString).toString(Charsets.UTF_8)
        assertTrue(decrypted.contains("<b>safe</b>"))
        assertFalse(decrypted.contains("alert('xss')"))
    }

    @Test
    fun `request and response switches are independent`() {
        val requestOnly = performEncrypted(
            "/admin-api/test/request-only",
            """{"value":"request-value"}""",
        )
        assertNull(requestOnly.response.getHeader(ENCRYPT_HEADER))
        assertTrue(requestOnly.response.contentAsString.contains("request-value"))

        val responseOnly = mockMvc.perform(
            post("/admin-api/test/response-only")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value":"response-value"}"""),
        ).andExpect(status().isOk).andReturn()
        assertEquals("true", responseOnly.response.getHeader(ENCRYPT_HEADER))
        val decrypted = responseDecryptor.decrypt(responseOnly.response.contentAsString).toString(Charsets.UTF_8)
        assertTrue(decrypted.contains("response-value"))
    }

    @Test
    fun `unannotated endpoint bypasses encryption`() {
        val result = mockMvc.perform(
            post("/admin-api/test/plain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value":"plain-value"}"""),
        ).andExpect(status().isOk).andReturn()

        assertNull(result.response.getHeader(ENCRYPT_HEADER))
        assertTrue(result.response.contentAsString.contains("plain-value"))
    }

    @Test
    fun `required encrypted request rejects a missing header`() {
        val result = mockMvc.perform(
            post("/admin-api/test/encrypted")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value":"plain-value"}"""),
        ).andExpect(status().isOk).andReturn()

        assertNull(result.response.getHeader(ENCRYPT_HEADER))
        assertTrue(result.response.contentAsString.contains("请求未包含加密标头"))
    }

    private fun performEncrypted(path: String, body: String) = mockMvc.perform(
        post(path)
            .header(ENCRYPT_HEADER, "true")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestEncryptor.encrypt(body.toByteArray())),
    ).andExpect(status().isOk).andReturn()

    @RestController
    class TestController {
        @PostMapping("/test/encrypted")
        @ApiEncrypt
        fun encrypted(@RequestBody body: Map<String, String>): CommonResult<Map<String, String>> = echo(body)

        @PostMapping("/test/request-only")
        @ApiEncrypt(response = false)
        fun requestOnly(@RequestBody body: Map<String, String>): CommonResult<Map<String, String>> = echo(body)

        @PostMapping("/test/response-only")
        @ApiEncrypt(request = false)
        fun responseOnly(@RequestBody body: Map<String, String>): CommonResult<Map<String, String>> = echo(body)

        @PostMapping("/test/plain")
        fun plain(@RequestBody body: Map<String, String>): CommonResult<Map<String, String>> = echo(body)

        private fun echo(body: Map<String, String>): CommonResult<Map<String, String>> =
            CommonResult.success(mapOf("echo" to body.getValue("value")))
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SupportConfiguration {
        @Bean
        fun errorLogApi(): ApiErrorLogCommonApi = object : ApiErrorLogCommonApi {
            override fun createApiErrorLog(createDTO: ApiErrorLogCreateReqDTO) = Unit
        }
    }

    @SpringBootConfiguration
    class TestApplication

}
