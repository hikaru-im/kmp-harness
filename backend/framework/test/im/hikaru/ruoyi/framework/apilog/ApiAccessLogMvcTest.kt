package im.hikaru.ruoyi.framework.apilog

import im.hikaru.ruoyi.framework.apilog.config.YudaoApiLogAutoConfiguration
import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiAccessLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiErrorLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.jackson.config.YudaoJacksonAutoConfiguration
import im.hikaru.ruoyi.framework.web.config.YudaoWebAutoConfiguration
import im.hikaru.ruoyi.framework.xss.config.YudaoXssAutoConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CopyOnWriteArrayList

@WebMvcTest(
    controllers = [ApiAccessLogMvcTest.TestController::class],
    properties = [
        "spring.application.name=web-test",
        "yudao.web.admin-api.controller=**",
        "yudao.web.admin-ui.url=http://localhost",
        "yudao.xss.enable=true",
    ],
)
@AutoConfigureMockMvc
@ContextConfiguration(classes = [ApiAccessLogMvcTest.TestApplication::class])
@ImportAutoConfiguration(
    YudaoWebAutoConfiguration::class,
    YudaoApiLogAutoConfiguration::class,
    YudaoJacksonAutoConfiguration::class,
    YudaoXssAutoConfiguration::class,
)
@Import(
    ApiAccessLogMvcTest.SupportConfiguration::class,
    ApiAccessLogMvcTest.TestController::class,
)
class ApiAccessLogMvcTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var accessLogApi: RecordingApiAccessLogApi

    @BeforeEach
    fun clearLogs() {
        accessLogApi.logs.clear()
    }

    @Test
    fun `mvc request traverses the complete access log chain`() {
        mockMvc.perform(
            post("/admin-api/test/log")
                .queryParam("token", "query-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"<script>alert('xss')</script><b>request-name</b>","password":"request-password"}""",
                ),
        ).andExpect(status().isOk)

        val log = accessLogApi.logs.single()
        assertEquals("web-test", log.applicationName)
        assertEquals("/admin-api/test/log", log.requestUrl)
        assertEquals("POST", log.requestMethod)
        assertEquals("MVC test", log.operateModule)
        assertEquals("Log request", log.operateName)
        assertTrue(log.requestParams.orEmpty().contains("request-name"))
        assertFalse(log.requestParams.orEmpty().contains("request-password"))
        assertFalse(log.requestParams.orEmpty().contains("query-token"))
        assertTrue(log.responseBody.orEmpty().contains("response-value"))
        assertTrue(log.responseBody.orEmpty().contains("<b>request-name</b>"))
        assertFalse(log.responseBody.orEmpty().contains("alert('xss')"))
        assertFalse(log.responseBody.orEmpty().contains("response-password"))
    }

    @RestController
    @Tag(name = "MVC test")
    class TestController {
        @PostMapping("/test/log")
        @Operation(summary = "Log request")
        @ApiAccessLog(responseEnable = true)
        fun log(@RequestBody body: Map<String, String>): CommonResult<Map<String, String>> =
            CommonResult.success(
                linkedMapOf(
                    "response" to "response-value",
                    "password" to "response-password",
                    "request" to body.getValue("name"),
                ),
            )
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SupportConfiguration {
        @Bean
        fun accessLogApi(): RecordingApiAccessLogApi = RecordingApiAccessLogApi()

        @Bean
        fun errorLogApi(): ApiErrorLogCommonApi = object : ApiErrorLogCommonApi {
            override fun createApiErrorLog(createDTO: ApiErrorLogCreateReqDTO) = Unit
        }
    }

    @SpringBootConfiguration
    class TestApplication

    class RecordingApiAccessLogApi : ApiAccessLogCommonApi {
        val logs = CopyOnWriteArrayList<ApiAccessLogCreateReqDTO>()
        override fun createApiAccessLog(createDTO: ApiAccessLogCreateReqDTO) {
            logs += createDTO
        }
    }
}
