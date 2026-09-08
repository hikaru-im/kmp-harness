package im.hikaru.ruoyi.framework.web.core.filter

import im.hikaru.ruoyi.framework.common.enums.WebFilterOrderEnum
import im.hikaru.ruoyi.framework.web.config.YudaoWebAutoConfiguration
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.client.RestClient

class DemoFilterTest {

    @Test
    fun `authenticated write request is rejected with demo error`() {
        val request = MockHttpServletRequest().apply {
            method = "POST"
            requestURI = "/admin-api/system/user"
        }
        WebFrameworkUtils.setLoginUserId(request, 1L)
        val response = MockHttpServletResponse()
        var continued = false

        DemoFilter().doFilter(request, response, FilterChain { _, _ -> continued = true })

        assertFalse(continued)
        assertTrue(response.contentAsString.contains("\"code\":901"))
    }

    @Test
    fun `read and anonymous requests continue through the chain`() {
        listOf(
            MockHttpServletRequest().apply {
                method = "GET"
                requestURI = "/admin-api/system/user"
                WebFrameworkUtils.setLoginUserId(this, 1L)
            },
            MockHttpServletRequest().apply {
                method = "DELETE"
                requestURI = "/admin-api/system/user/1"
            },
        ).forEach { request ->
            var continued = false
            DemoFilter().doFilter(
                request,
                MockHttpServletResponse(),
                FilterChain { _, _ -> continued = true },
            )
            assertTrue(continued)
        }
    }

    @Test
    fun `web configuration assigns demo order and creates rest client`() {
        val configuration = YudaoWebAutoConfiguration()
        val builderProvider = StaticListableBeanFactory()
            .getBeanProvider(RestClient.Builder::class.java)

        assertEquals(WebFilterOrderEnum.DEMO_FILTER, configuration.demoFilter().order)
        assertNotNull(configuration.restClient(builderProvider))
    }
}
