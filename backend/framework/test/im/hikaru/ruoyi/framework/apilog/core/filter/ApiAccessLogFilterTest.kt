package im.hikaru.ruoyi.framework.apilog.core.filter

import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum
import im.hikaru.ruoyi.framework.apilog.core.interceptor.ApiAccessLogInterceptor
import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiAccessLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.web.config.WebProperties
import im.hikaru.ruoyi.framework.web.core.filter.CacheRequestBodyWrapper
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod

class ApiAccessLogFilterTest {
    @Test
    fun `api log captures handler result and sanitized repeatable request data`() {
        val api = RecordingApiAccessLogApi()
        val filter = ApiAccessLogFilter(WebProperties(), "test-application", api)
        val request = MockHttpServletRequest().apply {
            method = "POST"
            requestURI = "/admin-api/test"
            servletPath = "/admin-api/test"
            contentType = MediaType.APPLICATION_JSON_VALUE
            setContent("""{"name":"body-name","password":"body-password","secret":"body-secret"}""".toByteArray())
            addParameter("name", "query-name")
            addParameter("token", "query-token")
            addHeader("User-Agent", "JUnit")
            remoteAddr = "127.0.0.1"
            setAttribute(
                ApiAccessLogInterceptor.ATTRIBUTE_HANDLER_METHOD,
                HandlerMethod(TestController(), TestController::class.java.getDeclaredMethod("logged")),
            )
        }
        WebFrameworkUtils.setLoginUserId(request, 42L)
        WebFrameworkUtils.setLoginUserType(request, 2)
        WebFrameworkUtils.setCommonResult(
            request,
            CommonResult.success(
                linkedMapOf(
                    "visible" to "response-value",
                    "password" to "response-password",
                    "secret" to "response-secret",
                ),
            ),
        )
        var controllerBody: String? = null

        filter.doFilter(
            CacheRequestBodyWrapper(request),
            MockHttpServletResponse(),
            object : FilterChain {
                override fun doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse) {
                    controllerBody = (servletRequest as jakarta.servlet.http.HttpServletRequest).reader.readText()
                }
            },
        )

        assertEquals(
            """{"name":"body-name","password":"body-password","secret":"body-secret"}""",
            controllerBody,
        )
        assertNotNull(api.logs.single())
        val log = api.logs.single()
        assertEquals(42L, log.userId)
        assertEquals(2, log.userType)
        assertEquals("test-application", log.applicationName)
        assertEquals("/admin-api/test", log.requestUrl)
        assertEquals("POST", log.requestMethod)
        assertEquals("Test module", log.operateModule)
        assertEquals("Create test", log.operateName)
        assertEquals(OperateTypeEnum.EXPORT.type, log.operateType)
        assertTrue(log.requestParams.orEmpty().contains("query-name"))
        assertTrue(log.requestParams.orEmpty().contains("body-name"))
        assertFalse(log.requestParams.orEmpty().contains("query-token"))
        assertFalse(log.requestParams.orEmpty().contains("body-password"))
        assertFalse(log.requestParams.orEmpty().contains("body-secret"))
        assertTrue(log.responseBody.orEmpty().contains("response-value"))
        assertFalse(log.responseBody.orEmpty().contains("response-password"))
        assertFalse(log.responseBody.orEmpty().contains("response-secret"))
        assertTrue(requireNotNull(log.duration) >= 0)
    }

    @Test
    fun `disabled access log annotation suppresses persistence`() {
        val api = RecordingApiAccessLogApi()
        val filter = ApiAccessLogFilter(WebProperties(), "test-application", api)
        val request = MockHttpServletRequest().apply {
            method = "GET"
            requestURI = "/admin-api/test/disabled"
            servletPath = "/admin-api/test/disabled"
            setAttribute(
                ApiAccessLogInterceptor.ATTRIBUTE_HANDLER_METHOD,
                HandlerMethod(TestController(), TestController::class.java.getDeclaredMethod("disabled")),
            )
        }

        filter.doFilter(request, MockHttpServletResponse()) { _, _ -> }

        assertTrue(api.logs.isEmpty())
    }

    private class RecordingApiAccessLogApi : ApiAccessLogCommonApi {
        val logs = mutableListOf<ApiAccessLogCreateReqDTO>()
        override fun createApiAccessLog(createDTO: ApiAccessLogCreateReqDTO) {
            logs += createDTO
        }
    }

    @Tag(name = "Test module")
    private class TestController {
        @Operation(summary = "Create test")
        @ApiAccessLog(
            responseEnable = true,
            sanitizeKeys = ["secret"],
            operateType = [OperateTypeEnum.EXPORT],
        )
        fun logged(): CommonResult<Boolean> = CommonResult.success(true)

        @ApiAccessLog(enable = false)
        fun disabled(): CommonResult<Boolean> = CommonResult.success(true)
    }
}
