package im.hikaru.ruoyi.framework.xss

import im.hikaru.ruoyi.framework.xss.config.XssProperties
import im.hikaru.ruoyi.framework.xss.core.clean.JsoupXssCleaner
import im.hikaru.ruoyi.framework.xss.core.filter.XssFilter
import im.hikaru.ruoyi.framework.xss.core.filter.XssRequestWrapper
import im.hikaru.ruoyi.framework.xss.core.json.XssStringJsonDeserializer
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.util.AntPathMatcher
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule

class XssTest {
    private val cleaner = JsoupXssCleaner()

    @Test
    fun `jsoup cleaner keeps safe rich text and removes executable content`() {
        val cleaned = cleaner.clean(
            """<script>alert('xss')</script><p class="lead" onclick="bad()">Hello <a href="javascript:bad()" target="_blank">link</a></p>""",
        )

        assertFalse(cleaned.contains("script", ignoreCase = true))
        assertFalse(cleaned.contains("onclick", ignoreCase = true))
        assertFalse(cleaned.contains("javascript", ignoreCase = true))
        assertTrue(cleaned.contains("<p class=\"lead\">"))
        assertTrue(cleaned.contains("target=\"_blank\""))
    }

    @Test
    fun `request wrapper cleans parameters attributes headers and query without mutating source`() {
        val dirty = "<script>bad()</script><b>safe</b>"
        val request = MockHttpServletRequest().apply {
            addParameter("value", dirty)
            addHeader("X-Test", dirty)
            setAttribute("value", dirty)
            queryString = "value=$dirty"
        }
        val wrapper = XssRequestWrapper(request, cleaner)

        assertEquals("<b>safe</b>", wrapper.getParameter("value"))
        assertEquals("<b>safe</b>", wrapper.getParameterValues("value")?.single())
        assertEquals("<b>safe</b>", wrapper.parameterMap.getValue("value").single())
        assertEquals("<b>safe</b>", wrapper.getHeader("X-Test"))
        assertEquals("<b>safe</b>", wrapper.getAttribute("value"))
        assertFalse(wrapper.queryString.orEmpty().contains("script"))
        assertEquals(dirty, request.getParameter("value"))
    }

    @Test
    fun `filter honors excluded urls`() {
        val properties = XssProperties().apply {
            excludeUrls = listOf("/admin-api/excluded/**")
        }
        val filter = XssFilter(properties, AntPathMatcher(), cleaner)

        val included = MockHttpServletRequest().apply { requestURI = "/admin-api/test" }
        var includedRequest: HttpServletRequest? = null
        filter.doFilter(included, MockHttpServletResponse()) { request, _ ->
            includedRequest = request as HttpServletRequest
        }
        assertTrue(includedRequest is XssRequestWrapper)

        val excluded = MockHttpServletRequest().apply { requestURI = "/admin-api/excluded/test" }
        var excludedRequest: HttpServletRequest? = null
        filter.doFilter(excluded, MockHttpServletResponse()) { request, _ ->
            excludedRequest = request as HttpServletRequest
        }
        assertSame(excluded, excludedRequest)
    }

    @Test
    fun `json deserializer cleans strings and honors excluded urls`() {
        val properties = XssProperties().apply {
            excludeUrls = listOf("/admin-api/excluded/**")
        }
        val mapper = JsonMapper.builder()
            .addModule(
                SimpleModule().addDeserializer(
                    String::class.java,
                    XssStringJsonDeserializer(properties, AntPathMatcher(), cleaner),
                ),
            )
            .build()
        val json = """{"value":"<script>bad()</script><b>safe</b>"}"""

        val included = withRequest("/admin-api/test") { mapper.readValue(json, Payload::class.java) }
        assertEquals("<b>safe</b>", included.value)

        val excluded = withRequest("/admin-api/excluded/test") { mapper.readValue(json, Payload::class.java) }
        assertEquals("<script>bad()</script><b>safe</b>", excluded.value)
    }

    private fun <T> withRequest(uri: String, block: () -> T): T {
        val request = MockHttpServletRequest().apply { requestURI = uri }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        return try {
            block()
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    class Payload {
        var value: String? = null
    }
}
