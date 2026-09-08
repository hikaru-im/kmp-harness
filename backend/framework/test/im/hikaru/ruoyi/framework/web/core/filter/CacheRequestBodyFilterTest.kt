package im.hikaru.ruoyi.framework.web.core.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CacheRequestBodyFilterTest {
    @Test
    fun `json request body remains readable across the filter chain`() {
        val request = MockHttpServletRequest().apply {
            method = "POST"
            requestURI = "/admin-api/test"
            contentType = MediaType.APPLICATION_JSON_VALUE
            setContent("{\"name\":\"yudao\"}".toByteArray())
        }
        var filteredRequest: HttpServletRequest? = null
        var firstRead: String? = null
        var secondRead: String? = null

        CacheRequestBodyFilter().doFilter(request, MockHttpServletResponse(), object : FilterChain {
            override fun doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse) {
                filteredRequest = servletRequest as HttpServletRequest
                firstRead = filteredRequest!!.reader.readText()
                secondRead = filteredRequest!!.reader.readText()
            }
        })

        assertTrue(filteredRequest is CacheRequestBodyWrapper)
        assertEquals("{\"name\":\"yudao\"}", firstRead)
        assertEquals(firstRead, secondRead)
    }

    @Test
    fun `non json and actuator requests are not wrapped`() {
        val filter = CacheRequestBodyFilter()
        val response = MockHttpServletResponse()

        val textRequest = MockHttpServletRequest().apply {
            requestURI = "/admin-api/test"
            contentType = MediaType.TEXT_PLAIN_VALUE
        }
        var textFiltered: ServletRequest? = null
        filter.doFilter(textRequest, response) { request, _ -> textFiltered = request }
        assertSame(textRequest, textFiltered)

        val actuatorRequest = MockHttpServletRequest().apply {
            requestURI = "/actuator/health"
            contentType = MediaType.APPLICATION_JSON_VALUE
        }
        var actuatorFiltered: ServletRequest? = null
        filter.doFilter(actuatorRequest, response) { request, _ -> actuatorFiltered = request }
        assertSame(actuatorRequest, actuatorFiltered)
    }
}
